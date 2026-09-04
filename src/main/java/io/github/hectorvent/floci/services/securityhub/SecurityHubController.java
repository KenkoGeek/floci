package io.github.hectorvent.floci.services.securityhub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Path("/") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class SecurityHubController {
    private final ObjectMapper mapper; private final RegionResolver region; private final StorageBackend<String,State> states;
    @Inject public SecurityHubController(ObjectMapper mapper,RegionResolver region,StorageFactory f){this.mapper=mapper;this.region=region;this.states=f.create("securityhub","securityhub-state.json",new TypeReference<Map<String,State>>(){});}
    private String key(HttpHeaders h){return region.resolveRegion(h)+"::"+region.getAccountId();}
    private State state(HttpHeaders h){return states.get(key(h)).orElseGet(State::new);}
    private void save(HttpHeaders h,State s){states.put(key(h),s);}

    @GET @Path("/organization/admin") public Response listAdmin(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("AdminAccounts");if(s.adminAccountId!=null)a.addObject().put("AccountId",s.adminAccountId).put("Status","ENABLED");return Response.ok(o).build();}
    @POST @Path("/organization/admin/enable") public Response enableAdmin(@Context HttpHeaders h,String b){State s=state(h);s.adminAccountId=req(parse(b),"AdminAccountId");save(h,s);return ok();}
    @GET @Path("/accounts") public Response describeHub(@Context HttpHeaders h){State s=state(h);if(!s.enabled)throw notFound("Security Hub is not enabled");var o=mapper.createObjectNode();o.put("HubArn",hubArn(h));o.put("AutoEnableControls",true);o.put("ControlFindingGenerator","SECURITY_CONTROL");return Response.ok(o).build();}
    @POST @Path("/accounts") public Response enableHub(@Context HttpHeaders h,String b){State s=state(h);s.enabled=true;save(h,s);var o=mapper.createObjectNode();o.put("HubArn",hubArn(h));return Response.ok(o).build();}
    @GET @Path("/findingAggregator/list") public Response listAggregators(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("FindingAggregators");if(s.aggregatorArn!=null)a.addObject().put("FindingAggregatorArn",s.aggregatorArn);return Response.ok(o).build();}
    @POST @Path("/findingAggregator/create") public Response createAggregator(@Context HttpHeaders h,String b){State s=state(h);if(s.aggregatorArn==null)s.aggregatorArn="arn:aws:securityhub:"+region.resolveRegion(h)+":"+region.getAccountId()+":finding-aggregator/"+shortId();JsonNode in=parse(b);s.regionLinkingMode=text(in,"RegionLinkingMode");s.regions=in.get("Regions");save(h,s);return aggregator(s,h);}
    @GET @Path("/findingAggregator/get/{id}") public Response getAggregator(@Context HttpHeaders h,@PathParam("id") String id){State s=state(h);if(s.aggregatorArn==null)throw notFound("Finding aggregator not found");return aggregator(s,h);}
    @PATCH @Path("/findingAggregator/update") public Response updateAggregator(@Context HttpHeaders h,String b){State s=state(h);if(s.aggregatorArn==null)throw notFound("Finding aggregator not found");JsonNode in=parse(b);if(in.has("RegionLinkingMode"))s.regionLinkingMode=text(in,"RegionLinkingMode");if(in.has("Regions"))s.regions=in.get("Regions");save(h,s);var o=mapper.createObjectNode();o.put("FindingAggregatorArn",s.aggregatorArn);return Response.ok(o).build();}
    @GET @Path("/organization/configuration") public Response getOrg(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();o.put("AutoEnable",false);o.put("AutoEnableStandards","NONE");var c=o.putObject("OrganizationConfiguration");c.put("ConfigurationType",s.central?"CENTRAL":"LOCAL");c.put("Status","ENABLED");return Response.ok(o).build();}
    @POST @Path("/organization/configuration") public Response updateOrg(@Context HttpHeaders h,String b){State s=state(h);s.central=true;save(h,s);return ok();}
    @GET @Path("/configurationPolicy/list") public Response listPolicies(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("ConfigurationPolicySummaries");s.policies.forEach((id,p)->a.add(policySummary(h,id,p)));return Response.ok(o).build();}
    @POST @Path("/configurationPolicy/create") public Response createPolicy(@Context HttpHeaders h,String b){JsonNode in=parse(b);State s=state(h);String id=shortId();s.policies.put(id,in.deepCopy());save(h,s);return Response.ok(policyDocument(h,id,in)).build();}
    @GET @Path("/configurationPolicy/get/{id}") public Response getPolicy(@Context HttpHeaders h,@PathParam("id") String id){State s=state(h);JsonNode p=s.policies.get(id);if(p==null)throw notFound("Configuration policy not found");return Response.ok(policyDocument(h,id,p)).build();}
    @PATCH @Path("/configurationPolicy/{id}") public Response updatePolicy(@Context HttpHeaders h,@PathParam("id") String id,String b){State s=state(h);if(!s.policies.containsKey(id))throw notFound("Configuration policy not found");JsonNode in=parse(b);s.policies.put(id,in.deepCopy());save(h,s);return Response.ok(policyDocument(h,id,in)).build();}
    @POST @Path("/configurationPolicyAssociation/get") public Response getAssociation(@Context HttpHeaders h,String b){State s=state(h);String target=target(parse(b));String policy=s.associations.get(target);if(policy==null)throw notFound("Association not found");return Response.ok(association(target,policy)).build();}
    @POST @Path("/configurationPolicyAssociation/associate") public Response associate(@Context HttpHeaders h,String b){JsonNode in=parse(b);State s=state(h);String target=target(in);String policy=req(in,"ConfigurationPolicyIdentifier");s.associations.put(target,policy);save(h,s);return Response.ok(association(target,policy)).build();}
    @POST @Path("/configurationPolicyAssociation/disassociate") public Response disassociate(@Context HttpHeaders h,String b){State s=state(h);s.associations.remove(target(parse(b)));save(h,s);return ok();}
    @POST @Path("/configurationPolicyAssociation/list") public Response listAssociations(@Context HttpHeaders h,String b){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("ConfigurationPolicyAssociationSummaries");s.associations.forEach((t,p)->a.add(association(t,p)));return Response.ok(o).build();}
    @GET @Path("/tags/{resource: .+}") public Response tags(@PathParam("resource") String resource){var o=mapper.createObjectNode();o.putObject("Tags").put("managed_by","cloud-launchpad");return Response.ok(o).build();}

    private Response aggregator(State s,HttpHeaders h){var o=mapper.createObjectNode();o.put("FindingAggregatorArn",s.aggregatorArn);o.put("FindingAggregationRegion",region.resolveRegion(h));o.put("RegionLinkingMode",s.regionLinkingMode==null?"SPECIFIED_REGIONS":s.regionLinkingMode);if(s.regions!=null)o.set("Regions",s.regions);return Response.ok(o).build();}
    private ObjectNode policySummary(HttpHeaders h,String id,JsonNode p){var o=mapper.createObjectNode();o.put("Arn",policyArn(h,id));o.put("Id",id);o.put("Name",text(p,"Name")==null?"Cloud Launchpad Core":text(p,"Name"));o.put("Description",text(p,"Description")==null?"Cloud Launchpad centralized Security Hub baseline":text(p,"Description"));o.put("UpdatedAt",Instant.now().toString());return o;}
    private ObjectNode policyDocument(HttpHeaders h,String id,JsonNode p){var o=policySummary(h,id,p);JsonNode config=p.get("ConfigurationPolicy");if(config!=null)o.set("ConfigurationPolicy",config);o.put("CreatedAt",Instant.now().toString());return o;}
    private ObjectNode association(String target,String policy){var o=mapper.createObjectNode();o.put("AssociationStatus","SUCCESS");o.put("AssociationType","APPLIED");o.put("ConfigurationPolicyId",policy.substring(policy.lastIndexOf('/')+1));o.put("TargetId",target);o.put("TargetType",target.startsWith("ou-")?"ORGANIZATIONAL_UNIT":target.startsWith("r-")?"ROOT":"ACCOUNT");o.put("UpdatedAt",Instant.now().toString());return o;}
    private String hubArn(HttpHeaders h){return "arn:aws:securityhub:"+region.resolveRegion(h)+":"+region.getAccountId()+":hub/default";}
    private String policyArn(HttpHeaders h,String id){return "arn:aws:securityhub:"+region.resolveRegion(h)+":"+region.getAccountId()+":configuration-policy/"+id;}
    private static String target(JsonNode in){JsonNode t=in.get("Target");if(t==null||!t.isObject())throw new AwsException("ValidationException","Target is required.",400);for(String k:new String[]{"AccountId","OrganizationalUnitId","RootId"})if(t.path(k).isTextual()&&!t.path(k).asText().isBlank())return t.path(k).asText();throw new AwsException("ValidationException","Target identifier is required.",400);}
    private JsonNode parse(String b){try{return mapper.readTree(b==null||b.isBlank()?"{}":b);}catch(Exception e){throw new AwsException("ValidationException","Invalid JSON.",400);}}
    private static String req(JsonNode n,String f){String v=text(n,f);if(v==null||v.isBlank())throw new AwsException("ValidationException",f+" is required.",400);return v;}
    private static String text(JsonNode n,String f){JsonNode v=n==null?null:n.get(f);return v!=null&&v.isTextual()?v.asText():null;}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,16);}
    private static AwsException notFound(String m){return new AwsException("ResourceNotFoundException",m,404);}
    private Response ok(){return Response.ok(mapper.createObjectNode()).build();}
    public static class State {public String adminAccountId;public boolean enabled;public String aggregatorArn;public String regionLinkingMode;public JsonNode regions;public boolean central;public Map<String,JsonNode> policies=new LinkedHashMap<>();public Map<String,String> associations=new LinkedHashMap<>();public State(){}}
}
