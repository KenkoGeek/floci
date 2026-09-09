package io.github.hectorvent.floci.services.identitystore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.identitystore.model.Group;
import io.github.hectorvent.floci.services.ssoadmin.SsoAdminService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Path("/{tenantId}/scim/v2")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ScimController {
    private static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    private static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";
    private static final Pattern PREFIXED_TENANT = Pattern.compile(
            "([0-9a-f]{10})-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_TENANT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private final IdentityStoreService identityStoreService;
    private final SsoAdminService ssoAdminService;
    private final ObjectMapper mapper;

    @Inject
    public ScimController(IdentityStoreService identityStoreService, SsoAdminService ssoAdminService, ObjectMapper mapper) {
        this.identityStoreService = identityStoreService;
        this.ssoAdminService = ssoAdminService;
        this.mapper = mapper;
    }

    @POST
    @Path("/Groups")
    public Response createGroup(@PathParam("tenantId") String tenantId,
                                @HeaderParam("Authorization") String authorization,
                                String body) {
        try {
            requireBearer(authorization);
            String identityStoreId = resolveIdentityStore(tenantId);
            JsonNode request = parseObject(body);
            String displayName = requiredText(request, "displayName");

            JsonNode members = request.get("members");
            if (members != null && !members.isNull() && (!members.isArray() || members.size() > 100)) {
                throw validation("members must be an array containing at most 100 users.");
            }

            ObjectNode createRequest = mapper.createObjectNode();
            createRequest.put("IdentityStoreId", identityStoreId);
            createRequest.put("DisplayName", displayName);
            String externalId = optionalText(request, "externalId");
            if (externalId != null) {
                createRequest.putArray("ExternalIds").addObject().put("Issuer", GROUP_SCHEMA).put("Id", externalId);
            }

            Group group = identityStoreService.createGroup(createRequest);
            try {
                if (members != null && members.isArray()) {
                    for (JsonNode member : members) {
                        createMembership(identityStoreId, group.groupId(), member);
                    }
                }
            } catch (RuntimeException failure) {
                ObjectNode deleteRequest = mapper.createObjectNode();
                deleteRequest.put("IdentityStoreId", identityStoreId);
                deleteRequest.put("GroupId", group.groupId());
                identityStoreService.deleteGroup(deleteRequest);
                throw failure;
            }

            return Response.status(Response.Status.CREATED).entity(groupResponse(group)).build();
        } catch (AwsException exception) {
            return scimError(exception.getHttpStatus(), exception.getMessage());
        }
    }

    private void createMembership(String identityStoreId, String groupId, JsonNode member) {
        if (member == null || !member.isObject()) {
            throw validation("Each members entry must be an object.");
        }
        String userId = requiredText(member, "value");
        String type = optionalText(member, "type");
        if (type != null && !"User".equals(type)) {
            throw validation("members.type must be User when specified.");
        }
        ObjectNode membershipRequest = mapper.createObjectNode();
        membershipRequest.put("IdentityStoreId", identityStoreId);
        membershipRequest.put("GroupId", groupId);
        membershipRequest.putObject("MemberId").put("UserId", userId);
        identityStoreService.createMembership(membershipRequest);
    }

    private ObjectNode groupResponse(Group group) {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("schemas").add(GROUP_SCHEMA);
        response.put("id", group.groupId());
        response.put("displayName", group.displayName());
        String externalId = scimExternalId(group.attributes().get("ExternalIds"));
        if (externalId != null) {
            response.put("externalId", externalId);
        }
        ObjectNode meta = response.putObject("meta");
        meta.put("resourceType", "Group");
        meta.put("created", group.createdAt());
        meta.put("lastModified", group.updatedAt());
        return response;
    }

    private String resolveIdentityStore(String tenantId) {
        if (tenantId == null) {
            throw unauthorized("Authorization header is invalid or tenant ID is incorrect.");
        }
        String identityStoreId;
        Matcher prefixed = PREFIXED_TENANT.matcher(tenantId);
        if (prefixed.matches()) {
            identityStoreId = "d-" + prefixed.group(1).toLowerCase(java.util.Locale.ROOT);
        } else if (LEGACY_TENANT.matcher(tenantId).matches()) {
            identityStoreId = tenantId.toLowerCase(java.util.Locale.ROOT);
        } else {
            throw unauthorized("Authorization header is invalid or tenant ID is incorrect.");
        }
        if (!ssoAdminService.hasIdentityStore(identityStoreId)) {
            throw unauthorized("Authorization header is invalid or tenant ID is incorrect.");
        }
        return identityStoreId;
    }

    private static void requireBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) {
            throw unauthorized("Authorization header is invalid or missing.");
        }
    }

    private JsonNode parseObject(String body) {
        try {
            JsonNode node = mapper.readTree(body == null ? "" : body);
            if (node == null || !node.isObject()) {
                throw validation("Request is unparsable, syntactically incorrect, or violates schema.");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw validation("Request is unparsable, syntactically incorrect, or violates schema.");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw validation(field + " is required.");
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw validation(field + " must be a string.");
        }
        return value.textValue();
    }

    private static String scimExternalId(JsonNode externalIds) {
        if (externalIds == null || !externalIds.isArray()) {
            return null;
        }
        for (JsonNode externalId : externalIds) {
            if (GROUP_SCHEMA.equals(optionalText(externalId, "Issuer"))) {
                return optionalText(externalId, "Id");
            }
        }
        return null;
    }

    private Response scimError(int status, String detail) {
        ObjectNode error = mapper.createObjectNode();
        ArrayNode schemas = error.putArray("schemas");
        schemas.add(ERROR_SCHEMA);
        error.put("detail", detail);
        error.put("status", Integer.toString(status));
        return Response.status(status).entity(error).build();
    }

    private static AwsException validation(String message) {
        return new AwsException("ValidationException", message, 400);
    }

    private static AwsException unauthorized(String message) {
        return new AwsException("UnauthorizedException", message, 401);
    }
}
