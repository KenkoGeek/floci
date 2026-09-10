package io.github.hectorvent.floci.services.ssooidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.ssooidc.model.RegisteredClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsoOidcServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private SsoOidcService service;

    @BeforeEach
    void setUp() {
        service = new SsoOidcService(new InMemoryStorage<>(), new InMemoryStorage<>(), "http://localhost:4566/");
    }

    @Test
    void registerClientPersistsAwsOidcMetadata() {
        ObjectNode request = mapper.createObjectNode();
        request.put("clientName", "Floci CLI");
        request.put("clientType", "public");
        request.putArray("scopes").add("sso:account:access");
        request.putArray("redirectUris").add("http://127.0.0.1:8400/callback");
        request.putArray("grantTypes")
                .add("authorization_code")
                .add("refresh_token");

        RegisteredClient client = service.registerClient(request);

        assertNotNull(client.clientId());
        assertEquals(32, client.clientId().length());
        assertNotNull(client.clientSecret());
        assertEquals(64, client.clientSecret().length());
        assertTrue(client.clientSecretExpiresAt() > client.clientIdIssuedAt());
        assertEquals(client, service.requireClient(client.clientId()));
        assertEquals("http://localhost:4566/authorize", service.authorizationEndpoint());
        assertEquals("http://localhost:4566/token", service.tokenEndpoint());
    }

    @Test
    void startDeviceAuthorizationValidatesCredentialsAndPersistsChallenge() {
        ObjectNode register = mapper.createObjectNode();
        register.put("clientName", "Device Client");
        register.put("clientType", "public");
        register.putArray("grantTypes").add("urn:ietf:params:oauth:grant-type:device_code");
        RegisteredClient client = service.registerClient(register);

        ObjectNode request = mapper.createObjectNode();
        request.put("clientId", client.clientId());
        request.put("clientSecret", client.clientSecret());
        request.put("startUrl", "https://example.awsapps.com/start");
        var authorization = service.startDeviceAuthorization(request);

        assertEquals(client.clientId(), authorization.clientId());
        assertEquals(64, authorization.deviceCode().length());
        assertTrue(authorization.userCode().matches("[0-9A-F]{4}-[0-9A-F]{4}"));
        assertEquals(authorization, service.requireDeviceAuthorization(authorization.deviceCode()));
        assertEquals("http://localhost:4566/device", service.verificationUri());
        assertTrue(service.verificationUriComplete(authorization).endsWith("user_code=" + authorization.userCode()));

        ObjectNode wrongSecret = request.deepCopy().put("clientSecret", "wrong");
        assertOidcError("invalid_client", () -> service.startDeviceAuthorization(wrongSecret));
    }

    @Test
    void registerClientValidatesPublicClientAndGrantType() {
        ObjectNode confidential = mapper.createObjectNode();
        confidential.put("clientName", "Bad Client");
        confidential.put("clientType", "confidential");
        assertOidcError("invalid_client_metadata", () -> service.registerClient(confidential));

        ObjectNode unsupportedGrant = mapper.createObjectNode();
        unsupportedGrant.put("clientName", "Bad Grant");
        unsupportedGrant.put("clientType", "public");
        unsupportedGrant.putArray("grantTypes").add("client_credentials");
        assertOidcError("unsupported_grant_type", () -> service.registerClient(unsupportedGrant));
    }

    @Test
    void registerClientValidatesRequestShapes() {
        ObjectNode missingName = mapper.createObjectNode().put("clientType", "public");
        assertOidcError("invalid_request", () -> service.registerClient(missingName));

        ObjectNode badScopes = mapper.createObjectNode();
        badScopes.put("clientName", "Bad Scope");
        badScopes.put("clientType", "public");
        badScopes.put("scopes", "not-an-array");
        assertOidcError("invalid_request", () -> service.registerClient(badScopes));

        ObjectNode badArn = mapper.createObjectNode();
        badArn.put("clientName", "Bad ARN");
        badArn.put("clientType", "public");
        badArn.put("entitledApplicationArn", "not-an-arn");
        assertOidcError("invalid_client_metadata", () -> service.registerClient(badArn));
    }

    private static void assertOidcError(String code, Runnable action) {
        SsoOidcException error = assertThrows(SsoOidcException.class, action::run);
        assertEquals(code, error.error());
    }
}
