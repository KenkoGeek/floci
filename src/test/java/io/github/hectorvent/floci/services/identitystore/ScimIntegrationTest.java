package io.github.hectorvent.floci.services.identitystore;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ScimIntegrationTest {
    private static final String TENANT = "9067f2a3c1-00000000-0000-0000-0000-000000000000";
    private static final String STORE = "d-9067f2a3c1";
    private static final String BEARER = "Bearer floci-scim-token";
    private static final String AWS_AUTH = "AWS4-HMAC-SHA256 Credential=AKID/20260904/us-east-1/identitystore/aws4_request";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void createGroupUsesAwsScimShapeAndPersistsToIdentityStore() {
        String groupId = given()
                .contentType("application/json")
                .header("Authorization", BEARER)
                .body("{\"externalId\":\"701984\",\"displayName\":\"SCIM Platform Admins\"}")
            .when()
                .post("/" + TENANT + "/scim/v2/Groups")
            .then()
                .statusCode(201)
                .body("schemas[0]", equalTo("urn:ietf:params:scim:schemas:core:2.0:Group"))
                .body("id", notNullValue())
                .body("externalId", equalTo("701984"))
                .body("displayName", equalTo("SCIM Platform Admins"))
                .body("meta.resourceType", equalTo("Group"))
                .body("meta.created", notNullValue())
                .body("meta.lastModified", notNullValue())
                .extract().path("id");

        given()
                .contentType("application/x-amz-json-1.1")
                .header("Authorization", AWS_AUTH)
                .header("X-Amz-Target", "AWSIdentityStore.ListGroups")
                .body("{\"IdentityStoreId\":\"" + STORE + "\"}")
            .when()
                .post("/")
            .then()
                .statusCode(200)
                .body("Groups.GroupId", hasItem(groupId))
                .body("Groups.DisplayName", hasItem("SCIM Platform Admins"));
    }

    @Test
    void createGroupRequiresBearerAndKnownTenant() {
        given()
                .contentType("application/json")
                .body("{\"displayName\":\"Unauthorized Group\"}")
            .when()
                .post("/" + TENANT + "/scim/v2/Groups")
            .then()
                .statusCode(401)
                .body("schemas[0]", equalTo("urn:ietf:params:scim:api:messages:2.0:Error"))
                .body("status", equalTo("401"));

        given()
                .contentType("application/json")
                .header("Authorization", BEARER)
                .body("{\"displayName\":\"Wrong Tenant Group\"}")
            .when()
                .post("/aaaaaaaaaa-00000000-0000-0000-0000-000000000000/scim/v2/Groups")
            .then()
                .statusCode(401)
                .body("status", equalTo("401"));
    }

    @Test
    void createGroupValidatesRequiredDisplayNameAndMemberLimit() {
        given()
                .contentType("application/json")
                .header("Authorization", BEARER)
                .body("{}")
            .when()
                .post("/" + TENANT + "/scim/v2/Groups")
            .then()
                .statusCode(400)
                .body("status", equalTo("400"));

        StringBuilder members = new StringBuilder("[\n");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                members.append(',');
            }
            members.append("{\"value\":\"11111111-2222-3333-4444-")
                    .append(String.format("%012d", i))
                    .append("\"}");
        }
        members.append(']');

        given()
                .contentType("application/json")
                .header("Authorization", BEARER)
                .body("{\"displayName\":\"Too Many Members\",\"members\":" + members + "}")
            .when()
                .post("/" + TENANT + "/scim/v2/Groups")
            .then()
                .statusCode(400)
                .body("status", equalTo("400"));
    }
}
