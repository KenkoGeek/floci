package io.github.hectorvent.floci.services;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/** Covers the AWS wire surfaces that replace Cloud Launchpad Core's local test shims. */
@QuarkusTest
class CloudLaunchpadCoreCompatibilityIntegrationTest {
    private static final String JSON11 = "application/x-amz-json-1.1";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void ssoAdminPermissionSetLifecycle() {
        String instanceArn = json11("sso", "SWBExternalService.ListInstances", "{}")
                .then().statusCode(200).extract().path("Instances[0].InstanceArn");
        String arn = json11("sso", "SWBExternalService.CreatePermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"CoreAdmins\",\"SessionDuration\":\"PT8H\"}")
                .then().statusCode(200).body("PermissionSet.PermissionSetArn", notNullValue())
                .extract().path("PermissionSet.PermissionSetArn");
        json11("sso", "SWBExternalService.DescribePermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + arn + "\"}")
                .then().statusCode(200).body("PermissionSet.Name", equalTo("CoreAdmins"));
    }

    @Test
    void identityStoreGroupUserAndMembershipLifecycle() {
        String store = "d-1234567890";
        String group = json11("identitystore", "AWSIdentityStore.CreateGroup",
                "{\"IdentityStoreId\":\"" + store + "\",\"DisplayName\":\"CoreAdmins\"}")
                .then().statusCode(200).extract().path("GroupId");
        String user = json11("identitystore", "AWSIdentityStore.CreateUser",
                "{\"IdentityStoreId\":\"" + store + "\",\"UserName\":\"core@example.com\"}")
                .then().statusCode(200).extract().path("UserId");
        json11("identitystore", "AWSIdentityStore.CreateGroupMembership",
                "{\"IdentityStoreId\":\"" + store + "\",\"GroupId\":\"" + group + "\",\"MemberId\":{\"UserId\":\"" + user + "\"}}")
                .then().statusCode(200).body("MembershipId", notNullValue());
        json11("identitystore", "AWSIdentityStore.IsMemberInGroups",
                "{\"IdentityStoreId\":\"" + store + "\",\"GroupIds\":[\"" + group + "\"],\"MemberId\":{\"UserId\":\"" + user + "\"}}")
                .then().statusCode(200).body("Results[0].MembershipExists", equalTo(true));
    }

    @Test
    void budgetsLifecycle() {
        json11("budgets", "AWSBudgetServiceGateway.CreateBudget",
                "{\"AccountId\":\"000000000000\",\"Budget\":{\"BudgetName\":\"core-budget\",\"BudgetType\":\"COST\",\"TimeUnit\":\"MONTHLY\",\"BudgetLimit\":{\"Amount\":\"100\",\"Unit\":\"USD\"}}}")
                .then().statusCode(200);
        json11("budgets", "AWSBudgetServiceGateway.DescribeBudget",
                "{\"AccountId\":\"000000000000\",\"BudgetName\":\"core-budget\"}")
                .then().statusCode(200).body("Budget.BudgetName", equalTo("core-budget"));
    }

    @Test
    void accountAlternateContactLifecycle() {
        given().contentType("application/json").header("Authorization", auth("account"))
                .body("{\"AlternateContactType\":\"SECURITY\",\"Name\":\"Security\",\"Title\":\"Owner\",\"EmailAddress\":\"security@example.com\",\"PhoneNumber\":\"+12025550123\"}")
                .post("/putAlternateContact").then().statusCode(200);
        given().contentType("application/json").header("Authorization", auth("account"))
                .body("{\"AlternateContactType\":\"SECURITY\"}")
                .post("/getAlternateContact").then().statusCode(200)
                .body("AlternateContact.EmailAddress", equalTo("security@example.com"));
    }

