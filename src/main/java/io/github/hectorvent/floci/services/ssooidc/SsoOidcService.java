package io.github.hectorvent.floci.services.ssooidc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.Resettable;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.ssooidc.model.DeviceAuthorization;
import io.github.hectorvent.floci.services.ssooidc.model.RegisteredClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class SsoOidcService implements Resettable {
    private static final long CLIENT_SECRET_LIFETIME_SECONDS = 90L * 24L * 60L * 60L;
    private static final int DEVICE_CODE_LIFETIME_SECONDS = 600;
    private static final int DEVICE_POLL_INTERVAL_SECONDS = 5;
    private static final Set<String> SUPPORTED_GRANT_TYPES = Set.of(
            "authorization_code",
            "urn:ietf:params:oauth:grant-type:device_code",
            "refresh_token");
    private static final Pattern APPLICATION_ARN = Pattern.compile(
            "arn:aws(?:-[a-z]{1,5}){0,3}:sso::[0-9]{12}:application/(?:sso)?ins-[a-zA-Z0-9-.]{16}/apl-[a-zA-Z0-9]{16}");

    private final StorageBackend<String, RegisteredClient> clients;
    private final StorageBackend<String, DeviceAuthorization> deviceAuthorizations;
    private final String baseUrl;

    @Inject
    public SsoOidcService(StorageFactory storageFactory, EmulatorConfig config) {
        this(storageFactory.create("ssooidc", "ssooidc-registered-clients.json",
                        new TypeReference<Map<String, RegisteredClient>>() {}),
                storageFactory.create("ssooidc", "ssooidc-device-authorizations.json",
                        new TypeReference<Map<String, DeviceAuthorization>>() {}),
                trimTrailingSlash(config.effectiveBaseUrl()));
    }

    SsoOidcService(StorageBackend<String, RegisteredClient> clients,
                   StorageBackend<String, DeviceAuthorization> deviceAuthorizations,
                   String baseUrl) {
        this.clients = clients;
        this.deviceAuthorizations = deviceAuthorizations;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public synchronized RegisteredClient registerClient(JsonNode request) {
        String clientName = requiredText(request, "clientName");
        if (clientName.isBlank()) {
            throw invalidClientMetadata("clientName must not be empty");
        }
        String clientType = requiredText(request, "clientType");
        if (!"public".equals(clientType)) {
            throw invalidClientMetadata("clientType must be public");
        }

        List<String> scopes = optionalStringList(request, "scopes", "invalid_scope");
        List<String> redirectUris = optionalStringList(request, "redirectUris", "invalid_redirect_uri");
        List<String> grantTypes = optionalStringList(request, "grantTypes", "unsupported_grant_type");
        for (String grantType : grantTypes) {
            if (!SUPPORTED_GRANT_TYPES.contains(grantType)) {
                throw new SsoOidcException("unsupported_grant_type",
                        "Unsupported grant type: " + grantType, 400);
            }
        }

        String issuerUrl = optionalText(request, "issuerUrl");
        if (issuerUrl != null && issuerUrl.isBlank()) {
            throw invalidClientMetadata("issuerUrl must not be empty");
        }
        String entitledApplicationArn = optionalText(request, "entitledApplicationArn");
        if (entitledApplicationArn != null && !APPLICATION_ARN.matcher(entitledApplicationArn).matches()) {
            throw invalidClientMetadata("entitledApplicationArn is invalid");
        }

        long issuedAt = System.currentTimeMillis() / 1000L;
        String clientId = UUID.randomUUID().toString().replace("-", "");
        String clientSecret = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        RegisteredClient client = new RegisteredClient(
                clientId,
                clientSecret,
                issuedAt,
                issuedAt + CLIENT_SECRET_LIFETIME_SECONDS,
                clientName,
                clientType,
                scopes,
                redirectUris,
                grantTypes,
                issuerUrl,
                entitledApplicationArn);
        clients.put(clientId, client);
        return client;
    }

    public synchronized DeviceAuthorization startDeviceAuthorization(JsonNode request) {
        String clientId = requiredText(request, "clientId");
        String clientSecret = requiredText(request, "clientSecret");
        String startUrl = requiredText(request, "startUrl");
        if (startUrl.isBlank()) {
            throw new SsoOidcException("invalid_request", "startUrl must not be empty", 400);
        }
        RegisteredClient client = requireClientCredentials(clientId, clientSecret);
        if (!"public".equals(client.clientType())) {
            throw new SsoOidcException("unauthorized_client", "Client is not a public client", 400);
        }
        String deviceGrant = "urn:ietf:params:oauth:grant-type:device_code";
        if (!client.grantTypes().isEmpty() && !client.grantTypes().contains(deviceGrant)) {
            throw new SsoOidcException("unauthorized_client", "Client is not registered for the device code grant", 400);
        }

        long now = System.currentTimeMillis() / 1000L;
        String deviceCode = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String rawUserCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String userCode = rawUserCode.substring(0, 4) + "-" + rawUserCode.substring(4);
        DeviceAuthorization authorization = new DeviceAuthorization(
                deviceCode,
                userCode,
                clientId,
                startUrl,
                now + DEVICE_CODE_LIFETIME_SECONDS,
                DEVICE_POLL_INTERVAL_SECONDS,
                false);
        deviceAuthorizations.put(deviceCode, authorization);
        return authorization;
    }

    public RegisteredClient requireClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new SsoOidcException("invalid_client", "clientId is required", 400);
        }
        return clients.get(clientId)
                .orElseThrow(() -> new SsoOidcException("invalid_client", "Client not found", 401));
    }

    public RegisteredClient requireClientCredentials(String clientId, String clientSecret) {
        RegisteredClient client = requireClient(clientId);
        long now = System.currentTimeMillis() / 1000L;
        if (clientSecret == null || !client.clientSecret().equals(clientSecret)
                || client.clientSecretExpiresAt() <= now) {
            throw new SsoOidcException("invalid_client", "Client credentials are invalid or expired", 401);
        }
        return client;
    }

    public DeviceAuthorization requireDeviceAuthorization(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new SsoOidcException("invalid_request", "deviceCode is required", 400);
        }
        return deviceAuthorizations.get(deviceCode)
                .orElseThrow(() -> new SsoOidcException("invalid_grant", "Device code is invalid", 400));
    }

    public String verificationUri() {
        return baseUrl + "/device";
    }

    public String verificationUriComplete(DeviceAuthorization authorization) {
        return verificationUri() + "?user_code=" + authorization.userCode();
    }

    public String authorizationEndpoint() {
        return baseUrl + "/authorize";
    }

    public String tokenEndpoint() {
        return baseUrl + "/token";
    }

    @Override
    public void clear() {
        clients.clear();
        deviceAuthorizations.clear();
    }

    private static String requiredText(JsonNode request, String field) {
        String value = optionalText(request, field);
        if (value == null) {
            throw new SsoOidcException("invalid_request", field + " is required", 400);
        }
        return value;
    }

    private static String optionalText(JsonNode request, String field) {
        if (request == null || !request.has(field) || request.get(field).isNull()) {
            return null;
        }
        JsonNode value = request.get(field);
        if (!value.isTextual()) {
            throw new SsoOidcException("invalid_request", field + " must be a string", 400);
        }
        return value.textValue();
    }

    private static List<String> optionalStringList(JsonNode request, String field, String semanticError) {
        if (request == null || !request.has(field) || request.get(field).isNull()) {
            return List.of();
        }
        JsonNode node = request.get(field);
        if (!node.isArray()) {
            throw new SsoOidcException("invalid_request", field + " must be an array", 400);
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                String description = field + " must contain non-empty strings";
                throw new SsoOidcException(semanticError, description, 400);
            }
            values.add(item.textValue());
        }
        return new ArrayList<>(values);
    }

    private static SsoOidcException invalidClientMetadata(String description) {
        return new SsoOidcException("invalid_client_metadata", description, 400);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:4566";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
