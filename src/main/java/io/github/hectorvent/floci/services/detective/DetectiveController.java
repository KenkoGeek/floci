package io.github.hectorvent.floci.services.detective;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class DetectiveController {
    private final ObjectMapper mapper; private final RegionResolver region; private final StorageBackend<String,State> states;
    @Inject public DetectiveController(ObjectMapper mapper,RegionResolver region,StorageFactory f){this.mapper=mapper;this.region=region;this.states=f.create("detective","detective-state.json",new TypeReference<Map<String,State>>(){});}
    private String key(HttpHeaders h){return region.resolveRegion(h)+"::"+region.getAccountId();}
    private State state(HttpHeaders h){return states.get(key(h)).orElseGet(State::new);} private void save(HttpHeaders h,State s){states.put(key(h),s);}
    private String graphArn(HttpHeaders h){return "arn:aws:detective:"+region.resolveRegion(h)+":"+region.getAccountId()+":graph:floci";}
    @GET @Path("/orgs/adminAccountslist") public Response admins(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("Administrators");if(s.admin!=null)a.addObject().put("AccountId",s.admin);return Response.ok(o).build();}
    @POST @Path("/orgs/enableAdminAccount") public Response enableAdmin(@Context HttpHeaders h,String b){State s=state(h);s.admin=req(parse(b),"AccountId");s.graph=true;save(h,s);return ok();}
    @GET @Path("/graphs/list") public Response graphs(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("GraphList");if(s.graph)a.addObject().put("Arn",graphArn(h));return Response.ok(o).build();}
    @GET @Path("/orgs/describeOrganizationConfiguration") public Response describeOrg(@Context HttpHeaders h){var o=mapper.createObjectNode();o.put("AutoEnable",state(h).autoEnable);return Response.ok(o).build();}
    @POST @Path("/orgs/updateOrganizationConfiguration") public Response updateOrg(@Context HttpHeaders h,String b){State s=state(h);JsonNode in=parse(b);s.autoEnable=in.path("AutoEnable").asBoolean(false);save(h,s);return ok();}
    @GET @Path("/graph/members/list") public Response members(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("MemberDetails");s.members.forEach((id,email)->a.addObject().put("AccountId",id).put("EmailAddress",email).put("Status","ENABLED").put("GraphArn",graphArn(h)));return Response.ok(o).build();}
    @POST @Path("/graph/members") public Response createMembers(@Context HttpHeaders h,String b){State s=state(h);JsonNode in=parse(b);JsonNode accts=in.get("Accounts");if(accts!=null&&accts.isArray())for(JsonNode a:accts)s.members.put(a.path("AccountId").asText(),a.path("EmailAddress").asText("member@example.com"));save(h,s);var o=mapper.createObjectNode();o.putArray("Members");o.putArray("UnprocessedAccounts");return Response.ok(o).build();}
    @POST @Path("/graph/member/monitoringstate") public Response monitoring(@Context HttpHeaders h,String b){State s=state(h);String id=req(parse(b),"AccountId");s.members.putIfAbsent(id,"member@example.com");save(h,s);return ok();}
    private JsonNode parse(String b){try{return mapper.readTree(b==null||b.isBlank()?"{}":b);}catch(Exception e){throw new AwsException("ValidationException","Invalid JSON.",400);}}
    private static String req(JsonNode n,String f){if(!n.path(f).isTextual()||n.path(f).asText().isBlank())throw new AwsException("ValidationException",f+" is required.",400);return n.path(f).asText();}
    private Response ok(){return Response.ok(mapper.createObjectNode()).build();}
    public static class State{public String admin;public boolean graph;public boolean autoEnable;public Map<String,String> members=new LinkedHashMap<>();public State(){}}
}
