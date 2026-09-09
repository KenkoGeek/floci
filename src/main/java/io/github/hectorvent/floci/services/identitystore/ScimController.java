package io.github.hectorvent.floci.services.identitystore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.identitystore.model.Group;
import io.github.hectorvent.floci.services.identitystore.model.User;
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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Path("/{tenantId}/scim/v2")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ScimController {
    private static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    private static final String IDENTITYSTORE_ENTERPRISE_EXTENSION = "aws:identitystore:enterprise";
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
    @Path("/Users")
    public Response createUser(@PathParam("tenantId") String tenantId,
                               @HeaderParam("Authorization") String authorization,
                               String body) {
        try {
            requireBearer(authorization);
            String identityStoreId = resolveIdentityStore(tenantId);
            JsonNode request = parseObject(body);
            validateCreateUser(request);
            User user = identityStoreService.createUser(toIdentityStoreUser(identityStoreId, request));
            return Response.status(Response.Status.CREATED).entity(userResponse(user)).build();
        } catch (AwsException exception) {
            return scimError(exception.getHttpStatus(), exception.getMessage());
        }
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

    private void validateCreateUser(JsonNode request) {
        requiredText(request, "userName");
        requiredText(request, "displayName");
        JsonNode name = request.get("name");
        if (name == null || !name.isObject()) {
            throw validation("name is required.");
        }
        requiredText(name, "givenName");
        requiredText(name, "familyName");

        if (request.has("groups")) {
            throw validation("groups cannot be specified when creating a user.");
        }
        for (String unsupported : java.util.List.of("ims", "photos", "x509Certificates", "entitlements", "password")) {
            if (request.has(unsupported)) {
                throw validation(unsupported + " is not supported.");
            }
        }

        validateSingleValueArray(request, "emails", true, true);
        validateSingleValueArray(request, "addresses", false, true);
        validateSingleValueArray(request, "phoneNumbers", false, true);
        validateSingleValueArray(request, "roles", false, false);

        JsonNode roles = request.get("roles");
        if (roles != null && roles.isArray() && !roles.isEmpty() && roles.get(0).has("display")) {
            throw validation("roles.display is not supported.");
        }
        JsonNode enterprise = request.get(ENTERPRISE_USER_SCHEMA);
        if (enterprise != null && !enterprise.isNull()) {
            if (!enterprise.isObject()) {
                throw validation(ENTERPRISE_USER_SCHEMA + " must be an object.");
            }
            JsonNode manager = enterprise.get("manager");
            if (manager != null && manager.isObject() && manager.has("displayName")) {
                throw validation("manager.displayName is not supported.");
            }
        }
        JsonNode active = request.get("active");
        if (active != null && !active.isBoolean()) {
            throw validation("active must be a boolean.");
        }
    }

    private void validateSingleValueArray(JsonNode request, String field, boolean requirePrimary, boolean rejectDisplay) {
        JsonNode values = request.get(field);
        if (values == null || values.isNull()) {
            return;
        }
        if (!values.isArray() || values.size() != 1 || !values.get(0).isObject()) {
            throw validation(field + " must contain exactly one value when specified.");
        }
        JsonNode value = values.get(0);
        if (rejectDisplay && value.has("display")) {
            throw validation(field + ".display is not supported.");
        }
        if (requirePrimary && (!value.path("primary").isBoolean() || !value.path("primary").booleanValue())) {
            throw validation(field + " must be marked primary.");
        }
    }

    private ObjectNode toIdentityStoreUser(String identityStoreId, JsonNode request) {
        ObjectNode out = mapper.createObjectNode();
        out.put("IdentityStoreId", identityStoreId);
        copyText(request, out, "userName", "UserName");
        copyText(request, out, "displayName", "DisplayName");
        copyText(request, out, "nickName", "NickName");
        copyText(request, out, "profileUrl", "ProfileUrl");
        copyText(request, out, "userType", "UserType");
        copyText(request, out, "title", "Title");
        copyText(request, out, "preferredLanguage", "PreferredLanguage");
        copyText(request, out, "locale", "Locale");
        copyText(request, out, "timezone", "Timezone");
        copyText(request, out, "birthdate", "Birthdate");
        out.put("UserStatus", request.path("active").isBoolean() && !request.path("active").booleanValue()
                ? "DISABLED" : "ENABLED");

        String externalId = optionalText(request, "externalId");
        if (externalId != null) {
            out.putArray("ExternalIds").addObject().put("Issuer", USER_SCHEMA).put("Id", externalId);
        }

        ObjectNode sourceName = (ObjectNode) request.get("name");
        ObjectNode targetName = out.putObject("Name");
        copyText(sourceName, targetName, "formatted", "Formatted");
        copyText(sourceName, targetName, "familyName", "FamilyName");
        copyText(sourceName, targetName, "givenName", "GivenName");
        copyText(sourceName, targetName, "middleName", "MiddleName");
        copyText(sourceName, targetName, "honorificPrefix", "HonorificPrefix");
        copyText(sourceName, targetName, "honorificSuffix", "HonorificSuffix");

        copyObjectArray(request, out, "emails", "Emails",
                Map.of("value", "Value", "type", "Type", "primary", "Primary"));
        copyObjectArray(request, out, "addresses", "Addresses",
                Map.ofEntries(
                        Map.entry("formatted", "Formatted"),
                        Map.entry("streetAddress", "StreetAddress"),
                        Map.entry("locality", "Locality"),
                        Map.entry("region", "Region"),
                        Map.entry("postalCode", "PostalCode"),
                        Map.entry("country", "Country"),
                        Map.entry("type", "Type"),
                        Map.entry("primary", "Primary")));
        copyObjectArray(request, out, "phoneNumbers", "PhoneNumbers",
                Map.of("value", "Value", "type", "Type", "primary", "Primary"));
        copyObjectArray(request, out, "roles", "Roles",
                Map.of("value", "Value", "type", "Type", "primary", "Primary"));

        JsonNode enterprise = request.get(ENTERPRISE_USER_SCHEMA);
        if (enterprise != null && enterprise.isObject()) {
            out.putObject("Extensions").set(IDENTITYSTORE_ENTERPRISE_EXTENSION, enterprise.deepCopy());
        }
        return out;
    }

    private ObjectNode userResponse(User user) {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode schemas = response.putArray("schemas");
        schemas.add(USER_SCHEMA);
        response.put("id", user.userId());
        String externalId = scimExternalId(user.attributes().get("ExternalIds"), USER_SCHEMA);
        if (externalId != null) {
            response.put("externalId", externalId);
        }
        ObjectNode meta = response.putObject("meta");
        meta.put("resourceType", "User");
        meta.put("created", user.createdAt());
        meta.put("lastModified", user.updatedAt());

        copyTextBack(user.attributes(), response, "UserName", "userName");
        copyTextBack(user.attributes(), response, "DisplayName", "displayName");
        copyTextBack(user.attributes(), response, "NickName", "nickName");
        copyTextBack(user.attributes(), response, "ProfileUrl", "profileUrl");
        copyTextBack(user.attributes(), response, "UserType", "userType");
        copyTextBack(user.attributes(), response, "Title", "title");
        copyTextBack(user.attributes(), response, "PreferredLanguage", "preferredLanguage");
        copyTextBack(user.attributes(), response, "Locale", "locale");
        copyTextBack(user.attributes(), response, "Timezone", "timezone");
        copyTextBack(user.attributes(), response, "Birthdate", "birthdate");
        response.put("active", !"DISABLED".equals(optionalText(user.attributes(), "UserStatus")));

        JsonNode name = user.attributes().get("Name");
        if (name != null && name.isObject()) {
            ObjectNode targetName = response.putObject("name");
            copyTextBack(name, targetName, "Formatted", "formatted");
            copyTextBack(name, targetName, "FamilyName", "familyName");
            copyTextBack(name, targetName, "GivenName", "givenName");
            copyTextBack(name, targetName, "MiddleName", "middleName");
            copyTextBack(name, targetName, "HonorificPrefix", "honorificPrefix");
            copyTextBack(name, targetName, "HonorificSuffix", "honorificSuffix");
        }
        copyObjectArrayBack(user.attributes(), response, "Emails", "emails",
                Map.of("Value", "value", "Type", "type", "Primary", "primary"));
        copyObjectArrayBack(user.attributes(), response, "Addresses", "addresses",
                Map.ofEntries(
                        Map.entry("Formatted", "formatted"),
                        Map.entry("StreetAddress", "streetAddress"),
                        Map.entry("Locality", "locality"),
                        Map.entry("Region", "region"),
                        Map.entry("PostalCode", "postalCode"),
                        Map.entry("Country", "country"),
                        Map.entry("Type", "type"),
                        Map.entry("Primary", "primary")));
        copyObjectArrayBack(user.attributes(), response, "PhoneNumbers", "phoneNumbers",
                Map.of("Value", "value", "Type", "type", "Primary", "primary"));
        copyObjectArrayBack(user.attributes(), response, "Roles", "roles",
                Map.of("Value", "value", "Type", "type", "Primary", "primary"));

        JsonNode extensions = user.attributes().get("Extensions");
        if (extensions != null && extensions.isObject()) {
            JsonNode enterprise = extensions.get(IDENTITYSTORE_ENTERPRISE_EXTENSION);
            if (enterprise != null && enterprise.isObject()) {
                schemas.add(ENTERPRISE_USER_SCHEMA);
                ObjectNode enterpriseResponse = enterprise.deepCopy();
                JsonNode manager = enterpriseResponse.get("manager");
                if (manager instanceof ObjectNode managerObject) {
                    managerObject.remove("$ref");
                    managerObject.remove("displayName");
                }
                response.set(ENTERPRISE_USER_SCHEMA, enterpriseResponse);
            }
        }
        return response;
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
        String externalId = scimExternalId(group.attributes().get("ExternalIds"), GROUP_SCHEMA);
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

    private void copyText(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        String value = optionalText(source, sourceField);
        if (value != null) {
            target.put(targetField, value);
        }
    }

    private static void copyTextBack(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        JsonNode value = source == null ? null : source.get(sourceField);
        if (value != null && value.isTextual()) {
            target.put(targetField, value.textValue());
        }
    }

    private void copyObjectArray(JsonNode source, ObjectNode target, String sourceField, String targetField,
                                 Map<String, String> fields) {
        JsonNode array = source.get(sourceField);
        if (array == null || !array.isArray()) {
            return;
        }
        ArrayNode targetArray = target.putArray(targetField);
        for (JsonNode item : array) {
            ObjectNode targetItem = targetArray.addObject();
            for (var field : fields.entrySet()) {
                JsonNode value = item.get(field.getKey());
                if (value != null && !value.isNull()) {
                    targetItem.set(field.getValue(), value.deepCopy());
                }
            }
        }
    }

    private void copyObjectArrayBack(JsonNode source, ObjectNode target, String sourceField, String targetField,
                                     Map<String, String> fields) {
        JsonNode array = source.get(sourceField);
        if (array == null || !array.isArray()) {
            return;
        }
        ArrayNode targetArray = target.putArray(targetField);
        for (JsonNode item : array) {
            ObjectNode targetItem = targetArray.addObject();
            for (var field : fields.entrySet()) {
                JsonNode value = item.get(field.getKey());
                if (value != null && !value.isNull()) {
                    targetItem.set(field.getValue(), value.deepCopy());
                }
            }
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

    private static String scimExternalId(JsonNode externalIds, String issuer) {
        if (externalIds == null || !externalIds.isArray()) {
            return null;
        }
        for (JsonNode externalId : externalIds) {
            if (issuer.equals(optionalText(externalId, "Issuer"))) {
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
