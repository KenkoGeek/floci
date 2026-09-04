package io.github.hectorvent.floci.services.budgets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@ApplicationScoped
public class BudgetsJsonHandler {
    private final ObjectMapper mapper;
    private final StorageBackend<String, BudgetRecord> budgets;

    @Inject
    public BudgetsJsonHandler(ObjectMapper mapper, StorageFactory storageFactory) {
        this.mapper=mapper;
        this.budgets=storageFactory.create("budgets","budgets.json",new TypeReference<Map<String,BudgetRecord>>(){});
    }

    public Response handle(String action, JsonNode request) {
        String account=text(request,"AccountId"); String name=budgetName(request);
        String key=(account==null?"":account)+"::"+(name==null?"":name);
        return switch(action) {
            case "DescribeBudget" -> describe(key);
            case "CreateBudget" -> create(key,request);
            case "UpdateBudget" -> update(key,request);
            case "DeleteBudget" -> delete(key);
            case "ListTagsForResource" -> tags(request);
            case "DescribeNotificationsForBudget" -> notifications(key);
            case "DescribeSubscribersForNotification" -> subscribers(key);
            case "CreateNotification" -> createNotification(key,request);
            case "DeleteNotification" -> Response.ok(mapper.createObjectNode()).build();
            default -> throw new AwsException("UnknownOperationException","Operation "+action+" is not supported.",400);
        };
    }
    private Response describe(String key){BudgetRecord r=budgets.get(key).orElseThrow(()->new AwsException("NotFoundException","Budget not found.",404));ObjectNode o=mapper.createObjectNode();o.set("Budget",r.budget());return Response.ok(o).build();}
    private Response create(String key,JsonNode request){if(key.endsWith("::"))throw validation("Budget.BudgetName is required."); JsonNode budget=request.get("Budget"); if(budget==null||!budget.isObject())throw validation("Budget is required."); budgets.put(key,new BudgetRecord(budget.deepCopy(),request.get("ResourceTags"),request.get("NotificationsWithSubscribers")));return ok();}
    private Response update(String key,JsonNode request){BudgetRecord old=budgets.get(key).orElseThrow(()->new AwsException("NotFoundException","Budget not found.",404));JsonNode budget=request.get("NewBudget");if(budget==null)budget=request.get("Budget");budgets.put(key,new BudgetRecord(budget==null?old.budget():budget.deepCopy(),old.tags(),old.notifications()));return ok();}
    private Response delete(String key){budgets.delete(key);return ok();}
    private Response tags(JsonNode request){String arn=text(request,"ResourceARN");BudgetRecord found=budgets.scan(k->true).stream().filter(r->arn==null||arn.endsWith("budget/"+r.budget().path("BudgetName").asText())).findFirst().orElse(null);ObjectNode o=mapper.createObjectNode();o.set("ResourceTags",found==null||found.tags()==null?mapper.createArrayNode():found.tags());return Response.ok(o).build();}
    private Response notifications(String key){BudgetRecord r=budgets.get(key).orElseThrow(()->new AwsException("NotFoundException","Budget not found.",404));ObjectNode o=mapper.createObjectNode();var a=o.putArray("Notifications");if(r.notifications()!=null&&r.notifications().isArray())for(JsonNode n:r.notifications()){JsonNode x=n.get("Notification");if(x!=null)a.add(x);}return Response.ok(o).build();}
    private Response subscribers(String key){BudgetRecord r=budgets.get(key).orElseThrow(()->new AwsException("NotFoundException","Budget not found.",404));ObjectNode o=mapper.createObjectNode();var a=o.putArray("Subscribers");if(r.notifications()!=null&&r.notifications().isArray())for(JsonNode n:r.notifications()){JsonNode x=n.get("Subscribers");if(x!=null&&x.isArray())x.forEach(a::add);}return Response.ok(o).build();}
    private Response createNotification(String key,JsonNode request){BudgetRecord r=budgets.get(key).orElseThrow(()->new AwsException("NotFoundException","Budget not found.",404));com.fasterxml.jackson.databind.node.ArrayNode arr=r.notifications()==null?mapper.createArrayNode():(com.fasterxml.jackson.databind.node.ArrayNode)r.notifications().deepCopy();ObjectNode n=mapper.createObjectNode();n.set("Notification",request.get("Notification"));var subscribers=mapper.createArrayNode();if(request.get("Subscribers")!=null)request.get("Subscribers").forEach(subscribers::add);else if(request.get("Subscriber")!=null)subscribers.add(request.get("Subscriber"));n.set("Subscribers",subscribers);arr.add(n);budgets.put(key,new BudgetRecord(r.budget(),r.tags(),arr));return ok();}
    private Response ok(){return Response.ok(mapper.createObjectNode()).build();}
    private static String budgetName(JsonNode r){JsonNode b=r==null?null:r.get("Budget");if(b!=null&&b.isObject()&&b.path("BudgetName").isTextual())return b.path("BudgetName").asText();String n=text(r,"BudgetName");if(n==null){b=r==null?null:r.get("NewBudget");if(b!=null&&b.path("BudgetName").isTextual())n=b.path("BudgetName").asText();}return n;}
    private static String text(JsonNode r,String f){JsonNode v=r==null?null:r.get(f);return v!=null&&v.isTextual()?v.asText():null;}
    private static AwsException validation(String m){return new AwsException("InvalidParameterException",m,400);}
    public record BudgetRecord(JsonNode budget,JsonNode tags,JsonNode notifications){}
}
