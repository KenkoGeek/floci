package io.github.hectorvent.floci.services.ssoadmin;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * IAM Identity Center (SSO Admin, target prefix {@code SWBExternalService.}).
 *
 * <p>LZA's Custom::GetIdentityCenterInstanceMetadata Lambda calls {@code ListInstances} and
 * requires exactly one instance carrying {@code InstanceArn} and {@code IdentityStoreId}.
 */
@QuarkusTest
class SsoAdminIntegrationTest {

    private static final String AUTH_HEADER =
            "AWS4-HMAC-SHA256 Credential=AKID/20260101/us-east-1/sso/aws4_request";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void listInstances_returnsExactlyOneInstanceWithArnAndIdentityStore() {
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.ListInstances")
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Instances.size()", equalTo(1))
            .body("Instances[0].InstanceArn", matchesPattern("arn:aws:sso:::instance/ssoins-[0-9a-f]{16}"))
            .body("Instances[0].IdentityStoreId", matchesPattern("d-[0-9a-f]{10}"))
            .body("Instances[0].Status", equalTo("ACTIVE"));
    }

    @Test
    void listInstances_isStableAcrossCalls() {
        String firstArn = listInstancesArn();
        String secondArn = listInstancesArn();
        org.junit.jupiter.api.Assertions.assertEquals(firstArn, secondArn);
    }

    @Test
    void addRegionReturnsAddingAndRejectsDuplicate() {
        String request = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\",\"RegionName\":\"eu-west-2\"}";
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.AddRegion")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Status", equalTo("ADDING"));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.AddRegion")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ConflictException"));
    }

    @Test
    void attachCustomerManagedPolicyReferenceReturnsAnEmptyAwsResponse() {
        String permissionSetArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreatePermissionSet")
                .body("{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\",\"Name\":\"CustomerPolicyIntegration\"}")
            .when().post("/")
            .then().statusCode(200)
            .extract().path("PermissionSet.PermissionSetArn");

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.AttachCustomerManagedPolicyReferenceToPermissionSet")
            .body("{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\",\"PermissionSetArn\":\"" + permissionSetArn
                    + "\",\"CustomerManagedPolicyReference\":{\"Name\":\"PlatformPolicy\",\"Path\":\"/platform/\"}}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));
    }

    @Test
    void createApplicationReturnsAwsIdentifiersAndIsIdempotent() {
        String request = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"Integration OAuth\",\"ClientToken\":\"integration-token\"}";

        String applicationArn = given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateApplication")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("InstanceArn", equalTo("arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e"))
            .body("IdentityStoreArn", equalTo("arn:aws:identitystore::000000000000:identitystore/d-9067f2a3c1"))
            .body("ApplicationArn", matchesPattern("arn:aws:sso::000000000000:application/ssoins-7223b02a5d9f7c8e/apl-[0-9a-f]{16}"))
            .extract().path("ApplicationArn");

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateApplication")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("ApplicationArn", equalTo(applicationArn));
    }

    @Test
    void unknownAction_returnsUnknownOperationException() {
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DescribeInstance")
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("UnknownOperationException"));
    }

    @Test
    void listInstances_ownerAccountIdTracksTheCaller() {
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization",
                    "AWS4-HMAC-SHA256 Credential=111122223333/20260101/us-east-1/sso/aws4_request")
            .header("X-Amz-Target", "SWBExternalService.ListInstances")
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Instances[0].OwnerAccountId", org.hamcrest.Matchers.equalTo("111122223333"));
    }

    private static String listInstancesArn() {
        return given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ListInstances")
                .body("{}")
            .when()
                .post("/")
            .then()
                .statusCode(200)
            .extract().path("Instances[0].InstanceArn");
    }
}
