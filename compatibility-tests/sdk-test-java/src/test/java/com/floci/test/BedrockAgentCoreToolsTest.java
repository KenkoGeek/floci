package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.BrowserNetworkConfiguration;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.BrowserNetworkMode;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateBrowserRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateBrowserResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetBrowserRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetBrowserResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bedrock AgentCore tools")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BedrockAgentCoreToolsTest {

    private static BedrockAgentCoreControlClient client;
    private static String browserName;
    private static String browserId;

    @BeforeAll
    static void setup() {
        client = TestFixtures.bedrockAgentCoreControlClient();
        browserName = "browser" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @AfterAll
    static void cleanup() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @Order(1)
    void createBrowser() {
        CreateBrowserResponse response = client.createBrowser(CreateBrowserRequest.builder()
                .name(browserName)
                .networkConfiguration(BrowserNetworkConfiguration.builder()
                        .networkMode(BrowserNetworkMode.PUBLIC)
                        .build())
                .build());

        browserId = response.browserId();
        assertThat(browserId).startsWith(browserName + "-");
        assertThat(response.browserArn()).contains(":bedrock-agentcore:").contains(":browser-custom/");
        assertThat(response.statusAsString()).isEqualTo("READY");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @Order(2)
    void getBrowser() {
        GetBrowserResponse response = client.getBrowser(GetBrowserRequest.builder()
                .browserId(browserId)
                .build());

        assertThat(response.browserId()).isEqualTo(browserId);
        assertThat(response.name()).isEqualTo(browserName);
        assertThat(response.browserArn()).contains(":browser-custom/");
        assertThat(response.networkConfiguration().networkModeAsString()).isEqualTo("PUBLIC");
        assertThat(response.statusAsString()).isEqualTo("READY");
    }
}
