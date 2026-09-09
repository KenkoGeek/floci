package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsErrorResponse;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BedrockAgentCoreToolsController {

    private static final Logger LOG = Logger.getLogger(BedrockAgentCoreToolsController.class);

    private final BedrockAgentCoreToolsService service;
    private final RegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Inject
    public BedrockAgentCoreToolsController(BedrockAgentCoreToolsService service,
                                           RegionResolver regionResolver,
                                           ObjectMapper objectMapper) {
        this.service = service;
        this.regionResolver = regionResolver;
        this.objectMapper = objectMapper;
    }

    @PUT
    @Path("/browsers")
    public Response createBrowser(@Context HttpHeaders headers, String body) {
        String region = regionResolver.resolveRegion(headers);
        try {
            ObjectNode request = object(body);
            ObjectNode browser = service.createBrowser(request, region);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("browserArn", browser.path("browserArn").asText());
            response.put("browserId", browser.path("browserId").asText());
            response.put("createdAt", browser.path("createdAt").asText());
            response.put("status", browser.path("status").asText());
            return Response.status(202).entity(response).build();
        } catch (Exception e) {
            return error(e, "creating browser");
        }
    }

    @GET
    @Path("/browsers/{browserId}")
    public Response getBrowser(@Context HttpHeaders headers, @PathParam("browserId") String browserId) {
        String region = regionResolver.resolveRegion(headers);
        try {
            ObjectNode browser = service.getBrowser(browserId, region);
            ObjectNode response = browser.deepCopy();
            response.remove("clientToken");
            response.remove("tags");
            return Response.ok(response).build();
        } catch (Exception e) {
            return error(e, "getting browser");
        }
    }

    private ObjectNode object(String body) throws Exception {
        JsonNode request = objectMapper.readTree(body != null && !body.isBlank() ? body : "{}");
        if (!request.isObject()) {
            throw new AwsException("ValidationException", "request body must be a JSON object", 400);
        }
        return (ObjectNode) request;
    }

    private Response error(Exception e, String action) {
        if (e instanceof AwsException aws) {
            return Response.status(aws.getHttpStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .header("X-Amzn-Errortype", aws.jsonType())
                    .entity(new AwsErrorResponse(aws.jsonType(), aws.getMessage()))
                    .build();
        }
        LOG.errorv(e, "Error {0}", action);
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .header("X-Amzn-Errortype", "ValidationException")
                .entity(new AwsErrorResponse("ValidationException", e.getMessage()))
                .build();
    }
}
