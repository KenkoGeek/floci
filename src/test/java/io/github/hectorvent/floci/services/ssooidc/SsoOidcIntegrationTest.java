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
    void startDeviceAuthorizationUsesRegisteredClient() {
        var registration = given()
                .contentType("application/json")
                .body("{\"clientName\":\"Device Integration\",\"clientType\":\"public\","
                        + "\"grantTypes\":[\"urn:ietf:params:oauth:grant-type:device_code\"]}")
            .when().post("/client/register")
            .then().statusCode(200)
            .extract().response();
        String clientId = registration.path("clientId");
        String clientSecret = registration.path("clientSecret");

        given()
                .contentType("application/json")
                .body("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + clientSecret
                        + "\",\"startUrl\":\"https://example.awsapps.com/start\"}")
            .when().post("/device_authorization")
            .then().statusCode(200)
                .body("deviceCode", matchesPattern("[0-9a-f]{64}"))
                .body("userCode", matchesPattern("[0-9A-F]{4}-[0-9A-F]{4}"))
                .body("verificationUri", equalTo("http://localhost:4566/device"))
                .body("expiresIn", greaterThan(0))
                .body("interval", equalTo(5));

        given()
                .contentType("application/json")
                .body("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"wrong\","
                        + "\"startUrl\":\"https://example.awsapps.com/start\"}")
            .when().post("/device_authorization")
            .then().statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    @Test
    void createTokenCompletesDeviceFlowAndRefreshesToken() {
        var registration = given()
                .contentType("application/json")
                .body("{\"clientName\":\"Token Integration\",\"clientType\":\"public\","
                        + "\"grantTypes\":[\"urn:ietf:params:oauth:grant-type:device_code\",\"refresh_token\"]}")
            .when().post("/client/register")
            .then().statusCode(200)
            .extract().response();
        String clientId = registration.path("clientId");
        String clientSecret = registration.path("clientSecret");
        var authorization = given()
                .contentType("application/json")
                .body("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + clientSecret
                        + "\",\"startUrl\":\"https://example.awsapps.com/start\"}")
            .when().post("/device_authorization")
            .then().statusCode(200)
            .extract().response();
        String deviceCode = authorization.path("deviceCode");
        String userCode = authorization.path("userCode");

        given().queryParam("user_code", userCode)
            .when().get("/device")
            .then().statusCode(200).body("status", equalTo("authorized"));

        var token = given()
                .contentType("application/json")
                .body("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + clientSecret
                        + "\",\"grantType\":\"urn:ietf:params:oauth:grant-type:device_code\","
                        + "\"deviceCode\":\"" + deviceCode + "\"}")
            .when().post("/token")
            .then().statusCode(200)
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", matchesPattern("[0-9a-f]{64}"))
            .extract().response();
        String refreshToken = token.path("refreshToken");

        given()
                .contentType("application/json")
                .body("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + clientSecret
                        + "\",\"grantType\":\"refresh_token\",\"refreshToken\":\"" + refreshToken + "\"}")
            .when().post("/token")
            .then().statusCode(200)
                .body("tokenType", equalTo("Bearer"));
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
