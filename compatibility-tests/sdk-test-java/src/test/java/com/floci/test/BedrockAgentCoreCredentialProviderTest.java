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

    @Test
    @Order(3)
    void listApiKeyCredentialProviders() {
        var response = client.listApiKeyCredentialProviders(builder -> builder.maxResults(100));

        assertThat(response.credentialProviders())
                .anyMatch(provider -> apiKeyProviderName.equals(provider.name()));
    }

    @Test
    @Order(4)
    void updateApiKeyCredentialProvider() {
        var response = client.updateApiKeyCredentialProvider(builder -> builder
                .name(apiKeyProviderName)
                .apiKey("rotated-value")
                .apiKeySecretSource(SecretSourceType.MANAGED));

        assertThat(response.name()).isEqualTo(apiKeyProviderName);
        assertThat(response.apiKeySecretSourceAsString()).isEqualTo("MANAGED");
        assertThat(response.lastUpdatedTime()).isNotNull();
    }

    @Test
    @Order(5)
    void deleteApiKeyCredentialProvider() {
        var response = client.deleteApiKeyCredentialProvider(builder -> builder.name(apiKeyProviderName));
        assertThat(response.sdkHttpResponse().statusCode()).isEqualTo(204);
    }
}
