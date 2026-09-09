package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
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

    private static String key(String region, String gatewayId, String ruleId) {
        return prefix(region, gatewayId) + ruleId;
    }

    private static String prefix(String region, String gatewayId) {
        return "gateway-rule:" + region + ":" + gatewayId + ":";
    }
}
