package io.github.hectorvent.floci.services.ssoadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.ssoadmin.model.Assignment;
import io.github.hectorvent.floci.services.ssoadmin.model.AssignmentOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSet;
import io.github.hectorvent.floci.services.ssoadmin.model.RegionMetadata;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoApplication;
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

    @BeforeEach
    void setUp() {
        service = new SsoAdminService(
                new InMemoryStorage<String, PermissionSet>(),
                new InMemoryStorage<String, Assignment>(),
                new InMemoryStorage<String, AssignmentOperation>(),
                new InMemoryStorage<String, RegionMetadata>(),
                new InMemoryStorage<String, SsoApplication>(),
                new InMemoryStorage<String, String>());
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
