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
import io.github.hectorvent.floci.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSet;
import io.github.hectorvent.floci.services.ssoadmin.model.RegionMetadata;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoApplication;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoInstance;
import io.github.hectorvent.floci.services.ssoadmin.model.TrustedTokenIssuer;
import io.github.hectorvent.floci.services.organizations.OrganizationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoAdminServiceTest {
    private static final String ACCOUNT_ID = "123456789012";
    private static final String PRINCIPAL_ID = "11111111-2222-3333-4444-555555555555";

    private final ObjectMapper mapper = new ObjectMapper();
    private SsoAdminService service;
    private OrganizationsService organizationsService;
    private InMemoryStorage<String, ApplicationAccessScope> applicationAccessScopes;

    @BeforeEach
    void setUp() {
        organizationsService = org.mockito.Mockito.mock(OrganizationsService.class);
        applicationAccessScopes = new InMemoryStorage<>();
        service = new SsoAdminService(
                new InMemoryStorage<String, PermissionSet>(),
                new InMemoryStorage<String, Assignment>(),
                new InMemoryStorage<String, AssignmentOperation>(),
                new InMemoryStorage<String, AssignmentDeletionOperation>(),
                new InMemoryStorage<String, RegionMetadata>(),
                new InMemoryStorage<String, SsoApplication>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, ApplicationAssignment>(),
                applicationAccessScopes,
                new InMemoryStorage<String, SsoInstance>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, InstanceAccessControlAttributeConfiguration>(),
                new InMemoryStorage<String, TrustedTokenIssuer>(),
                new InMemoryStorage<String, String>(),
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

        ObjectNode mismatch = request.deepCopy();
        mismatch.put("Name", "IssuerTwo");
        assertError("IdempotentParameterMismatch",
                () -> service.createTrustedTokenIssuer(mismatch, ACCOUNT_ID));
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

        PermissionSet stored = service.getPermissionSet(service.getInstanceArn(), permissionSet.arn());
        assertEquals(1, stored.customerManagedPolicies().size());
        assertEquals("/", stored.customerManagedPolicies().values().iterator().next().path());

        ObjectNode caseInsensitiveDuplicate = request.deepCopy();
        caseInsensitiveDuplicate.withObject("CustomerManagedPolicyReference").put("Name", "platformpolicy");
        assertError("ConflictException", () -> service.attachCustomerManagedPolicyReference(caseInsensitiveDuplicate));

        ObjectNode invalidPath = request.deepCopy();
        invalidPath.withObject("CustomerManagedPolicyReference").put("Name", "OtherPolicy").put("Path", "missing-slash");
        assertError("ValidationException", () -> service.attachCustomerManagedPolicyReference(invalidPath));
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
    void assignmentValidationAndDuplicateDetectionAreModeled() {
        PermissionSet permissionSet = createPermissionSet("AssignmentAdmins");
        ObjectNode request = assignmentRequest(permissionSet.arn());
        AssignmentOperation created = service.createAssignment(request);
        assertEquals("SUCCEEDED", created.status());

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
                new InMemoryStorage<String, RegionMetadata>(),
                new InMemoryStorage<String, SsoApplication>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, ApplicationAssignment>(),
                new InMemoryStorage<String, ApplicationAccessScope>(),
                new InMemoryStorage<String, SsoInstance>(),
                new InMemoryStorage<String, String>(),
                new InMemoryStorage<String, InstanceAccessControlAttributeConfiguration>(),
                new InMemoryStorage<String, TrustedTokenIssuer>(),
                new InMemoryStorage<String, String>(),
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
