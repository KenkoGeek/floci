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
}
