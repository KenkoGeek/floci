package io.github.hectorvent.floci.services.marketplace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Resettable;
import io.github.hectorvent.floci.core.storage.AccountAwareStorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class MarketplaceDeploymentService implements Resettable {
    private static final String CATALOG = "AWSMarketplace";
    private static final Pattern PRODUCT_ID = Pattern.compile("[A-Za-z0-9_/-]{1,64}");
    private static final Pattern PARAMETER_NAME = Pattern.compile("[a-zA-Z0-9/_+=.@-]{1,400}");
    private static final Pattern TAG_KEY = Pattern.compile("[a-zA-Z0-9/_+=.:@-]{1,128}");
    private static final Pattern TAG_VALUE = Pattern.compile("[a-zA-Z0-9/_+=.:@-]{1,256}");

    private final ObjectMapper mapper;
    private final AccountAwareStorageBackend<JsonNode> parameters;
    private final AccountAwareStorageBackend<JsonNode> idempotency;

    @Inject
    public MarketplaceDeploymentService(StorageFactory factory, ObjectMapper mapper) {
        this.mapper = mapper;
        this.parameters = factory.create("marketplace", "marketplace-deployment-parameters.json", type());
        this.idempotency = factory.create("marketplace", "marketplace-deployment-idempotency.json", type());
    }

    private static TypeReference<Map<String, JsonNode>> type() {
        return new TypeReference<>() {};
    }

    public synchronized ObjectNode putDeploymentParameter(String catalog, String productId,
                                                          JsonNode request, String region, String accountId) {
        validateCatalog(catalog);
        if (productId == null || !PRODUCT_ID.matcher(productId).matches()) {
            throw validation("productId must match [A-Za-z0-9_/-]+ and be at most 64 characters.");
        }
        String agreementId = requireText(request, "agreementId", 1, 64);
        JsonNode input = request.get("deploymentParameter");
        if (input == null || !input.isObject()) {
            throw validation("deploymentParameter is required.");
        }
        String name = requireText(input, "name", 1, 400);
        if (!PARAMETER_NAME.matcher(name).matches()) {
            throw validation("deploymentParameter.name contains unsupported characters.");
        }
        String secret = requireText(input, "secretString", 1, 15000);
        String clientToken = optionalText(request, "clientToken");
        if (clientToken != null && (clientToken.length() < 32 || clientToken.length() > 64)) {
            throw validation("clientToken must be between 32 and 64 characters.");
        }
        Instant expiration = parseExpiration(request.get("expirationDate"));
        Map<String, String> requestedTags = parseTags(request.get("tags"));

        String logicalKey = catalog + "/" + productId + "/" + agreementId + "/" + name;
        if (clientToken != null) {
            JsonNode replay = idempotency.get(clientToken).orElse(null);
            if (replay != null) {
                if (!logicalKey.equals(replay.path("logicalKey").asText())) {
                    throw new AwsException("ConflictException",
                            "clientToken was already used for a different deployment parameter.", 409);
                }
                return response((ObjectNode) replay.path("response").deepCopy());
            }
        }

        ObjectNode existing = parameters.get(logicalKey)
                .filter(JsonNode::isObject)
                .map(value -> (ObjectNode) value.deepCopy())
                .orElse(null);
        boolean create = existing == null;
        ObjectNode record = create ? mapper.createObjectNode() : existing;
        String id = create ? "dp-" + compactId() : record.path("deploymentParameterId").asText();
        String arn = create
                ? "arn:aws:aws-marketplace:" + region + ":" + accountId
                    + ":DeploymentParameter:catalogs/" + catalog + "/products/" + productId + "/" + id
                : record.path("resourceArn").asText();

        record.put("catalog", catalog);
        record.put("productId", productId);
        record.put("agreementId", agreementId);
        record.put("deploymentParameterId", id);
        record.put("resourceArn", arn);
        record.put("name", name);
        record.put("secretString", secret);
        record.put("updatedAt", Instant.now().toString());
        if (expiration != null) {
            record.put("expirationDate", expiration.toString());
        } else {
            record.remove("expirationDate");
        }
        if (create) {
            record.set("tags", tagsNode(requestedTags));
            record.put("createdAt", Instant.now().toString());
        }
        parameters.put(logicalKey, record);

        ObjectNode output = response(record);
        if (clientToken != null) {
            ObjectNode replay = mapper.createObjectNode();
            replay.put("logicalKey", logicalKey);
            replay.set("response", output.deepCopy());
            idempotency.put(clientToken, replay);
        }
        return output;
    }

    public ObjectNode listTagsForResource(String resourceArn) {
        ObjectNode record = requireByArn(resourceArn);
        ObjectNode response = mapper.createObjectNode();
        response.set("tags", record.path("tags").deepCopy());
        return response;
    }

    public synchronized void tagResource(String resourceArn, JsonNode request) {
        ObjectNode record = requireByArn(resourceArn);
        Map<String, String> merged = tagsMap(record.path("tags"));
        merged.putAll(parseTags(request.get("tags")));
        if (merged.size() > 50) {
            throw validation("A deployment parameter can have at most 50 tags.");
        }
        record.set("tags", tagsNode(merged));
        saveRecord(record);
    }

    public synchronized void untagResource(String resourceArn, java.util.List<String> tagKeys) {
        ObjectNode record = requireByArn(resourceArn);
        if (tagKeys == null || tagKeys.isEmpty()) {
            throw validation("tagKeys is required.");
        }
        Map<String, String> existing = tagsMap(record.path("tags"));
        for (String key : tagKeys) {
            if (key == null || key.isBlank()) {
                throw validation("tagKeys entries must be non-empty strings.");
            }
            existing.remove(key);
        }
        record.set("tags", tagsNode(existing));
        saveRecord(record);
    }

    private ObjectNode requireByArn(String resourceArn) {
        if (resourceArn == null || resourceArn.isBlank()) {
            throw validation("resourceArn is required.");
        }
        for (JsonNode value : parameters.scan(key -> true)) {
            if (resourceArn.equals(value.path("resourceArn").asText())) {
                ObjectNode record = (ObjectNode) value.deepCopy();
                if (isExpired(record)) {
                    throw notFound(resourceArn);
                }
                return record;
            }
        }
        throw notFound(resourceArn);
    }

    private void saveRecord(ObjectNode record) {
        String key = record.path("catalog").asText() + "/" + record.path("productId").asText()
                + "/" + record.path("agreementId").asText() + "/" + record.path("name").asText();
        record.put("updatedAt", Instant.now().toString());
        parameters.put(key, record);
    }

    private boolean isExpired(JsonNode record) {
        String value = record.path("expirationDate").asText(null);
        return value != null && Instant.parse(value).isBefore(Instant.now());
    }

    private ObjectNode response(ObjectNode record) {
        ObjectNode out = mapper.createObjectNode();
        out.put("agreementId", record.path("agreementId").asText());
        out.put("deploymentParameterId", record.path("deploymentParameterId").asText());
        out.put("resourceArn", record.path("resourceArn").asText());
        out.set("tags", record.path("tags").deepCopy());
        return out;
    }

    private Map<String, String> parseTags(JsonNode value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isNull()) {
            return result;
        }
        if (!value.isObject()) {
            throw validation("tags must be an object.");
        }
        if (value.size() > 50) {
            throw validation("tags can contain at most 50 entries.");
        }
        value.fields().forEachRemaining(entry -> {
            if (!TAG_KEY.matcher(entry.getKey()).matches()) {
                throw validation("Tag key contains unsupported characters or length.");
            }
            if (!entry.getValue().isTextual() || !TAG_VALUE.matcher(entry.getValue().asText()).matches()) {
                throw validation("Tag value contains unsupported characters or length.");
            }
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return result;
    }

    private ObjectNode tagsNode(Map<String, String> values) {
        ObjectNode node = mapper.createObjectNode();
        values.forEach(node::put);
        return node;
    }

    private static Map<String, String> tagsMap(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        }
        return result;
    }

    private static Instant parseExpiration(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            double seconds = value.asDouble();
            long millis = Math.round(seconds * 1000.0d);
            return Instant.ofEpochMilli(millis);
        }
        if (value.isTextual()) {
            try {
                return Instant.parse(value.asText());
            } catch (DateTimeParseException e) {
                throw validation("expirationDate must be a valid timestamp.");
            }
        }
        throw validation("expirationDate must be a valid timestamp.");
    }

    private static String requireText(JsonNode node, String field, int min, int max) {
        String value = optionalText(node, field);
        if (value == null) {
            throw validation(field + " is required.");
        }
        if (value.length() < min || value.length() > max) {
            throw validation(field + " must be between " + min + " and " + max + " characters.");
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw validation(field + " must be a non-empty string.");
        }
        return value.asText();
    }

    private static void validateCatalog(String catalog) {
        if (!CATALOG.equals(catalog)) {
            throw validation("catalog must be AWSMarketplace.");
        }
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static AwsException validation(String message) {
        return new AwsException("ValidationException", message, 400);
    }

    private static AwsException notFound(String resourceArn) {
        return new AwsException("ResourceNotFoundException",
                "Deployment parameter " + resourceArn + " was not found.", 404);
    }

    @Override
    public void clear() {
        parameters.clear();
        idempotency.clear();
    }
}
