package com.floci.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.identitystore.model.CreateUserRequest;
import software.amazon.awssdk.services.sso.SsoClient;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.PrincipalType;
import software.amazon.awssdk.services.ssoadmin.model.TargetType;
import software.amazon.awssdk.services.ssooidc.SsoOidcClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("IAM Identity Center access portal")
class SsoPortalTest {

    @Test
    @DisplayName("lists accounts assigned to the authenticated user through the AWS SDK")
    void listAccountsUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses emulator-only local sign-in completion");

        try (SsoAdminClient admin = TestFixtures.ssoAdminClient();
             IdentitystoreClient identityStore = TestFixtures.identityStoreClient();
             SsoOidcClient oidc = TestFixtures.ssoOidcClient();
             SsoClient portal = TestFixtures.ssoPortalClient()) {
            var instance = admin.listInstances(request -> {}).instances().get(0);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String userId = identityStore.createUser(CreateUserRequest.builder()
                    .identityStoreId(instance.identityStoreId())
                    .userName("portal-sdk-" + suffix + "@example.com")
                    .displayName("Portal SDK " + suffix)
                    .build()).userId();
            String permissionSetArn = admin.createPermissionSet(request -> request
                    .instanceArn(instance.instanceArn())
                    .name("PortalSdk" + suffix))
                    .permissionSet().permissionSetArn();
            String accountId = "111111111111";
            admin.createAccountAssignment(request -> request
                    .instanceArn(instance.instanceArn())
                    .targetId(accountId)
                    .targetType(TargetType.AWS_ACCOUNT)
                    .permissionSetArn(permissionSetArn)
                    .principalType(PrincipalType.USER)
                    .principalId(userId));

            var client = oidc.registerClient(request -> request
                    .clientName("Portal SDK " + suffix)
                    .clientType("public")
                    .grantTypes("urn:ietf:params:oauth:grant-type:device_code")
                    .scopes("sso:account:access"));
            var authorization = oidc.startDeviceAuthorization(request -> request
                    .clientId(client.clientId())
                    .clientSecret(client.clientSecret())
                    .startUrl("https://example.awsapps.com/start"));
            authorizeDevice(authorization.verificationUriComplete(), userId);
            String accessToken = oidc.createToken(request -> request
                    .clientId(client.clientId())
                    .clientSecret(client.clientSecret())
                    .grantType("urn:ietf:params:oauth:grant-type:device_code")
                    .deviceCode(authorization.deviceCode()))
                    .accessToken();

            var response = portal.listAccounts(request -> request
                    .accessToken(accessToken)
                    .maxResults(100));
            assertThat(response.accountList())
                    .extracting(software.amazon.awssdk.services.sso.model.AccountInfo::accountId)
                    .contains(accountId);

            var roles = portal.listAccountRoles(request -> request
                    .accessToken(accessToken)
                    .accountId(accountId)
                    .maxResults(100));
            assertThat(roles.roleList())
                    .extracting(software.amazon.awssdk.services.sso.model.RoleInfo::roleName)
                    .contains("PortalSdk" + suffix);

            assertThatThrownBy(() -> portal.listAccounts(request -> request.accessToken("invalid")))
                    .isInstanceOf(software.amazon.awssdk.services.sso.model.UnauthorizedException.class);
        }
    }

    private static void authorizeDevice(String verificationUriComplete, String userId) {
        try {
            String separator = verificationUriComplete.contains("?") ? "&" : "?";
            URI uri = URI.create(verificationUriComplete + separator + "principal_id="
                    + URLEncoder.encode(userId, StandardCharsets.UTF_8));
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
