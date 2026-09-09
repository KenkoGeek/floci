package io.github.hectorvent.floci.services.bedrock;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.JsonErrorResponseUtils;
import io.github.hectorvent.floci.core.common.RegionResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BedrockControlPlaneController {

    private final BedrockControlPlaneService service;
    private final RegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Inject
    public BedrockControlPlaneController(BedrockControlPlaneService service,
                                         RegionResolver regionResolver,
                                         ObjectMapper objectMapper) {
        this.service = service;
        this.regionResolver = regionResolver;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/use-case-for-model-access")
    public Response getUseCaseForModelAccess(@Context HttpHeaders headers) {
        return execute("GetUseCaseForModelAccess", null, Map.of(), headers);
    }

    @POST
    @Path("/use-case-for-model-access")
    public Response putUseCaseForModelAccess(String body, @Context HttpHeaders headers) {
        return execute("PutUseCaseForModelAccess", body, Map.of(), headers);
    }

    @GET
    @Path("/foundation-model-availability/{modelId:.+}")
    public Response getFoundationModelAvailability(@PathParam("modelId") String modelId, @Context HttpHeaders headers) {
        return execute("GetFoundationModelAvailability", null, Map.of("modelId", modelId), headers);
    }

    @GET
    @Path("/list-foundation-model-agreement-offers/{modelId:.+}")
    public Response listFoundationModelAgreementOffers(@PathParam("modelId") String modelId, @Context HttpHeaders headers) {
        return execute("ListFoundationModelAgreementOffers", null, Map.of("modelId", modelId), headers);
    }

    @POST
    @Path("/create-foundation-model-agreement")
    public Response createFoundationModelAgreement(String body, @Context HttpHeaders headers) {
        return execute("CreateFoundationModelAgreement", body, Map.of(), headers);
    }

    @GET
    @Path("/foundation-models/{modelIdentifier:.+}")
    public Response getFoundationModel(@PathParam("modelIdentifier") String modelIdentifier, @Context HttpHeaders headers) {
        return execute("GetFoundationModel", null, Map.of("modelIdentifier", modelIdentifier), headers);
    }

    @GET
    @Path("/foundation-models")
    public Response listFoundationModels(@Context HttpHeaders headers) {
        return execute("ListFoundationModels", null, Map.of(), headers);
    }

    @POST
    @Path("/delete-foundation-model-agreement")
    public Response deleteFoundationModelAgreement(String body, @Context HttpHeaders headers) {
        return execute("DeleteFoundationModelAgreement", body, Map.of(), headers);
    }

    @GET
    @Path("/logging/modelinvocations")
    public Response getModelInvocationLoggingConfiguration(@Context HttpHeaders headers) {
        return execute("GetModelInvocationLoggingConfiguration", null, Map.of(), headers);
    }

    @PUT
    @Path("/logging/modelinvocations")
    public Response putModelInvocationLoggingConfiguration(String body, @Context HttpHeaders headers) {
        return execute("PutModelInvocationLoggingConfiguration", body, Map.of(), headers);
    }

    @DELETE
    @Path("/logging/modelinvocations")
    public Response deleteModelInvocationLoggingConfiguration(@Context HttpHeaders headers) {
        return execute("DeleteModelInvocationLoggingConfiguration", null, Map.of(), headers);
    }

    @POST
    @Path("/listTagsForResource")
    public Response listTagsForResource(String body, @Context HttpHeaders headers) {
        return execute("ListTagsForResource", body, Map.of(), headers);
    }

    @POST
    @Path("/tagResource")
    public Response tagResource(String body, @Context HttpHeaders headers) {
        return execute("TagResource", body, Map.of(), headers);
    }

    @POST
    @Path("/untagResource")
    public Response untagResource(String body, @Context HttpHeaders headers) {
        return execute("UntagResource", body, Map.of(), headers);
    }

    @GET
    @Path("/resource-policy/{resourceArn:.+}")
    public Response getResourcePolicy(@PathParam("resourceArn") String resourceArn, @Context HttpHeaders headers) {
        return execute("GetResourcePolicy", null, Map.of("resourceArn", resourceArn), headers);
    }

    @POST
    @Path("/resource-policy")
    public Response putResourcePolicy(String body, @Context HttpHeaders headers) {
        return execute("PutResourcePolicy", body, Map.of(), headers);
    }

    @DELETE
    @Path("/resource-policy/{resourceArn:.+}")
    public Response deleteResourcePolicy(@PathParam("resourceArn") String resourceArn, @Context HttpHeaders headers) {
        return execute("DeleteResourcePolicy", null, Map.of("resourceArn", resourceArn), headers);
    }

    @GET
    @Path("/data-retention")
    public Response getAccountDataRetention(@Context HttpHeaders headers) {
        return execute("GetAccountDataRetention", null, Map.of(), headers);
    }

    @PUT
    @Path("/data-retention")
    public Response putAccountDataRetention(String body, @Context HttpHeaders headers) {
        return execute("PutAccountDataRetention", body, Map.of(), headers);
    }

    @POST
    @Path("/guardrails")
    public Response createGuardrail(String body, @Context HttpHeaders headers) {
        return execute("CreateGuardrail", body, Map.of(), headers);
    }

    @POST
    @Path("/guardrails/{guardrailIdentifier:.+}")
    public Response createGuardrailVersion(@PathParam("guardrailIdentifier") String guardrailIdentifier, String body, @Context HttpHeaders headers) {
        return execute("CreateGuardrailVersion", body, Map.of("guardrailIdentifier", guardrailIdentifier), headers);
    }

    @DELETE
    @Path("/guardrails/{guardrailIdentifier:.+}")
    public Response deleteGuardrail(@PathParam("guardrailIdentifier") String guardrailIdentifier, @Context HttpHeaders headers) {
        return execute("DeleteGuardrail", null, Map.of("guardrailIdentifier", guardrailIdentifier), headers);
    }

    @GET
    @Path("/guardrails/{guardrailIdentifier:.+}")
    public Response getGuardrail(@PathParam("guardrailIdentifier") String guardrailIdentifier, @Context HttpHeaders headers) {
        return execute("GetGuardrail", null, Map.of("guardrailIdentifier", guardrailIdentifier), headers);
    }

    private Response execute(String operation, String body, Map<String, String> path, HttpHeaders headers) {
        ObjectNode request = readRequest(body);
        BedrockControlPlaneService.Result result = service.execute(
                operation, request, path, regionResolver.resolveRegion(headers));
        return Response.status(result.status()).entity(result.body()).build();
    }

    private ObjectNode readRequest(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            var parsed = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body);
            if (!(parsed instanceof ObjectNode object)) {
                throw new IllegalArgumentException("request body must be a JSON object");
            }
            return object;
        } catch (Exception e) {
            throw new WebApplicationException(JsonErrorResponseUtils.createSerializationErrorResponse());
        }
    }
}
