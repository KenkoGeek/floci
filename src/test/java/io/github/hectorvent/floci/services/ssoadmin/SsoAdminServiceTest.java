package io.github.hectorvent.floci.services.ssoadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.ssoadmin.model.Assignment;
import io.github.hectorvent.floci.services.ssoadmin.model.AssignmentOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.AssignmentDeletionOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationAccessScope;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationAssignment;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationAuthenticationMethod;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationGrant;
import io.github.hectorvent.floci.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSet;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSetProvisioning;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSetProvisioningOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.RegionMetadata;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoApplication;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoInstance;
import io.github.hectorvent.floci.services.ssoadmin.model.TrustedTokenIssuer;
import io.github.hectorvent.floci.services.identitystore.IdentityStoreService;
import io.github.hectorvent.floci.services.organizations.OrganizationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoAdminServiceTest {
    private static final String ACCOUNT_ID = "123456789012";
    private static final String PRINCIPAL_ID = "11111111-2222-3333-4444-555555555555";

    private final ObjectMapper mapper = new ObjectMapper();
    private SsoAdminService service;
    private IdentityStoreService identityStoreService;
    private OrganizationsService organizationsService;
    private InMemoryStorage<String, ApplicationAccessScope> applicationAccessScopes;
    private InMemoryStorage<String, ApplicationAuthenticationMethod> applicationAuthenticationMethods;
    private InMemoryStorage<String, ApplicationGrant> applicationGrants;

    @BeforeEach
    void setUp() {
        identityStoreService = org.mockito.Mockito.mock(IdentityStoreService.class);
        organizationsService = org.mockito.Mockito.mock(OrganizationsService.class);
        applicationAccessScopes = new InMemoryStorage<>();
        applicationAuthenticationMethods = new InMemoryStorage<>();
        applicationGrants = new InMemoryStorage<>();
        service = new SsoAdminService(
                new InMemoryStorage<String, PermissionSet>(),
                new InMemoryStorage<String, Assignment>(),
                new InMemoryStorage<String, AssignmentOperation>(),
                new InMemoryStorage<String, AssignmentDeletionOperation>(),
                new InMemoryStorage<String, PermissionSetProvisioning>(),
                new InMemoryStorage<String, PermissionSetProvisioningOperation>(),
                new InMemoryStorage<String, RegionMetadata>(),
                new InMemoryStorage<String, SsoApplication>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, ApplicationAssignment>(),
                applicationAccessScopes,
                applicationAuthenticationMethods,
                applicationGrants,
                new InMemoryStorage<String, SsoInstance>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, Boolean>(),
                new InMemoryStorage<String, InstanceAccessControlAttributeConfiguration>(),
                new InMemoryStorage<String, TrustedTokenIssuer>(),
                new InMemoryStorage<String, String>(),
                identityStoreService,
                organizationsService,
                ACCOUNT_ID,
                "us-east-1");
        service.ensureBootstrapInstance(ACCOUNT_ID, "us-east-1");
    }

    @Test
    void createTrustedTokenIssuerPersistsOidcConfigurationAndSupportsIdempotency() {
        ObjectNode request = trustedTokenIssuerRequest("IssuerOne", "tti-token-one");

        TrustedTokenIssuer created = service.createTrustedTokenIssuer(request, ACCOUNT_ID);
        assertTrue(created.trustedTokenIssuerArn().matches(
                "arn:aws:sso::123456789012:trustedTokenIssuer/ssoins-[0-9a-f]{16}/tti-[0-9a-f-]{36}"));
        assertEquals("OIDC_JWT", created.trustedTokenIssuerType());
        assertEquals("https://issuer.example.com", created.oidcJwtConfiguration().issuerUrl());
        assertEquals(created, service.createTrustedTokenIssuer(request, ACCOUNT_ID));
        assertEquals(created, service.getTrustedTokenIssuer(created.trustedTokenIssuerArn()));
        ObjectNode describe = mapper.createObjectNode().put("TrustedTokenIssuerArn", created.trustedTokenIssuerArn());
        assertEquals(created, service.describeTrustedTokenIssuer(describe));

        ObjectNode mismatch = request.deepCopy();
        mismatch.put("Name", "IssuerTwo");
        assertError("IdempotentParameterMismatch",
                () -> service.createTrustedTokenIssuer(mismatch, ACCOUNT_ID));
    }

    @Test
    void deleteTrustedTokenIssuerRemovesIssuerAndIdempotencyToken() {
        ObjectNode create = trustedTokenIssuerRequest("DeleteIssuer", "delete-tti-token");
        TrustedTokenIssuer issuer = service.createTrustedTokenIssuer(create, ACCOUNT_ID);

        ObjectNode request = mapper.createObjectNode();
        request.put("TrustedTokenIssuerArn", issuer.trustedTokenIssuerArn());
        service.deleteTrustedTokenIssuer(request);
        assertError("ResourceNotFoundException", () -> service.getTrustedTokenIssuer(issuer.trustedTokenIssuerArn()));
        assertError("ResourceNotFoundException", () -> service.deleteTrustedTokenIssuer(request));

        TrustedTokenIssuer recreated = service.createTrustedTokenIssuer(create, ACCOUNT_ID);
        assertFalse(recreated.trustedTokenIssuerArn().equals(issuer.trustedTokenIssuerArn()));

        ObjectNode malformed = mapper.createObjectNode();
        malformed.put("TrustedTokenIssuerArn", "not-an-arn");
        assertError("ValidationException", () -> service.deleteTrustedTokenIssuer(malformed));
    }

    @Test
    void createTrustedTokenIssuerValidatesUnionOidcFieldsAndQuota() {
        ObjectNode invalidType = trustedTokenIssuerRequest("InvalidType", null);
        invalidType.put("TrustedTokenIssuerType", "SAML");
        assertError("ValidationException", () -> service.createTrustedTokenIssuer(invalidType, ACCOUNT_ID));

        ObjectNode invalidIssuerUrl = trustedTokenIssuerRequest("InvalidUrl", null);
        invalidIssuerUrl.withObject("TrustedTokenIssuerConfiguration")
                .withObject("OidcJwtConfiguration").put("IssuerUrl", "ftp://issuer.example.com");
        assertError("ValidationException", () -> service.createTrustedTokenIssuer(invalidIssuerUrl, ACCOUNT_ID));

        for (int i = 0; i < 10; i++) {
            service.createTrustedTokenIssuer(trustedTokenIssuerRequest("Issuer" + i, null), ACCOUNT_ID);
        }
        assertError("ServiceQuotaExceededException",
                () -> service.createTrustedTokenIssuer(trustedTokenIssuerRequest("IssuerOverQuota", null), ACCOUNT_ID));
    }

    @Test
    void createInstanceAccessControlAttributeConfigurationPersistsAndValidatesAwsShape() {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.putObject("InstanceAccessControlAttributeConfiguration")
                .putArray("AccessControlAttributes")
                .addObject()
                .put("Key", "Department")
                .putObject("Value")
                .putArray("Source")
                .add("${path:enterprise.department}");

        InstanceAccessControlAttributeConfiguration created =
                service.createInstanceAccessControlAttributeConfiguration(request);
        assertEquals("ENABLED", created.status());
        assertEquals(1, created.accessControlAttributes().size());
        assertEquals("Department", created.accessControlAttributes().get(0).key());
        assertEquals("${path:enterprise.department}", created.accessControlAttributes().get(0).source());
        assertEquals(created, service.getInstanceAccessControlAttributeConfiguration(service.getInstanceArn()));
        ObjectNode describeRequest = mapper.createObjectNode().put("InstanceArn", service.getInstanceArn());
        assertEquals(created, service.describeInstanceAccessControlAttributeConfiguration(describeRequest));
        assertError("ConflictException", () -> service.createInstanceAccessControlAttributeConfiguration(request));
    }

    @Test
    void createInstanceAccessControlAttributeConfigurationRejectsInvalidAttributes() {
        ObjectNode malformedInstanceArn = mapper.createObjectNode();
        malformedInstanceArn.put("InstanceArn", "not-an-arn");
        malformedInstanceArn.putObject("InstanceAccessControlAttributeConfiguration")
                .putArray("AccessControlAttributes");
        assertError("ValidationException",
                () -> service.createInstanceAccessControlAttributeConfiguration(malformedInstanceArn));

        ObjectNode missingConfiguration = mapper.createObjectNode();
        missingConfiguration.put("InstanceArn", service.getInstanceArn());
        assertError("ValidationException",
                () -> service.createInstanceAccessControlAttributeConfiguration(missingConfiguration));

        ObjectNode tooMany = mapper.createObjectNode();
        tooMany.put("InstanceArn", service.getInstanceArn());
        var attributes = tooMany.putObject("InstanceAccessControlAttributeConfiguration")
                .putArray("AccessControlAttributes");
        for (int i = 0; i < 51; i++) {
            attributes.addObject().put("Key", "Key" + i).putObject("Value").putArray("Source").add("value");
        }
        assertError("ValidationException", () -> service.createInstanceAccessControlAttributeConfiguration(tooMany));

        ObjectNode invalidSourceCount = mapper.createObjectNode();
        invalidSourceCount.put("InstanceArn", service.getInstanceArn());
        invalidSourceCount.putObject("InstanceAccessControlAttributeConfiguration")
                .putArray("AccessControlAttributes")
                .addObject().put("Key", "Department").putObject("Value").putArray("Source")
                .add("one").add("two");
        assertError("ValidationException",
                () -> service.createInstanceAccessControlAttributeConfiguration(invalidSourceCount));
    }

    @Test
    void deleteInstanceAccessControlAttributeConfigurationRemovesAbacConfiguration() {
        ObjectNode create = mapper.createObjectNode();
        create.put("InstanceArn", service.getInstanceArn());
        create.putObject("InstanceAccessControlAttributeConfiguration")
                .putArray("AccessControlAttributes")
                .addObject()
                .put("Key", "Department")
                .putObject("Value")
                .putArray("Source")
                .add("${path:enterprise.department}");
        service.createInstanceAccessControlAttributeConfiguration(create);

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        service.deleteInstanceAccessControlAttributeConfiguration(request);

        assertError("ResourceNotFoundException",
                () -> service.getInstanceAccessControlAttributeConfiguration(service.getInstanceArn()));
        assertError("ResourceNotFoundException",
                () -> service.deleteInstanceAccessControlAttributeConfiguration(request));

        InstanceAccessControlAttributeConfiguration recreated =
                service.createInstanceAccessControlAttributeConfiguration(create);
        assertEquals("ENABLED", recreated.status());
    }

    @Test
    void deleteInstanceRequiresOwnerAndAllowsRecreation() {
        SsoAdminService emptyService = emptyService();
        ObjectNode create = mapper.createObjectNode();
        create.put("Name", "DisposableInstance");
        create.put("ClientToken", "delete-instance-token");
        SsoInstance instance = emptyService.createInstance(create, ACCOUNT_ID, "us-west-2");

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", instance.instanceArn());
        assertError("AccessDeniedException", () -> emptyService.deleteInstance(request, "210987654321"));

        emptyService.deleteInstance(request, ACCOUNT_ID);
        assertTrue(emptyService.listInstances(ACCOUNT_ID).isEmpty());
        assertError("AccessDeniedException", () -> emptyService.deleteInstance(request, ACCOUNT_ID));

        ObjectNode recreate = mapper.createObjectNode();
        recreate.put("Name", "ReplacementInstance");
        recreate.put("ClientToken", "replacement-instance-token");
        SsoInstance replacement = emptyService.createInstance(recreate, ACCOUNT_ID, "us-west-2");
        assertFalse(replacement.instanceArn().equals(instance.instanceArn()));
    }

    @Test
    void deleteInstanceRejectsMalformedArnAndCascadesOwnedResources() {
        SsoAdminService emptyService = emptyService();
        ObjectNode malformed = mapper.createObjectNode();
        malformed.put("InstanceArn", "not-an-arn");
        assertError("ValidationException", () -> emptyService.deleteInstance(malformed, ACCOUNT_ID));

        SsoInstance instance = emptyService.createInstance(mapper.createObjectNode(), ACCOUNT_ID, "us-east-1");
        ObjectNode application = mapper.createObjectNode();
        application.put("InstanceArn", instance.instanceArn());
        application.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom");
        application.put("Name", "AttachedApplication");
        SsoApplication createdApplication = emptyService.createApplication(application, ACCOUNT_ID, "us-east-1");
        assertEquals(instance.instanceArn(), createdApplication.instanceArn());

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", instance.instanceArn());
        emptyService.deleteInstance(request, ACCOUNT_ID);
        assertError("ResourceNotFoundException", () -> emptyService.getApplication(createdApplication.applicationArn()));
        org.mockito.Mockito.verify(identityStoreService).deleteIdentityStore(instance.identityStoreId());
    }

    @Test
    void createInstancePersistsMetadataAndSupportsIdempotentReplay() {
        SsoAdminService emptyService = emptyService();
        ObjectNode request = mapper.createObjectNode();
        request.put("Name", "AccountInstance");
        request.put("ClientToken", "create-instance-token");
        request.putArray("Tags").addObject().put("Key", "Environment").put("Value", "dev");

        assertTrue(emptyService.listInstances(ACCOUNT_ID).isEmpty());

        SsoInstance created = emptyService.createInstance(request, ACCOUNT_ID, "us-west-2");
        assertEquals(ACCOUNT_ID, created.ownerAccountId());
        assertEquals("us-west-2", created.primaryRegion());
        assertEquals("ACTIVE", created.status());
        assertTrue(created.accountInstance());
        assertTrue(created.instanceArn().matches("arn:aws:sso:::instance/ssoins-[0-9a-f]{16}"));
        assertTrue(created.identityStoreId().matches("d-[0-9a-f]{10}"));
        ObjectNode describeRequest = mapper.createObjectNode().put("InstanceArn", created.instanceArn());
        assertEquals(created, emptyService.describeInstance(describeRequest));
        assertEquals(created.instanceArn(), emptyService.createInstance(request, ACCOUNT_ID, "us-west-2").instanceArn());
        assertEquals(1, emptyService.listInstances(ACCOUNT_ID).size());

        ObjectNode mismatch = request.deepCopy();
        mismatch.put("Name", "DifferentName");
        assertError("IdempotentParameterMismatch",
                () -> emptyService.createInstance(mismatch, ACCOUNT_ID, "us-west-2"));
    }

    @Test
    void createInstanceEnforcesSingletonAndValidatesInputs() {
        ObjectNode duplicate = mapper.createObjectNode();
        duplicate.put("Name", "SecondInstance");
        assertError("ServiceQuotaExceededException",
                () -> service.createInstance(duplicate, ACCOUNT_ID, "us-east-1"));

        SsoAdminService emptyService = emptyService();
        ObjectNode invalidName = mapper.createObjectNode();
        invalidName.put("Name", "bad name");
        assertError("ValidationException",
                () -> emptyService.createInstance(invalidName, ACCOUNT_ID, "us-east-1"));

        ObjectNode invalidToken = mapper.createObjectNode();
        invalidToken.put("ClientToken", "bad token");
        assertError("ValidationException",
                () -> emptyService.createInstance(invalidToken, ACCOUNT_ID, "us-east-1"));

        ObjectNode nonStringToken = mapper.createObjectNode();
        nonStringToken.put("ClientToken", 123);
        assertError("ValidationException",
                () -> emptyService.createInstance(nonStringToken, ACCOUNT_ID, "us-east-1"));

        ObjectNode reservedTag = mapper.createObjectNode();
        reservedTag.putArray("Tags").addObject().put("Key", "aws:reserved").put("Value", "x");
        assertError("ValidationException",
                () -> emptyService.createInstance(reservedTag, ACCOUNT_ID, "us-east-1"));
    }

    @Test
    void createInstanceRejectsOrganizationsManagementAccounts() {
        org.mockito.Mockito.when(organizationsService.isManagementAccount(ACCOUNT_ID)).thenReturn(true);
        SsoAdminService emptyService = emptyService();

        assertError("AccessDeniedException",
                () -> emptyService.createInstance(mapper.createObjectNode(), ACCOUNT_ID, "us-east-1"));
    }

    @Test
    void describeApplicationReturnsPersistedApplicationAndValidatesArn() {
        SsoApplication application = createApplication("Describe App", "describe-app-token");
        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        assertEquals(application, service.describeApplication(request));

        ObjectNode missing = mapper.createObjectNode();
        missing.put("ApplicationArn", "arn:aws:sso::123456789012:application/ssoins-7223b02a5d9f7c8e/apl-1111111111111111");
        assertError("ResourceNotFoundException", () -> service.describeApplication(missing));

        ObjectNode malformed = mapper.createObjectNode();
        malformed.put("ApplicationArn", "not-an-arn");
        assertError("ValidationException", () -> service.describeApplication(malformed));
    }

    @Test
    void listApplicationsSupportsFiltersPaginationAndMemberAccountIsolation() {
        SsoApplication first = createApplication("List Apps One", "list-apps-one-token");
        SsoApplication second = createApplication("List Apps Two", "list-apps-two-token");
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("MaxResults", 1);
        var firstPage = service.listApplications(request, ACCOUNT_ID);
        assertEquals(1, firstPage.items().size());
        assertNotNull(firstPage.nextToken());
        request.put("NextToken", firstPage.nextToken());
        assertEquals(1, service.listApplications(request, ACCOUNT_ID).items().size());

        ObjectNode providerFilter = mapper.createObjectNode();
        providerFilter.put("InstanceArn", service.getInstanceArn());
        providerFilter.putObject("Filter")
                .put("ApplicationProvider", "arn:aws:sso::aws:applicationProvider/custom")
                .put("ApplicationAccount", ACCOUNT_ID);
        assertTrue(service.listApplications(providerFilter, ACCOUNT_ID).items().containsAll(java.util.List.of(first, second)));

        ObjectNode memberWithoutFilter = mapper.createObjectNode().put("InstanceArn", service.getInstanceArn());
        assertError("AccessDeniedException",
                () -> service.listApplications(memberWithoutFilter, "222233334444"));
        memberWithoutFilter.putObject("Filter").put("ApplicationAccount", "222233334444");
        assertTrue(service.listApplications(memberWithoutFilter, "222233334444").items().isEmpty());
    }

    @Test
    void deleteApplicationRemovesApplicationAssignmentsAndIdempotencyReferences() {
        ObjectNode createRequest = mapper.createObjectNode();
        createRequest.put("InstanceArn", service.getInstanceArn());
        createRequest.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom");
        createRequest.put("Name", "DeleteMeApplication");
        createRequest.put("ClientToken", "delete-app-token");
        SsoApplication application = service.createApplication(createRequest, ACCOUNT_ID, "us-east-1");

        ObjectNode assignment = mapper.createObjectNode();
        assignment.put("ApplicationArn", application.applicationArn());
        assignment.put("PrincipalId", PRINCIPAL_ID);
        assignment.put("PrincipalType", "GROUP");
        service.createApplicationAssignment(assignment);

        service.deleteApplication(application.applicationArn());
        assertError("ResourceNotFoundException", () -> service.getApplication(application.applicationArn()));
        assertError("ResourceNotFoundException", () -> service.createApplicationAssignment(assignment));

        SsoApplication recreated = service.createApplication(createRequest, ACCOUNT_ID, "us-east-1");
        assertFalse(recreated.applicationArn().equals(application.applicationArn()));
        assertError("ResourceNotFoundException", () -> service.deleteApplication(application.applicationArn()));
    }

    @Test
    void deleteApplicationAccessScopeDeletesStoredScopeAndValidatesRequest() {
        SsoApplication application = createApplication("Scope App", "scope-app-token");
        String scope = "api:read";
        String key = SsoAdminService.applicationAccessScopeKey(application.applicationArn(), scope);
        applicationAccessScopes.put(key, new ApplicationAccessScope(
                application.applicationArn(), scope, java.util.List.of(service.getInstanceArn())));

        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("Scope", scope);
        service.deleteApplicationAccessScope(request);
        assertTrue(applicationAccessScopes.get(key).isEmpty());
        assertError("ResourceNotFoundException", () -> service.deleteApplicationAccessScope(request));

        ObjectNode invalid = request.deepCopy();
        invalid.put("Scope", "bad scope");
        assertError("ValidationException", () -> service.deleteApplicationAccessScope(invalid));
    }

    @Test
    void deleteApplicationAuthenticationMethodDeletesIamMethodAndValidatesType() {
        SsoApplication application = createApplication("Authentication App", "authentication-app-token");
        String key = SsoAdminService.applicationAuthenticationMethodKey(application.applicationArn(), "IAM");
        ObjectNode method = mapper.createObjectNode();
        method.putObject("Iam").putObject("ActorPolicy").put("Version", "2012-10-17");
        applicationAuthenticationMethods.put(key, new ApplicationAuthenticationMethod(
                application.applicationArn(), "IAM", method));

        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("AuthenticationMethodType", "IAM");
        service.deleteApplicationAuthenticationMethod(request);
        assertTrue(applicationAuthenticationMethods.get(key).isEmpty());
        assertError("ResourceNotFoundException", () -> service.deleteApplicationAuthenticationMethod(request));

        ObjectNode invalidType = request.deepCopy();
        invalidType.put("AuthenticationMethodType", "SAML");
        assertError("ValidationException", () -> service.deleteApplicationAuthenticationMethod(invalidType));
    }

    @Test
    void deleteApplicationGrantDeletesConfiguredGrantAndValidatesGrantType() {
        SsoApplication application = createApplication("Grant App", "grant-app-token");
        String grantType = "authorization_code";
        String key = SsoAdminService.applicationGrantKey(application.applicationArn(), grantType);
        ObjectNode grant = mapper.createObjectNode();
        grant.putObject("AuthorizationCode");
        applicationGrants.put(key, new ApplicationGrant(application.applicationArn(), grantType, grant));

        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("GrantType", grantType);
        service.deleteApplicationGrant(request);
        assertTrue(applicationGrants.get(key).isEmpty());
        assertError("ResourceNotFoundException", () -> service.deleteApplicationGrant(request));

        ObjectNode invalidType = request.deepCopy();
        invalidType.put("GrantType", "client_credentials");
        assertError("ValidationException", () -> service.deleteApplicationGrant(invalidType));
    }

    @Test
    void createApplicationAssignmentValidatesApplicationPrincipalAndDuplicates() {
        SsoApplication application = createApplication("Assignment App", "assignment-app-token");
        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("PrincipalId", PRINCIPAL_ID);
        request.put("PrincipalType", "GROUP");

        ApplicationAssignment assignment = service.createApplicationAssignment(request);
        assertEquals(application.applicationArn(), assignment.applicationArn());
        assertEquals(PRINCIPAL_ID, assignment.principalId());
        assertEquals("GROUP", assignment.principalType());
        assertError("ConflictException", () -> service.createApplicationAssignment(request));

        ObjectNode invalidPrincipalType = request.deepCopy();
        invalidPrincipalType.put("PrincipalType", "ROLE");
        invalidPrincipalType.put("PrincipalId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertError("ValidationException", () -> service.createApplicationAssignment(invalidPrincipalType));

        ObjectNode missingApplication = request.deepCopy();
        missingApplication.put("ApplicationArn", "arn:aws:sso::123456789012:application/ssoins-7223b02a5d9f7c8e/apl-1111111111111111");
        assertError("ResourceNotFoundException", () -> service.createApplicationAssignment(missingApplication));
    }

    @Test
    void describeApplicationAssignmentReturnsDirectAssignmentAndValidatesRequest() {
        SsoApplication application = createApplication("Describe Assignment App", "describe-assignment-app-token");
        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("PrincipalId", PRINCIPAL_ID);
        request.put("PrincipalType", "GROUP");
        ApplicationAssignment created = service.createApplicationAssignment(request);

        assertEquals(created, service.describeApplicationAssignment(request));

        ObjectNode wrongType = request.deepCopy();
        wrongType.put("PrincipalType", "USER");
        assertError("ResourceNotFoundException", () -> service.describeApplicationAssignment(wrongType));

        ObjectNode invalidType = request.deepCopy();
        invalidType.put("PrincipalType", "ROLE");
        assertError("ValidationException", () -> service.describeApplicationAssignment(invalidType));

        ObjectNode invalidPrincipal = request.deepCopy();
        invalidPrincipal.put("PrincipalId", "not-a-guid");
        assertError("ValidationException", () -> service.describeApplicationAssignment(invalidPrincipal));
    }

    @Test
    void describeApplicationProviderReturnsCustomOauthProviderAndValidatesArn() {
        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom");
        assertEquals("arn:aws:sso::aws:applicationProvider/custom", service.describeApplicationProvider(request));

        ObjectNode missing = mapper.createObjectNode();
        missing.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/example");
        assertError("ResourceNotFoundException", () -> service.describeApplicationProvider(missing));

        ObjectNode malformed = mapper.createObjectNode();
        malformed.put("ApplicationProviderArn", "not-an-arn");
        assertError("ValidationException", () -> service.describeApplicationProvider(malformed));
    }

    @Test
    void listApplicationProvidersReturnsCustomProviderAndValidatesPagination() {
        ObjectNode request = mapper.createObjectNode();
        var page = service.listApplicationProviders(request);
        assertEquals(java.util.List.of("arn:aws:sso::aws:applicationProvider/custom"), page.items());
        assertEquals(null, page.nextToken());

        ObjectNode invalidMaxResults = mapper.createObjectNode().put("MaxResults", 101);
        assertError("ValidationException", () -> service.listApplicationProviders(invalidMaxResults));
    }

    @Test
    void listApplicationAssignmentsPaginatesAndScopesToApplication() {
        SsoApplication application = createApplication("List Assignment App", "list-assignment-app-token");
        ObjectNode user = mapper.createObjectNode();
        user.put("ApplicationArn", application.applicationArn());
        user.put("PrincipalId", "11111111-2222-3333-4444-555555555555");
        user.put("PrincipalType", "USER");
        service.createApplicationAssignment(user);
        ObjectNode group = user.deepCopy();
        group.put("PrincipalId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        group.put("PrincipalType", "GROUP");
        service.createApplicationAssignment(group);

        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("MaxResults", 1);
        var first = service.listApplicationAssignments(request);
        assertEquals(1, first.items().size());
        assertNotNull(first.nextToken());

        request.put("NextToken", first.nextToken());
        var second = service.listApplicationAssignments(request);
        assertEquals(1, second.items().size());
        assertNull(second.nextToken());

        ObjectNode missing = mapper.createObjectNode();
        missing.put("ApplicationArn", "arn:aws:sso::123456789012:application/ssoins-7223b02a5d9f7c8e/apl-1111111111111111");
        assertError("ResourceNotFoundException", () -> service.listApplicationAssignments(missing));
    }

    @Test
    void listApplicationAssignmentsForPrincipalIncludesGroupBasedUserAccessAndRequiresMemberFilter() {
        SsoApplication application = createApplication("Principal Assignment App", "principal-assignment-app-token");
        String groupId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        ObjectNode groupAssignment = mapper.createObjectNode();
        groupAssignment.put("ApplicationArn", application.applicationArn());
        groupAssignment.put("PrincipalId", groupId);
        groupAssignment.put("PrincipalType", "GROUP");
        service.createApplicationAssignment(groupAssignment);
        org.mockito.Mockito.when(identityStoreService.groupIdsForUser(service.getIdentityStoreId(), PRINCIPAL_ID))
                .thenReturn(Set.of(groupId));

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("PrincipalId", PRINCIPAL_ID);
        request.put("PrincipalType", "USER");
        var page = service.listApplicationAssignmentsForPrincipal(request, ACCOUNT_ID);
        assertEquals(1, page.items().size());
        assertEquals(application.applicationArn(), page.items().get(0).applicationArn());
        assertEquals(PRINCIPAL_ID, page.items().get(0).principalId());
        assertEquals("USER", page.items().get(0).principalType());

        ObjectNode memberRequest = request.deepCopy();
        assertError("AccessDeniedException",
                () -> service.listApplicationAssignmentsForPrincipal(memberRequest, "222233334444"));
        memberRequest.putObject("Filter").put("ApplicationArn", application.applicationArn());
        assertEquals(1, service.listApplicationAssignmentsForPrincipal(memberRequest, "222233334444").items().size());
    }

    @Test
    void deleteApplicationAssignmentRevokesDirectAssignmentAndValidatesPrincipal() {
        SsoApplication application = createApplication("Delete Assignment App", "delete-assignment-app-token");
        ObjectNode request = mapper.createObjectNode();
        request.put("ApplicationArn", application.applicationArn());
        request.put("PrincipalId", PRINCIPAL_ID);
        request.put("PrincipalType", "USER");
        service.createApplicationAssignment(request);

        service.deleteApplicationAssignment(request);
        assertError("ResourceNotFoundException", () -> service.deleteApplicationAssignment(request));

        ObjectNode invalidType = request.deepCopy();
        invalidType.put("PrincipalType", "ROLE");
        assertError("ValidationException", () -> service.deleteApplicationAssignment(invalidType));

        ObjectNode invalidPrincipal = request.deepCopy();
        invalidPrincipal.put("PrincipalId", "not-a-guid");
        assertError("ValidationException", () -> service.deleteApplicationAssignment(invalidPrincipal));
    }

    @Test
    void createApplicationAssignmentEnforcesTheDocumentedGroupQuota() {
        SsoApplication application = createApplication("Group Quota App", "group-quota-app-token");
        for (int i = 0; i < 100; i++) {
            ObjectNode request = mapper.createObjectNode();
            request.put("ApplicationArn", application.applicationArn());
            request.put("PrincipalId", "00000000-0000-0000-0000-" + String.format("%012x", i));
            request.put("PrincipalType", "GROUP");
            service.createApplicationAssignment(request);
        }
        ObjectNode overQuota = mapper.createObjectNode();
        overQuota.put("ApplicationArn", application.applicationArn());
        overQuota.put("PrincipalId", "00000000-0000-0000-0000-000000000100");
        overQuota.put("PrincipalType", "GROUP");
        assertError("ServiceQuotaExceededException", () -> service.createApplicationAssignment(overQuota));
    }

    @Test
    void createApplicationSupportsOAuthProviderPortalOptionsTagsAndIdempotency() {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom");
        request.put("Name", "Platform Portal");
        request.put("Description", "Platform OAuth application");
        request.put("ClientToken", "token-123456");
        request.put("Status", "DISABLED");
        request.putObject("PortalOptions").put("Visibility", "ENABLED")
                .putObject("SignInOptions").put("Origin", "APPLICATION").put("ApplicationUrl", "https://example.com/login");
        request.putArray("Tags").addObject().put("Key", "Environment").put("Value", "dev");

        SsoApplication created = service.createApplication(request, ACCOUNT_ID, "us-east-1");
        assertTrue(created.applicationArn().matches("arn:aws:sso::123456789012:application/ssoins-7223b02a5d9f7c8e/apl-[0-9a-f]{16}"));
        assertEquals("arn:aws:identitystore::123456789012:identitystore/d-9067f2a3c1", created.identityStoreArn());
        assertEquals("DISABLED", created.status());
        assertEquals("APPLICATION", created.portalOptions().signInOptions().origin());
        assertEquals(created.applicationArn(), service.createApplication(request, ACCOUNT_ID, "us-east-1").applicationArn());

        ObjectNode mismatch = request.deepCopy();
        mismatch.put("Name", "Different Name");
        assertError("IdempotentParameterMismatch", () -> service.createApplication(mismatch, ACCOUNT_ID, "us-east-1"));
    }

    @Test
    void createApplicationRejectsUnsupportedProvidersAndInvalidPortalOptions() {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom");
        request.put("Name", "Portal");
        request.putObject("PortalOptions").putObject("SignInOptions").put("Origin", "APPLICATION");
        assertError("ValidationException", () -> service.createApplication(request, ACCOUNT_ID, "us-east-1"));

        request.remove("PortalOptions");
        request.putArray("Tags").addObject().put("Key", "bad*").put("Value", "value");
        assertError("ValidationException", () -> service.createApplication(request, ACCOUNT_ID, "us-east-1"));

        request.remove("Tags");
        request.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/aws-managed");
        assertError("ResourceNotFoundException", () -> service.createApplication(request, ACCOUNT_ID, "us-east-1"));
    }

    @Test
    void addRegionValidatesAndRejectsDuplicates() {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("RegionName", "us-west-2");

        RegionMetadata created = service.addRegion(request);
        assertEquals("us-west-2", created.regionName());
        assertEquals("ADDING", created.status());
        assertFalse(created.primaryRegion());
        assertNotNull(created.addedDate());

        RegionMetadata described = service.describeRegion(request);
        assertEquals("us-west-2", described.regionName());
        assertEquals("ACTIVE", described.status());
        assertFalse(described.primaryRegion());

        ObjectNode primaryDescribe = mapper.createObjectNode();
        primaryDescribe.put("InstanceArn", service.getInstanceArn());
        primaryDescribe.put("RegionName", "us-east-1");
        assertTrue(service.describeRegion(primaryDescribe).primaryRegion());

        ObjectNode missingDescribe = request.deepCopy();
        missingDescribe.put("RegionName", "eu-west-3");
        assertError("ResourceNotFoundException", () -> service.describeRegion(missingDescribe));

        assertError("ConflictException", () -> service.addRegion(request));

        ObjectNode primaryRegion = request.deepCopy();
        primaryRegion.put("RegionName", "us-east-1");
        assertError("ConflictException", () -> service.addRegion(primaryRegion));

        ObjectNode invalidRegion = request.deepCopy();
        invalidRegion.put("RegionName", "invalid");
        assertError("ValidationException", () -> service.addRegion(invalidRegion));
    }

    @Test
    void addRegionEnforcesTheDocumentedSixRegionQuotaIncludingPrimary() {
        for (String region : java.util.List.of("us-west-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-south-1")) {
            ObjectNode request = mapper.createObjectNode();
            request.put("InstanceArn", service.getInstanceArn());
            request.put("RegionName", region);
            service.addRegion(request);
        }

        ObjectNode overQuota = mapper.createObjectNode();
        overQuota.put("InstanceArn", service.getInstanceArn());
        overQuota.put("RegionName", "ap-northeast-1");
        assertError("ServiceQuotaExceededException", () -> service.addRegion(overQuota));
    }

    @Test
    void attachesCustomerManagedPolicyReferencesWithAwsValidationRules() {
        PermissionSet permissionSet = createPermissionSet("CustomerPolicyAdmins");
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("PermissionSetArn", permissionSet.arn());
        request.putObject("CustomerManagedPolicyReference").put("Name", "PlatformPolicy");

        service.attachCustomerManagedPolicyReference(request);

        ObjectNode list = mapper.createObjectNode();
        list.put("InstanceArn", service.getInstanceArn());
        list.put("PermissionSetArn", permissionSet.arn());
        assertEquals(1, service.listCustomerManagedPolicyReferences(list).items().size());
        assertEquals("PlatformPolicy", service.listCustomerManagedPolicyReferences(list).items().get(0).name());

        PermissionSet stored = service.getPermissionSet(service.getInstanceArn(), permissionSet.arn());
        assertEquals(1, stored.customerManagedPolicies().size());
        assertEquals("/", stored.customerManagedPolicies().values().iterator().next().path());

        ObjectNode caseInsensitiveDuplicate = request.deepCopy();
        caseInsensitiveDuplicate.withObject("CustomerManagedPolicyReference").put("Name", "platformpolicy");
        assertError("ConflictException", () -> service.attachCustomerManagedPolicyReference(caseInsensitiveDuplicate));

        ObjectNode invalidPath = request.deepCopy();
        invalidPath.withObject("CustomerManagedPolicyReference").put("Name", "OtherPolicy").put("Path", "missing-slash");
        assertError("ValidationException", () -> service.attachCustomerManagedPolicyReference(invalidPath));

        ObjectNode detach = request.deepCopy();
        detach.withObject("CustomerManagedPolicyReference").put("Name", "platformpolicy");
        service.detachCustomerManagedPolicyReference(detach);
        assertTrue(service.getPermissionSet(service.getInstanceArn(), permissionSet.arn()).customerManagedPolicies().isEmpty());
        assertError("ResourceNotFoundException", () -> service.detachCustomerManagedPolicyReference(detach));
    }

    @Test
    void customerManagedPoliciesShareTheDocumentedManagedPolicyQuota() {
        PermissionSet permissionSet = createPermissionSet("QuotaPolicyAdmins");
        for (int i = 0; i < 24; i++) {
            service.attachPolicy(service.getInstanceArn(), permissionSet.arn(),
                    "arn:aws:iam::aws:policy/TestPolicy" + i);
        }
        ObjectNode customer = mapper.createObjectNode();
        customer.put("InstanceArn", service.getInstanceArn());
        customer.put("PermissionSetArn", permissionSet.arn());
        customer.putObject("CustomerManagedPolicyReference").put("Name", "CustomerPolicy");
        service.attachCustomerManagedPolicyReference(customer);

        assertError("ServiceQuotaExceededException", () -> service.attachPolicy(service.getInstanceArn(), permissionSet.arn(),
                "arn:aws:iam::aws:policy/OverQuotaPolicy"));
    }

    @Test
    void managedPolicyPaginationHonorsMaxResultsAndNextToken() {
        PermissionSet permissionSet = createPermissionSet("PlatformAdmins");
        service.attachPolicy(service.getInstanceArn(), permissionSet.arn(), "arn:aws:iam::aws:policy/ReadOnlyAccess");
        service.attachPolicy(service.getInstanceArn(), permissionSet.arn(), "arn:aws:iam::aws:policy/SecurityAudit");
        service.attachPolicy(service.getInstanceArn(), permissionSet.arn(), "arn:aws:iam::aws:policy/ViewOnlyAccess");

        ObjectNode firstRequest = managedPoliciesRequest(permissionSet.arn());
        firstRequest.put("MaxResults", 1);
        var first = service.listManagedPolicies(firstRequest);

        assertEquals(1, first.items().size());
        assertNotNull(first.nextToken());

        ObjectNode secondRequest = managedPoliciesRequest(permissionSet.arn());
        secondRequest.put("MaxResults", 1);
        secondRequest.put("NextToken", first.nextToken());
        var second = service.listManagedPolicies(secondRequest);

        assertEquals(1, second.items().size());
        assertNotNull(second.nextToken());
        assertFalse(first.items().get(0).getKey().equals(second.items().get(0).getKey()));
    }

    @Test
    void managedPolicyPaginationRejectsInvalidInputs() {
        PermissionSet permissionSet = createPermissionSet("AuditAdmins");

        ObjectNode badLimit = managedPoliciesRequest(permissionSet.arn());
        badLimit.put("MaxResults", 101);
        assertError("ValidationException", () -> service.listManagedPolicies(badLimit));

        ObjectNode badToken = managedPoliciesRequest(permissionSet.arn());
        badToken.put("NextToken", "not%base64");
        assertError("ValidationException", () -> service.listManagedPolicies(badToken));
    }

    @Test
    void duplicateManagedPolicyReturnsConflict() {
        PermissionSet permissionSet = createPermissionSet("SecurityAdmins");
        String policyArn = "arn:aws:iam::aws:policy/SecurityAudit";
        service.attachPolicy(service.getInstanceArn(), permissionSet.arn(), policyArn);

        assertError("ConflictException",
                () -> service.attachPolicy(service.getInstanceArn(), permissionSet.arn(), policyArn));
    }

    @Test
    void deletePermissionSetRemovesAssignmentsAndProvisioningState() {
        PermissionSet permissionSet = createPermissionSet("DeletePermissionSetAdmins");
        service.createAssignment(assignmentRequest(permissionSet.arn()));
        assertFalse(service.listAssignments(service.getInstanceArn(), ACCOUNT_ID, permissionSet.arn()).isEmpty());

        service.deletePermissionSet(service.getInstanceArn(), permissionSet.arn());

        assertError("ResourceNotFoundException",
                () -> service.getPermissionSet(service.getInstanceArn(), permissionSet.arn()));
        assertTrue(service.listPermissionSetsProvisionedToAccount(mapper.createObjectNode()
                .put("InstanceArn", service.getInstanceArn())
                .put("AccountId", ACCOUNT_ID)).items().stream().noneMatch(permissionSet.arn()::equals));
        assertError("ResourceNotFoundException",
                () -> service.deletePermissionSet(service.getInstanceArn(), permissionSet.arn()));
    }

    @Test
    void listAccountAssignmentsForPrincipalFiltersAndPaginatesUserOrGroupAccess() {
        PermissionSet first = createPermissionSet("PrincipalListOne");
        PermissionSet second = createPermissionSet("PrincipalListTwo");
        service.createAssignment(assignmentRequest(first.arn()));
        service.createAssignment(assignmentRequest(second.arn()));

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("PrincipalId", PRINCIPAL_ID);
        request.put("PrincipalType", "GROUP");
        request.put("MaxResults", 1);

        var firstPage = service.listAssignmentsForPrincipal(request, ACCOUNT_ID);
        assertEquals(1, firstPage.items().size());
        assertNotNull(firstPage.nextToken());

        request.put("NextToken", firstPage.nextToken());
        var secondPage = service.listAssignmentsForPrincipal(request, ACCOUNT_ID);
        assertEquals(1, secondPage.items().size());
        assertTrue(secondPage.nextToken() == null);

        request.remove("NextToken");
        request.remove("MaxResults");
        request.putObject("Filter").put("AccountId", ACCOUNT_ID);
        assertEquals(2, service.listAssignmentsForPrincipal(request, ACCOUNT_ID).items().size());

        ObjectNode invalidType = request.deepCopy();
        invalidType.put("PrincipalType", "ROLE");
        assertError("ValidationException", () -> service.listAssignmentsForPrincipal(invalidType, ACCOUNT_ID));

        SsoAdminService accountInstanceService = emptyService();
        SsoInstance accountInstance = accountInstanceService.createInstance(mapper.createObjectNode(), ACCOUNT_ID, "us-east-1");
        ObjectNode accountInstanceRequest = mapper.createObjectNode();
        accountInstanceRequest.put("InstanceArn", accountInstance.instanceArn());
        accountInstanceRequest.put("PrincipalId", PRINCIPAL_ID);
        accountInstanceRequest.put("PrincipalType", "USER");
        assertError("AccessDeniedException",
                () -> accountInstanceService.listAssignmentsForPrincipal(accountInstanceRequest, ACCOUNT_ID));
    }

    @Test
    void provisionPermissionSetSupportsSingleAndAllProvisionedAccounts() {
        PermissionSet permissionSet = createPermissionSet("ProvisionAdmins");

        ObjectNode single = mapper.createObjectNode();
        single.put("InstanceArn", service.getInstanceArn());
        single.put("PermissionSetArn", permissionSet.arn());
        single.put("TargetType", "AWS_ACCOUNT");
        single.put("TargetId", ACCOUNT_ID);
        PermissionSetProvisioningOperation operation = service.provisionPermissionSet(single);
        assertEquals("SUCCEEDED", operation.status());
        assertEquals(ACCOUNT_ID, operation.accountId());
        assertEquals(permissionSet.arn(), operation.permissionSetArn());
        assertTrue(operation.createdDateEpochMillis() > 0);
        assertEquals(operation, service.getPermissionSetProvisioningOperation(service.getInstanceArn(), operation.requestId()));
        assertError("ValidationException",
                () -> service.getPermissionSetProvisioningOperation(service.getInstanceArn(), "not-a-uuid"));

        ObjectNode assignment = assignmentRequest(permissionSet.arn());
        service.createAssignment(assignment);
        ObjectNode all = mapper.createObjectNode();
        all.put("InstanceArn", service.getInstanceArn());
        all.put("PermissionSetArn", permissionSet.arn());
        all.put("TargetType", "ALL_PROVISIONED_ACCOUNTS");
        PermissionSetProvisioningOperation allOperation = service.provisionPermissionSet(all);
        assertEquals("SUCCEEDED", allOperation.status());
        assertTrue(allOperation.accountId() == null);

        ObjectNode listStatus = mapper.createObjectNode();
        listStatus.put("InstanceArn", service.getInstanceArn());
        listStatus.put("MaxResults", 1);
        listStatus.putObject("Filter").put("Status", "SUCCEEDED");
        var statusPage = service.listPermissionSetProvisioningStatus(listStatus);
        assertEquals(1, statusPage.items().size());
        assertNotNull(statusPage.nextToken());
        listStatus.putObject("Filter").put("Status", "INVALID");
        assertError("ValidationException", () -> service.listPermissionSetProvisioningStatus(listStatus));

        ObjectNode invalid = single.deepCopy();
        invalid.put("TargetType", "ORGANIZATION");
        assertError("ValidationException", () -> service.provisionPermissionSet(invalid));

        ObjectNode invalidAll = all.deepCopy();
        invalidAll.put("TargetId", ACCOUNT_ID);
        assertError("ValidationException", () -> service.provisionPermissionSet(invalidAll));

        SsoAdminService accountInstanceService = emptyService();
        SsoInstance accountInstance = accountInstanceService.createInstance(mapper.createObjectNode(), ACCOUNT_ID, "us-east-1");
        ObjectNode accountInstanceRequest = single.deepCopy();
        accountInstanceRequest.put("InstanceArn", accountInstance.instanceArn());
        assertError("AccessDeniedException", () -> accountInstanceService.provisionPermissionSet(accountInstanceRequest));
    }

    @Test
    void listPermissionSetsProvisionedToAccountFiltersCurrentAndStaleProvisioning() {
        PermissionSet first = createPermissionSet("ProvisionedListOne");
        PermissionSet second = createPermissionSet("ProvisionedListTwo");
        for (PermissionSet permissionSet : java.util.List.of(first, second)) {
            ObjectNode provision = mapper.createObjectNode();
            provision.put("InstanceArn", service.getInstanceArn());
            provision.put("PermissionSetArn", permissionSet.arn());
            provision.put("TargetType", "AWS_ACCOUNT");
            provision.put("TargetId", ACCOUNT_ID);
            service.provisionPermissionSet(provision);
        }

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("AccountId", ACCOUNT_ID);
        request.put("MaxResults", 1);
        var firstPage = service.listPermissionSetsProvisionedToAccount(request);
        assertEquals(1, firstPage.items().size());
        assertNotNull(firstPage.nextToken());

        request.remove("MaxResults");
        request.remove("NextToken");
        request.put("ProvisioningStatus", "LATEST_PERMISSION_SET_PROVISIONED");
        assertEquals(2, service.listPermissionSetsProvisionedToAccount(request).items().size());

        ObjectNode update = mapper.createObjectNode();
        update.put("InstanceArn", service.getInstanceArn());
        update.put("PermissionSetArn", first.arn());
        update.put("Description", "Changed after provisioning");
        service.updatePermissionSet(update);
        assertEquals(1, service.listPermissionSetsProvisionedToAccount(request).items().size());

        request.put("ProvisioningStatus", "LATEST_PERMISSION_SET_NOT_PROVISIONED");
        assertEquals(java.util.List.of(first.arn()), service.listPermissionSetsProvisionedToAccount(request).items());

        request.put("ProvisioningStatus", "FAILED");
        assertError("ValidationException", () -> service.listPermissionSetsProvisionedToAccount(request));
    }

    @Test
    void listAccountsForProvisionedPermissionSetFiltersCurrentAndStaleAccounts() {
        PermissionSet permissionSet = createPermissionSet("ProvisionedAccountsList");
        for (String accountId : java.util.List.of(ACCOUNT_ID, "210987654321")) {
            ObjectNode provision = mapper.createObjectNode();
            provision.put("InstanceArn", service.getInstanceArn());
            provision.put("PermissionSetArn", permissionSet.arn());
            provision.put("TargetType", "AWS_ACCOUNT");
            provision.put("TargetId", accountId);
            service.provisionPermissionSet(provision);
        }

        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("PermissionSetArn", permissionSet.arn());
        request.put("MaxResults", 1);
        var firstPage = service.listAccountsForProvisionedPermissionSet(request);
        assertEquals(1, firstPage.items().size());
        assertNotNull(firstPage.nextToken());

        request.remove("MaxResults");
        request.remove("NextToken");
        request.put("ProvisioningStatus", "LATEST_PERMISSION_SET_PROVISIONED");
        assertEquals(2, service.listAccountsForProvisionedPermissionSet(request).items().size());

        ObjectNode update = mapper.createObjectNode();
        update.put("InstanceArn", service.getInstanceArn());
        update.put("PermissionSetArn", permissionSet.arn());
        update.put("Description", "Changed after provisioning");
        service.updatePermissionSet(update);
        assertEquals(0, service.listAccountsForProvisionedPermissionSet(request).items().size());

        request.put("ProvisioningStatus", "LATEST_PERMISSION_SET_NOT_PROVISIONED");
        assertEquals(2, service.listAccountsForProvisionedPermissionSet(request).items().size());

        request.put("ProvisioningStatus", "FAILED");
        assertError("ValidationException", () -> service.listAccountsForProvisionedPermissionSet(request));
    }

    @Test
    void assignmentValidationAndDuplicateDetectionAreModeled() {
        PermissionSet permissionSet = createPermissionSet("AssignmentAdmins");
        ObjectNode request = assignmentRequest(permissionSet.arn());
        AssignmentOperation created = service.createAssignment(request);
        assertEquals("SUCCEEDED", created.status());
        assertTrue(created.createdDateEpochMillis() > 0);

        ObjectNode listStatus = mapper.createObjectNode();
        listStatus.put("InstanceArn", service.getInstanceArn());
        listStatus.putObject("Filter").put("Status", "SUCCEEDED");
        var statusPage = service.listAccountAssignmentCreationStatus(listStatus);
        assertEquals(1, statusPage.items().size());
        assertEquals(created.requestId(), statusPage.items().get(0).requestId());
        listStatus.putObject("Filter");
        assertEquals(1, service.listAccountAssignmentCreationStatus(listStatus).items().size());
        listStatus.putObject("Filter").put("Status", "INVALID");
        assertError("ValidationException", () -> service.listAccountAssignmentCreationStatus(listStatus));

        assertError("ConflictException", () -> service.createAssignment(request));

        ObjectNode invalidPrincipalType = assignmentRequest(permissionSet.arn());
        invalidPrincipalType.put("PrincipalType", "ROLE");
        invalidPrincipalType.put("PrincipalId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertError("ValidationException", () -> service.createAssignment(invalidPrincipalType));
    }

    @Test
    void deleteAccountAssignmentRemovesAssignmentAndCreatesDeletionOperation() {
        PermissionSet permissionSet = createPermissionSet("DeleteAssignmentAdmins");
        ObjectNode request = assignmentRequest(permissionSet.arn());
        service.createAssignment(request);

        AssignmentDeletionOperation deleted = service.deleteAssignment(request);
        assertEquals("SUCCEEDED", deleted.status());
        assertTrue(deleted.createdDateEpochMillis() > 0);
        assertTrue(service.listAssignments(service.getInstanceArn(), ACCOUNT_ID, permissionSet.arn()).isEmpty());
        assertEquals(deleted, service.getAssignmentDeletionOperation(service.getInstanceArn(), deleted.requestId()));

        ObjectNode listStatus = mapper.createObjectNode();
        listStatus.put("InstanceArn", service.getInstanceArn());
        listStatus.putObject("Filter").put("Status", "SUCCEEDED");
        var statusPage = service.listAccountAssignmentDeletionStatus(listStatus);
        assertEquals(1, statusPage.items().size());
        assertEquals(deleted.requestId(), statusPage.items().get(0).requestId());
        listStatus.putObject("Filter").put("Status", "INVALID");
        assertError("ValidationException", () -> service.listAccountAssignmentDeletionStatus(listStatus));

        assertError("ResourceNotFoundException", () -> service.deleteAssignment(request));
    }

    @Test
    void deleteAccountAssignmentValidatesPrincipalTypeAndTargetType() {
        PermissionSet permissionSet = createPermissionSet("DeleteValidationAdmins");
        ObjectNode request = assignmentRequest(permissionSet.arn());
        service.createAssignment(request);

        ObjectNode invalidTargetType = request.deepCopy();
        invalidTargetType.put("TargetType", "APPLICATION");
        assertError("ValidationException", () -> service.deleteAssignment(invalidTargetType));

        ObjectNode wrongPrincipalType = request.deepCopy();
        wrongPrincipalType.put("PrincipalType", "USER");
        assertError("ResourceNotFoundException", () -> service.deleteAssignment(wrongPrincipalType));
    }

    @Test
    void putPermissionsBoundarySupportsAwsManagedAndCustomerManagedPolicies() {
        PermissionSet permissionSet = createPermissionSet("BoundaryAdmins");
        assertError("ResourceNotFoundException",
                () -> service.getPermissionsBoundary(service.getInstanceArn(), permissionSet.arn()));

        ObjectNode managed = mapper.createObjectNode();
        managed.put("InstanceArn", service.getInstanceArn());
        managed.put("PermissionSetArn", permissionSet.arn());
        managed.putObject("PermissionsBoundary")
                .put("ManagedPolicyArn", "arn:aws:iam::aws:policy/PowerUserAccess");
        service.putPermissionsBoundary(managed);
        assertEquals("arn:aws:iam::aws:policy/PowerUserAccess",
                service.getPermissionsBoundary(service.getInstanceArn(), permissionSet.arn()).managedPolicyArn());

        ObjectNode customer = mapper.createObjectNode();
        customer.put("InstanceArn", service.getInstanceArn());
        customer.put("PermissionSetArn", permissionSet.arn());
        customer.putObject("PermissionsBoundary")
                .putObject("CustomerManagedPolicyReference")
                .put("Name", "BoundaryPolicy")
                .put("Path", "/platform/");
        service.putPermissionsBoundary(customer);
        assertEquals("BoundaryPolicy", service.getPermissionSet(service.getInstanceArn(), permissionSet.arn())
                .permissionsBoundary().customerManagedPolicyReference().name());
        service.deletePermissionsBoundary(service.getInstanceArn(), permissionSet.arn());
        assertError("ResourceNotFoundException",
                () -> service.getPermissionsBoundary(service.getInstanceArn(), permissionSet.arn()));
        assertError("ResourceNotFoundException",
                () -> service.deletePermissionsBoundary(service.getInstanceArn(), permissionSet.arn()));

        ObjectNode invalid = managed.deepCopy();
        invalid.withObject("PermissionsBoundary")
                .putObject("CustomerManagedPolicyReference")
                .put("Name", "BoundaryPolicy");
        assertError("ValidationException", () -> service.putPermissionsBoundary(invalid));
    }

    @Test
    void clearRemovesPersistedServiceState() {
        PermissionSet permissionSet = createPermissionSet("ResetAdmins");
        AssignmentOperation operation = service.createAssignment(assignmentRequest(permissionSet.arn()));
        assertFalse(service.listPermissionSets(service.getInstanceArn()).isEmpty());
        assertNotNull(service.getAssignmentOperation(service.getInstanceArn(), operation.requestId()));

        service.clear();

        assertTrue(service.listPermissionSets(service.getInstanceArn()).isEmpty());
        assertError("ResourceNotFoundException",
                () -> service.getAssignmentOperation(service.getInstanceArn(), operation.requestId()));
    }

    private SsoAdminService emptyService() {
        return new SsoAdminService(
                new InMemoryStorage<String, PermissionSet>(),
                new InMemoryStorage<String, Assignment>(),
                new InMemoryStorage<String, AssignmentOperation>(),
                new InMemoryStorage<String, AssignmentDeletionOperation>(),
                new InMemoryStorage<String, PermissionSetProvisioning>(),
                new InMemoryStorage<String, PermissionSetProvisioningOperation>(),
                new InMemoryStorage<String, RegionMetadata>(),
                new InMemoryStorage<String, SsoApplication>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, ApplicationAssignment>(),
                new InMemoryStorage<String, ApplicationAccessScope>(),
                new InMemoryStorage<String, ApplicationAuthenticationMethod>(),
                new InMemoryStorage<String, ApplicationGrant>(),
                new InMemoryStorage<String, SsoInstance>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, Boolean>(),
                new InMemoryStorage<String, InstanceAccessControlAttributeConfiguration>(),
                new InMemoryStorage<String, TrustedTokenIssuer>(),
                new InMemoryStorage<String, String>(),
                identityStoreService,
                organizationsService,
                "999999999999",
                "us-east-1");
    }

    private ObjectNode trustedTokenIssuerRequest(String name, String clientToken) {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("Name", name);
        request.put("TrustedTokenIssuerType", "OIDC_JWT");
        if (clientToken != null) {
            request.put("ClientToken", clientToken);
        }
        request.putObject("TrustedTokenIssuerConfiguration")
                .putObject("OidcJwtConfiguration")
                .put("ClaimAttributePath", "sub")
                .put("IdentityStoreAttributePath", "userName")
                .put("IssuerUrl", "https://issuer.example.com")
                .put("JwksRetrievalOption", "OPEN_ID_DISCOVERY");
        return request;
    }

    private SsoApplication createApplication(String name, String clientToken) {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom");
        request.put("Name", name);
        request.put("ClientToken", clientToken);
        return service.createApplication(request, ACCOUNT_ID, "us-east-1");
    }

    private PermissionSet createPermissionSet(String name) {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("Name", name);
        return service.createPermissionSet(request);
    }

    private ObjectNode managedPoliciesRequest(String permissionSetArn) {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("PermissionSetArn", permissionSetArn);
        return request;
    }

    private ObjectNode assignmentRequest(String permissionSetArn) {
        ObjectNode request = mapper.createObjectNode();
        request.put("InstanceArn", service.getInstanceArn());
        request.put("TargetId", ACCOUNT_ID);
        request.put("TargetType", "AWS_ACCOUNT");
        request.put("PermissionSetArn", permissionSetArn);
        request.put("PrincipalType", "GROUP");
        request.put("PrincipalId", PRINCIPAL_ID);
        return request;
    }

    private static void assertError(String expectedCode, Runnable operation) {
        AwsException error = assertThrows(AwsException.class, operation::run);
        assertEquals(expectedCode, error.getErrorCode());
    }
}
