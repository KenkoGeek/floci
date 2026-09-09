package io.github.hectorvent.floci.services.marketplace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Resettable;
import io.github.hectorvent.floci.core.storage.AccountAwareStorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MarketplaceService implements Resettable {
    private static final String CATALOG = "AWSMarketplace";
    private final ObjectMapper mapper;
    private final AccountAwareStorageBackend<JsonNode> entities;
    private final AccountAwareStorageBackend<JsonNode> changeSets;
    private final AccountAwareStorageBackend<JsonNode> resourcePolicies;
    private final AccountAwareStorageBackend<JsonNode> tags;
    private final AccountAwareStorageBackend<JsonNode> assessments;

    @Inject
    public MarketplaceService(StorageFactory factory, ObjectMapper mapper) {
        this(mapper,
                factory.create("marketplace", "marketplace-entities.json", type()),
                factory.create("marketplace", "marketplace-change-sets.json", type()),
                factory.create("marketplace", "marketplace-resource-policies.json", type()),
                factory.create("marketplace", "marketplace-tags.json", type()),
                factory.create("marketplace", "marketplace-assessments.json", type()));
    }

    MarketplaceService(ObjectMapper mapper, AccountAwareStorageBackend<JsonNode> entities,
                       AccountAwareStorageBackend<JsonNode> changeSets,
                       AccountAwareStorageBackend<JsonNode> resourcePolicies,
                       AccountAwareStorageBackend<JsonNode> tags,
                       AccountAwareStorageBackend<JsonNode> assessments) {
        this.mapper = mapper; this.entities = entities; this.changeSets = changeSets;
        this.resourcePolicies = resourcePolicies; this.tags = tags; this.assessments = assessments;
    }

    private static TypeReference<Map<String, JsonNode>> type() { return new TypeReference<>() {}; }

    public ObjectNode batchDescribeEntities(JsonNode request, String region) {
        ArrayNode inputs = requireArray(request, "EntityRequestList");
        if (inputs.size() < 1 || inputs.size() > 20) throw validation("EntityRequestList must contain between 1 and 20 items.");
        completePending(region);
        ObjectNode out = mapper.createObjectNode(); ObjectNode details = out.putObject("EntityDetails"); ObjectNode errors = out.putObject("Errors");
        for (JsonNode input : inputs) {
            String catalog = text(input, "Catalog", true); String id = text(input, "EntityId", true);
            if (!CATALOG.equals(catalog)) { errors.set(id, errorDetail("ValidationException", "Catalog must be AWSMarketplace.")); continue; }
            JsonNode entity = entities.get(id).orElse(null);
            if (entity == null) errors.set(id, errorDetail("ResourceNotFoundException", "Entity not found."));
            else details.set(id, entity.deepCopy());
        }
        return out;
    }

    public ObjectNode startChangeSet(JsonNode request, String region) {
        requireCatalog(text(request, "Catalog", true));
        ArrayNode changes = requireArray(request, "ChangeSet");
        if (changes.isEmpty() || changes.size() > 20) throw validation("ChangeSet must contain between 1 and 20 changes.");
        String token = text(request, "ClientRequestToken", false);
        if (token != null) {
            for (JsonNode existing : changeSets.scan(k -> true)) if (token.equals(existing.path("ClientRequestToken").asText(null))) {
                return ids(existing);
            }
        }
        String id = "cs-" + compactId(); String arn = arn(region, "AWSMarketplace/ChangeSet/" + id);
        ObjectNode cs = mapper.createObjectNode(); cs.put("ChangeSetId", id); cs.put("ChangeSetArn", arn);
        if (request.hasNonNull("ChangeSetName")) cs.put("ChangeSetName", request.path("ChangeSetName").asText());
        cs.put("Intent", request.path("Intent").asText("APPLY")); cs.put("StartTime", Instant.now().toString()); cs.put("Status", "PREPARING");
        cs.set("ChangeSet", normalizeChanges(changes, region));
        if (token != null) cs.put("ClientRequestToken", token);
        changeSets.put(id, cs);
        if (request.path("ChangeSetTags").isArray()) tags.put(arn, request.path("ChangeSetTags").deepCopy());
        return ids(cs);
    }

    public ObjectNode cancelChangeSet(String catalog, String id, String region) {
        requireCatalog(catalog); ObjectNode cs = changeSet(id);
        String status = cs.path("Status").asText();
        if (!"PREPARING".equals(status) && !"APPLYING".equals(status)) throw new AwsException("ResourceInUseException", "Change set cannot be cancelled in status " + status + ".", 423);
        cs.put("Status", "CANCELLED"); cs.put("EndTime", Instant.now().toString()); changeSets.put(id, cs); return ids(cs);
    }

    public ObjectNode describeChangeSet(String catalog, String id, String region) {
        requireCatalog(catalog); ObjectNode cs = changeSet(id);
        if ("PREPARING".equals(cs.path("Status").asText())) { applyChangeSet(cs, region); changeSets.put(id, cs); }
        ObjectNode copy = cs.deepCopy(); copy.remove("ClientRequestToken"); return copy;
    }

    public ObjectNode listChangeSets(JsonNode request, String region) {
        requireCatalog(text(request, "Catalog", true)); completePending(region);
        List<JsonNode> all = new ArrayList<>(changeSets.scan(k -> true)); all.sort(Comparator.comparing(n -> n.path("StartTime").asText(), Comparator.reverseOrder()));
        return page("ChangeSetSummaryList", all.stream().map(this::changeSummary).toList(), request, 20, 1, 100);
    }

    public ObjectNode describeEntity(String catalog, String id, String region) {
        requireCatalog(catalog); completePending(region); JsonNode entity = entities.get(id).orElseThrow(() -> notFound("Entity", id)); return (ObjectNode) entity.deepCopy();
    }

    public ObjectNode listEntities(JsonNode request, String region) {
        requireCatalog(text(request, "Catalog", true)); String type = text(request, "EntityType", true); completePending(region);
        List<JsonNode> list = entities.scan(k -> true).stream().filter(e -> type.equals(e.path("EntityType").asText())).sorted(Comparator.comparing(e -> e.path("EntityId").asText())).map(this::entitySummary).toList();
        return page("EntitySummaryList", list, request, 20, 1, 50);
    }

    public ObjectNode catalogListTags(JsonNode request) {
        String arn = text(request, "ResourceArn", true); ensureResource(arn); ObjectNode out = mapper.createObjectNode(); out.put("ResourceArn", arn); out.set("Tags", tags.get(arn).map(n -> (JsonNode) n.deepCopy()).orElseGet(mapper::createArrayNode)); return out;
    }
    public ObjectNode catalogTagResource(JsonNode request) {
        String arn = text(request, "ResourceArn", true); ensureResource(arn); ArrayNode incoming = requireArray(request, "Tags");
        Map<String,String> merged = new java.util.LinkedHashMap<>(); JsonNode old = tags.get(arn).orElse(null); if (old != null) old.forEach(t -> merged.put(t.path("Key").asText(), t.path("Value").asText())); incoming.forEach(t -> { String k=text(t,"Key",true); merged.put(k,text(t,"Value",true)); }); if (merged.size()>50) throw validation("A resource can have at most 50 tags.");
        ArrayNode out = mapper.createArrayNode(); merged.forEach((k,v)->out.addObject().put("Key",k).put("Value",v)); tags.put(arn,out); return mapper.createObjectNode();
    }
    public ObjectNode catalogUntagResource(JsonNode request) {
        String arn=text(request,"ResourceArn",true); ensureResource(arn); ArrayNode keys=requireArray(request,"TagKeys"); java.util.Set<String> remove=new java.util.HashSet<>(); keys.forEach(k->remove.add(k.asText())); ArrayNode out=mapper.createArrayNode(); tags.get(arn).ifPresent(old->old.forEach(t->{ if(!remove.contains(t.path("Key").asText())) out.add(t.deepCopy()); })); tags.put(arn,out); return mapper.createObjectNode();
    }

    public ObjectNode putResourcePolicy(JsonNode request) {
        String arn=text(request,"ResourceArn",true); ensureResource(arn); String policy=text(request,"Policy",true); try { mapper.readTree(policy); } catch(Exception e){ throw validation("Policy must be valid JSON."); } resourcePolicies.put(arn, mapper.getNodeFactory().textNode(policy)); return mapper.createObjectNode();
    }
    public ObjectNode getResourcePolicy(String arn) { if (arn==null||arn.isBlank()) throw validation("ResourceArn is required."); ensureResource(arn); JsonNode p=resourcePolicies.get(arn).orElseThrow(()->notFound("Resource policy",arn)); return mapper.createObjectNode().put("Policy",p.asText()); }
    public ObjectNode deleteResourcePolicy(String arn) { if (arn==null||arn.isBlank()) throw validation("ResourceArn is required."); ensureResource(arn); if(resourcePolicies.get(arn).isEmpty()) throw notFound("Resource policy",arn); resourcePolicies.delete(arn); return mapper.createObjectNode(); }

    public ObjectNode listAssessments(JsonNode request, String region) {
        requireCatalog(text(request,"Catalog",true)); List<JsonNode> list=assessments.scan(k->true).stream().sorted(Comparator.comparing(n->n.path("CreatedAt").asText(),Comparator.reverseOrder())).map(this::assessmentSummary).toList(); return page("AssessmentSummaryList",list,request,20,1,100);
    }
    public ObjectNode describeAssessment(JsonNode request, String region) {
        requireCatalog(text(request,"Catalog",true)); String id=text(request,"AssessmentIdentifier",true); JsonNode a=assessments.get(id).orElse(null); if(a==null && id.contains("/")) a=assessments.get(id.substring(id.lastIndexOf('/')+1)).orElse(null); if(a==null) throw notFound("Assessment",id); return (ObjectNode)a.deepCopy();
    }

    private ArrayNode normalizeChanges(ArrayNode changes, String region) {
        ArrayNode out=mapper.createArrayNode(); for(JsonNode raw:changes){ ObjectNode c=raw.deepCopy(); text(c,"ChangeType",true); JsonNode entity=c.path("Entity"); if(!entity.isObject()) throw validation("Each change requires Entity."); String type=text(entity,"Type",true); String identifier=text(entity,"Identifier",false); if(identifier==null||identifier.isBlank()||identifier.equals("@1")){ identifier=idForType(type); ((ObjectNode)entity).put("Identifier",identifier); } c.put("EntityArn",arn(region,"AWSMarketplace/"+type+"/"+identifier)); out.add(c); } return out;
    }
    private void applyChangeSet(ObjectNode cs,String region){ cs.put("Status","APPLYING"); for(JsonNode c:cs.path("ChangeSet")){ String changeType=c.path("ChangeType").asText(); JsonNode ref=c.path("Entity"); String type=ref.path("Type").asText(); String id=ref.path("Identifier").asText(); if(changeType.toLowerCase(Locale.ROOT).startsWith("delete")){ entities.delete(id); continue; } ObjectNode e=entities.get(id).filter(JsonNode::isObject).map(n->(ObjectNode)n.deepCopy()).orElseGet(mapper::createObjectNode); e.put("EntityType",type); e.put("EntityIdentifier",id); e.put("EntityId",id); e.put("EntityArn",arn(region,"AWSMarketplace/"+type+"/"+id)); e.put("LastModifiedDate",Instant.now().toString()); JsonNode d=c.get("DetailsDocument"); if(d!=null&&!d.isNull()) e.set("DetailsDocument",d.deepCopy()); JsonNode legacy=c.get("Details"); if(legacy!=null&&!legacy.isNull()){ e.put("Details",legacy.asText()); if(!e.has("DetailsDocument")){ try{e.set("DetailsDocument",mapper.readTree(legacy.asText()));}catch(Exception ignored){ /* Legacy Details is allowed to be an opaque string. */ } } } entities.put(id,e); } cs.put("Status","SUCCEEDED"); cs.put("EndTime",Instant.now().toString()); }
    private void completePending(String region){ for(String k:changeSets.keys()){ JsonNode n=changeSets.get(k).orElse(null); if(n instanceof ObjectNode o && "PREPARING".equals(o.path("Status").asText())){ applyChangeSet(o,region); changeSets.put(k,o); } } }
    private ObjectNode ids(JsonNode cs){ return mapper.createObjectNode().put("ChangeSetId",cs.path("ChangeSetId").asText()).put("ChangeSetArn",cs.path("ChangeSetArn").asText()); }
    private ObjectNode changeSet(String id){ if(id==null||id.isBlank()) throw validation("ChangeSetId is required."); JsonNode n=changeSets.get(id).orElseThrow(()->notFound("Change set",id)); return (ObjectNode)n.deepCopy(); }
    private JsonNode changeSummary(JsonNode cs){ ObjectNode n=mapper.createObjectNode(); for(String f:List.of("ChangeSetId","ChangeSetArn","ChangeSetName","Intent","StartTime","EndTime","Status")) if(cs.has(f))n.set(f,cs.get(f)); return n; }
    private JsonNode entitySummary(JsonNode e){ ObjectNode n=mapper.createObjectNode(); n.put("Name",e.path("EntityIdentifier").asText()); n.put("EntityType",e.path("EntityType").asText()); n.put("EntityId",e.path("EntityId").asText()); n.put("EntityArn",e.path("EntityArn").asText()); n.put("LastModifiedDate",e.path("LastModifiedDate").asText()); return n; }
    private JsonNode assessmentSummary(JsonNode a){ ObjectNode n=mapper.createObjectNode(); for(String f:List.of("AssessmentArn","AssessmentId","FrameworkId","AssessmentResult","CreatedAt","ExpiresAt"))if(a.has(f))n.set(f,a.get(f)); return n; }
    private ObjectNode page(String name,List<JsonNode> values,JsonNode req,int def,int min,int max){ int size=req.path("MaxResults").isInt()?req.path("MaxResults").asInt():def; if(size<min||size>max)throw validation("MaxResults is out of range."); int off=decodeToken(req.path("NextToken").asText(null)); if(off>values.size())throw validation("NextToken is invalid."); int end=Math.min(values.size(),off+size); ObjectNode out=mapper.createObjectNode(); ArrayNode a=out.putArray(name); values.subList(off,end).forEach(v->a.add(v.deepCopy())); if(end<values.size())out.put("NextToken",Integer.toString(end)); return out; }
    private static int decodeToken(String token){ if(token==null||token.isBlank())return 0; try{int i=Integer.parseInt(token);if(i<0)throw new NumberFormatException();return i;}catch(Exception e){throw validation("NextToken is invalid.");} }
    private ObjectNode errorDetail(String code,String message){return mapper.createObjectNode().put("ErrorCode",code).put("ErrorMessage",message);}
    private void ensureResource(String arn){ boolean exists=entities.scan(k->true).stream().anyMatch(e->arn.equals(e.path("EntityArn").asText()))||changeSets.scan(k->true).stream().anyMatch(c->arn.equals(c.path("ChangeSetArn").asText())); if(!exists)throw notFound("Resource",arn); }
    private static ArrayNode requireArray(JsonNode n,String field){ JsonNode v=n.get(field); if(v==null||!v.isArray())throw validation(field+" is required and must be an array."); return (ArrayNode)v; }
    private static String text(JsonNode n,String field,boolean required){ JsonNode v=n==null?null:n.get(field); if(v==null||v.isNull()||!v.isTextual()||v.asText().isBlank()){if(required)throw validation(field+" is required.");return null;} return v.asText(); }
    private static void requireCatalog(String c){ if(c==null||c.isBlank())throw validation("Catalog is required."); if(!CATALOG.equals(c))throw validation("Catalog must be AWSMarketplace."); }
    private static String idForType(String type){String prefix=type.toLowerCase(Locale.ROOT).contains("offer")?"offer-":"prod-";return prefix+compactId();}
    private static String compactId(){return UUID.randomUUID().toString().replace("-","").substring(0,16);}
    private static String arn(String region,String resource){return "arn:aws:aws-marketplace:"+region+"::"+resource;}
    private static AwsException validation(String msg){return new AwsException("ValidationException",msg,422);}
    private static AwsException notFound(String kind,String id){return new AwsException("ResourceNotFoundException",kind+" "+id+" was not found.",404);}

    @Override public void clear(){entities.clear();changeSets.clear();resourcePolicies.clear();tags.clear();assessments.clear();}
}
