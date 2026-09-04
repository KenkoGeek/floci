package io.github.hectorvent.floci.services;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CloudLaunchpadEnterpriseIdentityGovernanceCompatibilityIntegrationTest {
    private static final String ACCOUNT = "999999999999";
    private static final String REGION = "us-west-2";
    private static final String JSON11 = "application/x-amz-json-1.1";

    @BeforeAll
    static void configureRestAssured() { RestAssuredJsonUtils.configureAwsContentTypes(); }

    @Test
    void ssoAdminShimSurfaceIsNative() {
        String instanceArn = json11("sso", "SWBExternalService.ListInstances", "{}")
                .then().statusCode(200).extract().path("Instances[0].InstanceArn");
        json11("sso", "SWBExternalService.ListPermissionSets", body("InstanceArn", instanceArn))
                .then().statusCode(200).body("PermissionSets", hasSize(0));
        String permissionSetArn = json11("sso", "SWBExternalService.CreatePermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"Name\":\"EnterpriseAdmins\",\"SessionDuration\":\"PT8H\"}")
                .then().statusCode(200).extract().path("PermissionSet.PermissionSetArn");
        json11("sso", "SWBExternalService.DescribePermissionSet",
                permissionSetRequest(instanceArn, permissionSetArn)).then().statusCode(200)
                .body("PermissionSet.Name", equalTo("EnterpriseAdmins"));
        json11("sso", "SWBExternalService.UpdatePermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"Description\":\"Enterprise administrators\",\"SessionDuration\":\"PT4H\"}")
                .then().statusCode(200);
        String policyArn = "arn:aws:iam::aws:policy/ReadOnlyAccess";
        json11("sso", "SWBExternalService.AttachManagedPolicyToPermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"ManagedPolicyArn\":\"" + policyArn + "\"}").then().statusCode(200);
        json11("sso", "SWBExternalService.ListManagedPoliciesInPermissionSet",
                permissionSetRequest(instanceArn, permissionSetArn)).then().statusCode(200)
                .body("AttachedManagedPolicies.Arn", hasItem(policyArn));
        json11("sso", "SWBExternalService.PutInlinePolicyToPermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"InlinePolicy\":\"{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[]}\"}")
                .then().statusCode(200);
        json11("sso", "SWBExternalService.DeleteInlinePolicyFromPermissionSet",
                permissionSetRequest(instanceArn, permissionSetArn)).then().statusCode(200);
        json11("sso", "SWBExternalService.DetachManagedPolicyFromPermissionSet",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn
                        + "\",\"ManagedPolicyArn\":\"" + policyArn + "\"}").then().statusCode(200);

        String principalId = "00000000-0000-0000-0000-000000000001";
        String assignment = "{\"InstanceArn\":\"" + instanceArn + "\",\"TargetId\":\"" + ACCOUNT
                + "\",\"TargetType\":\"AWS_ACCOUNT\",\"PermissionSetArn\":\"" + permissionSetArn
                + "\",\"PrincipalType\":\"GROUP\",\"PrincipalId\":\"" + principalId + "\"}";
        String requestId = json11("sso", "SWBExternalService.CreateAccountAssignment", assignment)
                .then().statusCode(200).extract().path("AccountAssignmentCreationStatus.RequestId");
        json11("sso", "SWBExternalService.DescribeAccountAssignmentCreationStatus",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"AccountAssignmentCreationRequestId\":\"" + requestId + "\"}")
                .then().statusCode(200).body("AccountAssignmentCreationStatus.Status", equalTo("SUCCEEDED"));
        json11("sso", "SWBExternalService.ListAccountAssignments",
                "{\"InstanceArn\":\"" + instanceArn + "\",\"AccountId\":\"" + ACCOUNT
                        + "\",\"PermissionSetArn\":\"" + permissionSetArn + "\"}")
                .then().statusCode(200).body("AccountAssignments[0].PrincipalId", equalTo(principalId));
    }

    @Test
    void identityStoreShimSurfaceIsNative() {
        String store = "d-1234567890";
        json11("identitystore", "AWSIdentityStore.ListGroups", "{\"IdentityStoreId\":\"" + store + "\"}")
                .then().statusCode(200).body("Groups", hasSize(0));
        String group = json11("identitystore", "AWSIdentityStore.CreateGroup",
                "{\"IdentityStoreId\":\"" + store + "\",\"DisplayName\":\"EnterpriseAdmins\"}")
                .then().statusCode(200).extract().path("GroupId");
        json11("identitystore", "AWSIdentityStore.ListUsers", "{\"IdentityStoreId\":\"" + store + "\"}")
                .then().statusCode(200).body("Users", hasSize(0));
        String user = json11("identitystore", "AWSIdentityStore.CreateUser",
                "{\"IdentityStoreId\":\"" + store + "\",\"UserName\":\"enterprise@example.com\"}")
                .then().statusCode(200).extract().path("UserId");
        String membership = "{\"IdentityStoreId\":\"" + store + "\",\"GroupIds\":[\"" + group
                + "\"],\"MemberId\":{\"UserId\":\"" + user + "\"}}";
        json11("identitystore", "AWSIdentityStore.IsMemberInGroups", membership)
                .then().statusCode(200).body("Results[0].MembershipExists", equalTo(false));
        json11("identitystore", "AWSIdentityStore.CreateGroupMembership",
                "{\"IdentityStoreId\":\"" + store + "\",\"GroupId\":\"" + group
                        + "\",\"MemberId\":{\"UserId\":\"" + user + "\"}}")
                .then().statusCode(200).body("MembershipId", notNullValue());
        json11("identitystore", "AWSIdentityStore.IsMemberInGroups", membership)
                .then().statusCode(200).body("Results[0].MembershipExists", equalTo(true));
    }

    @Test
    void accountAccessAnalyzerAndServiceQuotasShimSurfaceIsNative() {
        postJson("account", "/putAlternateContact",
                "{\"AlternateContactType\":\"SECURITY\",\"Name\":\"Security\",\"Title\":\"Owner\",\"EmailAddress\":\"security@example.com\",\"PhoneNumber\":\"+12025550123\"}")
                .then().statusCode(200);
        given().contentType("application/json").header("Authorization", auth("access-analyzer"))
                .body("{\"analyzerName\":\"enterprise-analyzer\",\"type\":\"ORGANIZATION\"}")
                .put("/analyzer").then().statusCode(200).body("arn", notNullValue());
        given().header("Authorization", auth("access-analyzer")).get("/analyzer")
                .then().statusCode(200).body("analyzers[0].name", equalTo("enterprise-analyzer"));
        given().header("Authorization", auth("access-analyzer")).delete("/analyzer/enterprise-analyzer")
                .then().statusCode(200);

        json11("servicequotas", "ServiceQuotasV20190624.ListServiceQuotas",
                "{\"ServiceCode\":\"organizations\"}").then().statusCode(200)
                .body("Quotas.QuotaCode", hasItem("L-FLOCIACCOUNTS"));
        json11("servicequotas", "ServiceQuotasV20190624.GetServiceQuota",
                "{\"ServiceCode\":\"organizations\",\"QuotaCode\":\"L-FLOCIACCOUNTS\"}")
                .then().statusCode(200).body("Quota.Value", equalTo(50.0f));
        json11("servicequotas", "ServiceQuotasV20190624.ListRequestedServiceQuotaChangeHistoryByQuota",
                "{\"ServiceCode\":\"organizations\",\"QuotaCode\":\"L-FLOCIACCOUNTS\"}")
                .then().statusCode(200).body("RequestedQuotas", hasSize(0));
    }

    @Test
    void budgetsShimSurfaceIsNative() {
        String name = "enterprise-budget";
        String notification = "{\"ComparisonOperator\":\"GREATER_THAN\",\"NotificationType\":\"ACTUAL\",\"Threshold\":80,\"ThresholdType\":\"PERCENTAGE\"}";
        json11("budgets", "AWSBudgetServiceGateway.CreateBudget",
                "{\"AccountId\":\"" + ACCOUNT + "\",\"Budget\":{\"BudgetName\":\"" + name
                        + "\",\"BudgetType\":\"COST\",\"TimeUnit\":\"MONTHLY\",\"BudgetLimit\":{\"Amount\":\"1000\",\"Unit\":\"USD\"}},\"ResourceTags\":[{\"Key\":\"managed_by\",\"Value\":\"cloud-launchpad\"}]}")
                .then().statusCode(200);
        json11("budgets", "AWSBudgetServiceGateway.DescribeBudget", budgetRef(name))
                .then().statusCode(200).body("Budget.BudgetName", equalTo(name));
        json11("budgets", "AWSBudgetServiceGateway.ListTagsForResource",
                "{\"ResourceARN\":\"arn:aws:budgets::" + ACCOUNT + ":budget/" + name + "\"}")
                .then().statusCode(200).body("ResourceTags[0].Key", equalTo("managed_by"));
        json11("budgets", "AWSBudgetServiceGateway.UpdateBudget",
                "{\"AccountId\":\"" + ACCOUNT + "\",\"NewBudget\":{\"BudgetName\":\"" + name
                        + "\",\"BudgetType\":\"COST\",\"TimeUnit\":\"MONTHLY\",\"BudgetLimit\":{\"Amount\":\"1500\",\"Unit\":\"USD\"}}}")
                .then().statusCode(200);
        json11("budgets", "AWSBudgetServiceGateway.CreateNotification",
                "{\"AccountId\":\"" + ACCOUNT + "\",\"BudgetName\":\"" + name + "\",\"Notification\":" + notification
                        + ",\"Subscribers\":[{\"SubscriptionType\":\"EMAIL\",\"Address\":\"ops@example.com\"}]}")
                .then().statusCode(200);
        json11("budgets", "AWSBudgetServiceGateway.DescribeNotificationsForBudget", budgetRef(name))
                .then().statusCode(200).body("Notifications", hasSize(1));
        json11("budgets", "AWSBudgetServiceGateway.DescribeSubscribersForNotification",
                "{\"AccountId\":\"" + ACCOUNT + "\",\"BudgetName\":\"" + name + "\",\"Notification\":" + notification + "}")
                .then().statusCode(200).body("Subscribers[0].Address", equalTo("ops@example.com"));
        json11("budgets", "AWSBudgetServiceGateway.DeleteNotification",
                "{\"AccountId\":\"" + ACCOUNT + "\",\"BudgetName\":\"" + name + "\",\"Notification\":" + notification + "}")
                .then().statusCode(200);
        json11("budgets", "AWSBudgetServiceGateway.DeleteBudget", budgetRef(name)).then().statusCode(200);
    }

    private static String permissionSetRequest(String instanceArn, String permissionSetArn) {
        return "{\"InstanceArn\":\"" + instanceArn + "\",\"PermissionSetArn\":\"" + permissionSetArn + "\"}";
    }

    private static String budgetRef(String name) {
        return "{\"AccountId\":\"" + ACCOUNT + "\",\"BudgetName\":\"" + name + "\"}";
    }

    private static String body(String key, String value) { return "{\"" + key + "\":\"" + value + "\"}"; }

    private static io.restassured.response.Response postJson(String service, String path, String body) {
        return given().contentType("application/json").header("Authorization", auth(service)).body(body).post(path);
    }

    private static io.restassured.response.Response json11(String service, String target, String body) {
        return given().contentType(JSON11).header("Authorization", auth(service)).header("X-Amz-Target", target)
                .body(body).post("/");
    }

    private static String auth(String service) {
        return "AWS4-HMAC-SHA256 Credential=" + ACCOUNT + "/20260904/" + REGION + "/" + service + "/aws4_request";
    }
}
