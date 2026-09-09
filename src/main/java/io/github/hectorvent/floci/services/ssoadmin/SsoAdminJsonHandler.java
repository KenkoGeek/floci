package io.github.hectorvent.floci.services.ssoadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.ssoadmin.model.Assignment;
import io.github.hectorvent.floci.services.ssoadmin.model.AssignmentOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSet;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoApplication;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class SsoAdminJsonHandler {
    private final SsoAdminService service;
    private final ObjectMapper mapper;

    @Inject
    public SsoAdminJsonHandler(SsoAdminService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    public Response handle(String action, JsonNode request, String callerAccountId, String region) {
        return switch (action) {
            case "ListInstances" -> listInstances(callerAccountId);
            case "CreateInstance" -> createInstance(request, callerAccountId, region);
            case "CreateInstanceAccessControlAttributeConfiguration" -> createInstanceAccessControlAttributeConfiguration(request);
            case "CreateTrustedTokenIssuer" -> createTrustedTokenIssuer(request, callerAccountId);
            case "AddRegion" -> addRegion(request);
            case "CreateApplication" -> createApplication(request, callerAccountId, region);
            case "CreateApplicationAssignment" -> createApplicationAssignment(request);
            case "DeleteApplication" -> deleteApplication(request);
            case "DeleteApplicationAccessScope" -> deleteApplicationAccessScope(request);
            case "DeleteApplicationAssignment" -> deleteApplicationAssignment(request);
            case "DeleteApplicationAuthenticationMethod" -> deleteApplicationAuthenticationMethod(request);
            case "DeleteApplicationGrant" -> deleteApplicationGrant(request);
            case "ListPermissionSets" -> listPermissionSets(request);
            case "CreatePermissionSet" -> createPermissionSet(request);
            case "DescribePermissionSet" -> describePermissionSet(request);
            case "UpdatePermissionSet" -> updatePermissionSet(request);
            case "ListManagedPoliciesInPermissionSet" -> listManagedPolicies(request);
            case "AttachManagedPolicyToPermissionSet" -> attachManagedPolicy(request);
            case "AttachCustomerManagedPolicyReferenceToPermissionSet" -> attachCustomerManagedPolicyReference(request);
            case "DetachManagedPolicyFromPermissionSet" -> detachManagedPolicy(request);
            case "DeleteInlinePolicyFromPermissionSet" -> deleteInlinePolicy(request);
            case "PutInlinePolicyToPermissionSet" -> putInlinePolicy(request);
            case "ListAccountAssignments" -> listAccountAssignments(request);
            case "CreateAccountAssignment" -> createAccountAssignment(request);
            case "DeleteAccountAssignment" -> deleteAccountAssignment(request);
            case "DescribeAccountAssignmentCreationStatus" -> describeAssignment(request);
            default -> throw new AwsException("UnknownOperationException", "Operation " + action + " is not supported.", 400);
        };
    }

    private Response listInstances(String callerAccountId) {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode instances = response.putArray("Instances");
        service.listInstances(callerAccountId).forEach(instance -> {
            ObjectNode node = instances.addObject();
            node.put("InstanceArn", instance.instanceArn());
            node.put("IdentityStoreId", instance.identityStoreId());
            if (instance.name() != null) {
                node.put("Name", instance.name());
            }
            node.put("OwnerAccountId", instance.ownerAccountId());
            node.put("CreatedDate", instance.createdDateEpochMillis() / 1000.0d);
            node.put("PrimaryRegion", instance.primaryRegion());
            ArrayNode regionNodes = node.putArray("Regions");
            service.listRegionsForInstance(instance).forEach(region -> {
                ObjectNode regionNode = regionNodes.addObject();
                regionNode.put("RegionName", region.regionName());
                regionNode.put("Status", region.status());
                regionNode.put("IsPrimaryRegion", region.primaryRegion());
                regionNode.put("AddedDate", service.regionAddedDateEpochSeconds(region));
            });
            node.put("Status", instance.status());
            if (instance.statusReason() != null) {
                node.put("StatusReason", instance.statusReason());
            }
        });
        return Response.ok(response).build();
    }

    private Response createInstance(JsonNode request, String callerAccountId, String region) {
        var instance = service.createInstance(request, callerAccountId, region);
        return Response.ok(mapper.createObjectNode().put("InstanceArn", instance.instanceArn())).build();
    }

    private Response createInstanceAccessControlAttributeConfiguration(JsonNode request) {
        service.createInstanceAccessControlAttributeConfiguration(request);
        return Response.ok().build();
    }

    private Response createTrustedTokenIssuer(JsonNode request, String callerAccountId) {
        var issuer = service.createTrustedTokenIssuer(request, callerAccountId);
        return Response.ok(mapper.createObjectNode()
                .put("TrustedTokenIssuerArn", issuer.trustedTokenIssuerArn())).build();
    }

    private Response addRegion(JsonNode request) {
        var region = service.addRegion(request);
        return Response.ok(mapper.createObjectNode().put("Status", region.status())).build();
    }

    private Response deleteApplication(JsonNode request) {
        service.deleteApplication(SsoAdminService.required(request, "ApplicationArn"));
        return Response.ok().build();
    }

    private Response deleteApplicationAccessScope(JsonNode request) {
        service.deleteApplicationAccessScope(request);
        return Response.ok().build();
    }

    private Response createApplicationAssignment(JsonNode request) {
        service.createApplicationAssignment(request);
        return Response.ok().build();
    }

    private Response deleteApplicationAssignment(JsonNode request) {
        service.deleteApplicationAssignment(request);
        return Response.ok().build();
    }

    private Response deleteApplicationAuthenticationMethod(JsonNode request) {
        service.deleteApplicationAuthenticationMethod(request);
        return Response.ok().build();
    }

    private Response deleteApplicationGrant(JsonNode request) {
        service.deleteApplicationGrant(request);
        return Response.ok().build();
    }

    private Response createApplication(JsonNode request, String callerAccountId, String region) {
        SsoApplication application = service.createApplication(request, callerAccountId, region);
        ObjectNode response = mapper.createObjectNode();
        response.put("ApplicationArn", application.applicationArn());
        response.put("IdentityStoreArn", application.identityStoreArn());
        response.put("InstanceArn", application.instanceArn());
        return Response.ok(response).build();
    }

    private Response listPermissionSets(JsonNode request) {
        var page = service.listPermissionSets(request);
        ObjectNode response = mapper.createObjectNode();
        ArrayNode arns = response.putArray("PermissionSets");
        page.items().forEach(p -> arns.add(p.arn()));
        if (page.nextToken() != null) {
            response.put("NextToken", page.nextToken());
        }
        return Response.ok(response).build();
    }

    private Response createPermissionSet(JsonNode request) {
        PermissionSet p = service.createPermissionSet(request);
        ObjectNode response = mapper.createObjectNode();
        response.set("PermissionSet", permissionSetNode(p));
        return Response.ok(response).build();
    }

    private Response describePermissionSet(JsonNode request) {
        PermissionSet p = service.getPermissionSet(
                SsoAdminService.required(request, "InstanceArn"), SsoAdminService.required(request, "PermissionSetArn"));
        ObjectNode response = mapper.createObjectNode();
        response.set("PermissionSet", permissionSetNode(p));
        return Response.ok(response).build();
    }

    private Response updatePermissionSet(JsonNode request) {
        service.updatePermissionSet(request);
        return Response.ok(mapper.createObjectNode()).build();
    }

    private Response listManagedPolicies(JsonNode request) {
        var page = service.listManagedPolicies(request);
        ObjectNode response = mapper.createObjectNode();
        ArrayNode policies = response.putArray("AttachedManagedPolicies");
        page.items().forEach(policy -> policies.addObject()
                .put("Arn", policy.getKey()).put("Name", policy.getValue()));
        if (page.nextToken() != null) {
            response.put("NextToken", page.nextToken());
        }
        return Response.ok(response).build();
    }

    private Response attachManagedPolicy(JsonNode request) {
        service.attachPolicy(SsoAdminService.required(request, "InstanceArn"),
                SsoAdminService.required(request, "PermissionSetArn"), SsoAdminService.required(request, "ManagedPolicyArn"));
        return Response.ok(mapper.createObjectNode()).build();
    }

    private Response attachCustomerManagedPolicyReference(JsonNode request) {
        service.attachCustomerManagedPolicyReference(request);
        return Response.ok().build();
    }

    private Response detachManagedPolicy(JsonNode request) {
        service.detachPolicy(SsoAdminService.required(request, "InstanceArn"),
                SsoAdminService.required(request, "PermissionSetArn"), SsoAdminService.required(request, "ManagedPolicyArn"));
        return Response.ok(mapper.createObjectNode()).build();
    }

    private Response deleteInlinePolicy(JsonNode request) {
        service.deleteInlinePolicy(SsoAdminService.required(request, "InstanceArn"), SsoAdminService.required(request, "PermissionSetArn"));
        return Response.ok(mapper.createObjectNode()).build();
    }

    private Response putInlinePolicy(JsonNode request) {
        service.putInlinePolicy(SsoAdminService.required(request, "InstanceArn"), SsoAdminService.required(request, "PermissionSetArn"),
                SsoAdminService.required(request, "InlinePolicy"));
        return Response.ok(mapper.createObjectNode()).build();
    }

    private Response listAccountAssignments(JsonNode request) {
        var page = service.listAssignments(request);
        ObjectNode response = mapper.createObjectNode();
        ArrayNode array = response.putArray("AccountAssignments");
        for (Assignment a : page.items()) {
            array.addObject().put("AccountId", a.accountId()).put("PermissionSetArn", a.permissionSetArn())
                    .put("PrincipalId", a.principalId()).put("PrincipalType", a.principalType());
        }
        if (page.nextToken() != null) {
            response.put("NextToken", page.nextToken());
        }
        return Response.ok(response).build();
    }

    private Response createAccountAssignment(JsonNode request) {
        AssignmentOperation op = service.createAssignment(request);
        ObjectNode response = mapper.createObjectNode();
        response.set("AccountAssignmentCreationStatus", assignmentOperationNode(op));
        return Response.ok(response).build();
    }

    private Response deleteAccountAssignment(JsonNode request) {
        var operation = service.deleteAssignment(request);
        ObjectNode response = mapper.createObjectNode();
        ObjectNode status = response.putObject("AccountAssignmentDeletionStatus");
        status.put("RequestId", operation.requestId());
        status.put("Status", operation.status());
        status.put("CreatedDate", operation.createdDateEpochMillis() / 1000.0d);
        status.put("TargetId", operation.accountId());
        status.put("TargetType", "AWS_ACCOUNT");
        status.put("PermissionSetArn", operation.permissionSetArn());
        status.put("PrincipalId", operation.principalId());
        status.put("PrincipalType", operation.principalType());
        if (operation.failureReason() != null) {
            status.put("FailureReason", operation.failureReason());
        }
        return Response.ok(response).build();
    }

    private Response describeAssignment(JsonNode request) {
        AssignmentOperation op = service.getAssignmentOperation(
                SsoAdminService.required(request, "InstanceArn"),
                SsoAdminService.required(request, "AccountAssignmentCreationRequestId"));
        ObjectNode response = mapper.createObjectNode();
        response.set("AccountAssignmentCreationStatus", assignmentOperationNode(op));
        return Response.ok(response).build();
    }

    private ObjectNode permissionSetNode(PermissionSet p) {
        ObjectNode node = mapper.createObjectNode();
        node.put("PermissionSetArn", p.arn());
        node.put("Name", p.name());
        if (p.description() != null) {
            node.put("Description", p.description());
        }
        node.put("SessionDuration", p.sessionDuration());
        return node;
    }

    private ObjectNode assignmentOperationNode(AssignmentOperation op) {
        ObjectNode node = mapper.createObjectNode();
        node.put("RequestId", op.requestId()); node.put("Status", op.status());
        node.put("TargetId", op.accountId()); node.put("TargetType", "AWS_ACCOUNT");
        node.put("PermissionSetArn", op.permissionSetArn()); node.put("PrincipalId", op.principalId());
        node.put("PrincipalType", op.principalType());
        if (op.failureReason() != null) {
            node.put("FailureReason", op.failureReason());
        }
        return node;
    }
}
