package io.github.hectorvent.floci.services.inspector2;

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
public class Inspector2Controller {
    private final ObjectMapper mapper; private final RegionResolver region; private final StorageBackend<String, State> states;
    @Inject public Inspector2Controller(ObjectMapper mapper, RegionResolver region, StorageFactory f) {
        this.mapper=mapper; this.region=region;
        this.states=f.create("inspector2","inspector2-state.json",new TypeReference<Map<String,State>>(){});
    }
    private String key(HttpHeaders h){return region.resolveRegion(h)+"::"+region.getAccountId();}
    private State state(HttpHeaders h){return states.get(key(h)).orElse(new State(null,false,false));}
    private void save(HttpHeaders h,State s){states.put(key(h),s);}
    @GET @Path("/delegatedadminaccounts/list") public Response listAdmins(@Context HttpHeaders h){State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("delegatedAdminAccounts");if(s.admin()!=null)a.addObject().put("accountId",s.admin()).put("status","ENABLED");return Response.ok(o).build();}
    @POST @Path("/delegatedadminaccounts/enable") public Response enableAdmin(@Context HttpHeaders h,String b){State s=state(h);save(h,new State(req(parse(b),"delegatedAdminAccountId"),s.enabled(),s.orgConfigured()));return ok();}
    @POST @Path("/status/batch/get") public Response batchStatus(@Context HttpHeaders h,String b){JsonNode in=parse(b);State s=state(h);var o=mapper.createObjectNode();var a=o.putArray("accounts");JsonNode ids=in.get("accountIds");if(ids!=null&&ids.isArray())for(JsonNode id:ids){var n=a.addObject();n.put("accountId",id.asText());n.putObject("state").put("status",s.enabled()?"ENABLED":"DISABLED");n.putObject("resourceState");}o.putArray("failedAccounts");return Response.ok(o).build();}
    @POST @Path("/enable") public Response enable(@Context HttpHeaders h,String b){State s=state(h);save(h,new State(s.admin(),true,s.orgConfigured()));var o=mapper.createObjectNode();o.putArray("accounts");o.putArray("failedAccounts");return Response.ok(o).build();}
    @POST @Path("/organizationconfiguration/update") public Response updateOrg(@Context HttpHeaders h,String b){State s=state(h);save(h,new State(s.admin(),s.enabled(),true));return ok();}
    @GET @Path("/organizationconfiguration/describe") public Response describeOrg(@Context HttpHeaders h){var o=mapper.createObjectNode();var a=o.putObject("autoEnable");a.put("ec2",true);a.put("ecr",true);a.put("lambda",true);a.put("lambdaCode",true);o.put("maxAccountLimitReached",false);return Response.ok(o).build();}
    private JsonNode parse(String b){try{return mapper.readTree(b==null||b.isBlank()?"{}":b);}catch(Exception e){throw new AwsException("ValidationException","Invalid JSON.",400);}}
    private static String req(JsonNode n,String f){if(!n.path(f).isTextual()||n.path(f).asText().isBlank())throw new AwsException("ValidationException",f+" is required.",400);return n.path(f).asText();}
    private Response ok(){return Response.ok(mapper.createObjectNode()).build();}
    public record State(String admin,boolean enabled,boolean orgConfigured){}
}
