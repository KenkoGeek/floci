package io.github.hectorvent.floci.services.ssooidc;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;

@QuarkusTest
class SsoOidcIntegrationTest {

    @Test
    void registerClientReturnsAwsOidcShapeWithoutSigV4() {
        given()
                .contentType("application/json")
                .body("{\"clientName\":\"Floci CLI\",\"clientType\":\"public\","
                        + "\"grantTypes\":[\"authorization_code\",\"refresh_token\"],"
                        + "\"redirectUris\":[\"http://127.0.0.1:8400/callback\"],"
                        + "\"scopes\":[\"sso:account:access\"]}")
            .when().post("/client/register")
            .then().statusCode(200)
                .body("clientId", matchesPattern("[0-9a-f]{32}"))
                .body("clientSecret", matchesPattern("[0-9a-f]{64}"))
                .body("clientIdIssuedAt", greaterThan(0))
                .body("clientSecretExpiresAt", greaterThan(0))
                .body("authorizationEndpoint", equalTo("http://localhost:4566/authorize"))
                .body("tokenEndpoint", equalTo("http://localhost:4566/token"));
    }

    @Test
    void registerClientReturnsOidcErrorShape() {
        given()
                .contentType("application/json")
                .body("{\"clientName\":\"Bad Client\",\"clientType\":\"confidential\"}")
            .when().post("/client/register")
            .then().statusCode(400)
                .body("error", equalTo("invalid_client_metadata"));
    }
}
