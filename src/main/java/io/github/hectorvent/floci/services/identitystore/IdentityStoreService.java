package io.github.hectorvent.floci.services.identitystore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class IdentityStoreService {
    private final StorageBackend<String, Group> groups;
    private final StorageBackend<String, User> users;
    private final StorageBackend<String, Membership> memberships;

    @Inject
    public IdentityStoreService(StorageFactory storageFactory) {
        this(
                storageFactory.create("identitystore", "identitystore-groups.json", new TypeReference<Map<String, Group>>() {}),
                storageFactory.create("identitystore", "identitystore-users.json", new TypeReference<Map<String, User>>() {}),
                storageFactory.create("identitystore", "identitystore-memberships.json", new TypeReference<Map<String, Membership>>() {}));
    }

    IdentityStoreService(StorageBackend<String, Group> groups, StorageBackend<String, User> users,
                         StorageBackend<String, Membership> memberships) {
        this.groups = groups;
        this.users = users;
        this.memberships = memberships;
    }

    public synchronized Group createGroup(JsonNode request) {
        String storeId = required(request, "IdentityStoreId");
        String name = required(request, "DisplayName");
        if (listGroups(storeId, name).size() > 0) {
            throw conflict("A group with DisplayName " + name + " already exists.");
        }
        Group group = new Group(id("group"), storeId, name, text(request, "Description"));
        groups.put(groupKey(storeId, group.groupId()), group);
        return group;
    }

    public List<Group> listGroups(String storeId, String displayName) {
        requireStore(storeId);
        return groups.scan(key -> key.startsWith(storeId + "::")).stream()
                .filter(group -> displayName == null || displayName.equals(group.displayName()))
                .sorted(Comparator.comparing(Group::displayName)).toList();
    }

    public synchronized User createUser(JsonNode request) {
        String storeId = required(request, "IdentityStoreId");
        String userName = required(request, "UserName");
        if (listUsers(storeId, userName).size() > 0) {
            throw conflict("A user with UserName " + userName + " already exists.");
        }
        User user = new User(id("user"), storeId, userName, text(request, "DisplayName"));
        users.put(userKey(storeId, user.userId()), user);
        return user;
    }

    public List<User> listUsers(String storeId, String userName) {
        requireStore(storeId);
        return users.scan(key -> key.startsWith(storeId + "::")).stream()
                .filter(user -> userName == null || userName.equals(user.userName()))
                .sorted(Comparator.comparing(User::userName)).toList();
    }

    public synchronized Membership createMembership(JsonNode request) {
        String storeId = required(request, "IdentityStoreId");
        String groupId = required(request, "GroupId");
        String userId = memberUserId(request.get("MemberId"));
        requireGroup(storeId, groupId);
        requireUser(storeId, userId);
        String key = membershipPairKey(storeId, userId, groupId);
        Membership existing = memberships.get(key).orElse(null);
        if (existing != null) {
            throw conflict("The user is already a member of the group.");
        }
        Membership membership = new Membership(id("membership"), storeId, groupId, userId);
        memberships.put(key, membership);
        return membership;
    }

    public boolean isMember(String storeId, String userId, String groupId) {
        requireStore(storeId);
        return memberships.get(membershipPairKey(storeId, userId, groupId)).isPresent();
    }

    private void requireGroup(String storeId, String groupId) {
        if (groups.get(groupKey(storeId, groupId)).isEmpty()) {
            throw notFound("Group not found: " + groupId);
        }
    }

    private void requireUser(String storeId, String userId) {
        if (users.get(userKey(storeId, userId)).isEmpty()) {
            throw notFound("User not found: " + userId);
        }
    }

    static String filterValue(JsonNode request) {
        JsonNode filters = request == null ? null : request.get("Filters");
        if (filters == null || !filters.isArray() || filters.isEmpty()) {
            return null;
        }
        JsonNode value = filters.get(0).get("AttributeValue");
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    static String memberUserId(JsonNode member) {
        if (member == null || !member.isObject() || !member.path("UserId").isTextual()) {
            throw new AwsException("ValidationException", "MemberId.UserId must be a string.", 400);
        }
        return member.path("UserId").textValue();
    }

    static String required(JsonNode request, String field) {
        String value = text(request, field);
        if (value == null || value.isBlank()) {
            throw new AwsException("ValidationException", field + " must be a non-empty string.", 400);
        }
        return value;
    }

    static String text(JsonNode request, String field) {
        JsonNode value = request == null ? null : request.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static void requireStore(String storeId) {
        if (storeId == null || storeId.isBlank()) {
            throw new AwsException("ValidationException", "IdentityStoreId must be a non-empty string.", 400);
        }
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    private static String groupKey(String store, String id) { return store + "::" + id; }
    private static String userKey(String store, String id) { return store + "::" + id; }
    private static String membershipPairKey(String store, String user, String group) { return store + "::" + user + "::" + group; }
    private static AwsException conflict(String message) { return new AwsException("ConflictException", message, 400); }
    private static AwsException notFound(String message) { return new AwsException("ResourceNotFoundException", message, 400); }

    public record Group(String groupId, String identityStoreId, String displayName, String description) {}
    public record User(String userId, String identityStoreId, String userName, String displayName) {}
    public record Membership(String membershipId, String identityStoreId, String groupId, String userId) {}
}
