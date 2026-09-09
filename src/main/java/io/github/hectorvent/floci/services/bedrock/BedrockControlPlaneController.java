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

    @GET
    @Path("/guardrails")
    public Response listGuardrails(@Context HttpHeaders headers) {
        return execute("ListGuardrails", null, Map.of(), headers);
    }

    @PUT
    @Path("/guardrails/{guardrailIdentifier:.+}")
    public Response updateGuardrail(@PathParam("guardrailIdentifier") String guardrailIdentifier, String body, @Context HttpHeaders headers) {
        return execute("UpdateGuardrail", body, Map.of("guardrailIdentifier", guardrailIdentifier), headers);
    }

    @GET
    @Path("/enforcedGuardrailsConfiguration")
    public Response listEnforcedGuardrailsConfiguration(@Context HttpHeaders headers) {
        return execute("ListEnforcedGuardrailsConfiguration", null, Map.of(), headers);
    }

    @PUT
    @Path("/enforcedGuardrailsConfiguration")
    public Response putEnforcedGuardrailConfiguration(String body, @Context HttpHeaders headers) {
        return execute("PutEnforcedGuardrailConfiguration", body, Map.of(), headers);
    }

    @DELETE
    @Path("/enforcedGuardrailsConfiguration/{configId:.+}")
    public Response deleteEnforcedGuardrailConfiguration(@PathParam("configId") String configId, @Context HttpHeaders headers) {
        return execute("DeleteEnforcedGuardrailConfiguration", null, Map.of("configId", configId), headers);
    }

    @POST
    @Path("/inference-profiles")
    public Response createInferenceProfile(String body, @Context HttpHeaders headers) {
        return execute("CreateInferenceProfile", body, Map.of(), headers);
    }

    @GET
    @Path("/inference-profiles/{inferenceProfileIdentifier:.+}")
    public Response getInferenceProfile(@PathParam("inferenceProfileIdentifier") String inferenceProfileIdentifier, @Context HttpHeaders headers) {
        return execute("GetInferenceProfile", null, Map.of("inferenceProfileIdentifier", inferenceProfileIdentifier), headers);
    }

    @GET
    @Path("/inference-profiles")
    public Response listInferenceProfiles(@Context HttpHeaders headers) {
        return execute("ListInferenceProfiles", null, Map.of(), headers);
    }

    @DELETE
    @Path("/inference-profiles/{inferenceProfileIdentifier:.+}")
    public Response deleteInferenceProfile(@PathParam("inferenceProfileIdentifier") String inferenceProfileIdentifier, @Context HttpHeaders headers) {
        return execute("DeleteInferenceProfile", null, Map.of("inferenceProfileIdentifier", inferenceProfileIdentifier), headers);
    }

    @POST
    @Path("/provisioned-model-throughput")
    public Response createProvisionedModelThroughput(String body, @Context HttpHeaders headers) {
        return execute("CreateProvisionedModelThroughput", body, Map.of(), headers);
    }

    @GET
    @Path("/provisioned-model-throughput/{provisionedModelId:.+}")
    public Response getProvisionedModelThroughput(@PathParam("provisionedModelId") String provisionedModelId, @Context HttpHeaders headers) {
        return execute("GetProvisionedModelThroughput", null, Map.of("provisionedModelId", provisionedModelId), headers);
    }

    @GET
    @Path("/provisioned-model-throughputs")
    public Response listProvisionedModelThroughputs(@Context HttpHeaders headers) {
        return execute("ListProvisionedModelThroughputs", null, Map.of(), headers);
    }

    @PATCH
    @Path("/provisioned-model-throughput/{provisionedModelId:.+}")
    public Response updateProvisionedModelThroughput(@PathParam("provisionedModelId") String provisionedModelId, String body, @Context HttpHeaders headers) {
        return execute("UpdateProvisionedModelThroughput", body, Map.of("provisionedModelId", provisionedModelId), headers);
    }

    @DELETE
    @Path("/provisioned-model-throughput/{provisionedModelId:.+}")
    public Response deleteProvisionedModelThroughput(@PathParam("provisionedModelId") String provisionedModelId, @Context HttpHeaders headers) {
        return execute("DeleteProvisionedModelThroughput", null, Map.of("provisionedModelId", provisionedModelId), headers);
    }

    @POST
    @Path("/model-import-jobs")
    public Response createModelImportJob(String body, @Context HttpHeaders headers) {
        return execute("CreateModelImportJob", body, Map.of(), headers);
    }

    @GET
    @Path("/model-import-jobs/{jobIdentifier:.+}")
    public Response getModelImportJob(@PathParam("jobIdentifier") String jobIdentifier, @Context HttpHeaders headers) {
        return execute("GetModelImportJob", null, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @GET
    @Path("/model-import-jobs")
    public Response listModelImportJobs(@Context HttpHeaders headers) {
        return execute("ListModelImportJobs", null, Map.of(), headers);
    }

    @GET
    @Path("/imported-models/{modelIdentifier:.+}")
    public Response getImportedModel(@PathParam("modelIdentifier") String modelIdentifier, @Context HttpHeaders headers) {
        return execute("GetImportedModel", null, Map.of("modelIdentifier", modelIdentifier), headers);
    }

    @GET
    @Path("/imported-models")
    public Response listImportedModels(@Context HttpHeaders headers) {
        return execute("ListImportedModels", null, Map.of(), headers);
    }

    @DELETE
    @Path("/imported-models/{modelIdentifier:.+}")
    public Response deleteImportedModel(@PathParam("modelIdentifier") String modelIdentifier, @Context HttpHeaders headers) {
        return execute("DeleteImportedModel", null, Map.of("modelIdentifier", modelIdentifier), headers);
    }

    @POST
    @Path("/custom-models/create-custom-model")
    public Response createCustomModel(String body, @Context HttpHeaders headers) {
        return execute("CreateCustomModel", body, Map.of(), headers);
    }

    @GET
    @Path("/custom-models/{modelIdentifier:.+}")
    public Response getCustomModel(@PathParam("modelIdentifier") String modelIdentifier, @Context HttpHeaders headers) {
        return execute("GetCustomModel", null, Map.of("modelIdentifier", modelIdentifier), headers);
    }

    @GET
    @Path("/custom-models")
    public Response listCustomModels(@Context HttpHeaders headers) {
        return execute("ListCustomModels", null, Map.of(), headers);
    }

    @DELETE
    @Path("/custom-models/{modelIdentifier:.+}")
    public Response deleteCustomModel(@PathParam("modelIdentifier") String modelIdentifier, @Context HttpHeaders headers) {
        return execute("DeleteCustomModel", null, Map.of("modelIdentifier", modelIdentifier), headers);
    }

    @POST
    @Path("/model-customization-jobs")
    public Response createModelCustomizationJob(String body, @Context HttpHeaders headers) {
        return execute("CreateModelCustomizationJob", body, Map.of(), headers);
    }

    @GET
    @Path("/model-customization-jobs/{jobIdentifier:.+}")
    public Response getModelCustomizationJob(@PathParam("jobIdentifier") String jobIdentifier, @Context HttpHeaders headers) {
        return execute("GetModelCustomizationJob", null, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @GET
    @Path("/model-customization-jobs")
    public Response listModelCustomizationJobs(@Context HttpHeaders headers) {
        return execute("ListModelCustomizationJobs", null, Map.of(), headers);
    }

    @POST
    @Path("/model-customization-jobs/{jobIdentifier:.+}/stop")
    public Response stopModelCustomizationJob(@PathParam("jobIdentifier") String jobIdentifier, String body, @Context HttpHeaders headers) {
        return execute("StopModelCustomizationJob", body, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @POST
    @Path("/model-copy-jobs")
    public Response createModelCopyJob(String body, @Context HttpHeaders headers) {
        return execute("CreateModelCopyJob", body, Map.of(), headers);
    }

    @GET
    @Path("/model-copy-jobs/{jobArn:.+}")
    public Response getModelCopyJob(@PathParam("jobArn") String jobArn, @Context HttpHeaders headers) {
        return execute("GetModelCopyJob", null, Map.of("jobArn", jobArn), headers);
    }

    @GET
    @Path("/model-copy-jobs")
    public Response listModelCopyJobs(@Context HttpHeaders headers) {
        return execute("ListModelCopyJobs", null, Map.of(), headers);
    }

    @POST
    @Path("/model-invocation-job")
    public Response createModelInvocationJob(String body, @Context HttpHeaders headers) {
        return execute("CreateModelInvocationJob", body, Map.of(), headers);
    }

    @GET
    @Path("/model-invocation-job/{jobIdentifier:.+}")
    public Response getModelInvocationJob(@PathParam("jobIdentifier") String jobIdentifier, @Context HttpHeaders headers) {
        return execute("GetModelInvocationJob", null, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @GET
    @Path("/model-invocation-jobs")
    public Response listModelInvocationJobs(@Context HttpHeaders headers) {
        return execute("ListModelInvocationJobs", null, Map.of(), headers);
    }

    @POST
    @Path("/model-invocation-job/{jobIdentifier:.+}/stop")
    public Response stopModelInvocationJob(@PathParam("jobIdentifier") String jobIdentifier, String body, @Context HttpHeaders headers) {
        return execute("StopModelInvocationJob", body, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @POST
    @Path("/marketplace-model/endpoints")
    public Response createMarketplaceModelEndpoint(String body, @Context HttpHeaders headers) {
        return execute("CreateMarketplaceModelEndpoint", body, Map.of(), headers);
    }

    @GET
    @Path("/marketplace-model/endpoints/{endpointArn:.+}")
    public Response getMarketplaceModelEndpoint(@PathParam("endpointArn") String endpointArn, @Context HttpHeaders headers) {
        return execute("GetMarketplaceModelEndpoint", null, Map.of("endpointArn", endpointArn), headers);
    }

    @GET
    @Path("/marketplace-model/endpoints")
    public Response listMarketplaceModelEndpoints(@Context HttpHeaders headers) {
        return execute("ListMarketplaceModelEndpoints", null, Map.of(), headers);
    }

    @PATCH
    @Path("/marketplace-model/endpoints/{endpointArn:.+}")
    public Response updateMarketplaceModelEndpoint(@PathParam("endpointArn") String endpointArn, String body, @Context HttpHeaders headers) {
        return execute("UpdateMarketplaceModelEndpoint", body, Map.of("endpointArn", endpointArn), headers);
    }

    @DELETE
    @Path("/marketplace-model/endpoints/{endpointArn:.+}")
    public Response deleteMarketplaceModelEndpoint(@PathParam("endpointArn") String endpointArn, @Context HttpHeaders headers) {
        return execute("DeleteMarketplaceModelEndpoint", null, Map.of("endpointArn", endpointArn), headers);
    }

    @POST
    @Path("/marketplace-model/endpoints/{endpointIdentifier:.+}/registration")
    public Response registerMarketplaceModelEndpoint(@PathParam("endpointIdentifier") String endpointIdentifier, String body, @Context HttpHeaders headers) {
        return execute("RegisterMarketplaceModelEndpoint", body, Map.of("endpointIdentifier", endpointIdentifier), headers);
    }

    @DELETE
    @Path("/marketplace-model/endpoints/{endpointArn:.+}/registration")
    public Response deregisterMarketplaceModelEndpoint(@PathParam("endpointArn") String endpointArn, @Context HttpHeaders headers) {
        return execute("DeregisterMarketplaceModelEndpoint", null, Map.of("endpointArn", endpointArn), headers);
    }

    @POST
    @Path("/prompt-routers")
    public Response createPromptRouter(String body, @Context HttpHeaders headers) {
        return execute("CreatePromptRouter", body, Map.of(), headers);
    }

    @DELETE
    @Path("/prompt-routers/{promptRouterArn:.+}")
    public Response deletePromptRouter(@PathParam("promptRouterArn") String promptRouterArn, @Context HttpHeaders headers) {
        return execute("DeletePromptRouter", null, Map.of("promptRouterArn", promptRouterArn), headers);
    }

    @GET
    @Path("/prompt-routers/{promptRouterArn:.+}")
    public Response getPromptRouter(@PathParam("promptRouterArn") String promptRouterArn, @Context HttpHeaders headers) {
        return execute("GetPromptRouter", null, Map.of("promptRouterArn", promptRouterArn), headers);
    }

    @GET
    @Path("/prompt-routers")
    public Response listPromptRouters(@Context HttpHeaders headers) {
        return execute("ListPromptRouters", null, Map.of(), headers);
    }

    @POST
    @Path("/model-customization/custom-model-deployments")
    public Response createCustomModelDeployment(String body, @Context HttpHeaders headers) {
        return execute("CreateCustomModelDeployment", body, Map.of(), headers);
    }

    @GET
    @Path("/model-customization/custom-model-deployments/{customModelDeploymentIdentifier:.+}")
    public Response getCustomModelDeployment(@PathParam("customModelDeploymentIdentifier") String customModelDeploymentIdentifier, @Context HttpHeaders headers) {
        return execute("GetCustomModelDeployment", null, Map.of("customModelDeploymentIdentifier", customModelDeploymentIdentifier), headers);
    }

    @GET
    @Path("/model-customization/custom-model-deployments")
    public Response listCustomModelDeployments(@Context HttpHeaders headers) {
        return execute("ListCustomModelDeployments", null, Map.of(), headers);
    }

    @PATCH
    @Path("/model-customization/custom-model-deployments/{customModelDeploymentIdentifier:.+}")
    public Response updateCustomModelDeployment(@PathParam("customModelDeploymentIdentifier") String customModelDeploymentIdentifier, String body, @Context HttpHeaders headers) {
        return execute("UpdateCustomModelDeployment", body, Map.of("customModelDeploymentIdentifier", customModelDeploymentIdentifier), headers);
    }

    @DELETE
    @Path("/model-customization/custom-model-deployments/{customModelDeploymentIdentifier:.+}")
    public Response deleteCustomModelDeployment(@PathParam("customModelDeploymentIdentifier") String customModelDeploymentIdentifier, @Context HttpHeaders headers) {
        return execute("DeleteCustomModelDeployment", null, Map.of("customModelDeploymentIdentifier", customModelDeploymentIdentifier), headers);
    }

    @POST
    @Path("/evaluation-jobs")
    public Response createEvaluationJob(String body, @Context HttpHeaders headers) {
        return execute("CreateEvaluationJob", body, Map.of(), headers);
    }

    @GET
    @Path("/evaluation-jobs/{jobIdentifier:.+}")
    public Response getEvaluationJob(@PathParam("jobIdentifier") String jobIdentifier, @Context HttpHeaders headers) {
        return execute("GetEvaluationJob", null, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @GET
    @Path("/evaluation-jobs")
    public Response listEvaluationJobs(@Context HttpHeaders headers) {
        return execute("ListEvaluationJobs", null, Map.of(), headers);
    }

    @POST
    @Path("/evaluation-job/{jobIdentifier:.+}/stop")
    public Response stopEvaluationJob(@PathParam("jobIdentifier") String jobIdentifier, String body, @Context HttpHeaders headers) {
        return execute("StopEvaluationJob", body, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @POST
    @Path("/evaluation-jobs/batch-delete")
    public Response batchDeleteEvaluationJob(String body, @Context HttpHeaders headers) {
        return execute("BatchDeleteEvaluationJob", body, Map.of(), headers);
    }

    @POST
    @Path("/advanced-prompt-optimization-jobs")
    public Response createAdvancedPromptOptimizationJob(String body, @Context HttpHeaders headers) {
        return execute("CreateAdvancedPromptOptimizationJob", body, Map.of(), headers);
    }

    @GET
    @Path("/advanced-prompt-optimization-jobs/{jobIdentifier:.+}")
    public Response getAdvancedPromptOptimizationJob(@PathParam("jobIdentifier") String jobIdentifier, @Context HttpHeaders headers) {
        return execute("GetAdvancedPromptOptimizationJob", null, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @GET
    @Path("/advanced-prompt-optimization-jobs")
    public Response listAdvancedPromptOptimizationJobs(@Context HttpHeaders headers) {
        return execute("ListAdvancedPromptOptimizationJobs", null, Map.of(), headers);
    }

    @POST
    @Path("/advanced-prompt-optimization-jobs/{jobIdentifier:.+}/stop")
    public Response stopAdvancedPromptOptimizationJob(@PathParam("jobIdentifier") String jobIdentifier, String body, @Context HttpHeaders headers) {
        return execute("StopAdvancedPromptOptimizationJob", body, Map.of("jobIdentifier", jobIdentifier), headers);
    }

    @POST
    @Path("/advanced-prompt-optimization-job/batch-delete")
    public Response batchDeleteAdvancedPromptOptimizationJob(String body, @Context HttpHeaders headers) {
        return execute("BatchDeleteAdvancedPromptOptimizationJob", body, Map.of(), headers);
    }

    @POST
    @Path("/automated-reasoning-policies")
    public Response createAutomatedReasoningPolicy(String body, @Context HttpHeaders headers) {
        return execute("CreateAutomatedReasoningPolicy", body, Map.of(), headers);
    }

    @GET
    @Path("/automated-reasoning-policies/{policyArn:.+}")
    public Response getAutomatedReasoningPolicy(@PathParam("policyArn") String policyArn, @Context HttpHeaders headers) {
        return execute("GetAutomatedReasoningPolicy", null, Map.of("policyArn", policyArn), headers);
    }

    @GET
    @Path("/automated-reasoning-policies")
    public Response listAutomatedReasoningPolicies(@Context HttpHeaders headers) {
        return execute("ListAutomatedReasoningPolicies", null, Map.of(), headers);
    }

    @PATCH
    @Path("/automated-reasoning-policies/{policyArn:.+}")
    public Response updateAutomatedReasoningPolicy(@PathParam("policyArn") String policyArn, String body, @Context HttpHeaders headers) {
        return execute("UpdateAutomatedReasoningPolicy", body, Map.of("policyArn", policyArn), headers);
    }

    @DELETE
    @Path("/automated-reasoning-policies/{policyArn:.+}")
    public Response deleteAutomatedReasoningPolicy(@PathParam("policyArn") String policyArn, @Context HttpHeaders headers) {
        return execute("DeleteAutomatedReasoningPolicy", null, Map.of("policyArn", policyArn), headers);
    }

    @POST
    @Path("/automated-reasoning-policies/{policyArn:.+}/test-cases")
    public Response createAutomatedReasoningPolicyTestCase(@PathParam("policyArn") String policyArn, String body, @Context HttpHeaders headers) {
        return execute("CreateAutomatedReasoningPolicyTestCase", body, Map.of("policyArn", policyArn), headers);
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
