package io.github.hectorvent.floci.services.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MarketplaceDeploymentController {
    private final MarketplaceDeploymentService service;
    private final ObjectMapper mapper;
    private final RequestContext requestContext;

    @Inject
    public MarketplaceDeploymentController(MarketplaceDeploymentService service, ObjectMapper mapper,
                                           RequestContext requestContext) {
        this.service = service;
        this.mapper = mapper;
        this.requestContext = requestContext;
    }

    @POST
    @Path("/catalogs/{catalog}/products/{productId}/deployment-parameters")
    public Response putDeploymentParameter(@PathParam("catalog") String catalog,
                                           @PathParam("productId") String productId,
                                           String body) {
        return Response.ok(service.putDeploymentParameter(
                catalog, productId, parse(body), region(), accountId())).build();
    }

    private JsonNode parse(String body) {
        try {
            JsonNode request = mapper.readTree(body == null || body.isBlank() ? "{}" : body);
            if (request == null || !request.isObject()) {
                throw validation("Request body must be a JSON object.");
            }
            return request;
        } catch (AwsException e) {
            throw e;
        } catch (Exception e) {
            throw validation("Request body is not valid JSON.");
        }
    }

    private String region() {
        return requestContext.getRegion() == null ? "us-east-1" : requestContext.getRegion();
    }

    private String accountId() {
        return requestContext.getAccountId() == null ? "000000000000" : requestContext.getAccountId();
    }

    private static AwsException validation(String message) {
        return new AwsException("ValidationException", message, 400);
    }
}
