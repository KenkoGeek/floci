package io.github.hectorvent.floci.services;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** Failure and asynchronous-state contracts for the AWS services used by Cloud Launchpad. */
@QuarkusTest
class CloudLaunchpadAwsFailureCompatibilityIntegrationTest {
    private static final String SECURITY = "000000000002";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void securityHubCentralConfigurationConvergesFromPendingToEnabled() {
        post("securityhub", "000000000001", "/organization/admin/enable",
                "{\"AdminAccountId\":\"" + SECURITY + "\"}").then().statusCode(200);
        post("securityhub", SECURITY, "/accounts", "{}").then().statusCode(200);

        post("securityhub", SECURITY, "/organization/configuration",
                "{\"AutoEnable\":false,\"AutoEnableStandards\":\"NONE\","
                        + "\"OrganizationConfiguration\":{\"ConfigurationType\":\"CENTRAL\"}}")
                .then().statusCode(200);

        given().header("Authorization", auth(SECURITY, "securityhub"))
                .get("/organization/configuration")
                .then().statusCode(200)
                .body("OrganizationConfiguration.ConfigurationType", equalTo("CENTRAL"))
                .body("OrganizationConfiguration.Status", equalTo("PENDING"));

        given().header("Authorization", auth(SECURITY, "securityhub"))
                .get("/organization/configuration")
                .then().statusCode(200)
                .body("OrganizationConfiguration.Status", equalTo("ENABLED"));
    }

    @Test
    void securityHubAssociationConvergesAndDuplicateConflicts() {
        String account = "000000000003";
        post("securityhub", "000000000006", "/organization/admin/enable",
                "{\"AdminAccountId\":\"" + account + "\"}").then().statusCode(200);
        post("securityhub", account, "/accounts", "{}").then().statusCode(200);
        String policyArn = post("securityhub", account, "/configurationPolicy/create",
                "{\"Name\":\"failure-contract-policy\",\"ConfigurationPolicy\":{\"SecurityHub\":{"
                        + "\"ServiceEnabled\":true,\"EnabledStandardIdentifiers\":[],"
                        + "\"SecurityControlsConfiguration\":{\"DisabledSecurityControlIdentifiers\":[]}}}}")
                .then().statusCode(200).extract().path("Arn");

        String request = "{\"ConfigurationPolicyIdentifier\":\"" + policyArn
                + "\",\"Target\":{\"AccountId\":\"111111111111\"}}";
        post("securityhub", account, "/configurationPolicyAssociation/associate", request)
                .then().statusCode(200).body("AssociationStatus", equalTo("PENDING"));
        post("securityhub", account, "/configurationPolicyAssociation/get",
                "{\"Target\":{\"AccountId\":\"111111111111\"}}")
                .then().statusCode(200).body("AssociationStatus", equalTo("PENDING"));
        post("securityhub", account, "/configurationPolicyAssociation/get",
                "{\"Target\":{\"AccountId\":\"111111111111\"}}")
                .then().statusCode(200).body("AssociationStatus", equalTo("SUCCESS"));
        post("securityhub", account, "/configurationPolicyAssociation/associate", request)
                .then().statusCode(409).body("__type", equalTo("ResourceConflictException"));
    }

    @Test
    void inspectorEnableExposesEnablingBeforeEnabled() {
        String account = "000000000004";
        post("inspector2", "000000000007", "/delegatedadminaccounts/enable",
                "{\"delegatedAdminAccountId\":\"" + account + "\"}").then().statusCode(200);

        // Delegated administrators are activated by Inspector. A separate disabled account can
        // still exercise the asynchronous Enable contract.
        String member = "111111111111";
        post("inspector2", account, "/enable",
                "{\"accountIds\":[\"" + member + "\"],\"resourceTypes\":[\"EC2\",\"ECR\"]}")
                .then().statusCode(200).body("accounts[0].status", equalTo("ENABLING"));
        post("inspector2", account, "/status/batch/get",
                "{\"accountIds\":[\"" + member + "\"]}")
                .then().statusCode(200).body("accounts[0].state.status", equalTo("ENABLED"));
    }

    @Test
    void detectiveMemberRequiresStartMonitoringTransition() {
        String account = "000000000005";
        post("detective", "000000000008", "/orgs/enableAdminAccount",
                "{\"AccountId\":\"" + account + "\"}").then().statusCode(200);
        String graphArn = post("detective", account, "/graphs/list", "{}")
                .then().statusCode(200).extract().path("GraphList[0].Arn");
        String member = "111111111112";
        post("detective", account, "/graph/members",
                "{\"GraphArn\":\"" + graphArn + "\",\"Accounts\":[{\"AccountId\":\""
                        + member + "\",\"EmailAddress\":\"member@example.com\"}]}")
                .then().statusCode(200).body("Members[0].Status", equalTo("ACCEPTED_BUT_DISABLED"));
        post("detective", account, "/graph/member/monitoringstate",
                "{\"GraphArn\":\"" + graphArn + "\",\"AccountId\":\"" + member + "\"}")
                .then().statusCode(200);
        post("detective", account, "/graph/members/list",
                "{\"GraphArn\":\"" + graphArn + "\"}")
                .then().statusCode(200).body("MemberDetails[0].Status", equalTo("ENABLED"));
        post("detective", account, "/graph/member/monitoringstate",
                "{\"GraphArn\":\"" + graphArn + "\",\"AccountId\":\"" + member + "\"}")
                .then().statusCode(409).body("__type", equalTo("ConflictException"));
    }

    private static io.restassured.response.Response post(String service, String account, String path, String body) {
        return given().contentType("application/json")
                .header("Authorization", auth(account, service)).body(body).post(path);
    }

    private static String auth(String account, String service) {
        return "AWS4-HMAC-SHA256 Credential=" + account + "/20260903/us-east-1/" + service + "/aws4_request";
    }
}
