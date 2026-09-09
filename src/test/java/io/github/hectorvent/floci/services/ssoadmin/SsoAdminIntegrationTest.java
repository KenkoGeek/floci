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
            .header("X-Amz-Target", "SWBExternalService.DescribeRegion")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("RegionName", equalTo("eu-west-2"))
            .body("Status", equalTo("ACTIVE"))
            .body("IsPrimaryRegion", equalTo(false))
            .body("AddedDate", org.hamcrest.Matchers.notNullValue());

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
    void describeApplicationReturnsAwsApplicationShape() {
        String createRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"Describe Integration\",\"Description\":\"Describe application\","
                + "\"Status\":\"DISABLED\",\"ClientToken\":\"describe-integration-token\","
                + "\"PortalOptions\":{\"Visibility\":\"ENABLED\",\"SignInOptions\":{"
                + "\"Origin\":\"APPLICATION\",\"ApplicationUrl\":\"https://example.com/login\"}}}";
        String applicationArn = given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateApplication")
            .body(createRequest)
        .when().post("/")
        .then().statusCode(200)
            .extract().path("ApplicationArn");

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DescribeApplication")
            .body("{\"ApplicationArn\":\"" + applicationArn + "\"}")
        .when().post("/")
        .then().statusCode(200)
            .body("ApplicationAccount", equalTo("000000000000"))
            .body("ApplicationArn", equalTo(applicationArn))
            .body("ApplicationProviderArn", equalTo("arn:aws:sso::aws:applicationProvider/custom"))
            .body("CreatedDate", org.hamcrest.Matchers.notNullValue())
            .body("CreatedFrom", equalTo("us-east-1"))
            .body("Description", equalTo("Describe application"))
            .body("IdentityStoreArn", equalTo("arn:aws:identitystore::000000000000:identitystore/d-9067f2a3c1"))
            .body("InstanceArn", equalTo("arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e"))
            .body("Name", equalTo("Describe Integration"))
            .body("PortalOptions.Visibility", equalTo("ENABLED"))
            .body("PortalOptions.SignInOptions.Origin", equalTo("APPLICATION"))
            .body("PortalOptions.SignInOptions.ApplicationUrl", equalTo("https://example.com/login"))
            .body("Status", equalTo("DISABLED"));
    }

    @Test
    void createApplicationAssignmentGrantsDirectAccessWithEmptyResponse() {
        String appRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"Assignment Integration\",\"ClientToken\":\"assignment-integration-token\"}";
        String applicationArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateApplication")
                .body(appRequest)
            .when().post("/")
            .then().statusCode(200)
            .extract().path("ApplicationArn");

        String assignmentRequest = "{\"ApplicationArn\":\"" + applicationArn + "\","
                + "\"PrincipalId\":\"11111111-2222-3333-4444-555555555555\",\"PrincipalType\":\"USER\"}";
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateApplicationAssignment")
            .body(assignmentRequest)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DescribeApplicationAssignment")
            .body(assignmentRequest)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("ApplicationArn", equalTo(applicationArn))
            .body("PrincipalId", equalTo("11111111-2222-3333-4444-555555555555"))
            .body("PrincipalType", equalTo("USER"));
    }

    @Test
    void describeApplicationProviderReturnsCustomOauthProvider() {
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DescribeApplicationProvider")
            .body("{\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\"}")
        .when().post("/")
        .then()
            .statusCode(200)
            .body("ApplicationProviderArn", equalTo("arn:aws:sso::aws:applicationProvider/custom"))
            .body("FederationProtocol", equalTo("OAUTH"));
    }

    @Test
    void deleteApplicationAssignmentReturnsEmptyResponseAndRevokesAssignment() {
        String appRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"Delete Assignment Integration\"}";
        String applicationArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateApplication")
                .body(appRequest)
            .when().post("/")
            .then().statusCode(200)
            .extract().path("ApplicationArn");

        String assignmentRequest = "{\"ApplicationArn\":\"" + applicationArn + "\","
                + "\"PrincipalId\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"PrincipalType\":\"GROUP\"}";
        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateApplicationAssignment")
                .body(assignmentRequest)
            .when().post("/")
            .then().statusCode(200);

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplicationAssignment")
                .body(assignmentRequest)
            .when().post("/")
            .then().statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplicationAssignment")
                .body(assignmentRequest)
            .when().post("/")
            .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ResourceNotFoundException"));
    }

    @Test
    void deleteApplicationAuthenticationMethodValidatesIamTypeAndMissingMethod() {
        String appRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"Delete Authentication Integration\"}";
        String applicationArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateApplication")
                .body(appRequest)
            .when().post("/")
            .then().statusCode(200)
            .extract().path("ApplicationArn");

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplicationAuthenticationMethod")
                .body("{\"ApplicationArn\":\"" + applicationArn + "\",\"AuthenticationMethodType\":\"IAM\"}")
            .when().post("/")
            .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ResourceNotFoundException"));

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplicationAuthenticationMethod")
                .body("{\"ApplicationArn\":\"" + applicationArn + "\",\"AuthenticationMethodType\":\"SAML\"}")
            .when().post("/")
            .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ValidationException"));
    }

    @Test
    void deleteApplicationGrantValidatesGrantTypeAndMissingGrant() {
        String appRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"Delete Grant Integration\"}";
        String applicationArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateApplication")
                .body(appRequest)
            .when().post("/")
            .then().statusCode(200)
            .extract().path("ApplicationArn");

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplicationGrant")
                .body("{\"ApplicationArn\":\"" + applicationArn + "\",\"GrantType\":\"authorization_code\"}")
            .when().post("/")
            .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ResourceNotFoundException"));

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplicationGrant")
                .body("{\"ApplicationArn\":\"" + applicationArn + "\",\"GrantType\":\"client_credentials\"}")
            .when().post("/")
            .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ValidationException"));
    }

    @Test
    void createTrustedTokenIssuerReturnsAwsArnAndSupportsIdempotency() {
        String request = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"Name\":\"IntegrationIssuer\",\"ClientToken\":\"tti-integration-token\","
                + "\"TrustedTokenIssuerType\":\"OIDC_JWT\",\"TrustedTokenIssuerConfiguration\":{"
                + "\"OidcJwtConfiguration\":{\"ClaimAttributePath\":\"sub\","
                + "\"IdentityStoreAttributePath\":\"userName\",\"IssuerUrl\":\"https://issuer.example.com\","
                + "\"JwksRetrievalOption\":\"OPEN_ID_DISCOVERY\"}}}";

        String arn = given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateTrustedTokenIssuer")
            .body(request)
        .when().post("/")
        .then()
            .statusCode(200)
            .body("TrustedTokenIssuerArn", matchesPattern(
                    "arn:aws:sso::000000000000:trustedTokenIssuer/ssoins-[0-9a-f]{16}/tti-[0-9a-f-]{36}"))
            .extract().path("TrustedTokenIssuerArn");

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateTrustedTokenIssuer")
            .body(request)
        .when().post("/")
        .then()
            .statusCode(200)
            .body("TrustedTokenIssuerArn", equalTo(arn));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DescribeTrustedTokenIssuer")
            .body("{\"TrustedTokenIssuerArn\":\"" + arn + "\"}")
        .when().post("/")
        .then()
            .statusCode(200)
            .body("Name", equalTo("IntegrationIssuer"))
            .body("TrustedTokenIssuerArn", equalTo(arn))
            .body("TrustedTokenIssuerType", equalTo("OIDC_JWT"))
            .body("TrustedTokenIssuerConfiguration.OidcJwtConfiguration.ClaimAttributePath", equalTo("sub"))
            .body("TrustedTokenIssuerConfiguration.OidcJwtConfiguration.IdentityStoreAttributePath", equalTo("userName"))
            .body("TrustedTokenIssuerConfiguration.OidcJwtConfiguration.IssuerUrl", equalTo("https://issuer.example.com"))
            .body("TrustedTokenIssuerConfiguration.OidcJwtConfiguration.JwksRetrievalOption", equalTo("OPEN_ID_DISCOVERY"));
    }

    @Test
    void deleteTrustedTokenIssuerReturnsEmptyAwsResponse() {
        String createRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"Name\":\"DeleteIntegrationIssuer\",\"ClientToken\":\"delete-tti-integration-token\","
                + "\"TrustedTokenIssuerType\":\"OIDC_JWT\",\"TrustedTokenIssuerConfiguration\":{"
                + "\"OidcJwtConfiguration\":{\"ClaimAttributePath\":\"sub\","
                + "\"IdentityStoreAttributePath\":\"userName\",\"IssuerUrl\":\"https://delete-issuer.example.com\","
                + "\"JwksRetrievalOption\":\"OPEN_ID_DISCOVERY\"}}}";
        String arn = given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateTrustedTokenIssuer")
            .body(createRequest)
        .when().post("/")
        .then().statusCode(200)
            .extract().path("TrustedTokenIssuerArn");

        String deleteRequest = "{\"TrustedTokenIssuerArn\":\"" + arn + "\"}";
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DeleteTrustedTokenIssuer")
            .body(deleteRequest)
        .when().post("/")
        .then().statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DeleteTrustedTokenIssuer")
            .body(deleteRequest)
        .when().post("/")
        .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ResourceNotFoundException"));
    }

    @Test
    void createInstanceAccessControlAttributeConfigurationReturnsEmptyAwsResponse() {
        String request = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"InstanceAccessControlAttributeConfiguration\":{\"AccessControlAttributes\":[{"
                + "\"Key\":\"Department\",\"Value\":{\"Source\":[\"${path:enterprise.department}\"]}}]}}";

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateInstanceAccessControlAttributeConfiguration")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DescribeInstanceAccessControlAttributeConfiguration")
            .body("{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Status", equalTo("ENABLED"))
            .body("InstanceAccessControlAttributeConfiguration.AccessControlAttributes[0].Key", equalTo("Department"))
            .body("InstanceAccessControlAttributeConfiguration.AccessControlAttributes[0].Value.Source[0]",
                    equalTo("${path:enterprise.department}"));
    }

    @Test
    void deleteInstanceAccessControlAttributeConfigurationReturnsEmptyAwsResponse() {
        String createRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"InstanceAccessControlAttributeConfiguration\":{\"AccessControlAttributes\":[{"
                + "\"Key\":\"CostCenter\",\"Value\":{\"Source\":[\"${path:enterprise.costCenter}\"]}}]}}";

        String request = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\"}";
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DeleteInstanceAccessControlAttributeConfiguration")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(400)));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.CreateInstanceAccessControlAttributeConfiguration")
            .body(createRequest)
        .when()
            .post("/")
        .then()
            .statusCode(200);

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DeleteInstanceAccessControlAttributeConfiguration")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.DeleteInstanceAccessControlAttributeConfiguration")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ResourceNotFoundException"));
    }

    @Test
    void createInstanceCreatesOneAccountInstanceAndReplaysClientToken() {
        String auth = "AWS4-HMAC-SHA256 Credential=222233334444/20260101/us-west-2/sso/aws4_request";
        String request = "{\"Name\":\"StandaloneInstance\",\"ClientToken\":\"instance-integration-token\","
                + "\"Tags\":[{\"Key\":\"Environment\",\"Value\":\"test\"}]}";

        String instanceArn = given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", auth)
            .header("X-Amz-Target", "SWBExternalService.CreateInstance")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("InstanceArn", matchesPattern("arn:aws:sso:::instance/ssoins-[0-9a-f]{16}"))
            .extract().path("InstanceArn");

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", auth)
            .header("X-Amz-Target", "SWBExternalService.CreateInstance")
            .body(request)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("InstanceArn", equalTo(instanceArn));

        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", auth)
            .header("X-Amz-Target", "SWBExternalService.DescribeInstance")
            .body("{\"InstanceArn\":\"" + instanceArn + "\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("InstanceArn", equalTo(instanceArn))
            .body("OwnerAccountId", equalTo("222233334444"))
            .body("Name", equalTo("StandaloneInstance"))
            .body("Status", equalTo("ACTIVE"))
            .body("PermissionSetsEnabled", equalTo(false))
            .body("IdentityStoreId", matchesPattern("d-[0-9a-f]{10}"))
            .body("CreatedDate", org.hamcrest.Matchers.notNullValue());
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", auth)
            .header("X-Amz-Target", "SWBExternalService.ListInstances")
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Instances.size()", equalTo(1))
            .body("Instances[0].InstanceArn", equalTo(instanceArn))
            .body("Instances[0].Name", equalTo("StandaloneInstance"))
            .body("Instances[0].OwnerAccountId", equalTo("222233334444"))
            .body("Instances[0].PrimaryRegion", equalTo("us-west-2"))
            .body("Instances[0].CreatedDate", org.hamcrest.Matchers.greaterThan(0.0f))
            .body("Instances[0].Regions.size()", equalTo(1))
            .body("Instances[0].Regions[0].RegionName", equalTo("us-west-2"))
            .body("Instances[0].Regions[0].IsPrimaryRegion", equalTo(true))
            .body("Instances[0].Regions[0].Status", equalTo("ACTIVE"));
    }
    @Test
    void deleteInstanceReturnsEmptyResponseAndRemovesAccountInstance() {
        String auth = "AWS4-HMAC-SHA256 Credential=666677778888/20260101/us-east-1/sso/aws4_request";
        String instanceArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", auth)
                .header("X-Amz-Target", "SWBExternalService.CreateInstance")
                .body("{\"Name\":\"DisposableIntegrationInstance\"}")
            .when().post("/")
            .then().statusCode(200)
            .extract().path("InstanceArn");

        given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", auth)
                .header("X-Amz-Target", "SWBExternalService.DeleteInstance")
                .body("{\"InstanceArn\":\"" + instanceArn + "\"}")
            .when().post("/")
            .then().statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", auth)
                .header("X-Amz-Target", "SWBExternalService.ListInstances")
                .body("{}")
            .when().post("/")
            .then().statusCode(200)
            .body("Instances.size()", equalTo(0));
    }

    @Test
    void deleteApplicationReturnsEmptyResponseAndRemovesApplication() {
        String createRequest = "{\"InstanceArn\":\"arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e\","
                + "\"ApplicationProviderArn\":\"arn:aws:sso::aws:applicationProvider/custom\","
                + "\"Name\":\"DeleteApplicationIntegration\"}";
        String applicationArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateApplication")
                .body(createRequest)
            .when().post("/")
            .then().statusCode(200)
            .extract().path("ApplicationArn");

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplication")
                .body("{\"ApplicationArn\":\"" + applicationArn + "\"}")
            .when().post("/")
            .then().statusCode(200)
            .body(org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()));

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteApplication")
                .body("{\"ApplicationArn\":\"" + applicationArn + "\"}")
            .when().post("/")
            .then().statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("ResourceNotFoundException"));
    }

    @Test
    void listAccountAssignmentsForPrincipalReturnsPrincipalAccessWithPagination() {
        String instanceArn = listInstancesArn();
        String principalId = "cccccccc-dddd-eeee-ffff-000000000001";
        for (String name : java.util.List.of("PrincipalIntegrationOne", "PrincipalIntegrationTwo")) {
            String permissionSetArn = given()
                    .contentType("application/x-amz-json-1.1")
                    .header("Authorization", AUTH_HEADER)
                    .header("X-Amz-Target", "SWBExternalService.CreatePermissionSet")
                    .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"" + name + "\"}")
                .when().post("/")
                .then().statusCode(200)
                .extract().path("PermissionSet.PermissionSetArn");
            String assignmentRequest = "{\"InstanceArn\":\"" + instanceArn + "\",\"TargetId\":\"123456789012\","
                    + "\"TargetType\":\"AWS_ACCOUNT\",\"PermissionSetArn\":\"" + permissionSetArn + "\","
                    + "\"PrincipalType\":\"GROUP\",\"PrincipalId\":\"" + principalId + "\"}";
            given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                    .header("X-Amz-Target", "SWBExternalService.CreateAccountAssignment")
                    .body(assignmentRequest).when().post("/").then().statusCode(200);
        }

        String request = "{\"InstanceArn\":\"" + instanceArn + "\",\"PrincipalId\":\"" + principalId + "\","
                + "\"PrincipalType\":\"GROUP\",\"MaxResults\":1}";
        String nextToken = given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ListAccountAssignmentsForPrincipal")
                .body(request)
            .when().post("/")
            .then().statusCode(200)
                .body("AccountAssignments.size()", equalTo(1))
                .extract().path("NextToken");
        org.junit.jupiter.api.Assertions.assertNotNull(nextToken);
    }

    @Test
    void provisionPermissionSetReturnsSuccessfulOperationStatus() {
        String instanceArn = listInstancesArn();
        String permissionSetArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreatePermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"ProvisionIntegration\"}")
            .when().post("/")
            .then().statusCode(200)
            .extract().path("PermissionSet.PermissionSetArn");

        String requestId = given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ProvisionPermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"TargetType\":\"AWS_ACCOUNT\",\"TargetId\":\"123456789012\"}")
            .when().post("/")
            .then().statusCode(200)
                .body("PermissionSetProvisioningStatus.Status", equalTo("SUCCEEDED"))
                .body("PermissionSetProvisioningStatus.AccountId", equalTo("123456789012"))
                .body("PermissionSetProvisioningStatus.PermissionSetArn", equalTo(permissionSetArn))
                .body("PermissionSetProvisioningStatus.RequestId", matchesPattern("[0-9a-f-]{36}"))
                .extract().path("PermissionSetProvisioningStatus.RequestId");

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DescribePermissionSetProvisioningStatus")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"ProvisionPermissionSetRequestId\":\"" + requestId + "\"}")
            .when().post("/")
            .then().statusCode(200)
                .body("PermissionSetProvisioningStatus.RequestId", equalTo(requestId))
                .body("PermissionSetProvisioningStatus.Status", equalTo("SUCCEEDED"));

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ListPermissionSetProvisioningStatus")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Filter\":{\"Status\":\"SUCCEEDED\"}}")
            .when().post("/")
            .then().statusCode(200)
                .body("PermissionSetsProvisioningStatus.RequestId", org.hamcrest.Matchers.hasItem(requestId));
    }

    @Test
    void listPermissionSetsProvisionedToAccountReturnsProvisionedArns() {
        String instanceArn = listInstancesArn();
        String permissionSetArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreatePermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"ProvisionedListIntegration\"}")
            .when().post("/")
            .then().statusCode(200)
            .extract().path("PermissionSet.PermissionSetArn");
        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ProvisionPermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"TargetType\":\"AWS_ACCOUNT\",\"TargetId\":\"210987654321\"}")
            .when().post("/").then().statusCode(200);

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ListPermissionSetsProvisionedToAccount")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"AccountId\":\"210987654321\","
                        + "\"ProvisioningStatus\":\"LATEST_PERMISSION_SET_PROVISIONED\"}")
            .when().post("/")
            .then().statusCode(200)
                .body("PermissionSets", org.hamcrest.Matchers.hasItem(permissionSetArn));
    }

    @Test
    void listAccountsForProvisionedPermissionSetReturnsAccountIds() {
        String instanceArn = listInstancesArn();
        String permissionSetArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreatePermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"ProvisionedAccountsIntegration\"}")
            .when().post("/")
            .then().statusCode(200)
            .extract().path("PermissionSet.PermissionSetArn");
        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ProvisionPermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"TargetType\":\"AWS_ACCOUNT\",\"TargetId\":\"321098765432\"}")
            .when().post("/").then().statusCode(200);

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ListAccountsForProvisionedPermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn + "\","
                        + "\"ProvisioningStatus\":\"LATEST_PERMISSION_SET_PROVISIONED\"}")
            .when().post("/")
            .then().statusCode(200)
                .body("AccountIds", org.hamcrest.Matchers.hasItem("321098765432"));
    }

    @Test
    void deleteAccountAssignmentReturnsDeletionOperationAndRemovesAssignment() {
        String instanceArn = listInstancesArn();
        String permissionSetArn = given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreatePermissionSet")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"DeleteAssignmentIntegration\"}")
            .when().post("/")
            .then().statusCode(200)
            .extract().path("PermissionSet.PermissionSetArn");
        String assignmentRequest = "{\"InstanceArn\":\"" + instanceArn + "\",\"TargetId\":\"123456789012\","
                + "\"TargetType\":\"AWS_ACCOUNT\",\"PermissionSetArn\":\"" + permissionSetArn + "\","
                + "\"PrincipalType\":\"GROUP\",\"PrincipalId\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}";

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.CreateAccountAssignment")
                .body(assignmentRequest).when().post("/").then().statusCode(200);

        String deletionRequestId = given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DeleteAccountAssignment")
                .body(assignmentRequest)
            .when().post("/")
            .then()
                .statusCode(200)
                .body("AccountAssignmentDeletionStatus.Status", equalTo("SUCCEEDED"))
                .body("AccountAssignmentDeletionStatus.RequestId", matchesPattern("[0-9a-f-]{36}"))
                .body("AccountAssignmentDeletionStatus.CreatedDate", org.hamcrest.Matchers.greaterThan(0.0f))
                .extract().path("AccountAssignmentDeletionStatus.RequestId");

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.DescribeAccountAssignmentDeletionStatus")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"AccountAssignmentDeletionRequestId\":\"" + deletionRequestId + "\"}")
            .when().post("/")
            .then().statusCode(200)
                .body("AccountAssignmentDeletionStatus.RequestId", equalTo(deletionRequestId))
                .body("AccountAssignmentDeletionStatus.Status", equalTo("SUCCEEDED"));

        given().contentType("application/x-amz-json-1.1").header("Authorization", AUTH_HEADER)
                .header("X-Amz-Target", "SWBExternalService.ListAccountAssignmentDeletionStatus")
                .body("{\"InstanceArn\":\"" + instanceArn + "\",\"Filter\":{\"Status\":\"SUCCEEDED\"}}")
            .when().post("/")
            .then().statusCode(200)
                .body("AccountAssignmentsDeletionStatus.RequestId", org.hamcrest.Matchers.hasItem(deletionRequestId));
    }

    @Test
    void unknownAction_returnsUnknownOperationException() {
        given()
            .contentType("application/x-amz-json-1.1")
            .header("Authorization", AUTH_HEADER)
            .header("X-Amz-Target", "SWBExternalService.NotARealOperation")
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(400)
            .body("__type", org.hamcrest.Matchers.containsString("UnknownOperationException"));
    }

    @Test
    void listInstancesReturnsEmptyForAnAccountWithoutAVisibleInstance() {
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
            .body("Instances.size()", org.hamcrest.Matchers.equalTo(0));
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
