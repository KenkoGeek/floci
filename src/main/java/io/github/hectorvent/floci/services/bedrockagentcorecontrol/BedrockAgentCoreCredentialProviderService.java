package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Pagination;
import io.github.hectorvent.floci.core.common.PaginatedResult;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BedrockAgentCoreCredentialProviderService {

    private final StorageBackend<String, ObjectNode> storage;
    private final RegionResolver regionResolver;

    @Inject
    public BedrockAgentCoreCredentialProviderService(StorageFactory storageFactory,
                                                     RegionResolver regionResolver) {
        this(storageFactory.create("bedrockagentcore", "bedrock-agentcore-credential-providers.json",
                new TypeReference<Map<String, ObjectNode>>() {}), regionResolver);
    }

    BedrockAgentCoreCredentialProviderService(StorageBackend<String, ObjectNode> storage,
                                              RegionResolver regionResolver) {
        this.storage = storage;
        this.regionResolver = regionResolver;
    }

    public ObjectNode createApiKey(ObjectNode request, String region) {
        String name = requiredName(request);
        String key = key("apikey", region, name);
        if (storage.get(key).isPresent()) {
            throw new AwsException("ConflictException", "API key credential provider already exists: " + name, 409);
        }
        String source = text(request, "apiKeySecretSource");
        if (source == null) {
            source = "MANAGED";
        }
        if (!source.equals("MANAGED") && !source.equals("EXTERNAL")) {
            throw new AwsException("ValidationException", "apiKeySecretSource must be MANAGED or EXTERNAL", 400);
        }
        String apiKey = text(request, "apiKey");
        if (apiKey != null && apiKey.length() > 65536) {
            throw new AwsException("ValidationException", "apiKey exceeds maximum length of 65536", 400);
        }
        JsonNode secretConfig = request.get("apiKeySecretConfig");
        if (source.equals("EXTERNAL")) {
            if (secretConfig == null || !secretConfig.isObject()
                    || !secretConfig.hasNonNull("secretId") || !secretConfig.hasNonNull("jsonKey")) {
                throw new AwsException("ValidationException",
                        "apiKeySecretConfig with secretId and jsonKey is required for EXTERNAL source", 400);
            }
            String secretId = secretConfig.path("secretId").asText();
            String jsonKey = secretConfig.path("jsonKey").asText();
            if (secretId.length() < 1 || secretId.length() > 2048) {
                throw new AwsException("ValidationException", "secretId must be between 1 and 2048 characters", 400);
            }
            if (jsonKey.length() < 1 || jsonKey.length() > 128) {
                throw new AwsException("ValidationException", "jsonKey must be between 1 and 128 characters", 400);
            }
        }
        validateTags(request.get("tags"));
        Instant now = Instant.now();
        ObjectNode item = JsonNodeFactory.instance.objectNode();
        item.put("name", name);
        item.put("credentialProviderArn", credentialProviderArn(region, name));
        item.put("apiKeySecretSource", source);
        if (source.equals("EXTERNAL")) {
            String secretId = secretConfig.path("secretId").asText();
            String jsonKey = secretConfig.path("jsonKey").asText();
            item.putObject("apiKeySecretArn").put("secretArn", secretId);
            item.put("apiKeySecretJsonKey", jsonKey);
        } else {
            item.putObject("apiKeySecretArn").put("secretArn", managedSecretArn(region, name));
            item.put("apiKeySecretJsonKey", "apiKey");
        }
        item.put("createdTime", now.getEpochSecond());
        item.put("lastUpdatedTime", now.getEpochSecond());
        if (request.has("tags")) {
            item.set("tags", request.get("tags").deepCopy());
        }
        storage.put(key, item);
        return item.deepCopy();
    }

    public ObjectNode getApiKey(String name, String region) {
        validateName(name);
        return storage.get(key("apikey", region, name))
                .map(ObjectNode::deepCopy)
                .orElseThrow(() -> new AwsException("ResourceNotFoundException",
                        "API key credential provider not found: " + name, 404));
    }

    public PaginatedResult<ObjectNode> listApiKeys(Integer maxResults, String nextToken, String region) {
        List<ObjectNode> items = storage.scan(k -> k.startsWith(prefix("apikey", region))).stream()
                .map(ObjectNode::deepCopy)
                .toList();
        return Pagination.paginate(items, node -> node.path("name").asText(),
                maxResults, nextToken, 100, 100, "ValidationException");
    }

    public ObjectNode updateApiKey(ObjectNode request, String region) {
        String name = requiredName(request);
        ObjectNode item = getApiKey(name, region);
        String apiKey = text(request, "apiKey");
        if (apiKey != null && apiKey.length() > 65536) {
            throw new AwsException("ValidationException", "apiKey exceeds maximum length of 65536", 400);
        }
        String source = text(request, "apiKeySecretSource");
        JsonNode secretConfig = request.get("apiKeySecretConfig");
        if (source != null) {
            if (!source.equals("MANAGED") && !source.equals("EXTERNAL")) {
                throw new AwsException("ValidationException", "apiKeySecretSource must be MANAGED or EXTERNAL", 400);
            }
            item.put("apiKeySecretSource", source);
        } else {
            source = item.path("apiKeySecretSource").asText("MANAGED");
        }
        if (source.equals("EXTERNAL")) {
            if (secretConfig == null || !secretConfig.isObject()
                    || !secretConfig.hasNonNull("secretId") || !secretConfig.hasNonNull("jsonKey")) {
                throw new AwsException("ValidationException",
                        "apiKeySecretConfig with secretId and jsonKey is required for EXTERNAL source", 400);
            }
        }
        if (secretConfig != null && secretConfig.isObject()) {
            String secretId = secretConfig.path("secretId").asText();
            String jsonKey = secretConfig.path("jsonKey").asText();
            if (secretId.length() < 1 || secretId.length() > 2048) {
                throw new AwsException("ValidationException", "secretId must be between 1 and 2048 characters", 400);
            }
            if (jsonKey.length() < 1 || jsonKey.length() > 128) {
                throw new AwsException("ValidationException", "jsonKey must be between 1 and 128 characters", 400);
            }
            item.putObject("apiKeySecretArn").put("secretArn", secretId);
            item.put("apiKeySecretJsonKey", jsonKey);
        } else if (source.equals("MANAGED")) {
            item.putObject("apiKeySecretArn").put("secretArn", managedSecretArn(region, name));
            item.put("apiKeySecretJsonKey", "apiKey");
        }
        item.put("lastUpdatedTime", Instant.now().getEpochSecond());
        storage.put(key("apikey", region, name), item);
        return item.deepCopy();
    }

    private String credentialProviderArn(String region, String name) {
        return "arn:aws:acps:" + region + ":" + regionResolver.getAccountId()
                + ":token-vault/default/apikeycredentialprovider/" + name;
    }

    private String managedSecretArn(String region, String name) {
        return "arn:aws:secretsmanager:" + region + ":" + regionResolver.getAccountId()
                + ":secret:agentcore-" + name;
    }

    private static String requiredName(ObjectNode request) {
        String name = text(request, "name");
        validateName(name);
        return name;
    }

    private static void validateName(String name) {
        if (name == null || name.length() < 1 || name.length() > 128 || !name.matches("[a-zA-Z0-9\\-_]+")) {
            throw new AwsException("ValidationException", "name must match [a-zA-Z0-9\\-_]+ and be 1-128 characters", 400);
        }
    }

    private static void validateTags(JsonNode tags) {
        if (tags == null || tags.isNull()) {
            return;
        }
        if (!tags.isObject() || tags.size() > 50) {
            throw new AwsException("ValidationException", "tags must be an object with at most 50 entries", 400);
        }
        tags.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = entry.getValue().asText();
            if (key.length() < 1 || key.length() > 128 || !key.matches("[a-zA-Z0-9\\s._:/=+@-]*")) {
                throw new AwsException("ValidationException", "tag key does not satisfy AgentCore constraints", 400);
            }
            if (value.length() > 256 || !value.matches("[a-zA-Z0-9\\s._:/=+@-]*")) {
                throw new AwsException("ValidationException", "tag value does not satisfy AgentCore constraints", 400);
            }
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String key(String type, String region, String name) {
        return prefix(type, region) + name;
    }

    private static String prefix(String type, String region) {
        return "credential-provider:" + type + ":" + region + ":";
    }
}
