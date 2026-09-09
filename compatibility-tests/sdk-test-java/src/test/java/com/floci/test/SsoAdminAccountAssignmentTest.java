package com.floci.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.PrincipalType;
import software.amazon.awssdk.services.ssoadmin.model.TargetType;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("IAM Identity Center account assignments")
class SsoAdminAccountAssignmentTest {

    @Test
    @DisplayName("adds an IAM Identity Center Region through the AWS SDK")
    void addRegionUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center instance");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            var added = sso.addRegion(request -> request
                    .instanceArn(instanceArn)
                    .regionName("ap-southeast-3"));

            assertThat(added.statusAsString()).isEqualTo("ADDING");
            assertThatThrownBy(() -> sso.addRegion(request -> request
                    .instanceArn(instanceArn)
                    .regionName("ap-southeast-3")))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ConflictException.class);
        }
    }

    @Test
    @DisplayName("attaches a customer managed policy reference through the AWS SDK")
    void customerManagedPolicyReferenceUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center instance");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            String permissionSetArn = sso.createPermissionSet(request -> request
                            .instanceArn(instanceArn)
                            .name("FlociCustomerPolicyAdmins"))
                    .permissionSet()
                    .permissionSetArn();

            var response = sso.attachCustomerManagedPolicyReferenceToPermissionSet(request -> request
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .customerManagedPolicyReference(reference -> reference
                            .name("PlatformPolicy")
                            .path("/platform/")));

            assertThat(response.sdkHttpResponse().isSuccessful()).isTrue();
            assertThatThrownBy(() -> sso.attachCustomerManagedPolicyReferenceToPermissionSet(request -> request
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .customerManagedPolicyReference(reference -> reference
                            .name("platformpolicy")
                            .path("/platform/"))))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ConflictException.class);
        }
    }

    @Test
    @DisplayName("creates OAuth applications through the AWS SDK")
    void createApplicationUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center instance");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            var created = sso.createApplication(request -> request
                    .instanceArn(instanceArn)
                    .applicationProviderArn("arn:aws:sso::aws:applicationProvider/custom")
                    .name("Floci OAuth SDK")
                    .clientToken("sdk-create-application-token")
                    .status("DISABLED")
                    .portalOptions(options -> options
                            .visibility("ENABLED")
                            .signInOptions(signIn -> signIn
                                    .origin("APPLICATION")
                                    .applicationUrl("https://example.com/login")))
                    .tags(tag -> tag.key("Environment").value("test")));

            assertThat(created.applicationArn()).matches(
                    "arn:aws:sso::000000000000:application/ssoins-7223b02a5d9f7c8e/apl-[0-9a-f]{16}");
            assertThat(created.identityStoreArn())
                    .isEqualTo("arn:aws:identitystore::000000000000:identitystore/d-9067f2a3c1");
            var replay = sso.createApplication(request -> request
                    .instanceArn(instanceArn)
                    .applicationProviderArn("arn:aws:sso::aws:applicationProvider/custom")
                    .name("Floci OAuth SDK")
                    .clientToken("sdk-create-application-token")
                    .status("DISABLED")
                    .portalOptions(options -> options
                            .visibility("ENABLED")
                            .signInOptions(signIn -> signIn
                                    .origin("APPLICATION")
                                    .applicationUrl("https://example.com/login")))
                    .tags(tag -> tag.key("Environment").value("test")));
            assertThat(replay.applicationArn()).isEqualTo(created.applicationArn());
        }
    }

    @Test
    @DisplayName("creates application assignments through the AWS SDK")
    void createApplicationAssignmentUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center instance");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            String applicationArn = sso.createApplication(request -> request
                    .instanceArn(instanceArn)
                    .applicationProviderArn("arn:aws:sso::aws:applicationProvider/custom")
                    .name("SDK Assignment App")
                    .clientToken("sdk-assignment-app-token")).applicationArn();

            var response = sso.createApplicationAssignment(request -> request
                    .applicationArn(applicationArn)
                    .principalId("11111111-2222-3333-4444-555555555555")
                    .principalType("USER"));
            assertThat(response.sdkHttpResponse().isSuccessful()).isTrue();
            assertThatThrownBy(() -> sso.createApplicationAssignment(request -> request
                    .applicationArn(applicationArn)
                    .principalId("11111111-2222-3333-4444-555555555555")
                    .principalType("USER")))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ConflictException.class);
        }
    }

    @Test
    @DisplayName("creates trusted token issuers through the AWS SDK")
    void createTrustedTokenIssuerUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center fixture");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            var created = sso.createTrustedTokenIssuer(request -> request
                    .instanceArn(instanceArn)
                    .name("SdkIssuer")
                    .clientToken("sdk-tti-token")
                    .trustedTokenIssuerType("OIDC_JWT")
                    .trustedTokenIssuerConfiguration(configuration -> configuration
                            .oidcJwtConfiguration(oidc -> oidc
                                    .claimAttributePath("sub")
                                    .identityStoreAttributePath("userName")
                                    .issuerUrl("https://issuer.example.com")
                                    .jwksRetrievalOption("OPEN_ID_DISCOVERY"))));

            assertThat(created.trustedTokenIssuerArn()).matches(
                    "arn:aws:sso::000000000000:trustedTokenIssuer/ssoins-[0-9a-f]{16}/tti-[0-9a-f-]{36}");
            var replay = sso.createTrustedTokenIssuer(request -> request
                    .instanceArn(instanceArn)
                    .name("SdkIssuer")
                    .clientToken("sdk-tti-token")
                    .trustedTokenIssuerType("OIDC_JWT")
                    .trustedTokenIssuerConfiguration(configuration -> configuration
                            .oidcJwtConfiguration(oidc -> oidc
                                    .claimAttributePath("sub")
                                    .identityStoreAttributePath("userName")
                                    .issuerUrl("https://issuer.example.com")
                                    .jwksRetrievalOption("OPEN_ID_DISCOVERY"))));
            assertThat(replay.trustedTokenIssuerArn()).isEqualTo(created.trustedTokenIssuerArn());
        }
    }

    @Test
    @DisplayName("creates an instance ABAC configuration through the AWS SDK")
    void createInstanceAccessControlAttributeConfigurationUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center fixture");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            var response = sso.createInstanceAccessControlAttributeConfiguration(request -> request
                    .instanceArn(instanceArn)
                    .instanceAccessControlAttributeConfiguration(configuration -> configuration
                            .accessControlAttributes(attribute -> attribute
                                    .key("Department")
                                    .value(value -> value.source("${path:enterprise.department}")))));

            assertThat(response.sdkHttpResponse().isSuccessful()).isTrue();
            assertThatThrownBy(() -> sso.createInstanceAccessControlAttributeConfiguration(request -> request
                    .instanceArn(instanceArn)
                    .instanceAccessControlAttributeConfiguration(configuration -> configuration
                            .accessControlAttributes(attribute -> attribute
                                    .key("Department")
                                    .value(value -> value.source("${path:enterprise.department}"))))))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ConflictException.class);
        }
    }

    @Test
    @DisplayName("creates an account instance through the AWS SDK")
    void createInstanceUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses an emulator-only account instance");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient("333344445555")) {
            assertThat(sso.listInstances(request -> {}).instances()).isEmpty();
            String instanceArn = sso.createInstance(request -> request
                    .name("SdkAccountInstance")
                    .clientToken("sdk-create-instance")
                    .tags(tag -> tag.key("Environment").value("test")))
                    .instanceArn();

            var listed = sso.listInstances(request -> {}).instances();
            assertThat(listed).hasSize(1);
            assertThat(listed.get(0).instanceArn()).isEqualTo(instanceArn);
            assertThat(listed.get(0).ownerAccountId()).isEqualTo("333344445555");
            assertThat(listed.get(0).primaryRegion()).isEqualTo("us-east-1");
        }
    }

    @Test
    @DisplayName("models the singleton CreateInstance quota through the AWS SDK")
    void createInstanceQuotaUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center fixture");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            sso.listInstances(request -> {});
            assertThatThrownBy(() -> sso.createInstance(request -> request
                    .name("SecondInstance")
                    .clientToken("sdk-create-instance-quota")))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ServiceQuotaExceededException.class);
        }
    }

    @Test
    @DisplayName("deletes application access scopes through the AWS SDK")
    void deleteApplicationAccessScopeUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center fixture");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            String applicationArn = sso.createApplication(request -> request
                    .instanceArn(instanceArn)
                    .applicationProviderArn("arn:aws:sso::aws:applicationProvider/custom")
                    .name("SdkDeleteApplicationScope"))
                    .applicationArn();

            assertThatThrownBy(() -> sso.deleteApplicationAccessScope(request -> request
                    .applicationArn(applicationArn)
                    .scope("api:read")))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException.class);
            assertThatThrownBy(() -> sso.deleteApplicationAccessScope(request -> request
                    .applicationArn(applicationArn)
                    .scope("bad scope")))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ValidationException.class);
        }
    }

    @Test
    @DisplayName("deletes applications through the AWS SDK")
    void deleteApplicationUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses the emulator IAM Identity Center fixture");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            String applicationArn = sso.createApplication(request -> request
                    .instanceArn(instanceArn)
                    .applicationProviderArn("arn:aws:sso::aws:applicationProvider/custom")
                    .name("SdkDeleteApplication"))
                    .applicationArn();

            var response = sso.deleteApplication(request -> request.applicationArn(applicationArn));
            assertThat(response.sdkHttpResponse().isSuccessful()).isTrue();
            assertThatThrownBy(() -> sso.deleteApplication(request -> request.applicationArn(applicationArn)))
                    .isInstanceOf(software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("deletes account assignments through the AWS SDK")
    void deleteAccountAssignmentUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses emulator-only account and principal identifiers");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            String permissionSetArn = sso.createPermissionSet(request -> request
                            .instanceArn(instanceArn)
                            .name("FlociDeleteAssignmentAdmins"))
                    .permissionSet().permissionSetArn();
            sso.createAccountAssignment(request -> request
                    .instanceArn(instanceArn)
                    .targetId("123456789012")
                    .targetType(TargetType.AWS_ACCOUNT)
                    .permissionSetArn(permissionSetArn)
                    .principalType(PrincipalType.GROUP)
                    .principalId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

            var deleted = sso.deleteAccountAssignment(request -> request
                    .instanceArn(instanceArn)
                    .targetId("123456789012")
                    .targetType(TargetType.AWS_ACCOUNT)
                    .permissionSetArn(permissionSetArn)
                    .principalType(PrincipalType.GROUP)
                    .principalId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

            assertThat(deleted.accountAssignmentDeletionStatus()).isNotNull();
            assertThat(deleted.accountAssignmentDeletionStatus().statusAsString()).isEqualTo("SUCCEEDED");
            assertThat(deleted.accountAssignmentDeletionStatus().requestId()).isNotBlank();
            assertThat(sso.listAccountAssignments(request -> request
                    .instanceArn(instanceArn)
                    .accountId("123456789012")
                    .permissionSetArn(permissionSetArn)).accountAssignments()).isEmpty();
        }
    }

    @Test
    @DisplayName("creates and describes account assignments through the AWS SDK")
    void accountAssignmentLifecycleUsesAwsSdk() {
        assumeFalse(TestFixtures.isRealAws(), "Uses emulator-only account and principal identifiers");

        try (SsoAdminClient sso = TestFixtures.ssoAdminClient()) {
            String instanceArn = sso.listInstances(request -> {}).instances().get(0).instanceArn();
            String permissionSetArn = sso.createPermissionSet(request -> request
                            .instanceArn(instanceArn)
                            .name("FlociPlatformAdmins"))
                    .permissionSet()
                    .permissionSetArn();

            var created = sso.createAccountAssignment(request -> request
                    .instanceArn(instanceArn)
                    .targetId("123456789012")
                    .targetType(TargetType.AWS_ACCOUNT)
                    .permissionSetArn(permissionSetArn)
                    .principalType(PrincipalType.GROUP)
                    .principalId("11111111-2222-3333-4444-555555555555"));

            assertThat(created.accountAssignmentCreationStatus()).isNotNull();
            assertThat(created.accountAssignmentCreationStatus().requestId()).isNotBlank();

            var status = sso.describeAccountAssignmentCreationStatus(request -> request
                    .instanceArn(instanceArn)
                    .accountAssignmentCreationRequestId(created.accountAssignmentCreationStatus().requestId()));
            assertThat(status.accountAssignmentCreationStatus().statusAsString()).isEqualTo("SUCCEEDED");

            var assignments = sso.listAccountAssignments(request -> request
                    .instanceArn(instanceArn)
                    .accountId("123456789012")
                    .permissionSetArn(permissionSetArn));
            assertThat(assignments.accountAssignments())
                    .anySatisfy(assignment -> {
                        assertThat(assignment.principalType()).isEqualTo(PrincipalType.GROUP);
                        assertThat(assignment.principalId()).isEqualTo("11111111-2222-3333-4444-555555555555");
                    });

            for (String policy : new String[] {"ReadOnlyAccess", "SecurityAudit", "ViewOnlyAccess"}) {
                sso.attachManagedPolicyToPermissionSet(request -> request
                        .instanceArn(instanceArn)
                        .permissionSetArn(permissionSetArn)
                        .managedPolicyArn("arn:aws:iam::aws:policy/" + policy));
            }
            var firstPolicies = sso.listManagedPoliciesInPermissionSet(request -> request
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .maxResults(1));
            assertThat(firstPolicies.attachedManagedPolicies()).hasSize(1);
            assertThat(firstPolicies.nextToken()).isNotBlank();
            var secondPolicies = sso.listManagedPoliciesInPermissionSet(request -> request
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .maxResults(1)
                    .nextToken(firstPolicies.nextToken()));
            assertThat(secondPolicies.attachedManagedPolicies()).hasSize(1);
            assertThat(secondPolicies.attachedManagedPolicies().get(0).arn())
                    .isNotEqualTo(firstPolicies.attachedManagedPolicies().get(0).arn());

            assertThatThrownBy(() -> sso.listManagedPoliciesInPermissionSet(request -> request
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .maxResults(101)))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
