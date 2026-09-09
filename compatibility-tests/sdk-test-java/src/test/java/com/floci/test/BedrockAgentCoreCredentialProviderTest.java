package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateApiKeyCredentialProviderRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateApiKeyCredentialProviderResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.SecretSourceType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bedrock AgentCore credential providers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BedrockAgentCoreCredentialProviderTest {

    private static BedrockAgentCoreControlClient client;
    private static String apiKeyProviderName;

    @BeforeAll
    static void setup() {
        client = TestFixtures.bedrockAgentCoreControlClient();
        apiKeyProviderName = "api_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @AfterAll
    static void cleanup() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @Order(1)
    void createApiKeyCredentialProvider() {
        CreateApiKeyCredentialProviderResponse response = client.createApiKeyCredentialProvider(
                CreateApiKeyCredentialProviderRequest.builder()
                        .name(apiKeyProviderName)
                        .apiKey("secret-value")
                        .apiKeySecretSource(SecretSourceType.MANAGED)
                        .build());

        assertThat(response.name()).isEqualTo(apiKeyProviderName);
        assertThat(response.apiKeySecretSourceAsString()).isEqualTo("MANAGED");
        assertThat(response.credentialProviderArn()).contains(":acps:").contains(":apikeycredentialprovider/");
        assertThat(response.apiKeySecretArn().secretArn()).contains(":secretsmanager:");
        assertThat(response.apiKeySecretJsonKey()).isEqualTo("apiKey");
    }

    @Test
    @Order(2)
    void getApiKeyCredentialProvider() {
        var response = client.getApiKeyCredentialProvider(builder -> builder.name(apiKeyProviderName));

        assertThat(response.name()).isEqualTo(apiKeyProviderName);
        assertThat(response.apiKeySecretSourceAsString()).isEqualTo("MANAGED");
        assertThat(response.credentialProviderArn()).contains(":apikeycredentialprovider/");
        assertThat(response.createdTime()).isNotNull();
        assertThat(response.lastUpdatedTime()).isNotNull();
    }
}
