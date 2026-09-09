package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Pagination;
import io.github.hectorvent.floci.core.common.PaginatedResult;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class BedrockAgentCoreGatewayRuleService {

    private final StorageBackend<String, ObjectNode> storage;
    private final BedrockAgentCoreGatewayService gatewayService;

    @Inject
    public BedrockAgentCoreGatewayRuleService(StorageFactory storageFactory,
                                              BedrockAgentCoreGatewayService gatewayService) {
        this(storageFactory.create("bedrockagentcore", "bedrock-agentcore-gateway-rules.json",
                new TypeReference<Map<String, ObjectNode>>() {}), gatewayService);
    }

    BedrockAgentCoreGatewayRuleService(StorageBackend<String, ObjectNode> storage,
                                       BedrockAgentCoreGatewayService gatewayService) {
        this.storage = storage;
        this.gatewayService = gatewayService;
    }

    public ObjectNode create(String gatewayId, ObjectNode request, String region) {
        var gateway = gatewayService.get(gatewayId, region);
        String clientToken = request.hasNonNull("clientToken") ? request.get("clientToken").asText() : null;
        if (clientToken != null) {
            if (clientToken.length() < 33 || clientToken.length() > 256
                    || !clientToken.matches("[a-zA-Z0-9](-*[a-zA-Z0-9]){0,256}")) {
                throw new AwsException("ValidationException",
                        "clientToken does not satisfy length or pattern constraints", 400);
            }
            ObjectNode existing = storage.scan(k -> k.startsWith(prefix(region, gatewayId))).stream()
                    .filter(rule -> clientToken.equals(rule.path("clientToken").asText(null)))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return existing.deepCopy();
            }
        }
        JsonNode actions = request.get("actions");
        if (actions == null || !actions.isArray() || actions.isEmpty() || actions.size() > 2) {
            throw new AwsException("ValidationException", "actions must contain between 1 and 2 items", 400);
        }
        if (!request.hasNonNull("priority") || !request.get("priority").canConvertToInt()) {
            throw new AwsException("ValidationException", "priority is required", 400);
        }
        int priority = request.get("priority").asInt();
        if (priority < 1 || priority > 1_000_000) {
            throw new AwsException("ValidationException", "priority must be between 1 and 1000000", 400);
        }
        JsonNode conditions = request.get("conditions");
        if (conditions != null && (!conditions.isArray() || conditions.size() > 2)) {
            throw new AwsException("ValidationException", "conditions must contain at most 2 items", 400);
        }
        String ruleId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        ObjectNode rule = request.deepCopy();
        rule.put("ruleId", ruleId);
        rule.put("gatewayArn", gatewayService.gatewayArn(gateway, region));
        rule.put("status", "ACTIVE");
        rule.put("createdAt", now.toString());
        rule.put("updatedAt", now.toString());
        storage.put(key(region, gatewayId, ruleId), rule);
        return rule.deepCopy();
    }

    public ObjectNode get(String gatewayId, String ruleId, String region) {
        gatewayService.get(gatewayId, region);
        if (ruleId == null || !ruleId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new AwsException("ValidationException", "ruleId does not satisfy the required UUID pattern", 400);
        }
        return storage.get(key(region, gatewayId, ruleId))
                .map(ObjectNode::deepCopy)
                .orElseThrow(() -> new AwsException("ResourceNotFoundException",
                        "Gateway rule not found: " + ruleId, 404));
    }

    public PaginatedResult<ObjectNode> list(String gatewayId, Integer maxResults,
                                             String nextToken, String region) {
        gatewayService.get(gatewayId, region);
        List<ObjectNode> rules = storage.scan(k -> k.startsWith(prefix(region, gatewayId))).stream()
                .map(ObjectNode::deepCopy)
                .toList();
        return Pagination.paginate(rules, node -> node.path("ruleId").asText(),
                maxResults, nextToken, 100, 100, "ValidationException");
    }

    public ObjectNode update(String gatewayId, String ruleId, ObjectNode request, String region) {
        ObjectNode rule = get(gatewayId, ruleId, region);
        if (request.has("actions")) {
            JsonNode actions = request.get("actions");
            if (actions == null || !actions.isArray() || actions.isEmpty() || actions.size() > 2) {
                throw new AwsException("ValidationException", "actions must contain between 1 and 2 items", 400);
            }
            rule.set("actions", actions.deepCopy());
        }
        if (request.has("conditions")) {
            JsonNode conditions = request.get("conditions");
            if (conditions == null || !conditions.isArray() || conditions.size() > 2) {
                throw new AwsException("ValidationException", "conditions must contain at most 2 items", 400);
            }
            rule.set("conditions", conditions.deepCopy());
        }
        if (request.hasNonNull("description")) {
            String description = request.get("description").asText();
            if (description.length() < 1 || description.length() > 256) {
                throw new AwsException("ValidationException", "description must be between 1 and 256 characters", 400);
            }
            rule.put("description", description);
        }
        if (request.has("priority")) {
            if (!request.hasNonNull("priority") || !request.get("priority").canConvertToInt()) {
                throw new AwsException("ValidationException", "priority must be an integer", 400);
            }
            int priority = request.get("priority").asInt();
            if (priority < 1 || priority > 1_000_000) {
                throw new AwsException("ValidationException", "priority must be between 1 and 1000000", 400);
            }
            rule.put("priority", priority);
        }
        rule.put("status", "ACTIVE");
        rule.put("updatedAt", Instant.now().toString());
        storage.put(key(region, gatewayId, ruleId), rule);
        return rule.deepCopy();
    }

    public ObjectNode delete(String gatewayId, String ruleId, String region) {
        ObjectNode rule = get(gatewayId, ruleId, region);
        storage.delete(key(region, gatewayId, ruleId));
        ObjectNode response = rule.objectNode();
        response.put("ruleId", ruleId);
        response.put("status", "DELETING");
        return response;
    }

    private static String key(String region, String gatewayId, String ruleId) {
        return prefix(region, gatewayId) + ruleId;
    }

    private static String prefix(String region, String gatewayId) {
        return "gateway-rule:" + region + ":" + gatewayId + ":";
    }
}
