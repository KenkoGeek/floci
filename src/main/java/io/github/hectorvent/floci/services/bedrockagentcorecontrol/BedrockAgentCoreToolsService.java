package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@ApplicationScoped
public class BedrockAgentCoreToolsService {

    private static final Pattern TOOL_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,47}");
    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final StorageBackend<String, ObjectNode> storage;
    private final RegionResolver regionResolver;

    @Inject
    public BedrockAgentCoreToolsService(StorageFactory storageFactory, RegionResolver regionResolver) {
        this(storageFactory.create("bedrockagentcore", "bedrock-agentcore-tools.json",
                new TypeReference<Map<String, ObjectNode>>() {}), regionResolver);
    }

    BedrockAgentCoreToolsService(StorageBackend<String, ObjectNode> storage, RegionResolver regionResolver) {
        this.storage = storage;
        this.regionResolver = regionResolver;
    }

    public ObjectNode createBrowser(ObjectNode request, String region) {
        String name = requiredText(request, "name");
        if (!TOOL_NAME.matcher(name).matches()) {
            throw new AwsException("ValidationException",
                    "name must match [a-zA-Z][a-zA-Z0-9_]{0,47}", 400);
        }
        JsonNode networkConfiguration = request.get("networkConfiguration");
        if (networkConfiguration == null || !networkConfiguration.isObject()) {
            throw new AwsException("ValidationException", "networkConfiguration is required", 400);
        }
        String clientToken = optionalText(request, "clientToken");
        if (clientToken != null) {
            if (clientToken.length() < 33 || clientToken.length() > 256
                    || !clientToken.matches("[a-zA-Z0-9](-*[a-zA-Z0-9]){0,256}")) {
                throw new AwsException("ValidationException", "clientToken does not satisfy length or pattern constraints", 400);
            }
            ObjectNode existing = findByClientToken("browser", region, clientToken);
            if (existing != null) {
                return existing.deepCopy();
            }
        }
        if (findByName("browser", region, name) != null) {
            throw new AwsException("ConflictException", "Browser already exists: " + name, 409);
        }

        String id = name + "-" + random(10);
        Instant now = Instant.now();
        ObjectNode browser = request.deepCopy();
        browser.put("browserId", id);
        browser.put("browserArn", regionResolver.buildArn("bedrock-agentcore", region, "browser-custom/" + id));
        browser.put("status", "READY");
        browser.put("createdAt", now.toString());
        browser.put("lastUpdatedAt", now.toString());
        storage.put(key("browser", region, id), browser);
        return browser.deepCopy();
    }

    private ObjectNode findByClientToken(String family, String region, String clientToken) {
        return storage.scan(k -> k.startsWith(prefix(family, region))).stream()
                .filter(node -> clientToken.equals(optionalText(node, "clientToken")))
                .findFirst()
                .map(ObjectNode::deepCopy)
                .orElse(null);
    }

    private ObjectNode findByName(String family, String region, String name) {
        return storage.scan(k -> k.startsWith(prefix(family, region))).stream()
                .filter(node -> name.equals(optionalText(node, "name")))
                .findFirst()
                .map(ObjectNode::deepCopy)
                .orElse(null);
    }

    private static String requiredText(ObjectNode request, String field) {
        String value = optionalText(request, field);
        if (value == null || value.isBlank()) {
            throw new AwsException("ValidationException", field + " is required", 400);
        }
        return value;
    }

    private static String optionalText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String random(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(ALNUM.charAt(ThreadLocalRandom.current().nextInt(ALNUM.length())));
        }
        return value.toString();
    }

    private static String key(String family, String region, String id) {
        return prefix(family, region) + id;
    }

    private static String prefix(String family, String region) {
        return family + ":" + region + ":";
    }
}
