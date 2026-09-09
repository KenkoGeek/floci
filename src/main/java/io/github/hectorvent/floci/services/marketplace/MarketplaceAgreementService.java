package io.github.hectorvent.floci.services.marketplace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Resettable;
import io.github.hectorvent.floci.core.common.RequestContext;
import io.github.hectorvent.floci.core.storage.AccountAwareStorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MarketplaceAgreementService implements Resettable {
    private final ObjectMapper mapper;
    private final AccountAwareStorageBackend<JsonNode> agreements;
    private final AccountAwareStorageBackend<JsonNode> agreementRequests;
    private final AccountAwareStorageBackend<JsonNode> cancellationRequests;
    private final AccountAwareStorageBackend<JsonNode> paymentRequests;
    private final AccountAwareStorageBackend<JsonNode> billingAdjustments;
    private final RequestContext requestContext;

    @Inject
    public MarketplaceAgreementService(StorageFactory factory, ObjectMapper mapper, RequestContext requestContext) {
        this.mapper = mapper;
        this.requestContext = requestContext;
        this.agreements = factory.create("marketplace", "marketplace-agreements.json", type());
        this.agreementRequests = factory.create("marketplace", "marketplace-agreement-requests.json", type());
        this.cancellationRequests = factory.create("marketplace", "marketplace-cancellation-requests.json", type());
        this.paymentRequests = factory.create("marketplace", "marketplace-payment-requests.json", type());
        this.billingAdjustments = factory.create("marketplace", "marketplace-billing-adjustments.json", type());
    }

    private static TypeReference<Map<String, JsonNode>> type() { return new TypeReference<>() {}; }

    public JsonNode handle(String action, JsonNode request, String region) {
        return switch (action) {
            case "AcceptAgreementCancellationRequest" -> acceptCancellation(request);
            case "AcceptAgreementPaymentRequest" -> transitionPayment(request, "ACCEPTED", null);
            case "AcceptAgreementRequest" -> acceptAgreementRequest(request);
            case "BatchCreateBillingAdjustmentRequest" -> batchCreateBillingAdjustments(request);
            case "CancelAgreement" -> cancelAgreement(request);
            case "CancelAgreementCancellationRequest" -> transitionCancellation(request, "CANCELLED", text(request, "cancellationReason", true));
            case "CancelAgreementPaymentRequest" -> transitionPayment(request, "CANCELLED", null);
            case "CreateAgreementRequest" -> createAgreementRequest(request);
            case "DescribeAgreement" -> describeAgreement(request);
            case "GetAgreementCancellationRequest" -> getCancellation(request);
            case "GetAgreementEntitlements" -> getAgreementEntitlements(request);
            case "GetAgreementPaymentRequest" -> getPayment(request);
            case "GetAgreementTerms" -> getAgreementTerms(request);
            case "GetBillingAdjustmentRequest" -> getBillingAdjustment(request);
            case "ListAgreementCancellationRequests" -> listCancellations(request);
            case "ListAgreementCharges" -> listAgreementCharges(request);
            case "ListAgreementInvoiceLineItems" -> listInvoiceLineItems(request);
            case "ListAgreementPaymentRequests" -> listPayments(request);
            case "ListBillingAdjustmentRequests" -> listBillingAdjustments(request);
            case "RejectAgreementCancellationRequest" -> transitionCancellation(request, "REJECTED", text(request, "rejectionReason", true));
            case "RejectAgreementPaymentRequest" -> transitionPayment(request, "REJECTED", null);
            case "SearchAgreements" -> searchAgreements(request);
            case "SendAgreementCancellationRequest" -> sendCancellation(request);
            case "SendAgreementPaymentRequest" -> sendPayment(request);
            case "UpdatePurchaseOrders" -> updatePurchaseOrders(request);
            default -> null;
        };
    }

    private ObjectNode createAgreementRequest(JsonNode request) {
        String intent = text(request, "intent", true);
        if (!List.of("NEW", "AMEND", "REPLACE").contains(intent)) {
            throw validation("intent must be NEW, AMEND, or REPLACE.");
        }
        ArrayNode terms = array(request, "requestedTerms", true);
        if (terms.isEmpty() || terms.size() > 30) {
            throw validation("requestedTerms must contain between 1 and 30 terms.");
        }
        String source = text(request, "sourceAgreementIdentifier", false);
        String proposal = text(request, "agreementProposalIdentifier", false);
        if ("NEW".equals(intent) && source != null) {
            throw validation("sourceAgreementIdentifier must not be provided for NEW intent.");
        }
        if (!"NEW".equals(intent) && source == null) {
            throw validation("sourceAgreementIdentifier is required for AMEND and REPLACE intents.");
        }
        if (!"AMEND".equals(intent) && proposal == null) {
            throw validation("agreementProposalIdentifier is required for NEW and REPLACE intents.");
        }
        if (source != null) {
            requireAgreement(source);
        }
        String token = text(request, "clientToken", false);
        if (token != null) {
            for (JsonNode existing : agreementRequests.scan(k -> true)) {
                if (token.equals(existing.path("clientToken").asText(null))) {
                    return createAgreementRequestResponse(existing);
                }
            }
        }
        String id = "ar-" + compactId(); ObjectNode record = mapper.createObjectNode();
        record.put("agreementRequestId", id); record.put("intent", intent); record.set("requestedTerms", terms.deepCopy());
        if (source != null) {
            record.put("sourceAgreementIdentifier", source);
        } if (proposal != null) {
            record.put("agreementProposalIdentifier", proposal);
        }
        if (token != null) {
            record.put("clientToken", token);
        } record.put("createdAt", epoch()); record.put("status", "PENDING");
        agreementRequests.put(id, record); return createAgreementRequestResponse(record);
    }

    private ObjectNode createAgreementRequestResponse(JsonNode record) {
        ObjectNode out = mapper.createObjectNode().put("agreementRequestId", record.path("agreementRequestId").asText());
        ObjectNode charges = out.putObject("chargeSummary"); charges.put("currencyCode", "USD"); charges.put("newAgreementValue", "0"); charges.putArray("expectedCharges"); charges.putArray("itemizedCharges"); return out;
    }

    private ObjectNode acceptAgreementRequest(JsonNode request) {
        String requestId = text(request, "agreementRequestId", true); ObjectNode ar = object(agreementRequests, requestId, "Agreement request");
        if ("ACCEPTED".equals(ar.path("status").asText())) {
            return mapper.createObjectNode().put("agreementId", ar.path("agreementId").asText());
        }
        String source = ar.path("sourceAgreementIdentifier").asText(null); String intent = ar.path("intent").asText();
        String id = "AMEND".equals(intent) && source != null ? source : "agr-" + compactId();
        ObjectNode agreement = source == null ? mapper.createObjectNode() : agreements.get(source).filter(JsonNode::isObject).map(n -> (ObjectNode)n.deepCopy()).orElseGet(mapper::createObjectNode);
        agreement.put("agreementId", id); agreement.put("agreementType", "PurchaseAgreement"); agreement.put("status", "ACTIVE");
        agreement.put("acceptanceTime", epoch()); agreement.put("startTime", epoch()); agreement.put("endTime", epoch() + 31536000d);
        String accountId = requestContext.getAccountId() == null ? "000000000000" : requestContext.getAccountId();
        agreement.putObject("acceptor").put("accountId", accountId); agreement.putObject("proposer").put("accountId", accountId);
        ObjectNode proposal = agreement.putObject("proposalSummary"); proposal.putArray("resources");
        if (ar.has("agreementProposalIdentifier")) {
            proposal.put("offerId", ar.path("agreementProposalIdentifier").asText());
        }
        agreement.set("acceptedTerms", ar.path("requestedTerms").deepCopy()); agreement.set("purchaseOrders", request.path("purchaseOrders").deepCopy());
        agreements.put(id, agreement); if ("REPLACE".equals(intent) && source != null) { ObjectNode old=requireAgreement(source); old.put("status","REPLACED"); agreements.put(source,old); }
        ar.put("status", "ACCEPTED"); ar.put("agreementId", id); agreementRequests.put(requestId, ar); return mapper.createObjectNode().put("agreementId", id);
    }

    private ObjectNode describeAgreement(JsonNode request) {
        ObjectNode a = requireAgreement(text(request, "agreementId", true)); ObjectNode out=a.deepCopy(); out.remove(List.of("acceptedTerms","purchaseOrders","agreementEntitlements")); return out;
    }

    private ObjectNode cancelAgreement(JsonNode request) { String id=text(request,"agreementId",true); ObjectNode a=requireAgreement(id); a.put("status","CANCELLED"); a.put("endTime",epoch()); agreements.put(id,a); return mapper.createObjectNode(); }

    private ObjectNode getAgreementTerms(JsonNode request) { ObjectNode a=requireAgreement(text(request,"agreementId",true)); return page("acceptedTerms", nodes(a.path("acceptedTerms")), request, 50); }
    private ObjectNode getAgreementEntitlements(JsonNode request) { ObjectNode a=requireAgreement(text(request,"agreementId",true)); return page("agreementEntitlements", nodes(a.path("agreementEntitlements")), request, 50); }

    private ObjectNode searchAgreements(JsonNode request) {
        List<JsonNode> found=new ArrayList<>(); for(JsonNode a:agreements.scan(k->true)) if(matchesAgreementFilters(a, request.path("filters"))) {
            found.add(agreementSummary(a));
        }
        found.sort(Comparator.comparing(n->n.path("startTime").asDouble(0))); if("DESCENDING".equals(request.path("sort").path("sortOrder").asText())) {
            java.util.Collections.reverse(found);
        }
        return page("agreementViewSummaries",found,request,50);
    }

    private boolean matchesAgreementFilters(JsonNode agreement, JsonNode filters) {
        if (!filters.isArray()) {
            return true;
        }
        for (JsonNode filter : filters) {
            String name = filter.path("name").asText();
            JsonNode values = filter.path("values");
            if (!values.isArray() || values.isEmpty()) {
                continue;
            }
            boolean any = false;
            for (JsonNode value : values) {
                String wanted = value.asText();
                if (matchesAgreementFilter(agreement, name, wanted)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAgreementFilter(JsonNode agreement, String name, String wanted) {
        if ("AgreementType".equals(name)) {
            return wanted.equals(agreement.path("agreementType").asText());
        }
        if ("Status".equals(name)) {
            return wanted.equals(agreement.path("status").asText());
        }
        if ("AcceptorAccountId".equals(name)) {
            return wanted.equals(agreement.path("acceptor").path("accountId").asText());
        }
        if ("ProposerAccountId".equals(name)) {
            return wanted.equals(agreement.path("proposer").path("accountId").asText());
        }
        if ("OfferId".equals(name)) {
            return wanted.equals(agreement.path("proposalSummary").path("offerId").asText());
        }
        if ("ResourceId".equals(name)) {
            return resourceMatches(agreement, wanted);
        }
        return false;
    }

    private static boolean resourceMatches(JsonNode agreement, String resourceId) {
        JsonNode resources = agreement.path("proposalSummary").path("resources");
        if (!resources.isArray()) {
            return false;
        }
        for (JsonNode resource : resources) {
            if (resourceId.equals(resource.path("id").asText())) {
                return true;
            }
        }
        return false;
    }
    private ObjectNode agreementSummary(JsonNode a){ObjectNode out=mapper.createObjectNode();for(String f:List.of("agreementId","agreementType","startTime","endTime","status"))if(a.has(f)){
        out.set(f,a.get(f));
    }copy(a,out,"acceptor");copy(a,out,"proposer");copy(a,out,"proposalSummary");return out;}

    private ObjectNode sendCancellation(JsonNode request) {
        String agreementId=text(request,"agreementId",true);requireAgreement(agreementId);String id="acr-"+compactId();ObjectNode r=mapper.createObjectNode().put("agreementCancellationRequestId",id).put("agreementId",agreementId).put("reasonCode",text(request,"reasonCode",true)).put("status","PENDING").put("createdAt",epoch()).put("updatedAt",epoch());if(request.hasNonNull("description")){
            r.put("description",request.path("description").asText());
        }cancellationRequests.put(id,r);return r.deepCopy();
    }
    private ObjectNode getCancellation(JsonNode request){String agreementId=text(request,"agreementId",true);String id=text(request,"agreementCancellationRequestId",true);ObjectNode r=object(cancellationRequests,id,"Agreement cancellation request");if(!agreementId.equals(r.path("agreementId").asText())){
        throw notFound("Agreement cancellation request",id);
    }return r;}
    private ObjectNode acceptCancellation(JsonNode request){ObjectNode r=transitionCancellation(request,"ACCEPTED",null);ObjectNode a=requireAgreement(r.path("agreementId").asText());a.put("status","CANCELLED");a.put("endTime",epoch());agreements.put(a.path("agreementId").asText(),a);return r;}
    private ObjectNode transitionCancellation(JsonNode request,String status,String statusMessage){ObjectNode r=getCancellation(request);r.put("status",status);r.put("updatedAt",epoch());if(statusMessage!=null){
        r.put("statusMessage",statusMessage);
    }cancellationRequests.put(r.path("agreementCancellationRequestId").asText(),r);return r.deepCopy();}
    private ObjectNode listCancellations(JsonNode request){text(request,"partyType",true);List<JsonNode> list=cancellationRequests.scan(k->true).stream().sorted(Comparator.comparing(n->n.path("createdAt").asDouble(),Comparator.reverseOrder())).toList();return page("items",list,request,50);}

    private ObjectNode sendPayment(JsonNode request){String agreementId=text(request,"agreementId",true);requireAgreement(agreementId);text(request,"termId",true);String id="pr-"+compactId();ObjectNode r=mapper.createObjectNode().put("paymentRequestId",id).put("agreementId",agreementId).put("status","PENDING").put("name",text(request,"name",true)).put("chargeAmount",text(request,"chargeAmount",true)).put("currencyCode",request.path("currencyCode").asText("USD")).put("createdAt",epoch());if(request.hasNonNull("description")){
        r.put("description",request.path("description").asText());
    }paymentRequests.put(id,r);return r.deepCopy();}
    private ObjectNode getPayment(JsonNode request){String agreementId=text(request,"agreementId",true);String id=text(request,"paymentRequestId",true);ObjectNode r=object(paymentRequests,id,"Agreement payment request");if(!agreementId.equals(r.path("agreementId").asText())){
        throw notFound("Agreement payment request",id);
    }return r;}
    private ObjectNode transitionPayment(JsonNode request,String status,String message){ObjectNode r=getPayment(request);r.put("status",status);r.put("updatedAt",epoch());if(message!=null){
        r.put("statusMessage",message);
    }paymentRequests.put(r.path("paymentRequestId").asText(),r);return r.deepCopy();}
    private ObjectNode listPayments(JsonNode request){text(request,"partyType",true);List<JsonNode> list=paymentRequests.scan(k->true).stream().sorted(Comparator.comparing(n->n.path("createdAt").asDouble(),Comparator.reverseOrder())).toList();return page("items",list,request,50);}

    private ObjectNode batchCreateBillingAdjustments(JsonNode request){ArrayNode entries=array(request,"billingAdjustmentRequestEntries",true);if(entries.isEmpty()||entries.size()>25){
        throw validation("billingAdjustmentRequestEntries must contain between 1 and 25 entries.");
    }ObjectNode out=mapper.createObjectNode();ArrayNode items=out.putArray("items");out.putArray("errors");for(JsonNode e:entries){String agreementId=text(e,"agreementId",true);requireAgreement(agreementId);String id="bar-"+compactId();ObjectNode r=mapper.createObjectNode().put("billingAdjustmentRequestId",id).put("agreementId",agreementId).put("status","PENDING").put("adjustmentReasonCode",e.path("adjustmentReasonCode").asText("OTHER")).put("adjustmentAmount",e.path("adjustmentAmount").asText("0")).put("currencyCode",e.path("currencyCode").asText("USD")).put("createdAt",epoch()).put("updatedAt",epoch());if(e.has("description")){
        r.set("description",e.get("description"));
    }billingAdjustments.put(id,r);items.add(r.deepCopy());}return out;}
    private ObjectNode getBillingAdjustment(JsonNode request){String agreementId=text(request,"agreementId",true);String id=text(request,"billingAdjustmentRequestId",true);ObjectNode r=object(billingAdjustments,id,"Billing adjustment request");if(!agreementId.equals(r.path("agreementId").asText())){
        throw notFound("Billing adjustment request",id);
    }return r;}
    private ObjectNode listBillingAdjustments(JsonNode request){return page("items",billingAdjustments.scan(k->true),request,50);}

    private ObjectNode listAgreementCharges(JsonNode request){List<JsonNode> list=new ArrayList<>();for(JsonNode p:paymentRequests.scan(k->true)){if(request.hasNonNull("agreementId")&&!request.path("agreementId").asText().equals(p.path("agreementId").asText())){
        continue;
    }ObjectNode charge=mapper.createObjectNode().put("agreementId",p.path("agreementId").asText()).put("chargeId",p.path("paymentRequestId").asText()).put("chargeAmount",p.path("chargeAmount").asText()).put("currencyCode",p.path("currencyCode").asText("USD"));list.add(charge);}return page("items",list,request,50);}
    private ObjectNode listInvoiceLineItems(JsonNode request){String agreementId=text(request,"agreementId",true);requireAgreement(agreementId);text(request,"groupBy",true);return page("agreementInvoiceLineItemGroupSummaries",List.of(),request,50);}
    private ObjectNode updatePurchaseOrders(JsonNode request){ArrayNode pos=array(request,"purchaseOrders",true);for(JsonNode po:pos){String agreementId=text(po,"agreementId",true);ObjectNode a=requireAgreement(agreementId);ArrayNode existing=a.withArray("purchaseOrders");existing.add(po.deepCopy());agreements.put(agreementId,a);}return mapper.createObjectNode();}

    private ObjectNode page(String field,List<JsonNode> values,JsonNode request,int defaultSize){int max=request.path("maxResults").isInt()?request.path("maxResults").asInt():defaultSize;if(max<1||max>100){
        throw validation("maxResults must be between 1 and 100.");
    }int offset=token(request.path("nextToken").asText(null));if(offset>values.size()){
        throw validation("nextToken is invalid.");
    }int end=Math.min(values.size(),offset+max);ObjectNode out=mapper.createObjectNode();ArrayNode a=out.putArray(field);values.subList(offset,end).forEach(n->a.add(n.deepCopy()));if(end<values.size()){
        out.put("nextToken",Integer.toString(end));
    }return out;}
    private static int token(String value){if(value==null||value.isBlank()){
        return 0;
    }try{int n=Integer.parseInt(value);if(n<0){
        throw new NumberFormatException();
    }return n;}catch(NumberFormatException e){throw validation("nextToken is invalid.");}}
    private ObjectNode requireAgreement(String id){return object(agreements,id,"Agreement");}
    private static ObjectNode object(AccountAwareStorageBackend<JsonNode> store,String id,String kind){if(id==null||id.isBlank()){
        throw validation(kind+" identifier is required.");
    }JsonNode n=store.get(id).orElseThrow(()->notFound(kind,id));if(!n.isObject()){
        throw new AwsException("InternalServerException","Stored "+kind+" is invalid.",500);
    }return (ObjectNode)n.deepCopy();}
    private static ArrayNode array(JsonNode request,String field,boolean required){JsonNode n=request.get(field);if(n==null||n.isNull()){if(required){
        throw validation(field+" is required.");
    }return new ObjectMapper().createArrayNode();}if(!n.isArray()){
        throw validation(field+" must be an array.");
    }return (ArrayNode)n;}
    private static String text(JsonNode request,String field,boolean required){JsonNode n=request==null?null:request.get(field);if(n==null||n.isNull()||!n.isTextual()||n.asText().isBlank()){if(required){
        throw validation(field+" is required.");
    }return null;}return n.asText();}
    private static List<JsonNode> nodes(JsonNode node){List<JsonNode> out=new ArrayList<>();if(node!=null&&node.isArray()){
        node.forEach(out::add);
    }return out;}
    private static void copy(JsonNode from,ObjectNode to,String field){if(from.has(field)){
        to.set(field,from.get(field).deepCopy());
    }}
    private static double epoch(){return Instant.now().toEpochMilli()/1000.0;}
    private static String compactId(){return UUID.randomUUID().toString().replace("-","").substring(0,20);}
    private static AwsException validation(String message){return new AwsException("ValidationException",message,400);}
    private static AwsException notFound(String kind,String id){return new AwsException("ResourceNotFoundException",kind+" "+id+" does not exist.",400);}
    @Override public void clear(){agreements.clear();agreementRequests.clear();cancellationRequests.clear();paymentRequests.clear();billingAdjustments.clear();}
}
