package com.floci.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssooidc.SsoOidcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("IAM Identity Center OIDC")
class SsoOidcTest {

    @Test
    @DisplayName("registers a public OIDC client through the AWS SDK")
    void registerClientUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses emulator-only client registration");

        try (SsoOidcClient oidc = TestFixtures.ssoOidcClient()) {
            var response = oidc.registerClient(request -> request
                    .clientName("Floci SDK CLI")
                    .clientType("public")
                    .grantTypes("authorization_code", "refresh_token")
                    .redirectUris("http://127.0.0.1:8400/callback")
                    .scopes("sso:account:access"));

            assertThat(response.clientId()).matches("[0-9a-f]{32}");
            assertThat(response.clientSecret()).matches("[0-9a-f]{64}");
            assertThat(response.clientIdIssuedAt()).isPositive();
            assertThat(response.clientSecretExpiresAt()).isGreaterThan(response.clientIdIssuedAt());
            assertThat(response.authorizationEndpoint()).endsWith("/authorize");
            assertThat(response.tokenEndpoint()).endsWith("/token");
        }
    }

    @Test
    @DisplayName("starts device authorization through the AWS SDK")
    void startDeviceAuthorizationUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses emulator-only device authorization");

        try (SsoOidcClient oidc = TestFixtures.ssoOidcClient()) {
            var client = oidc.registerClient(request -> request
                    .clientName("Floci Device SDK")
                    .clientType("public")
                    .grantTypes("urn:ietf:params:oauth:grant-type:device_code"));
            var response = oidc.startDeviceAuthorization(request -> request
                    .clientId(client.clientId())
                    .clientSecret(client.clientSecret())
                    .startUrl("https://example.awsapps.com/start"));

            assertThat(response.deviceCode()).matches("[0-9a-f]{64}");
            assertThat(response.userCode()).matches("[0-9A-F]{4}-[0-9A-F]{4}");
            assertThat(response.verificationUri()).endsWith("/device");
            assertThat(response.verificationUriComplete()).contains("user_code=" + response.userCode());
            assertThat(response.expiresIn()).isPositive();
            assertThat(response.interval()).isEqualTo(5);

            try {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(response.verificationUriComplete())).GET().build();
                var browserResponse = java.net.http.HttpClient.newHttpClient().send(
                        request, java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(browserResponse.statusCode()).isEqualTo(200);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            var token = oidc.createToken(request -> request
                    .clientId(client.clientId())
                    .clientSecret(client.clientSecret())
                    .grantType("urn:ietf:params:oauth:grant-type:device_code")
                    .deviceCode(response.deviceCode()));
            assertThat(token.tokenType()).isEqualTo("Bearer");
            assertThat(token.accessToken()).isNotBlank();
            assertThat(token.refreshToken()).isNotBlank();

            var refreshed = oidc.createToken(request -> request
                    .clientId(client.clientId())
                    .clientSecret(client.clientSecret())
                    .grantType("refresh_token")
                    .refreshToken(token.refreshToken()));
            assertThat(refreshed.accessToken()).isNotEqualTo(token.accessToken());
        }
    }
}
