package io.github.hectorvent.floci.services.accessanalyzer;

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
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class AccessAnalyzerController {
    private final ObjectMapper mapper; private final RegionResolver region; private final StorageBackend<String, Analyzer> analyzers;
    @Inject
    public AccessAnalyzerController(ObjectMapper mapper, RegionResolver region, StorageFactory storageFactory) {
        this.mapper=mapper; this.region=region;
        this.analyzers=storageFactory.create("accessanalyzer","accessanalyzer-analyzers.json",new TypeReference<Map<String,Analyzer>>(){});
    }
    @GET @Path("/analyzer")
    public Response list(@Context HttpHeaders headers) {
        String prefix=region.resolveRegion(headers)+"::";
        var out=mapper.createObjectNode(); var arr=out.putArray("analyzers");
        analyzers.scan(k->k.startsWith(prefix)).stream().sorted(Comparator.comparing(Analyzer::name)).forEach(a->{
            var n=arr.addObject(); n.put("arn",a.arn()); n.put("name",a.name()); n.put("type",a.type()); n.put("status","ACTIVE"); n.put("createdAt",a.createdAt());
        }); return Response.ok(out).build();
    }
    @PUT @Path("/analyzer") @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders headers,String body){return createInternal(headers,body);}
    @POST @Path("/analyzer") @Consumes(MediaType.APPLICATION_JSON)
    public Response createPost(@Context HttpHeaders headers,String body){return createInternal(headers,body);}
    private Response createInternal(HttpHeaders headers,String body){
        JsonNode in=parse(body); String name=req(in,"analyzerName"); String type=req(in,"type"); String r=region.resolveRegion(headers); String key=r+"::"+name;
        if(analyzers.get(key).isPresent()) throw new AwsException("ConflictException","Analyzer already exists.",409);
        String arn="arn:aws:access-analyzer:"+r+":"+region.getAccountId()+":analyzer/"+name;
        analyzers.put(key,new Analyzer(name,arn,type,Instant.now().toString())); var out=mapper.createObjectNode(); out.put("arn",arn); return Response.ok(out).build();
    }
    @DELETE @Path("/analyzer/{name}")
    public Response delete(@Context HttpHeaders headers,@PathParam("name") String name){analyzers.delete(region.resolveRegion(headers)+"::"+name);return Response.ok(mapper.createObjectNode()).build();}
    private JsonNode parse(String b){try{return mapper.readTree(b==null||b.isBlank()?"{}":b);}catch(Exception e){throw new AwsException("ValidationException","Invalid JSON.",400);}}
    private static String req(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||!v.isTextual()||v.textValue().isBlank())throw new AwsException("ValidationException",f+" is required.",400);return v.textValue();}
    public record Analyzer(String name,String arn,String type,String createdAt){}
}