    @Test
    void accessAnalyzerLifecycle() {
        given().contentType("application/json").header("Authorization", auth("access-analyzer"))
                .body("{\"analyzerName\":\"core-analyzer\",\"type\":\"ORGANIZATION\"}")
                .put("/analyzer").then().statusCode(200).body("arn", notNullValue());
        given().header("Authorization", auth("access-analyzer"))
                .get("/analyzer").then().statusCode(200).body("analyzers[0].name", equalTo("core-analyzer"));
        given().header("Authorization", auth("access-analyzer"))
                .delete("/analyzer/core-analyzer").then().statusCode(200);
    }

    @Test
    void securityServicesExposeCoreOrganizationFlows() {
        given().contentType("application/json").header("Authorization", auth("securityhub"))
                .body("{\"AdminAccountId\":\"111111111111\"}").post("/organization/admin/enable")
                .then().statusCode(200);
        given().header("Authorization", auth("securityhub")).get("/organization/admin")
                .then().statusCode(200).body("AdminAccounts[0].AccountId", equalTo("111111111111"));

        given().contentType("application/json").header("Authorization", auth("macie2"))
                .body("{\"adminAccountId\":\"222222222222\"}").post("/admin").then().statusCode(200);
        given().header("Authorization", auth("macie2")).get("/admin")
                .then().statusCode(200).body("adminAccounts[0].accountId", equalTo("222222222222"));

        given().contentType("application/json").header("Authorization", auth("inspector2"))
                .body("{\"delegatedAdminAccountId\":\"333333333333\"}").post("/delegatedadminaccounts/enable")
                .then().statusCode(200);
        given().contentType("application/json").header("Authorization", auth("inspector2"))
                .body("{}").post("/delegatedadminaccounts/list")
                .then().statusCode(200).body("delegatedAdminAccounts[0].accountId", equalTo("333333333333"));

        given().contentType("application/json").header("Authorization", auth("detective"))
                .body("{\"AccountId\":\"444444444444\"}").post("/orgs/enableAdminAccount").then().statusCode(200);
        given().contentType("application/json").header("Authorization", auth("detective"))
                .body("{}").post("/orgs/adminAccountslist")
                .then().statusCode(200).body("Administrators[0].AccountId", equalTo("444444444444"));
    }

    @Test
    void logsCrossAccountLifecycle() {
        String target = "arn:aws:kinesis:us-east-1:000000000000:stream/core";
        String role = "arn:aws:iam::000000000000:role/core-logs";
        json11("logs", "Logs_20140328.PutDestination",
                "{\"destinationName\":\"core-destination\",\"targetArn\":\"" + target + "\",\"roleArn\":\"" + role + "\"}")
                .then().statusCode(200).body("destination.destinationName", equalTo("core-destination"));
        json11("logs", "Logs_20140328.PutDestinationPolicy",
                "{\"destinationName\":\"core-destination\",\"accessPolicy\":\"{}\"}")
                .then().statusCode(200);
        json11("logs", "Logs_20140328.PutAccountPolicy",
                "{\"policyName\":\"core-policy\",\"policyDocument\":\"{}\",\"policyType\":\"SUBSCRIPTION_FILTER_POLICY\",\"scope\":\"ALL\"}")
                .then().statusCode(200).body("accountPolicy.policyName", equalTo("core-policy"));
        json11("logs", "Logs_20140328.DescribeAccountPolicies",
                "{\"policyType\":\"SUBSCRIPTION_FILTER_POLICY\"}")
                .then().statusCode(200).body("accountPolicies", hasSize(1));
    }

    @Test
    void serviceQuotaHistoryOperationIsNative() {
        json11("servicequotas", "ServiceQuotasV20190624.ListRequestedServiceQuotaChangeHistoryByQuota",
                "{\"ServiceCode\":\"organizations\",\"QuotaCode\":\"L-FLOCIACCOUNTS\"}")
                .then().statusCode(200).body("RequestedQuotas", hasSize(0));
    }

    private static io.restassured.response.Response json11(String service, String target, String body) {
        return given().contentType(JSON11).header("Authorization", auth(service)).header("X-Amz-Target", target)
                .body(body).post("/");
    }

    private static String auth(String service) {
        return "AWS4-HMAC-SHA256 Credential=000000000000/20260903/us-east-1/" + service + "/aws4_request";
    }
}
