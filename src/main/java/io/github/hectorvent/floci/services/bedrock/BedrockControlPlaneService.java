package io.github.hectorvent.floci.services.bedrock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.common.Resettable;
import io.github.hectorvent.floci.core.storage.AccountAwareStorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class BedrockControlPlaneService implements Resettable {

    private static final String USE_CASE_KEY = "config/use-case";
    private static final String LOGGING_KEY = "config/logging";
    private static final String RETENTION_KEY = "config/data-retention";
    private static final String ENFORCED_GUARDRAIL_KEY = "config/enforced-guardrail";

    private final AccountAwareStorageBackend<ObjectNode> state;
    private final ObjectMapper objectMapper;
    private final RegionResolver regionResolver;

    @Inject
    public BedrockControlPlaneService(StorageFactory storageFactory, ObjectMapper objectMapper,
                                      RegionResolver regionResolver) {
        this.state = storageFactory.create("bedrock", "bedrock-control-plane.json",
                new TypeReference<Map<String, ObjectNode>>() {});
        this.objectMapper = objectMapper;
        this.regionResolver = regionResolver;
    }

    public synchronized Result execute(String operation, ObjectNode request,
                                       Map<String, String> path, String region) {
        return switch (operation) {
            case "GetUseCaseForModelAccess" -> getUseCase();
            case "PutUseCaseForModelAccess" -> putUseCase(request);
            case "GetFoundationModelAvailability" -> foundationModelAvailability(path.get("modelId"));
            case "ListFoundationModelAgreementOffers" -> agreementOffers(path.get("modelId"));
            case "CreateFoundationModelAgreement" -> createAgreement(request);
            case "DeleteFoundationModelAgreement" -> deleteAgreement(request);
            case "GetFoundationModel" -> getFoundationModel(path.get("modelIdentifier"), region);
            case "ListFoundationModels" -> listFoundationModels(region);
            case "GetModelInvocationLoggingConfiguration" -> getLogging();
            case "PutModelInvocationLoggingConfiguration" -> putLogging(request);
            case "DeleteModelInvocationLoggingConfiguration" -> deleteLogging();
            case "ListTagsForResource" -> listTags(request);
            case "TagResource" -> tagResource(request);
            case "UntagResource" -> untagResource(request);
            case "GetResourcePolicy" -> getResourcePolicy(path.get("resourceArn"));
            case "PutResourcePolicy" -> putResourcePolicy(request);
            case "DeleteResourcePolicy" -> deleteResourcePolicy(path.get("resourceArn"));
            case "GetAccountDataRetention" -> getDataRetention();
            case "PutAccountDataRetention" -> putDataRetention(request);
            case "CreateGuardrail" -> createGuardrail(request, region);
            case "CreateGuardrailVersion" -> createGuardrailVersion(path.get("guardrailIdentifier"));
            case "DeleteGuardrail" -> deleteGuardrail(path.get("guardrailIdentifier"));
            case "GetGuardrail" -> getGuardrail(path.get("guardrailIdentifier"));
            case "ListGuardrails" -> listGuardrails();
            case "UpdateGuardrail" -> updateGuardrail(path.get("guardrailIdentifier"), request);
            case "ListEnforcedGuardrailsConfiguration" -> listEnforcedGuardrailsConfiguration();
            case "PutEnforcedGuardrailConfiguration" -> putEnforcedGuardrailsConfiguration(request);
            case "DeleteEnforcedGuardrailsConfiguration" -> deleteEnforcedGuardrailsConfiguration(path.get("configId"));
            case "CreateInferenceProfile" -> createInferenceProfile(request, region);
            case "GetInferenceProfile" -> getGeneric("inference-profile", path.get("inferenceProfileIdentifier"));
            case "ListInferenceProfiles" -> listGeneric("inference-profile", "inferenceProfileSummaries");
            case "DeleteInferenceProfile" -> deleteGeneric("inference-profile", path.get("inferenceProfileIdentifier"));
            case "CreateProvisionedModelThroughput" -> createProvisionedThroughput(request, region);
            case "GetProvisionedModelThroughput" -> getGeneric("provisioned-throughput", path.get("provisionedModelId"));
            case "ListProvisionedModelThroughputs" -> listGeneric("provisioned-throughput", "provisionedModelSummaries");
            case "UpdateProvisionedModelThroughput" -> updateGeneric("provisioned-throughput", path.get("provisionedModelId"), request);
            case "DeleteProvisionedModelThroughput" -> deleteGeneric("provisioned-throughput", path.get("provisionedModelId"));
            case "CreateModelImportJob" -> createModelImportJob(request, region);
            case "GetModelImportJob" -> getGeneric("model-import-job", path.get("jobIdentifier"));
            case "ListModelImportJobs" -> listGeneric("model-import-job", "modelImportJobSummaries");
            case "GetImportedModel" -> getGeneric("imported-model", path.get("modelIdentifier"));
            case "ListImportedModels" -> listGeneric("imported-model", "modelSummaries");
            case "DeleteImportedModel" -> deleteGeneric("imported-model", path.get("modelIdentifier"));
            case "CreateCustomModel" -> createCustomModel(request, region);
            case "GetCustomModel" -> getGeneric("custom-model", path.get("modelIdentifier"));
            case "ListCustomModels" -> listGeneric("custom-model", "modelSummaries");
            case "DeleteCustomModel" -> deleteGeneric("custom-model", path.get("modelIdentifier"));
            case "CreateModelCustomizationJob" -> createModelCustomizationJob(request, region);
            case "GetModelCustomizationJob" -> getGeneric("customization-job", path.get("jobIdentifier"));
            case "ListModelCustomizationJobs" -> listGeneric("customization-job", "modelCustomizationJobSummaries");
            case "StopModelCustomizationJob" -> stopGeneric("customization-job", path.get("jobIdentifier"));
            case "CreateModelCopyJob" -> createModelCopyJob(request, region);
            case "GetModelCopyJob" -> getGeneric("model-copy-job", path.get("jobArn"));
            case "ListModelCopyJobs" -> listGeneric("model-copy-job", "modelCopyJobSummaries");
            case "CreateModelInvocationJob" -> createModelInvocationJob(request, region);
            case "GetModelInvocationJob" -> getGeneric("model-invocation-job", path.get("jobIdentifier"));
            case "ListModelInvocationJobs" -> listGeneric("model-invocation-job", "invocationJobSummaries");
            case "StopModelInvocationJob" -> stopGeneric("model-invocation-job", path.get("jobIdentifier"));
            case "CreateMarketplaceModelEndpoint" -> createMarketplaceEndpoint(request, region);
            case "GetMarketplaceModelEndpoint" -> wrapGeneric("marketplace-endpoint", path.get("endpointArn"), "marketplaceModelEndpoint");
            case "ListMarketplaceModelEndpoints" -> listGeneric("marketplace-endpoint", "marketplaceModelEndpoints");
            case "UpdateMarketplaceModelEndpoint" -> wrapUpdateGeneric("marketplace-endpoint", path.get("endpointArn"), request, "marketplaceModelEndpoint");
            case "DeleteMarketplaceModelEndpoint" -> deleteGeneric("marketplace-endpoint", path.get("endpointArn"));
            case "RegisterMarketplaceModelEndpoint" -> registerMarketplaceEndpoint(path.get("endpointIdentifier"));
            case "DeregisterMarketplaceModelEndpoint" -> deregisterMarketplaceEndpoint(path.get("endpointArn"));
            case "CreatePromptRouter" -> createPromptRouter(request, region);
            default -> throw new AwsException("UnknownOperationException", "Unsupported Bedrock operation: " + operation, 404);
        };
    }

    private Result getUseCase() {
        ObjectNode stored = state.get(USE_CASE_KEY).orElseThrow(() ->
                new AwsException("ResourceNotFoundException", "Model access use case has not been configured.", 404));
        return ok(stored.deepCopy());
    }

    private Result putUseCase(ObjectNode request) {
        String formData = requiredText(request, "formData");
        int decodedLength;
        try {
            decodedLength = java.util.Base64.getDecoder().decode(formData).length;
        } catch (IllegalArgumentException e) {
            throw validation("formData must be valid base64-encoded binary data.");
        }
        if (decodedLength < 10 || decodedLength > 16384) {
            throw validation("formData must contain between 10 and 16384 bytes.");
        }
        ObjectNode stored = objectMapper.createObjectNode().put("formData", formData);
        state.put(USE_CASE_KEY, stored);
        return new Result(201, objectMapper.createObjectNode());
    }

    private Result foundationModelAvailability(String modelId) {
        requirePath(modelId, "modelId");
        boolean agreed = state.get(agreementKey(modelId)).isPresent();
        ObjectNode response = objectMapper.createObjectNode();
        response.put("modelId", modelId);
        response.putObject("agreementAvailability").put("status", agreed ? "AVAILABLE" : "NOT_AVAILABLE");
        response.put("authorizationStatus", agreed ? "AUTHORIZED" : "NOT_AUTHORIZED");
        response.put("entitlementAvailability", "AVAILABLE");
        response.put("regionAvailability", "AVAILABLE");
        return ok(response);
    }

    private Result agreementOffers(String modelId) {
        requirePath(modelId, "modelId");
        ObjectNode response = objectMapper.createObjectNode().put("modelId", modelId);
        ObjectNode offer = response.putArray("offers").addObject();
        offer.put("offerId", "floci-public-offer");
        offer.put("offerToken", offerToken(modelId));
        ObjectNode terms = offer.putObject("termDetails");
        terms.putObject("legalTerm").put("url", "https://aws.amazon.com/service-terms/");
        terms.putObject("supportTerm").put("refundPolicyDescription", "Local emulator offer");
        terms.putObject("validityTerm").put("agreementDuration", "P1Y");
        terms.putObject("usageBasedPricingTerm").putArray("rateCard");
        return ok(response);
    }

    private Result createAgreement(ObjectNode request) {
        String modelId = requiredText(request, "modelId");
        String token = requiredText(request, "offerToken");
        if (!offerToken(modelId).equals(token)) {
            throw validation("offerToken does not match an available offer for modelId.");
        }
        ObjectNode agreement = objectMapper.createObjectNode()
                .put("modelId", modelId)
                .put("offerToken", token)
                .put("createdAt", Instant.now().toString());
        state.put(agreementKey(modelId), agreement);
        return new Result(202, objectMapper.createObjectNode().put("modelId", modelId));
    }

    private Result deleteAgreement(ObjectNode request) {
        String modelId = requiredText(request, "modelId");
        state.delete(agreementKey(modelId));
        return ok(objectMapper.createObjectNode());
    }

    private Result getFoundationModel(String identifier, String region) {
        requirePath(identifier, "modelIdentifier");
        return ok(objectMapper.createObjectNode().set("modelDetails", foundationModel(identifier, region)));
    }

    private Result listFoundationModels(String region) {
        ArrayNode models = objectMapper.createArrayNode();
        models.add(foundationModel("anthropic.claude-3-5-sonnet-20241022-v2:0", region));
        models.add(foundationModel("amazon.nova-pro-v1:0", region));
        return ok(objectMapper.createObjectNode().set("modelSummaries", models));
    }

    private ObjectNode foundationModel(String modelId, String region) {
        String provider = modelId.startsWith("anthropic.") ? "Anthropic" : modelId.startsWith("amazon.") ? "Amazon" : "Floci";
        String name = modelId.substring(modelId.indexOf('.') + 1);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("modelArn", regionResolver.buildArn("bedrock", region, "foundation-model/" + modelId));
        node.put("modelId", modelId);
        node.put("modelName", name);
        node.put("providerName", provider);
        node.putArray("inputModalities").add("TEXT");
        node.putArray("outputModalities").add("TEXT");
        node.put("responseStreamingSupported", true);
        node.putArray("customizationsSupported");
        node.putArray("inferenceTypesSupported").add("ON_DEMAND");
        node.putObject("modelLifecycle").put("status", "ACTIVE");
        return node;
    }

    private Result getLogging() {
        ObjectNode response = objectMapper.createObjectNode();
        state.get(LOGGING_KEY).ifPresent(config -> response.set("loggingConfig", config.deepCopy()));
        return ok(response);
    }

    private Result putLogging(ObjectNode request) {
        JsonNode loggingConfig = request.get("loggingConfig");
        if (loggingConfig == null || !loggingConfig.isObject()) {
            throw validation("loggingConfig is required and must be an object.");
        }
        state.put(LOGGING_KEY, ((ObjectNode) loggingConfig).deepCopy());
        return ok(objectMapper.createObjectNode());
    }

    private Result deleteLogging() {
        state.delete(LOGGING_KEY);
        return ok(objectMapper.createObjectNode());
    }

    private Result listTags(ObjectNode request) {
        String arn = requiredText(request, "resourceARN");
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode tags = response.putArray("tags");
        state.get(tagKey(arn)).ifPresent(stored -> stored.fields().forEachRemaining(entry ->
                tags.addObject().put("key", entry.getKey()).put("value", entry.getValue().asText())));
        return ok(response);
    }

    private Result tagResource(ObjectNode request) {
        String arn = requiredText(request, "resourceARN");
        JsonNode requestedTags = request.get("tags");
        if (requestedTags == null || !requestedTags.isArray()) {
            throw validation("tags is required and must be an array.");
        }
        ObjectNode tags = state.get(tagKey(arn)).map(ObjectNode::deepCopy).orElseGet(objectMapper::createObjectNode);
        requestedTags.forEach(tag -> {
            String key = requiredText(tag, "key");
            if (key.startsWith("aws:")) {
                throw validation("Tag keys beginning with aws: are reserved.");
            }
            tags.put(key, requiredText(tag, "value"));
        });
        state.put(tagKey(arn), tags);
        return ok(objectMapper.createObjectNode());
    }

    private Result untagResource(ObjectNode request) {
        String arn = requiredText(request, "resourceARN");
        JsonNode keys = request.get("tagKeys");
        if (keys == null || !keys.isArray()) {
            throw validation("tagKeys is required and must be an array.");
        }
        ObjectNode tags = state.get(tagKey(arn)).map(ObjectNode::deepCopy).orElseGet(objectMapper::createObjectNode);
        keys.forEach(key -> tags.remove(key.asText()));
        state.put(tagKey(arn), tags);
        return ok(objectMapper.createObjectNode());
    }

    private Result getResourcePolicy(String arn) {
        requirePath(arn, "resourceArn");
        ObjectNode policy = state.get(policyKey(arn)).orElseThrow(() -> notFound("resource policy", arn));
        return ok(objectMapper.createObjectNode().put("resourcePolicy", policy.path("resourcePolicy").asText()));
    }

    private Result putResourcePolicy(ObjectNode request) {
        String arn = requiredText(request, "resourceArn");
        String policy = requiredText(request, "resourcePolicy");
        state.put(policyKey(arn), objectMapper.createObjectNode().put("resourcePolicy", policy));
        return ok(objectMapper.createObjectNode().put("resourceArn", arn));
    }

    private Result deleteResourcePolicy(String arn) {
        requirePath(arn, "resourceArn");
        state.delete(policyKey(arn));
        return ok(objectMapper.createObjectNode());
    }

    private Result getDataRetention() {
        ObjectNode stored = state.get(RETENTION_KEY).orElseGet(() -> objectMapper.createObjectNode()
                .put("mode", "DO_NOT_RETAIN")
                .put("updatedAt", Instant.EPOCH.toString()));
        return ok(stored.deepCopy());
    }

    private Result putDataRetention(ObjectNode request) {
        String mode = requiredText(request, "mode");
        ObjectNode stored = objectMapper.createObjectNode()
                .put("mode", mode)
                .put("updatedAt", Instant.now().toString());
        state.put(RETENTION_KEY, stored);
        return ok(stored.deepCopy());
    }

    private Result createGuardrail(ObjectNode request, String region) {
        String name = requiredText(request, "name");
        String id = compactId();
        ObjectNode resource = request.deepCopy();
        resource.put("name", name);
        resource.put("guardrailId", id);
        resource.put("guardrailArn", regionResolver.buildArn("bedrock", region, "guardrail/" + id));
        resource.put("version", "DRAFT");
        resource.put("status", "READY");
        resource.put("createdAt", Instant.now().toString());
        resource.put("updatedAt", resource.path("createdAt").asText());
        storeGeneric("guardrail", id, resource);
        return ok(select(resource, "guardrailId", "guardrailArn", "version", "createdAt"));
    }

    private Result createGuardrailVersion(String identifier) {
        ObjectNode guardrail = findGeneric("guardrail", identifier);
        int next = state.keys().stream()
                .filter(k -> k.startsWith("resource/guardrail-version/" + guardrail.path("guardrailId").asText() + "/"))
                .map(k -> k.substring(k.lastIndexOf('/') + 1))
                .mapToInt(v -> { try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; } })
                .max().orElse(0) + 1;
        ObjectNode versioned = guardrail.deepCopy().put("version", Integer.toString(next));
        state.put("resource/guardrail-version/" + guardrail.path("guardrailId").asText() + "/" + next, versioned);
        return ok(objectMapper.createObjectNode()
                .put("guardrailId", guardrail.path("guardrailId").asText())
                .put("version", Integer.toString(next)));
    }

    private Result deleteGuardrail(String identifier) {
        ObjectNode guardrail = findGeneric("guardrail", identifier);
        state.delete(genericKey("guardrail", guardrail.path("guardrailId").asText()));
        return ok(objectMapper.createObjectNode());
    }

    private Result getGuardrail(String identifier) {
        return ok(findGeneric("guardrail", identifier).deepCopy());
    }

    private Result listGuardrails() {
        ArrayNode items = objectMapper.createArrayNode();
        genericResources("guardrail").forEach(resource -> {
            ObjectNode summary = select(resource, "id", "arn", "status", "name", "description", "version", "createdAt", "updatedAt");
            summary.put("id", resource.path("guardrailId").asText());
            summary.put("arn", resource.path("guardrailArn").asText());
            items.add(summary);
        });
        return ok(objectMapper.createObjectNode().set("guardrails", items));
    }

    private Result updateGuardrail(String identifier, ObjectNode request) {
        ObjectNode resource = findGeneric("guardrail", identifier).deepCopy();
        merge(resource, request);
        resource.put("updatedAt", Instant.now().toString());
        storeGeneric("guardrail", resource.path("guardrailId").asText(), resource);
        return ok(select(resource, "guardrailId", "guardrailArn", "version", "updatedAt"));
    }

    private Result listEnforcedGuardrailsConfiguration() {
        ArrayNode configs = objectMapper.createArrayNode();
        state.get(ENFORCED_GUARDRAIL_KEY).ifPresent(configs::add);
        return ok(objectMapper.createObjectNode().set("guardrailsConfig", configs));
    }

    private Result putEnforcedGuardrailsConfiguration(ObjectNode request) {
        ObjectNode stored = request.deepCopy();
        String id = stored.hasNonNull("configId") ? stored.path("configId").asText() : UUID.randomUUID().toString();
        stored.put("configId", id);
        stored.put("updatedAt", Instant.now().toString());
        stored.put("updatedBy", "floci");
        state.put(ENFORCED_GUARDRAIL_KEY, stored);
        return ok(select(stored, "configId", "updatedAt", "updatedBy"));
    }

    private Result deleteEnforcedGuardrailsConfiguration(String configId) {
        requirePath(configId, "configId");
        state.delete(ENFORCED_GUARDRAIL_KEY);
        return ok(objectMapper.createObjectNode());
    }

    private Result createInferenceProfile(ObjectNode request, String region) {
        String name = requiredText(request, "inferenceProfileName");
        String id = compactId();
        ObjectNode resource = request.deepCopy();
        resource.put("inferenceProfileId", id);
        resource.put("inferenceProfileName", name);
        resource.put("inferenceProfileArn", regionResolver.buildArn("bedrock", region, "application-inference-profile/" + id));
        resource.put("status", "ACTIVE");
        resource.put("type", "APPLICATION");
        resource.put("createdAt", Instant.now().toString());
        resource.put("updatedAt", resource.path("createdAt").asText());
        storeGeneric("inference-profile", id, resource);
        return ok(select(resource, "inferenceProfileArn", "status"));
    }

    private Result createProvisionedThroughput(ObjectNode request, String region) {
        String name = requiredText(request, "provisionedModelName");
        String id = compactId();
        ObjectNode resource = request.deepCopy();
        resource.put("provisionedModelName", name);
        resource.put("provisionedModelArn", regionResolver.buildArn("bedrock", region, "provisioned-model/" + id));
        resource.put("provisionedModelId", id);
        resource.put("status", "InService");
        resource.put("creationTime", Instant.now().toString());
        resource.put("lastModifiedTime", resource.path("creationTime").asText());
        if (!resource.has("modelUnits")) resource.put("modelUnits", 1);
        resource.set("desiredModelUnits", resource.get("modelUnits"));
        if (resource.hasNonNull("modelId")) {
            resource.put("modelArn", regionResolver.buildArn("bedrock", region, "foundation-model/" + resource.path("modelId").asText()));
            resource.put("desiredModelArn", resource.path("modelArn").asText());
        }
        storeGeneric("provisioned-throughput", id, resource);
        return ok(objectMapper.createObjectNode().put("provisionedModelArn", resource.path("provisionedModelArn").asText()));
    }

    private Result createModelImportJob(ObjectNode request, String region) {
        String jobName = requiredText(request, "jobName");
        String modelName = requiredText(request, "importedModelName");
        String id = compactId();
        ObjectNode job = request.deepCopy();
        job.put("jobName", jobName);
        job.put("jobArn", regionResolver.buildArn("bedrock", region, "model-import-job/" + id));
        job.put("importedModelArn", regionResolver.buildArn("bedrock", region, "imported-model/" + id));
        job.put("status", "Completed");
        job.put("creationTime", Instant.now().toString());
        job.put("lastModifiedTime", job.path("creationTime").asText());
        job.put("endTime", job.path("creationTime").asText());
        storeGeneric("model-import-job", id, job);
        storeGeneric("imported-model", id, objectMapper.createObjectNode()
                .put("modelArn", job.path("importedModelArn").asText())
                .put("modelName", modelName)
                .put("jobName", jobName)
                .put("jobArn", job.path("jobArn").asText())
                .put("creationTime", job.path("creationTime").asText())
                .put("instructSupported", false));
        return ok(objectMapper.createObjectNode().put("jobArn", job.path("jobArn").asText()));
    }

    private Result createCustomModel(ObjectNode request, String region) {
        String name = requiredText(request, "modelName");
        String id = compactId();
        ObjectNode model = request.deepCopy();
        model.put("modelName", name);
        model.put("modelArn", regionResolver.buildArn("bedrock", region, "custom-model/" + id));
        model.put("creationTime", Instant.now().toString());
        model.put("modelStatus", "Active");
        storeGeneric("custom-model", id, model);
        return ok(objectMapper.createObjectNode().put("modelArn", model.path("modelArn").asText()));
    }

    private Result createModelCustomizationJob(ObjectNode request, String region) {
        return createJob("customization-job", request, region, "model-customization-job/", "jobArn",
                "jobName", "InProgress");
    }

    private Result createModelCopyJob(ObjectNode request, String region) {
        Result result = createJob("model-copy-job", request, region, "model-copy-job/", "jobArn",
                "targetModelName", "Completed");
        ObjectNode job = result.body();
        return ok(objectMapper.createObjectNode().put("jobArn", job.path("jobArn").asText()));
    }

    private Result createModelInvocationJob(ObjectNode request, String region) {
        return createJob("model-invocation-job", request, region, "model-invocation-job/", "jobArn",
                "jobName", "Submitted");
    }

    private Result createJob(String family, ObjectNode request, String region, String arnPrefix,
                             String arnField, String nameField, String status) {
        requiredText(request, nameField);
        String id = compactId();
        ObjectNode job = request.deepCopy();
        job.put(arnField, regionResolver.buildArn("bedrock", region, arnPrefix + id));
        job.put("jobIdentifier", id);
        job.put("status", status);
        job.put("creationTime", Instant.now().toString());
        job.put("lastModifiedTime", job.path("creationTime").asText());
        storeGeneric(family, id, job);
        return ok(job);
    }

    private Result createMarketplaceEndpoint(ObjectNode request, String region) {
        String name = requiredText(request, "endpointName");
        String id = compactId();
        ObjectNode endpoint = request.deepCopy();
        endpoint.put("endpointName", name);
        endpoint.put("endpointArn", regionResolver.buildArn("bedrock", region, "marketplace-model-endpoint/" + id));
        endpoint.put("status", "REGISTERED");
        endpoint.put("createdAt", Instant.now().toString());
        endpoint.put("updatedAt", endpoint.path("createdAt").asText());
        storeGeneric("marketplace-endpoint", id, endpoint);
        return ok(objectMapper.createObjectNode().set("marketplaceModelEndpoint", endpoint));
    }

    private Result registerMarketplaceEndpoint(String identifier) {
        ObjectNode endpoint = findGeneric("marketplace-endpoint", identifier).deepCopy();
        endpoint.put("status", "REGISTERED");
        endpoint.put("updatedAt", Instant.now().toString());
        storeGeneric("marketplace-endpoint", endpointKey(endpoint), endpoint);
        return ok(objectMapper.createObjectNode().set("marketplaceModelEndpoint", endpoint));
    }

    private Result deregisterMarketplaceEndpoint(String identifier) {
        ObjectNode endpoint = findGeneric("marketplace-endpoint", identifier).deepCopy();
        endpoint.put("status", "DEREGISTERED");
        endpoint.put("updatedAt", Instant.now().toString());
        storeGeneric("marketplace-endpoint", endpointKey(endpoint), endpoint);
        return ok(objectMapper.createObjectNode());
    }

    private Result createPromptRouter(ObjectNode request, String region) {
        String name = requiredText(request, "promptRouterName");
        String id = compactId();
        ObjectNode resource = request.deepCopy();
        resource.put("promptRouterName", name);
        resource.put("promptRouterArn", regionResolver.buildArn("bedrock", region, "default-prompt-router/" + id));
        resource.put("createdAt", Instant.now().toString());
        storeGeneric("prompt-router", id, resource);
        return ok(objectMapper.createObjectNode().put("promptRouterArn", resource.path("promptRouterArn").asText()));
    }

    private Result getGeneric(String family, String identifier) {
        return ok(findGeneric(family, identifier).deepCopy());
    }

    private Result wrapGeneric(String family, String identifier, String field) {
        return ok(objectMapper.createObjectNode().set(field, findGeneric(family, identifier).deepCopy()));
    }

    private Result listGeneric(String family, String field) {
        ArrayNode array = objectMapper.createArrayNode();
        genericResources(family).forEach(value -> array.add(value.deepCopy()));
        return ok(objectMapper.createObjectNode().set(field, array));
    }

    private Result updateGeneric(String family, String identifier, ObjectNode request) {
        ObjectNode resource = findGeneric(family, identifier).deepCopy();
        merge(resource, request);
        resource.put("lastModifiedTime", Instant.now().toString());
        storeGeneric(family, genericId(family, resource, identifier), resource);
        return ok(objectMapper.createObjectNode());
    }

    private Result wrapUpdateGeneric(String family, String identifier, ObjectNode request, String field) {
        ObjectNode resource = findGeneric(family, identifier).deepCopy();
        merge(resource, request);
        resource.put("updatedAt", Instant.now().toString());
        storeGeneric(family, genericId(family, resource, identifier), resource);
        return ok(objectMapper.createObjectNode().set(field, resource));
    }

    private Result stopGeneric(String family, String identifier) {
        ObjectNode resource = findGeneric(family, identifier).deepCopy();
        resource.put("status", "Stopped");
        resource.put("lastModifiedTime", Instant.now().toString());
        storeGeneric(family, genericId(family, resource, identifier), resource);
        return ok(objectMapper.createObjectNode());
    }

    private Result deleteGeneric(String family, String identifier) {
        ObjectNode resource = findGeneric(family, identifier);
        state.delete(genericKey(family, genericId(family, resource, identifier)));
        return ok(objectMapper.createObjectNode());
    }

    private ObjectNode findGeneric(String family, String identifier) {
        requirePath(identifier, "identifier");
        ObjectNode direct = state.get(genericKey(family, identifier)).orElse(null);
        if (direct != null) return direct;
        return genericResources(family).stream()
                .filter(value -> value.fields().hasNext())
                .filter(value -> containsIdentifier(value, identifier))
                .findFirst()
                .orElseThrow(() -> notFound(family, identifier));
    }

    private boolean containsIdentifier(ObjectNode value, String identifier) {
        var fields = value.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getValue().isTextual() && identifier.equals(entry.getValue().asText())) return true;
        }
        return false;
    }

    private List<ObjectNode> genericResources(String family) {
        String prefix = "resource/" + family + "/";
        return state.scan(key -> key.startsWith(prefix)).stream()
                .sorted(Comparator.comparing(this::stableResourceKey))
                .toList();
    }

    private String stableResourceKey(ObjectNode node) {
        for (String key : List.of("name", "jobName", "modelName", "endpointName", "guardrailId",
                "inferenceProfileId", "provisionedModelId", "jobArn", "modelArn", "endpointArn")) {
            if (node.hasNonNull(key)) return node.path(key).asText();
        }
        return node.toString();
    }

    private void storeGeneric(String family, String id, ObjectNode value) {
        state.put(genericKey(family, id), value.deepCopy());
    }

    private String genericId(String family, ObjectNode resource, String fallback) {
        if ("guardrail".equals(family) && resource.hasNonNull("guardrailId")) return resource.path("guardrailId").asText();
        if ("inference-profile".equals(family) && resource.hasNonNull("inferenceProfileId")) return resource.path("inferenceProfileId").asText();
        if ("provisioned-throughput".equals(family) && resource.hasNonNull("provisionedModelId")) return resource.path("provisionedModelId").asText();
        if ("marketplace-endpoint".equals(family)) return endpointKey(resource);
        if (resource.hasNonNull("jobIdentifier")) return resource.path("jobIdentifier").asText();
        return fallback;
    }

    private String endpointKey(ObjectNode endpoint) {
        String arn = endpoint.path("endpointArn").asText();
        int slash = arn.lastIndexOf('/');
        return slash >= 0 ? arn.substring(slash + 1) : arn;
    }

    private static String genericKey(String family, String id) {
        return "resource/" + family + "/" + id;
    }

    private static String agreementKey(String modelId) { return "agreement/" + modelId; }
    private static String tagKey(String arn) { return "tags/" + arn; }
    private static String policyKey(String arn) { return "policy/" + arn; }
    private static String offerToken(String modelId) { return "floci-offer-token-" + Integer.toUnsignedString(modelId.hashCode(), 36); }
    private static String compactId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    private ObjectNode select(ObjectNode source, String... fields) {
        ObjectNode result = objectMapper.createObjectNode();
        for (String field : fields) if (source.has(field)) result.set(field, source.get(field));
        return result;
    }

    private static void merge(ObjectNode destination, ObjectNode source) {
        source.fields().forEachRemaining(entry -> destination.set(entry.getKey(), entry.getValue().deepCopy()));
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw validation(field + " is required.");
        }
        return value.asText();
    }

    private static void requirePath(String value, String field) {
        if (value == null || value.isBlank()) throw validation(field + " is required.");
    }

    private static AwsException validation(String message) {
        return new AwsException("ValidationException", message, 400);
    }

    private static AwsException notFound(String type, String identifier) {
        return new AwsException("ResourceNotFoundException", "The specified " + type + " was not found: " + identifier, 404);
    }

    private Result ok(ObjectNode body) { return new Result(200, body); }

    @Override
    public void clear() {
        state.clear();
    }

    public record Result(int status, ObjectNode body) {}
}
