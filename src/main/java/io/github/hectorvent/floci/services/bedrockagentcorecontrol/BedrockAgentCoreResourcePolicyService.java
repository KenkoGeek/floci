package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class BedrockAgentCoreResourcePolicyService {

    private final StorageBackend<String, String> storage;

    @Inject
    public BedrockAgentCoreResourcePolicyService(StorageFactory storageFactory) {
        this(storageFactory.create("bedrockagentcore", "bedrock-agentcore-resource-policies.json",
                new TypeReference<Map<String, String>>() {}));
    }

    BedrockAgentCoreResourcePolicyService(StorageBackend<String, String> storage) {
        this.storage = storage;
    }

    public String get(String resourceArn) {
        validateResourceArn(resourceArn);
        return storage.get(resourceArn)
                .orElseThrow(() -> new AwsException("ResourceNotFoundException",
                        "Resource policy not found for: " + resourceArn, 404));
    }

    private static void validateResourceArn(String resourceArn) {
        if (resourceArn == null || resourceArn.length() < 20 || resourceArn.length() > 1011) {
            throw new AwsException("ValidationException",
                    "resourceArn must be between 20 and 1011 characters", 400);
        }
    }
}
