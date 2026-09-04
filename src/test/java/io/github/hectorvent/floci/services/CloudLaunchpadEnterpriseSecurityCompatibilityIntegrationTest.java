package io.github.hectorvent.floci.services;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CloudLaunchpadEnterpriseSecurityCompatibilityIntegrationTest {
    private static final String ACCOUNT = "999999999999";
    private static final String REGION = "us-west-2";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void macieShimSurfaceIsNative() {
        get("/admin", "macie2").then().statusCode(200).body("adminAccounts", hasSize(0));
        post("/admin", "macie2", "{\"adminAccountId\":\"" + ACCOUNT + "\"}").then().statusCode(200);
        get("/admin", "macie2").then().statusCode(200).body("adminAccounts[0].accountId", equalTo(ACCOUNT));
        post("/macie", "macie2", "{}").then().statusCode(200);
        get("/macie", "macie2").then().statusCode(200).body("status", equalTo("ENABLED"));
        patch("/admin/configuration", "macie2", "{\"autoEnable\":true}").then().statusCode(200);
        get("/admin/configuration", "macie2").then().statusCode(200).body("autoEnable", equalTo(true));
    }

    @Test
    void inspector2ShimSurfaceIsNative() {
        post("/delegatedadminaccounts/list", "inspector2", "{}").then().statusCode(200);
        post("/delegatedadminaccounts/enable", "inspector2",
                "{\"delegatedAdminAccountId\":\"" + ACCOUNT + "\"}")
                .then().statusCode(200).body("delegatedAdminAccountId", equalTo(ACCOUNT));
        post("/status/batch/get", "inspector2", "{\"accountIds\":[\"" + ACCOUNT + "\"]}")
                .then().statusCode(200).body("accounts[0].accountId", equalTo(ACCOUNT));
        post("/enable", "inspector2", "{\"accountIds\":[\"" + ACCOUNT
                + "\"],\"resourceTypes\":[\"EC2\",\"ECR\",\"LAMBDA\",\"LAMBDA_CODE\"]}")
                .then().statusCode(200);
        post("/organizationconfiguration/update", "inspector2",
                "{\"autoEnable\":{\"ec2\":true,\"ecr\":true,\"lambda\":true,\"lambdaCode\":true}}")
                .then().statusCode(200);
        post("/organizationconfiguration/describe", "inspector2", "{}").then().statusCode(200)
                .body("autoEnable.ec2", equalTo(true)).body("autoEnable.lambdaCode", equalTo(true));
    }

    @Test
    void detectiveShimSurfaceIsNative() {
        post("/orgs/adminAccountslist", "detective", "{}").then().statusCode(200);
        post("/orgs/enableAdminAccount", "detective", "{\"AccountId\":\"" + ACCOUNT + "\"}")
                .then().statusCode(200);
        String graphArn = post("/graphs/list", "detective", "{}").then().statusCode(200)
                .body("GraphList", hasSize(1)).extract().path("GraphList[0].Arn");
        post("/orgs/describeOrganizationConfiguration", "detective", "{\"GraphArn\":\"" + graphArn + "\"}")
                .then().statusCode(200).body("AutoEnable", equalTo(false));
        post("/orgs/updateOrganizationConfiguration", "detective",
                "{\"GraphArn\":\"" + graphArn + "\",\"AutoEnable\":true}").then().statusCode(200);
        post("/graph/members/list", "detective", "{\"GraphArn\":\"" + graphArn + "\"}")
                .then().statusCode(200).body("MemberDetails", hasSize(0));
        post("/graph/members", "detective", "{\"GraphArn\":\"" + graphArn
                + "\",\"Accounts\":[{\"AccountId\":\"111111111111\",\"EmailAddress\":\"security@example.com\"}]}")
                .then().statusCode(200).body("Members[0].Status", equalTo("ACCEPTED_BUT_DISABLED"));
        post("/graph/member/monitoringstate", "detective", "{\"GraphArn\":\"" + graphArn
                + "\",\"AccountId\":\"111111111111\"}").then().statusCode(200);
        post("/graph/members/list", "detective", "{\"GraphArn\":\"" + graphArn + "\"}")
                .then().statusCode(200).body("MemberDetails[0].Status", equalTo("ENABLED"));
    }

    @Test
    void guardDutyMemberShimSurfaceIsNative() {
        String detectorId = post("/detector", "guardduty", "{\"enable\":true}")
                .then().statusCode(200).extract().path("detectorId");
        post("/detector/" + detectorId + "/member", "guardduty",
                "{\"accountDetails\":[{\"accountId\":\"222222222222\",\"email\":\"member@example.com\"}]}")
                .then().statusCode(200).body("unprocessedAccounts", hasSize(0));
        get("/detector/" + detectorId + "/member", "guardduty").then().statusCode(200)
                .body("members[0].accountId", equalTo("222222222222"));
    }

    @Test
    void securityHubShimSurfaceIsNative() {
        get("/organization/admin", "securityhub").then().statusCode(200).body("AdminAccounts", hasSize(0));
        post("/organization/admin/enable", "securityhub", "{\"AdminAccountId\":\"" + ACCOUNT + "\"}")
                .then().statusCode(200);
        post("/accounts", "securityhub", "{}").then().statusCode(200);
        get("/accounts", "securityhub").then().statusCode(200).body("HubArn", notNullValue());
        get("/findingAggregator/list", "securityhub").then().statusCode(200).body("FindingAggregators", hasSize(0));
        String aggregatorArn = post("/findingAggregator/create", "securityhub",
                "{\"RegionLinkingMode\":\"ALL_REGIONS\"}").then().statusCode(200)
                .extract().path("FindingAggregatorArn");
        get("/findingAggregator/get/" + aggregatorArn, "securityhub").then().statusCode(200)
                .body("FindingAggregatorArn", equalTo(aggregatorArn));
        patch("/findingAggregator/update", "securityhub", "{\"FindingAggregatorArn\":\"" + aggregatorArn
                + "\",\"RegionLinkingMode\":\"NO_REGIONS\"}").then().statusCode(200);
        post("/organization/configuration", "securityhub",
                "{\"AutoEnable\":false,\"AutoEnableStandards\":\"NONE\",\"OrganizationConfiguration\":{\"ConfigurationType\":\"CENTRAL\"}}")
                .then().statusCode(200);
        get("/organization/configuration", "securityhub").then().statusCode(200)
                .body("OrganizationConfiguration.ConfigurationType", equalTo("CENTRAL"));

        String policyId = post("/configurationPolicy/create", "securityhub",
                "{\"Name\":\"enterprise-security\",\"ConfigurationPolicy\":{\"SecurityHub\":{\"ServiceEnabled\":true}},\"Tags\":{\"managed_by\":\"cloud-launchpad\"}}")
                .then().statusCode(200).extract().path("Id");
        get("/configurationPolicy/list", "securityhub").then().statusCode(200)
                .body("ConfigurationPolicySummaries", hasSize(1));
        get("/configurationPolicy/get/" + policyId, "securityhub").then().statusCode(200).body("Id", equalTo(policyId));
        String policyArn = "arn:aws:securityhub:" + REGION + ":" + ACCOUNT + ":configuration-policy/" + policyId;
        get("/configurationPolicy/get/" + policyArn, "securityhub").then().statusCode(200).body("Id", equalTo(policyId));
        patch("/configurationPolicy/" + policyId, "securityhub", "{\"Description\":\"updated\"}")
                .then().statusCode(200).body("Description", equalTo("updated"));

        String associationBody = "{\"ConfigurationPolicyIdentifier\":\"" + policyId
                + "\",\"Target\":{\"AccountId\":\"" + ACCOUNT + "\"}}";
        post("/configurationPolicyAssociation/associate", "securityhub", associationBody).then().statusCode(200);
        post("/configurationPolicyAssociation/get", "securityhub",
                "{\"Target\":{\"AccountId\":\"" + ACCOUNT + "\"}}")
                .then().statusCode(200).body("ConfigurationPolicyId", equalTo(policyId));
        post("/configurationPolicyAssociation/list", "securityhub", "{}").then().statusCode(200)
                .body("ConfigurationPolicyAssociationSummaries", hasSize(1));
        get("/tags/arn:aws:securityhub:" + REGION + ":" + ACCOUNT + ":configuration-policy/" + policyId, "securityhub")
                .then().statusCode(200).body("Tags.managed_by", equalTo("cloud-launchpad"));
        String managementAccount = "888888888888";
        String managementAssociation = "{\"ConfigurationPolicyIdentifier\":\"" + policyId
                + "\",\"Target\":{\"AccountId\":\"" + managementAccount + "\"}}";
        post("/configurationPolicyAssociation/associate", "securityhub", managementAssociation).then().statusCode(200);
        post("/configurationPolicyAssociation/get", "securityhub",
                "{\"Target\":{\"AccountId\":\"" + managementAccount + "\"}}")
                .then().statusCode(200).body("AssociationStatus", equalTo("PENDING"));
        post("/configurationPolicyAssociation/get", "securityhub",
                "{\"Target\":{\"AccountId\":\"" + managementAccount + "\"}}")
                .then().statusCode(200).body("AssociationStatus", equalTo("SUCCESS"));
        given().header("Authorization", auth(managementAccount, "securityhub")).get("/accounts")
                .then().statusCode(200);

        post("/configurationPolicyAssociation/disassociate", "securityhub", associationBody).then().statusCode(200);
    }

    @Test
    void malformedSecurityRequestsExposeAwsErrors() {
        post("/delegatedadminaccounts/enable", "inspector2", "{\"delegatedAdminAccountId\":\"bad\"}")
                .then().statusCode(400).body("__type", containsString("ValidationException"));
    }

    private static io.restassured.response.Response get(String path, String service) {
        return given().header("Authorization", auth(service)).get(path);
    }

    private static io.restassured.response.Response post(String path, String service, String body) {
        return given().contentType("application/json").header("Authorization", auth(service)).body(body).post(path);
    }

    private static io.restassured.response.Response patch(String path, String service, String body) {
        return given().contentType("application/json").header("Authorization", auth(service)).body(body).patch(path);
    }

    private static String auth(String service) {
        return auth(ACCOUNT, service);
    }

    private static String auth(String account, String service) {
        return "AWS4-HMAC-SHA256 Credential=" + account + "/20260904/" + REGION + "/" + service + "/aws4_request";
    }
}
