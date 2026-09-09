package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateFoundationModelAgreementRequest;
import software.amazon.awssdk.services.bedrock.model.GetFoundationModelAvailabilityRequest;
import software.amazon.awssdk.services.bedrock.model.GetUseCaseForModelAccessRequest;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelAgreementOffersRequest;
import software.amazon.awssdk.services.bedrock.model.OfferType;
import software.amazon.awssdk.services.bedrock.model.PutUseCaseForModelAccessRequest;
import software.amazon.awssdk.services.bedrock.model.ResourceNotFoundException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Amazon Bedrock control plane Cloud Launchpad contract")
class BedrockControlPlaneCloudLaunchpadTest {

    private static final String MODEL_ID = "anthropic.claude-3-5-sonnet-20241022-v2:0";
    private static BedrockClient client;

    @BeforeAll
    static void setup() {
        client = BedrockClient.builder()
                .endpointOverride(TestFixtures.endpoint())
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @AfterAll
    static void cleanup() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void cloudLaunchpadModelAccessFlowUsesRealSdkContracts() {
        assertThatThrownBy(() -> client.getUseCaseForModelAccess(GetUseCaseForModelAccessRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);

        SdkBytes formData = SdkBytes.fromString("local Cloud Launchpad Bedrock use case", StandardCharsets.UTF_8);
        client.putUseCaseForModelAccess(PutUseCaseForModelAccessRequest.builder().formData(formData).build());

        assertThat(client.getUseCaseForModelAccess(GetUseCaseForModelAccessRequest.builder().build()).formData())
                .isEqualTo(formData);

        var before = client.getFoundationModelAvailability(GetFoundationModelAvailabilityRequest.builder()
                .modelId(MODEL_ID)
                .build());
        assertThat(before.authorizationStatusAsString()).isEqualTo("NOT_AUTHORIZED");

        var offers = client.listFoundationModelAgreementOffers(ListFoundationModelAgreementOffersRequest.builder()
                .modelId(MODEL_ID)
                .offerType(OfferType.PUBLIC)
                .build());
        assertThat(offers.offers()).isNotEmpty();

        client.createFoundationModelAgreement(CreateFoundationModelAgreementRequest.builder()
                .modelId(MODEL_ID)
                .offerToken(offers.offers().get(0).offerToken())
                .build());

        var after = client.getFoundationModelAvailability(GetFoundationModelAvailabilityRequest.builder()
                .modelId(MODEL_ID)
                .build());
        assertThat(after.authorizationStatusAsString()).isEqualTo("AUTHORIZED");
        assertThat(after.agreementAvailability().statusAsString()).isEqualTo("AVAILABLE");
    }
}
