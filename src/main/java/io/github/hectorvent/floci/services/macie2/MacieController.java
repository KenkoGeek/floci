package io.github.hectorvent.floci.services.macie2;

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
import java.util.Map;

@Path("/") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class MacieController {
    private final ObjectMapper mapper; private final RegionResolver region; private final StorageBackend<String,State> states;
    @Inject public MacieController(ObjectMapper mapper,RegionResolver region,StorageFactory f){this.mapper=mapper;this.region=region;this.states=f.create("macie2","macie2-state.json",new TypeReference<Map<String,State>>(){});}
    private String key(HttpHeaders h){return region.resolveRegion(h)+"::"+region.getAccountId();}
    private State state(HttpHeaders h){return states.get(key(h)).orElse(new State(null,false,false));}
    public Response listAdminInternal(HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("adminAccounts");if(s.admin()!=null)a.addObject().put("accountId",s.admin()).put("status","ENABLED");return Response.ok(o).build();}
    @POST @Path("/admin") public Response enableAdmin(@Context HttpHeaders h,String body){JsonNode in=parse(body);State s=state(h);states.put(key(h),new State(req(in,"adminAccountId"),s.enabled(),s.autoEnable()));return ok();}
    @GET @Path("/macie") public Response get(@Context HttpHeaders h){State s=state(h);if(!s.enabled())throw new AwsException("ResourceNotFoundException","Macie is not enabled.",404);var o=mapper.createObjectNode();o.put("status","ENABLED");o.put("serviceRole","arn:aws:iam::"+region.getAccountId()+":role/aws-service-role/macie.amazonaws.com/AWSServiceRoleForAmazonMacie");return Response.ok(o).build();}
    @POST @Path("/macie") public Response enable(@Context HttpHeaders h,String body){State s=state(h);states.put(key(h),new State(s.admin(),true,s.autoEnable()));return ok();}
    @PATCH @Path("/admin/configuration") public Response updateConfig(@Context HttpHeaders h,String body){JsonNode in=parse(body);State s=state(h);boolean auto=in.has("autoEnable")?in.path("autoEnable").asBoolean():s.autoEnable();states.put(key(h),new State(s.admin(),s.enabled(),auto));return ok();}
    @GET @Path("/admin/configuration") public Response getConfig(@Context HttpHeaders h){var o=mapper.createObjectNode();o.put("autoEnable",state(h).autoEnable());return Response.ok(o).build();}
    private JsonNode parse(String b){try{return mapper.readTree(b==null||b.isBlank()?"{}":b);}catch(Exception e){throw new AwsException("ValidationException","Invalid JSON.",400);}}
    private static String req(JsonNode n,String f){if(!n.path(f).isTextual()||n.path(f).asText().isBlank())throw new AwsException("ValidationException",f+" is required.",400);return n.path(f).asText();}
    private Response ok(){return Response.ok(mapper.createObjectNode()).build();}
    public record State(String admin,boolean enabled,boolean autoEnable){}
}
