package io.github.hectorvent.floci.services.bedrock;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class BedrockControlPlaneIntegrationTest {
    private static final String AUTH = "AWS4-HMAC-SHA256 Credential=AKID/20260908/us-east-1/bedrock/aws4_request";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void getUseCaseForModelAccessReturnsAwsNotFoundBeforeConfiguration() {
        given().header("Authorization", AUTH)
                .get("/use-case-for-model-access")
                .then().statusCode(404)
                .body("__type", equalTo("ResourceNotFoundException"));
    }
}
