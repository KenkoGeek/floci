package io.github.hectorvent.floci.services.bedrock;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class BedrockControlPlaneExtendedIntegrationTest {
    private static final String AUTH = "AWS4-HMAC-SHA256 Credential=AKID/20260909/us-east-1/bedrock/aws4_request";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void promptRouterLifecycle() {
        String arn = given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"fallbackModel\":{\"modelArn\":\"arn:aws:bedrock:us-east-1::foundation-model/amazon.nova-pro-v1:0\"},\"models\":[{\"modelArn\":\"arn:aws:bedrock:us-east-1::foundation-model/amazon.nova-pro-v1:0\"}],\"promptRouterName\":\"router-a\",\"routingCriteria\":{\"responseQualityDifference\":0.1}}")
                .post("/prompt-routers").then().statusCode(200).extract().path("promptRouterArn");

        given().header("Authorization", AUTH).get("/prompt-routers/{arn}", arn)
                .then().statusCode(200).body("promptRouterArn", equalTo(arn));
        given().header("Authorization", AUTH).get("/prompt-routers")
                .then().statusCode(200).body("promptRouterSummaries.promptRouterArn", hasItem(arn));
        given().header("Authorization", AUTH).delete("/prompt-routers/{arn}", arn).then().statusCode(200);
    }

    @Test
    void customModelDeploymentLifecycle() {
        String arn = given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"clientRequestToken\":\"token-123\",\"modelDeploymentName\":\"deploy-a\",\"modelArn\":\"arn:aws:bedrock:us-east-1:000000000000:custom-model/model-a\"}")
                .post("/model-customization/custom-model-deployments").then().statusCode(202)
                .body("customModelDeploymentArn", notNullValue()).extract().path("customModelDeploymentArn");

        given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"modelArn\":\"arn:aws:bedrock:us-east-1:000000000000:custom-model/model-b\"}")
                .patch("/model-customization/custom-model-deployments/{id}", arn).then().statusCode(202);
        given().header("Authorization", AUTH).delete("/model-customization/custom-model-deployments/{id}", arn)
                .then().statusCode(200);
    }

    @Test
    void evaluationAndOptimizationJobsCanBeStoppedAndBatchDeleted() {
        String evaluationArn = given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"applicationType\":\"ModelEvaluation\",\"inferenceConfig\":{},\"jobDescription\":\"local\",\"jobTags\":[],\"roleArn\":\"arn:aws:iam::000000000000:role/eval\"}")
                .post("/evaluation-jobs").then().statusCode(202).extract().path("jobArn");
        given().contentType("application/json").header("Authorization", AUTH).body("{}")
                .post("/evaluation-job/{id}/stop", evaluationArn).then().statusCode(200);
        given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"jobIdentifiers\":[\"" + evaluationArn + "\"]}")
                .post("/evaluation-jobs/batch-delete").then().statusCode(202);

        String optimizationArn = given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"clientToken\":\"token-456\",\"jobDescription\":\"local\",\"modelConfigurations\":[],\"outputConfig\":{\"s3Uri\":\"s3://bucket/out\"}}")
                .post("/advanced-prompt-optimization-jobs").then().statusCode(200).extract().path("jobArn");
        given().contentType("application/json").header("Authorization", AUTH).body("{}")
                .post("/advanced-prompt-optimization-jobs/{id}/stop", optimizationArn).then().statusCode(200);
        given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"jobIdentifiers\":[\"" + optimizationArn + "\"]}")
                .post("/advanced-prompt-optimization-job/batch-delete").then().statusCode(202);
    }

    @Test
    void automatedReasoningPolicyAndTestCaseLifecycle() {
        String policyArn = given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"clientRequestToken\":\"token-789\",\"name\":\"policy-a\",\"description\":\"local\",\"policyDefinition\":{\"rules\":[],\"types\":[],\"variables\":[],\"version\":\"1\"}}")
                .post("/automated-reasoning-policies").then().statusCode(200).extract().path("policyArn");

        String testCaseId = given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"clientRequestToken\":\"case-token\",\"guardContent\":\"guard\"}")
                .post("/automated-reasoning-policies/{arn}/test-cases", policyArn)
                .then().statusCode(200).extract().path("testCaseId");
        given().header("Authorization", AUTH)
                .get("/automated-reasoning-policies/{arn}/test-cases/{id}", policyArn, testCaseId)
                .then().statusCode(200).body("testCase.testCaseId", equalTo(testCaseId));
        given().contentType("application/json").header("Authorization", AUTH)
                .body("{\"clientRequestToken\":\"version-token\"}")
                .post("/automated-reasoning-policies/{arn}/versions", policyArn).then().statusCode(200);
        given().header("Authorization", AUTH).get("/automated-reasoning-policies/{arn}/export", policyArn)
                .then().statusCode(200).body("rules", notNullValue());
    }
}
