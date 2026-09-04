package io.github.hectorvent.floci.services.ssoadmin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SsoAdminService {
    private static final String INSTANCE_ARN = "arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e";
    private static final String IDENTITY_STORE_ID = "d-9067f2a3c1";

    private final StorageBackend<String, PermissionSet> permissionSets;
    private final StorageBackend<String, Assignment> assignments;
    private final StorageBackend<String, AssignmentOperation> assignmentOperations;

    @Inject
    public SsoAdminService(StorageFactory storageFactory) {
        this(
                storageFactory.create("ssoadmin", "ssoadmin-permission-sets.json", new TypeReference<Map<String, PermissionSet>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-assignments.json", new TypeReference<Map<String, Assignment>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-assignment-operations.json", new TypeReference<Map<String, AssignmentOperation>>() {}));
    }

    SsoAdminService(StorageBackend<String, PermissionSet> permissionSets,
                    StorageBackend<String, Assignment> assignments,
                    StorageBackend<String, AssignmentOperation> assignmentOperations) {
        this.permissionSets = permissionSets;
        this.assignments = assignments;
        this.assignmentOperations = assignmentOperations;
    }

    public String getInstanceArn() { return INSTANCE_ARN; }
    public String getIdentityStoreId() { return IDENTITY_STORE_ID; }

    public List<PermissionSet> listPermissionSets(String instanceArn) {
        requireInstance(instanceArn);
        return permissionSets.scan(key -> true).stream().sorted(Comparator.comparing(PermissionSet::name)).toList();
    }

    public synchronized PermissionSet createPermissionSet(JsonNode request) {
        requireInstance(required(request, "InstanceArn"));
        String name = required(request, "Name");
        if (permissionSets.scan(key -> true).stream().anyMatch(p -> name.equals(p.name()))) {
            throw new AwsException("ConflictException", "Permission set already exists: " + name, 400);
        }
        String arn = "arn:aws:sso:::permissionSet/ssoins-7223b02a5d9f7c8e/ps-" + shortId();
        PermissionSet permissionSet = new PermissionSet(arn, name, text(request, "Description"),
                valueOr(request, "SessionDuration", "PT8H"), new LinkedHashMap<>(), null);
        permissionSets.put(arn, permissionSet);
        return permissionSet;
    }

    public PermissionSet getPermissionSet(String instanceArn, String arn) {
        requireInstance(instanceArn);
        return permissionSets.get(arn).orElseThrow(() -> notFound("Permission set not found: " + arn));
    }

    public synchronized PermissionSet updatePermissionSet(JsonNode request) {
        PermissionSet current = getPermissionSet(required(request, "InstanceArn"), required(request, "PermissionSetArn"));
        PermissionSet updated = new PermissionSet(current.arn(), current.name(),
                request.has("Description") ? text(request, "Description") : current.description(),
                request.has("SessionDuration") ? text(request, "SessionDuration") : current.sessionDuration(),
                new LinkedHashMap<>(current.managedPolicies()), current.inlinePolicy());
        permissionSets.put(updated.arn(), updated);
        return updated;
    }

    public synchronized void attachPolicy(String instanceArn, String arn, String policyArn) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        current.managedPolicies().put(policyArn, policyArn.substring(policyArn.lastIndexOf('/') + 1));
        permissionSets.put(arn, current);
    }

    public synchronized void detachPolicy(String instanceArn, String arn, String policyArn) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        current.managedPolicies().remove(policyArn);
        permissionSets.put(arn, current);
    }

    public synchronized void putInlinePolicy(String instanceArn, String arn, String policy) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        permissionSets.put(arn, new PermissionSet(current.arn(), current.name(), current.description(),
                current.sessionDuration(), current.managedPolicies(), policy));
    }

    public synchronized void deleteInlinePolicy(String instanceArn, String arn) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        permissionSets.put(arn, new PermissionSet(current.arn(), current.name(), current.description(),
                current.sessionDuration(), current.managedPolicies(), null));
    }

    public List<Assignment> listAssignments(String instanceArn, String accountId, String permissionSetArn) {
        requireInstance(instanceArn);
        return assignments.scan(key -> true).stream()
                .filter(a -> accountId.equals(a.accountId()) && permissionSetArn.equals(a.permissionSetArn()))
                .sorted(Comparator.comparing(Assignment::principalId)).toList();
    }

    public synchronized AssignmentOperation createAssignment(JsonNode request) {
        requireInstance(required(request, "InstanceArn"));
        String account = required(request, "TargetId");
        String permission = required(request, "PermissionSetArn");
        getPermissionSet(INSTANCE_ARN, permission);
        String principal = required(request, "PrincipalId");
        String principalType = required(request, "PrincipalType");
        Assignment assignment = new Assignment(account, permission, principal, principalType);
        assignments.put(account + "::" + permission + "::" + principal, assignment);
        String requestId = UUID.randomUUID().toString();
        AssignmentOperation operation = new AssignmentOperation(requestId, "SUCCEEDED", account, permission, principal, principalType, null);
        assignmentOperations.put(requestId, operation);
        return operation;
    }

    public AssignmentOperation getAssignmentOperation(String instanceArn, String requestId) {
        requireInstance(instanceArn);
        return assignmentOperations.get(requestId).orElseThrow(() -> notFound("Assignment operation not found: " + requestId));
    }

    static String required(JsonNode request, String field) {
        String value = text(request, field);
        if (value == null || value.isBlank()) {
            throw new AwsException("ValidationException", field + " must be a non-empty string.", 400);
        }
        return value;
    }
    static String text(JsonNode request, String field) {
        JsonNode node = request == null ? null : request.get(field);
        return node != null && node.isTextual() ? node.textValue() : null;
    }
    private static String valueOr(JsonNode request, String field, String fallback) {
        String value = text(request, field);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static String shortId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    private static AwsException notFound(String message) { return new AwsException("ResourceNotFoundException", message, 400); }
    private static void requireInstance(String arn) {
        if (!INSTANCE_ARN.equals(arn)) throw notFound("IAM Identity Center instance not found: " + arn);
    }

    public record PermissionSet(String arn, String name, String description, String sessionDuration,
                                Map<String, String> managedPolicies, String inlinePolicy) {}
    public record Assignment(String accountId, String permissionSetArn, String principalId, String principalType) {}
    public record AssignmentOperation(String requestId, String status, String accountId, String permissionSetArn,
                                      String principalId, String principalType, String failureReason) {}
}
