package io.github.hectorvent.floci.services.ssoadmin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.PaginatedResult;
import io.github.hectorvent.floci.core.common.Pagination;
import io.github.hectorvent.floci.core.common.Resettable;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.ssoadmin.model.Assignment;
import io.github.hectorvent.floci.services.ssoadmin.model.AssignmentOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.AssignmentDeletionOperation;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationAssignment;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationPortalOptions;
import io.github.hectorvent.floci.services.ssoadmin.model.ApplicationSignInOptions;
import io.github.hectorvent.floci.services.ssoadmin.model.AccessControlAttribute;
import io.github.hectorvent.floci.services.ssoadmin.model.CustomerManagedPolicyReference;
import io.github.hectorvent.floci.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration;
import io.github.hectorvent.floci.services.ssoadmin.model.OidcJwtIssuerConfiguration;
import io.github.hectorvent.floci.services.ssoadmin.model.PermissionSet;
import io.github.hectorvent.floci.services.ssoadmin.model.RegionMetadata;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoApplication;
import io.github.hectorvent.floci.services.ssoadmin.model.SsoInstance;
import io.github.hectorvent.floci.services.ssoadmin.model.TrustedTokenIssuer;
import io.github.hectorvent.floci.services.organizations.OrganizationsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class SsoAdminService implements Resettable {
    private static final String INSTANCE_ARN = "arn:aws:sso:::instance/ssoins-7223b02a5d9f7c8e";
    private static final String IDENTITY_STORE_ID = "d-9067f2a3c1";
    private static final String PRIMARY_REGION = "us-east-1";
    private static final Pattern INSTANCE_ARN_PATTERN = Pattern.compile("arn:aws(?:-[a-z]{1,5}){0,3}:sso:::instance/(?:sso)?ins-[a-zA-Z0-9-.]{16}");
    private static final Pattern PERMISSION_SET_NAME = Pattern.compile("[\\w+=,.@-]+");
    private static final Pattern PERMISSION_SET_ARN = Pattern.compile("arn:aws(?:-[a-z]{1,5}){0,3}:sso:::permissionSet/(?:sso)?ins-[a-zA-Z0-9-.]{16}/ps-[a-zA-Z0-9-./]{16}");
    private static final Pattern PRINCIPAL_ID = Pattern.compile("([0-9a-f]{10}-|)[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}");
    private static final Pattern MANAGED_POLICY_ARN = Pattern.compile("arn:aws:iam::aws:policy/.+");
    private static final Pattern CUSTOMER_MANAGED_POLICY_NAME = Pattern.compile("[\\w+=,.@-]+");
    private static final Pattern CUSTOMER_MANAGED_POLICY_PATH = Pattern.compile("((/[A-Za-z0-9\\.,\\+@=_-]+)*)/");
    private static final Pattern REGION_NAME = Pattern.compile("([a-z]+-){2,3}\\d");
    private static final Pattern APPLICATION_PROVIDER_ARN = Pattern.compile("arn:aws(?:-[a-z]{1,5}){0,3}:sso::aws:applicationProvider/[a-zA-Z0-9-/]+");
    private static final Pattern APPLICATION_ARN = Pattern.compile("arn:aws(?:-[a-z]{1,5}){0,3}:sso::\\d{12}:application/(?:sso)?ins-[a-zA-Z0-9-.]{16}/apl-[a-zA-Z0-9]{16}");
    private static final Pattern CLIENT_TOKEN = Pattern.compile("[!-~]+");
    private static final Pattern APPLICATION_URL = Pattern.compile("http(s)?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%?=~_|]");
    private static final Pattern TAG_VALUE = Pattern.compile("[\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]*");
    private static final Pattern INSTANCE_NAME = Pattern.compile("[\\w+=,.@-]+");
    private static final Pattern ACCESS_CONTROL_ATTRIBUTE_KEY = Pattern.compile("[\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]+");
    private static final Pattern ACCESS_CONTROL_ATTRIBUTE_SOURCE = Pattern.compile("[\\p{L}\\p{Z}\\p{N}_.:/=+\\-@\\[\\]\\{\\}$\\\\\"]*");
    private static final Pattern OIDC_CLAIM_ATTRIBUTE_PATH = Pattern.compile("\\p{L}+(?:(\\.|_)\\p{L}+){0,2}");
    private static final Pattern OIDC_IDENTITY_STORE_ATTRIBUTE_PATH = Pattern.compile("\\p{L}+(?:\\.\\p{L}+){0,2}");
    private static final Pattern OIDC_ISSUER_URL = Pattern.compile("https?://[-a-zA-Z0-9+&@/%=~_|!:,.;]*[-a-zA-Z0-9+&@/%=~_|]");
    private static final String CUSTOM_APPLICATION_PROVIDER_ARN = "arn:aws:sso::aws:applicationProvider/custom";
    private static final int PERMISSION_SET_QUOTA = 3500;
    private static final int REGION_QUOTA = 6;
    private static final int MANAGED_POLICY_QUOTA = 25;
    private static final int APPLICATION_QUOTA = 7000;
    private static final int APPLICATION_GROUP_ASSIGNMENT_QUOTA = 100;
    private static final int TRUSTED_TOKEN_ISSUER_QUOTA = 10;
    private static final int MAX_INLINE_POLICY_BYTES = 32_768;
    private static final int MAX_INLINE_NON_WHITESPACE = 10_240;
    private static final Set<String> PRINCIPAL_TYPES = Set.of("USER", "GROUP");

    private final StorageBackend<String, PermissionSet> permissionSets;
    private final StorageBackend<String, Assignment> assignments;
    private final StorageBackend<String, AssignmentOperation> assignmentOperations;
    private final StorageBackend<String, AssignmentDeletionOperation> assignmentDeletionOperations;
    private final StorageBackend<String, RegionMetadata> regions;
    private final StorageBackend<String, SsoApplication> applications;
    private final StorageBackend<String, String> applicationClientTokens;
    private final StorageBackend<String, ApplicationAssignment> applicationAssignments;
    private final StorageBackend<String, SsoInstance> instances;
    private final StorageBackend<String, String> instanceClientTokens;
    private final StorageBackend<String, InstanceAccessControlAttributeConfiguration> accessControlAttributeConfigurations;
    private final StorageBackend<String, TrustedTokenIssuer> trustedTokenIssuers;
    private final StorageBackend<String, String> trustedTokenIssuerClientTokens;
    private final OrganizationsService organizationsService;
    private final String defaultAccountId;
    private final String defaultRegion;

    @Inject
    public SsoAdminService(StorageFactory storageFactory, OrganizationsService organizationsService,
                           EmulatorConfig config) {
        this(
                storageFactory.create("ssoadmin", "ssoadmin-permission-sets.json", new TypeReference<Map<String, PermissionSet>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-assignments.json", new TypeReference<Map<String, Assignment>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-assignment-operations.json", new TypeReference<Map<String, AssignmentOperation>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-assignment-deletion-operations.json", new TypeReference<Map<String, AssignmentDeletionOperation>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-regions.json", new TypeReference<Map<String, RegionMetadata>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-applications.json", new TypeReference<Map<String, SsoApplication>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-application-client-tokens.json", new TypeReference<Map<String, String>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-application-assignments.json", new TypeReference<Map<String, ApplicationAssignment>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-instances.json", new TypeReference<Map<String, SsoInstance>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-instance-client-tokens.json", new TypeReference<Map<String, String>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-access-control-attribute-configurations.json", new TypeReference<Map<String, InstanceAccessControlAttributeConfiguration>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-trusted-token-issuers.json", new TypeReference<Map<String, TrustedTokenIssuer>>() {}),
                storageFactory.create("ssoadmin", "ssoadmin-trusted-token-issuer-client-tokens.json", new TypeReference<Map<String, String>>() {}),
                organizationsService,
                config.defaultAccountId(),
                config.defaultRegion());
    }

    SsoAdminService(StorageBackend<String, PermissionSet> permissionSets,
                    StorageBackend<String, Assignment> assignments,
                    StorageBackend<String, AssignmentOperation> assignmentOperations,
                    StorageBackend<String, AssignmentDeletionOperation> assignmentDeletionOperations,
                    StorageBackend<String, RegionMetadata> regions,
                    StorageBackend<String, SsoApplication> applications,
                    StorageBackend<String, String> applicationClientTokens,
                    StorageBackend<String, ApplicationAssignment> applicationAssignments,
                    StorageBackend<String, SsoInstance> instances,
                    StorageBackend<String, String> instanceClientTokens,
                    StorageBackend<String, InstanceAccessControlAttributeConfiguration> accessControlAttributeConfigurations,
                    StorageBackend<String, TrustedTokenIssuer> trustedTokenIssuers,
                    StorageBackend<String, String> trustedTokenIssuerClientTokens,
                    OrganizationsService organizationsService,
                    String defaultAccountId,
                    String defaultRegion) {
        this.permissionSets = permissionSets;
        this.assignments = assignments;
        this.assignmentOperations = assignmentOperations;
        this.assignmentDeletionOperations = assignmentDeletionOperations;
        this.regions = regions;
        this.applications = applications;
        this.applicationClientTokens = applicationClientTokens;
        this.applicationAssignments = applicationAssignments;
        this.instances = instances;
        this.instanceClientTokens = instanceClientTokens;
        this.accessControlAttributeConfigurations = accessControlAttributeConfigurations;
        this.trustedTokenIssuers = trustedTokenIssuers;
        this.trustedTokenIssuerClientTokens = trustedTokenIssuerClientTokens;
        this.organizationsService = organizationsService;
        this.defaultAccountId = defaultAccountId;
        this.defaultRegion = defaultRegion;
        ensureBootstrapInstance(defaultAccountId, defaultRegion);
    }

    public String getInstanceArn() { return INSTANCE_ARN; }
    public String getIdentityStoreId() { return IDENTITY_STORE_ID; }

    void ensureBootstrapInstance(String ownerAccountId, String region) {
        if (instances.get(ownerAccountId).isEmpty()) {
            instances.put(ownerAccountId, new SsoInstance(INSTANCE_ARN, IDENTITY_STORE_ID, "floci-identity-center",
                    ownerAccountId, region, System.currentTimeMillis(), "ACTIVE", null, false,
                    new LinkedHashMap<>()));
        }
    }

    public synchronized List<SsoInstance> listInstances(String callerAccountId) {
        validateAccountId(callerAccountId);
        if (callerAccountId.equals(defaultAccountId) && instances.get(callerAccountId).isEmpty()) {
            ensureBootstrapInstance(callerAccountId, defaultRegion);
        }
        return instances.get(callerAccountId).map(List::of).orElseGet(List::of);
    }

    public List<RegionMetadata> listRegionsForInstance(SsoInstance instance) {
        List<RegionMetadata> result = new ArrayList<>();
        result.add(new RegionMetadata(instance.primaryRegion(), "ACTIVE",
                java.time.Instant.ofEpochMilli(instance.createdDateEpochMillis()).toString(), true));
        if (!instance.accountInstance()) {
            result.addAll(regions.scan(key -> true).stream()
                    .filter(region -> !instance.primaryRegion().equals(region.regionName()))
                    .sorted(Comparator.comparing(RegionMetadata::regionName))
                    .toList());
        }
        return result;
    }

    public double regionAddedDateEpochSeconds(RegionMetadata region) {
        return java.time.Instant.parse(region.addedDate()).toEpochMilli() / 1000.0d;
    }

    public synchronized TrustedTokenIssuer createTrustedTokenIssuer(JsonNode request, String callerAccountId) {
        String instanceArn = required(request, "InstanceArn");
        SsoInstance instance = requireInstance(instanceArn);
        validateAccountId(callerAccountId);
        String name = required(request, "Name");
        if (name.length() > 255 || !INSTANCE_NAME.matcher(name).matches()) {
            throw validation("Name must be 1-255 characters and match [\\w+=,.@-]+.");
        }
        String issuerType = required(request, "TrustedTokenIssuerType");
        if (!"OIDC_JWT".equals(issuerType)) {
            throw validation("TrustedTokenIssuerType must be OIDC_JWT.");
        }
        JsonNode configurationNode = request.get("TrustedTokenIssuerConfiguration");
        if (configurationNode == null || !configurationNode.isObject()
                || configurationNode.size() != 1
                || !configurationNode.has("OidcJwtConfiguration")
                || !configurationNode.get("OidcJwtConfiguration").isObject()) {
            throw validation("TrustedTokenIssuerConfiguration must contain exactly one OidcJwtConfiguration.");
        }
        JsonNode oidcNode = configurationNode.get("OidcJwtConfiguration");
        String claimAttributePath = required(oidcNode, "ClaimAttributePath");
        if (claimAttributePath.length() > 255 || !OIDC_CLAIM_ATTRIBUTE_PATH.matcher(claimAttributePath).matches()) {
            throw validation("ClaimAttributePath is invalid.");
        }
        String identityStoreAttributePath = required(oidcNode, "IdentityStoreAttributePath");
        if (identityStoreAttributePath.length() > 255
                || !OIDC_IDENTITY_STORE_ATTRIBUTE_PATH.matcher(identityStoreAttributePath).matches()) {
            throw validation("IdentityStoreAttributePath is invalid.");
        }
        String issuerUrl = required(oidcNode, "IssuerUrl");
        if (issuerUrl.length() > 512 || !OIDC_ISSUER_URL.matcher(issuerUrl).matches()) {
            throw validation("IssuerUrl is invalid.");
        }
        String jwksRetrievalOption = required(oidcNode, "JwksRetrievalOption");
        if (!"OPEN_ID_DISCOVERY".equals(jwksRetrievalOption)) {
            throw validation("JwksRetrievalOption must be OPEN_ID_DISCOVERY.");
        }
        OidcJwtIssuerConfiguration oidcConfiguration = new OidcJwtIssuerConfiguration(
                claimAttributePath, identityStoreAttributePath, issuerUrl, jwksRetrievalOption);
        Map<String, String> tags = parseTags(request.get("Tags"));

        JsonNode clientTokenNode = request.get("ClientToken");
        String clientToken = null;
        if (clientTokenNode != null && !clientTokenNode.isNull()) {
            if (!clientTokenNode.isTextual()) {
                throw validation("ClientToken must be a string.");
            }
            clientToken = clientTokenNode.textValue();
            if (clientToken.isEmpty() || clientToken.length() > 64 || !CLIENT_TOKEN.matcher(clientToken).matches()) {
                throw validation("ClientToken must be between 1 and 64 visible ASCII characters.");
            }
        }
        String tokenKey = clientToken == null ? null : instanceArn + "::" + clientToken;
        if (tokenKey != null) {
            var priorArn = trustedTokenIssuerClientTokens.get(tokenKey);
            if (priorArn.isPresent()) {
                TrustedTokenIssuer prior = trustedTokenIssuers.get(priorArn.get()).orElse(null);
                if (prior != null && trustedTokenIssuerMatches(prior, name, issuerType, oidcConfiguration, tags)) {
                    return prior;
                }
                throw new AwsException("IdempotentParameterMismatch",
                        "ClientToken was reused with different request parameters.", 400);
            }
        }
        long count = trustedTokenIssuers.scan(ignored -> true).stream()
                .filter(issuer -> instanceArn.equals(issuer.instanceArn()))
                .count();
        if (count >= TRUSTED_TOKEN_ISSUER_QUOTA) {
            throw quota("An IAM Identity Center instance can have at most 10 trusted token issuers.");
        }
        String instanceId = instanceArn.substring(instanceArn.lastIndexOf('/') + 1);
        String ownerAccountId = instance.ownerAccountId() == null ? callerAccountId : instance.ownerAccountId();
        String issuerArn = "arn:aws:sso::" + ownerAccountId + ":trustedTokenIssuer/"
                + instanceId + "/tti-" + UUID.randomUUID();
        TrustedTokenIssuer issuer = new TrustedTokenIssuer(
                issuerArn, instanceArn, name, issuerType, oidcConfiguration, tags);
        trustedTokenIssuers.put(issuerArn, issuer);
        if (tokenKey != null) {
            trustedTokenIssuerClientTokens.put(tokenKey, issuerArn);
        }
        return issuer;
    }

    public TrustedTokenIssuer getTrustedTokenIssuer(String trustedTokenIssuerArn) {
        return trustedTokenIssuers.get(trustedTokenIssuerArn)
                .orElseThrow(() -> notFound("Trusted token issuer not found: " + trustedTokenIssuerArn));
    }

    private static boolean trustedTokenIssuerMatches(TrustedTokenIssuer issuer, String name, String issuerType,
                                                       OidcJwtIssuerConfiguration oidcConfiguration,
                                                       Map<String, String> tags) {
        return java.util.Objects.equals(issuer.name(), name)
                && java.util.Objects.equals(issuer.trustedTokenIssuerType(), issuerType)
                && java.util.Objects.equals(issuer.oidcJwtConfiguration(), oidcConfiguration)
                && java.util.Objects.equals(issuer.tags(), tags);
    }

    public synchronized InstanceAccessControlAttributeConfiguration createInstanceAccessControlAttributeConfiguration(JsonNode request) {
        String instanceArn = required(request, "InstanceArn");
        requireInstance(instanceArn);
        if (accessControlAttributeConfigurations.get(instanceArn).isPresent()) {
            throw conflict("An instance access control attribute configuration already exists for this IAM Identity Center instance.");
        }
        JsonNode configurationNode = request == null ? null : request.get("InstanceAccessControlAttributeConfiguration");
        if (configurationNode == null || !configurationNode.isObject()) {
            throw validation("InstanceAccessControlAttributeConfiguration must be an object.");
        }
        JsonNode attributesNode = configurationNode.get("AccessControlAttributes");
        if (attributesNode == null || !attributesNode.isArray()) {
            throw validation("AccessControlAttributes must be an array.");
        }
        if (attributesNode.size() > 50) {
            throw validation("AccessControlAttributes can contain at most 50 attributes.");
        }
        List<AccessControlAttribute> attributes = new ArrayList<>();
        for (JsonNode attributeNode : attributesNode) {
            if (!attributeNode.isObject()) {
                throw validation("Each access control attribute must be an object.");
            }
            String key = required(attributeNode, "Key");
            if (key.length() > 128 || !ACCESS_CONTROL_ATTRIBUTE_KEY.matcher(key).matches()) {
                throw validation("Access control attribute Key is invalid.");
            }
            JsonNode valueNode = attributeNode.get("Value");
            if (valueNode == null || !valueNode.isObject()) {
                throw validation("Access control attribute Value must be an object.");
            }
            JsonNode sourceNode = valueNode.get("Source");
            if (sourceNode == null || !sourceNode.isArray() || sourceNode.size() != 1 || !sourceNode.get(0).isTextual()) {
                throw validation("Access control attribute Source must contain exactly one string.");
            }
            String source = sourceNode.get(0).textValue();
            if (source.length() > 256 || !ACCESS_CONTROL_ATTRIBUTE_SOURCE.matcher(source).matches()) {
                throw validation("Access control attribute Source is invalid.");
            }
            attributes.add(new AccessControlAttribute(key, source));
        }
        InstanceAccessControlAttributeConfiguration configuration = new InstanceAccessControlAttributeConfiguration(
                instanceArn, attributes, "ENABLED", null);
        accessControlAttributeConfigurations.put(instanceArn, configuration);
        return configuration;
    }

    public InstanceAccessControlAttributeConfiguration getInstanceAccessControlAttributeConfiguration(String instanceArn) {
        requireInstance(instanceArn);
        return accessControlAttributeConfigurations.get(instanceArn)
                .orElseThrow(() -> notFound("Instance access control attribute configuration not found for: " + instanceArn));
    }

    public synchronized SsoInstance createInstance(JsonNode request, String callerAccountId, String region) {
        validateAccountId(callerAccountId);
        validateRegionName(region);
        if (organizationsService.isManagementAccount(callerAccountId)) {
            throw accessDenied("The AWS Organizations management account cannot create an account instance of IAM Identity Center.");
        }
        String name = optionalInstanceName(request);
        Map<String, String> tags = parseTags(request.get("Tags"));
        JsonNode clientTokenNode = request == null ? null : request.get("ClientToken");
        String clientToken;
        if (clientTokenNode == null || clientTokenNode.isNull()) {
            clientToken = UUID.randomUUID().toString();
        } else if (!clientTokenNode.isTextual()) {
            throw validation("ClientToken must be a string.");
        } else {
            clientToken = clientTokenNode.textValue();
            if (clientToken.length() > 64 || !CLIENT_TOKEN.matcher(clientToken).matches()) {
                throw validation("ClientToken must be between 1 and 64 visible ASCII characters.");
            }
        }
        String tokenKey = callerAccountId + "::" + clientToken;
        var priorArn = instanceClientTokens.get(tokenKey);
        if (priorArn.isPresent()) {
            SsoInstance prior = findInstanceByArn(priorArn.get());
            if (prior != null && java.util.Objects.equals(prior.name(), name)
                    && java.util.Objects.equals(prior.tags(), tags)) {
                return prior;
            }
            throw new AwsException("IdempotentParameterMismatch",
                    "ClientToken was reused with different request parameters.", 400);
        }
        if (instances.get(callerAccountId).isPresent()) {
            throw quota("Only one IAM Identity Center instance can exist in an AWS account.");
        }
        String instanceArn = "arn:aws:sso:::instance/ssoins-" + shortId();
        String identityStoreId = "d-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        SsoInstance instance = new SsoInstance(instanceArn, identityStoreId, name, callerAccountId, region,
                System.currentTimeMillis(), "ACTIVE", null, true, tags);
        instances.put(callerAccountId, instance);
        instanceClientTokens.put(tokenKey, instanceArn);
        return instance;
    }

    public synchronized ApplicationAssignment createApplicationAssignment(JsonNode request) {
        String applicationArn = validateApplicationArn(required(request, "ApplicationArn"));
        getApplication(applicationArn);
        String principalId = validatePrincipalId(required(request, "PrincipalId"));
        String principalType = required(request, "PrincipalType");
        if (!PRINCIPAL_TYPES.contains(principalType)) {
            throw validation("PrincipalType must be USER or GROUP.");
        }
        String key = applicationAssignmentKey(applicationArn, principalId, principalType);
        if (applicationAssignments.get(key).isPresent()) {
            throw conflict("The application assignment already exists.");
        }
        if ("GROUP".equals(principalType)
                && applicationAssignments.scan(ignored -> true).stream()
                        .filter(a -> applicationArn.equals(a.applicationArn()) && "GROUP".equals(a.principalType()))
                        .count() >= APPLICATION_GROUP_ASSIGNMENT_QUOTA) {
            throw quota("An application can have at most 100 directly assigned groups.");
        }
        ApplicationAssignment assignment = new ApplicationAssignment(applicationArn, principalId, principalType);
        applicationAssignments.put(key, assignment);
        return assignment;
    }

    public synchronized SsoApplication createApplication(JsonNode request, String callerAccountId, String region) {
        requireInstance(required(request, "InstanceArn"));
        validateAccountId(callerAccountId);
        validateRegionName(region);
        String providerArn = required(request, "ApplicationProviderArn");
        if (providerArn.length() > 1224 || !APPLICATION_PROVIDER_ARN.matcher(providerArn).matches()) {
            throw validation("ApplicationProviderArn is invalid.");
        }
        if (!CUSTOM_APPLICATION_PROVIDER_ARN.equals(providerArn)) {
            throw notFound("Application provider not found: " + providerArn);
        }
        String name = required(request, "Name");
        if (name.length() > 100) {
            throw validation("Name must be between 1 and 100 characters.");
        }
        String description = optionalString(request, "Description", 1, 128);
        String status = text(request, "Status");
        if (status == null) {
            status = "ENABLED";
        } else if (!Set.of("ENABLED", "DISABLED").contains(status)) {
            throw validation("Status must be ENABLED or DISABLED.");
        }
        ApplicationPortalOptions portalOptions = parsePortalOptions(request.get("PortalOptions"));
        Map<String, String> tags = parseTags(request.get("Tags"));
        String clientToken = text(request, "ClientToken");
        if (clientToken != null && (clientToken.length() > 64 || !CLIENT_TOKEN.matcher(clientToken).matches())) {
            throw validation("ClientToken must be between 1 and 64 visible ASCII characters.");
        }

        String tokenKey = clientToken == null ? null : callerAccountId + "::" + region + "::" + clientToken;
        if (tokenKey != null) {
            var existingArn = applicationClientTokens.get(tokenKey);
            if (existingArn.isPresent()) {
                SsoApplication existing = applications.get(existingArn.get()).orElse(null);
                if (existing != null && applicationMatches(existing, providerArn, description, name, portalOptions, status, tags)) {
                    return existing;
                }
                throw new AwsException("IdempotentParameterMismatch", "ClientToken was reused with different request parameters.", 400);
            }
        }
        if (applications.scan(a -> true).stream().filter(a -> callerAccountId.equals(a.applicationAccount())).count() >= APPLICATION_QUOTA) {
            throw quota("The IAM Identity Center application quota has been exceeded.");
        }

        String applicationArn = "arn:aws:sso::" + callerAccountId
                + ":application/ssoins-7223b02a5d9f7c8e/apl-" + shortId();
        String identityStoreArn = "arn:aws:identitystore::" + callerAccountId + ":identitystore/" + IDENTITY_STORE_ID;
        SsoApplication application = new SsoApplication(callerAccountId, applicationArn, providerArn,
                System.currentTimeMillis(), region, description, identityStoreArn, INSTANCE_ARN, name,
                portalOptions, status, tags);
        applications.put(applicationArn, application);
        if (tokenKey != null) {
            applicationClientTokens.put(tokenKey, applicationArn);
        }
        return application;
    }

    public synchronized RegionMetadata addRegion(JsonNode request) {
        requireInstance(required(request, "InstanceArn"));
        String regionName = validateRegionName(required(request, "RegionName"));
        if (PRIMARY_REGION.equals(regionName) || regions.get(regionName).isPresent()) {
            throw conflict("Region is already enabled for this IAM Identity Center instance: " + regionName);
        }
        if (regions.scan(key -> true).size() >= REGION_QUOTA - 1) {
            throw quota("The IAM Identity Center Region quota has been exceeded.");
        }
        String addedDate = java.time.Instant.now().toString();
        RegionMetadata response = new RegionMetadata(regionName, "ADDING", addedDate, false);
        regions.put(regionName, new RegionMetadata(regionName, "ACTIVE", addedDate, false));
        return response;
    }

    public PaginatedResult<PermissionSet> listPermissionSets(JsonNode request) {
        requireInstance(required(request, "InstanceArn"));
        return Pagination.paginate(permissionSets.scan(key -> true), PermissionSet::arn,
                optionalMaxResults(request), text(request, "NextToken"), 50, 100, "ValidationException");
    }

    public List<PermissionSet> listPermissionSets(String instanceArn) {
        requireInstance(instanceArn);
        return permissionSets.scan(key -> true).stream().sorted(Comparator.comparing(PermissionSet::name)).toList();
    }

    public PaginatedResult<Map.Entry<String, String>> listManagedPolicies(JsonNode request) {
        PermissionSet permissionSet = getPermissionSet(
                required(request, "InstanceArn"), required(request, "PermissionSetArn"));
        List<Map.Entry<String, String>> policies = permissionSet.managedPolicies().entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
        return Pagination.paginate(policies, Map.Entry::getKey,
                optionalMaxResults(request), text(request, "NextToken"), 50, 100, "ValidationException");
    }

    public synchronized PermissionSet createPermissionSet(JsonNode request) {
        requireInstance(required(request, "InstanceArn"));
        String name = validateName(required(request, "Name"));
        String description = optionalDescription(request);
        String sessionDuration = validateSession(valueOr(request, "SessionDuration", "PT1H"));
        if (permissionSets.scan(key -> true).stream().anyMatch(p -> name.equals(p.name()))) {
            throw conflict("Permission set already exists: " + name);
        }
        if (permissionSets.scan(key -> true).size() >= PERMISSION_SET_QUOTA) {
            throw quota("The IAM Identity Center permission set quota has been exceeded.");
        }
        String arn = "arn:aws:sso:::permissionSet/ssoins-7223b02a5d9f7c8e/ps-" + shortId();
        PermissionSet permissionSet = new PermissionSet(arn, name, description, sessionDuration,
                new LinkedHashMap<>(), new LinkedHashMap<>(), null);
        permissionSets.put(arn, permissionSet);
        return permissionSet;
    }

    public PermissionSet getPermissionSet(String instanceArn, String arn) {
        requireInstance(instanceArn);
        validatePermissionSetArn(arn);
        return permissionSets.get(arn).orElseThrow(() -> notFound("Permission set not found: " + arn));
    }

    public synchronized PermissionSet updatePermissionSet(JsonNode request) {
        PermissionSet current = getPermissionSet(required(request, "InstanceArn"), required(request, "PermissionSetArn"));
        String description = request.has("Description") ? optionalDescription(request) : current.description();
        String session = request.has("SessionDuration")
                ? validateSession(required(request, "SessionDuration")) : current.sessionDuration();
        PermissionSet updated = new PermissionSet(current.arn(), current.name(), description, session,
                new LinkedHashMap<>(current.managedPolicies()),
                new LinkedHashMap<>(current.customerManagedPolicies()), current.inlinePolicy());
        permissionSets.put(updated.arn(), updated);
        return updated;
    }

    public synchronized void attachPolicy(String instanceArn, String arn, String policyArn) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        validateManagedPolicyArn(policyArn);
        if (current.managedPolicies().containsKey(policyArn)) {
            throw conflict("The managed policy is already attached to the permission set.");
        }
        if (managedPolicyCount(current) >= MANAGED_POLICY_QUOTA) {
            throw quota("A permission set can have at most 25 AWS managed and customer managed policies.");
        }
        current.managedPolicies().put(policyArn, policyArn.substring(policyArn.lastIndexOf('/') + 1));
        permissionSets.put(arn, current);
    }

    public synchronized void attachCustomerManagedPolicyReference(JsonNode request) {
        PermissionSet current = getPermissionSet(required(request, "InstanceArn"), required(request, "PermissionSetArn"));
        JsonNode reference = request.get("CustomerManagedPolicyReference");
        if (reference == null || !reference.isObject()) {
            throw validation("CustomerManagedPolicyReference must be an object.");
        }
        String name = required(reference, "Name");
        if (name.length() > 128 || !CUSTOMER_MANAGED_POLICY_NAME.matcher(name).matches()) {
            throw validation("CustomerManagedPolicyReference.Name is invalid.");
        }
        String path = text(reference, "Path");
        if (path == null) {
            path = "/";
        } else if (path.length() > 512 || !CUSTOMER_MANAGED_POLICY_PATH.matcher(path).matches()) {
            throw validation("CustomerManagedPolicyReference.Path is invalid.");
        }
        String key = customerManagedPolicyKey(name, path);
        if (current.customerManagedPolicies().containsKey(key)) {
            throw conflict("The customer managed policy reference is already attached to the permission set.");
        }
        if (managedPolicyCount(current) >= MANAGED_POLICY_QUOTA) {
            throw quota("A permission set can have at most 25 AWS managed and customer managed policies.");
        }
        current.customerManagedPolicies().put(key, new CustomerManagedPolicyReference(name, path));
        permissionSets.put(current.arn(), current);
    }

    public synchronized void detachPolicy(String instanceArn, String arn, String policyArn) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        validateManagedPolicyArn(policyArn);
        if (current.managedPolicies().remove(policyArn) == null) {
            throw conflict("The managed policy is not attached to the permission set.");
        }
        permissionSets.put(arn, current);
    }

    public synchronized void putInlinePolicy(String instanceArn, String arn, String policy) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        validateInlinePolicy(policy);
        permissionSets.put(arn, new PermissionSet(current.arn(), current.name(), current.description(),
                current.sessionDuration(), new LinkedHashMap<>(current.managedPolicies()),
                new LinkedHashMap<>(current.customerManagedPolicies()), policy));
    }

    public synchronized void deleteInlinePolicy(String instanceArn, String arn) {
        PermissionSet current = getPermissionSet(instanceArn, arn);
        permissionSets.put(arn, new PermissionSet(current.arn(), current.name(), current.description(),
                current.sessionDuration(), new LinkedHashMap<>(current.managedPolicies()),
                new LinkedHashMap<>(current.customerManagedPolicies()), null));
    }

    public PaginatedResult<Assignment> listAssignments(JsonNode request) {
        String instanceArn = required(request, "InstanceArn");
        requireInstance(instanceArn);
        String accountId = validateAccountId(required(request, "AccountId"));
        String permissionSetArn = required(request, "PermissionSetArn");
        getPermissionSet(instanceArn, permissionSetArn);
        List<Assignment> matching = assignments.scan(key -> true).stream()
                .filter(a -> accountId.equals(a.accountId()) && permissionSetArn.equals(a.permissionSetArn()))
                .toList();
        return Pagination.paginate(matching, Assignment::principalId,
                optionalMaxResults(request), text(request, "NextToken"), 50, 100, "ValidationException");
    }

    public List<Assignment> listAssignments(String instanceArn, String accountId, String permissionSetArn) {
        requireInstance(instanceArn);
        validateAccountId(accountId);
        getPermissionSet(instanceArn, permissionSetArn);
        return assignments.scan(key -> true).stream()
                .filter(a -> accountId.equals(a.accountId()) && permissionSetArn.equals(a.permissionSetArn()))
                .sorted(Comparator.comparing(Assignment::principalId)).toList();
    }

    public synchronized AssignmentOperation createAssignment(JsonNode request) {
        requireInstance(required(request, "InstanceArn"));
        String account = validateAccountId(required(request, "TargetId"));
        if (!"AWS_ACCOUNT".equals(required(request, "TargetType"))) {
            throw validation("TargetType must be AWS_ACCOUNT.");
        }
        String permission = required(request, "PermissionSetArn");
        getPermissionSet(INSTANCE_ARN, permission);
        String principal = validatePrincipalId(required(request, "PrincipalId"));
        String principalType = required(request, "PrincipalType");
        if (!PRINCIPAL_TYPES.contains(principalType)) {
            throw validation("PrincipalType must be USER or GROUP.");
        }
        String key = account + "::" + permission + "::" + principal;
        if (assignments.get(key).isPresent()) {
            throw conflict("The account assignment already exists.");
        }
        Assignment assignment = new Assignment(account, permission, principal, principalType);
        assignments.put(key, assignment);
        String requestId = UUID.randomUUID().toString();
        AssignmentOperation operation = new AssignmentOperation(requestId, "SUCCEEDED", account, permission,
                principal, principalType, null);
        assignmentOperations.put(requestId, operation);
        return operation;
    }

    public synchronized AssignmentDeletionOperation deleteAssignment(JsonNode request) {
        String instanceArn = required(request, "InstanceArn");
        requireInstance(instanceArn);
        String account = validateAccountId(required(request, "TargetId"));
        if (!"AWS_ACCOUNT".equals(required(request, "TargetType"))) {
            throw validation("TargetType must be AWS_ACCOUNT.");
        }
        String permission = required(request, "PermissionSetArn");
        getPermissionSet(instanceArn, permission);
        String principal = validatePrincipalId(required(request, "PrincipalId"));
        String principalType = required(request, "PrincipalType");
        if (!PRINCIPAL_TYPES.contains(principalType)) {
            throw validation("PrincipalType must be USER or GROUP.");
        }
        String key = account + "::" + permission + "::" + principal;
        Assignment existing = assignments.get(key)
                .orElseThrow(() -> notFound("Account assignment not found."));
        if (!principalType.equals(existing.principalType())) {
            throw notFound("Account assignment not found.");
        }
        assignments.delete(key);
        String requestId = UUID.randomUUID().toString();
        AssignmentDeletionOperation operation = new AssignmentDeletionOperation(
                requestId, "SUCCEEDED", System.currentTimeMillis(), account, permission, principal, principalType, null);
        assignmentDeletionOperations.put(requestId, operation);
        return operation;
    }

    public AssignmentDeletionOperation getAssignmentDeletionOperation(String instanceArn, String requestId) {
        requireInstance(instanceArn);
        if (requestId == null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw validation("AccountAssignmentDeletionRequestId must be a UUID.");
        }
        return assignmentDeletionOperations.get(requestId)
                .orElseThrow(() -> notFound("Assignment deletion operation not found: " + requestId));
    }

    public AssignmentOperation getAssignmentOperation(String instanceArn, String requestId) {
        requireInstance(instanceArn);
        if (requestId == null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw validation("AccountAssignmentCreationRequestId must be a UUID.");
        }
        return assignmentOperations.get(requestId).orElseThrow(() -> notFound("Assignment operation not found: " + requestId));
    }

    static String required(JsonNode request, String field) {
        String value = text(request, field);
        if (value == null || value.isBlank()) {
            throw validation(field + " must be a non-empty string.");
        }
        return value;
    }

    static String text(JsonNode request, String field) {
        JsonNode node = request == null ? null : request.get(field);
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private static Integer optionalMaxResults(JsonNode request) {
        JsonNode node = request == null ? null : request.get("MaxResults");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber()) {
            throw validation("MaxResults must be an integer.");
        }
        return node.intValue();
    }

    private static String validateName(String name) {
        if (name.length() > 32 || !PERMISSION_SET_NAME.matcher(name).matches()) {
            throw validation("Name must be 1-32 characters and match [\\w+=,.@-]+.");
        }
        return name;
    }

    private static String optionalDescription(JsonNode request) {
        if (!request.has("Description") || request.get("Description").isNull()) {
            return null;
        }
        String description = text(request, "Description");
        if (description == null || description.length() < 1 || description.length() > 700) {
            throw validation("Description must be between 1 and 700 characters.");
        }
        return description;
    }

    private static String validateSession(String value) {
        if (value == null || value.length() > 100) {
            throw validation("SessionDuration is invalid.");
        }
        try {
            Duration duration = Duration.parse(value);
            if (duration.compareTo(Duration.ofHours(1)) < 0 || duration.compareTo(Duration.ofHours(12)) > 0) {
                throw validation("SessionDuration must be between 1 and 12 hours.");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw validation("SessionDuration must use ISO-8601 duration syntax.");
        }
        return value;
    }

    private static void validatePermissionSetArn(String arn) {
        if (arn == null || !PERMISSION_SET_ARN.matcher(arn).matches()) {
            throw validation("PermissionSetArn is invalid.");
        }
    }

    private static void validateManagedPolicyArn(String arn) {
        if (arn == null || arn.length() > 2048 || !MANAGED_POLICY_ARN.matcher(arn).matches()) {
            throw validation("ManagedPolicyArn must identify an AWS managed IAM policy.");
        }
    }

    private static void validateInlinePolicy(String policy) {
        if (policy == null || policy.isEmpty() || policy.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_INLINE_POLICY_BYTES) {
            throw validation("InlinePolicy must be between 1 and 32768 bytes.");
        }
        long nonWhitespace = policy.chars().filter(ch -> !Character.isWhitespace(ch)).count();
        if (nonWhitespace > MAX_INLINE_NON_WHITESPACE) {
            throw quota("InlinePolicy exceeds the non-whitespace quota.");
        }
    }

    SsoApplication getApplication(String applicationArn) {
        validateApplicationArn(applicationArn);
        return applications.get(applicationArn).orElseThrow(() -> notFound("Application not found: " + applicationArn));
    }

    private static String validateApplicationArn(String arn) {
        if (arn == null || arn.length() > 1224 || !APPLICATION_ARN.matcher(arn).matches()) {
            throw validation("ApplicationArn is invalid.");
        }
        return arn;
    }

    private static String applicationAssignmentKey(String applicationArn, String principalId, String principalType) {
        return applicationArn + "::" + principalType + "::" + principalId;
    }

    private static boolean applicationMatches(SsoApplication application, String providerArn, String description,
                                              String name, ApplicationPortalOptions portalOptions, String status,
                                              Map<String, String> tags) {
        return java.util.Objects.equals(application.applicationProviderArn(), providerArn)
                && java.util.Objects.equals(application.description(), description)
                && java.util.Objects.equals(application.name(), name)
                && java.util.Objects.equals(application.portalOptions(), portalOptions)
                && java.util.Objects.equals(application.status(), status)
                && java.util.Objects.equals(application.tags(), tags);
    }

    private static ApplicationPortalOptions parsePortalOptions(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw validation("PortalOptions must be an object.");
        }
        String visibility = text(node, "Visibility");
        if (visibility != null && !Set.of("ENABLED", "DISABLED").contains(visibility)) {
            throw validation("PortalOptions.Visibility must be ENABLED or DISABLED.");
        }
        JsonNode signIn = node.get("SignInOptions");
        ApplicationSignInOptions signInOptions = null;
        if (signIn != null && !signIn.isNull()) {
            if (!signIn.isObject()) {
                throw validation("PortalOptions.SignInOptions must be an object.");
            }
            String origin = required(signIn, "Origin");
            if (!Set.of("IDENTITY_CENTER", "APPLICATION").contains(origin)) {
                throw validation("SignInOptions.Origin must be IDENTITY_CENTER or APPLICATION.");
            }
            String applicationUrl = text(signIn, "ApplicationUrl");
            if ("APPLICATION".equals(origin) && applicationUrl == null) {
                throw validation("SignInOptions.ApplicationUrl is required when Origin is APPLICATION.");
            }
            if (applicationUrl != null && (applicationUrl.length() > 512 || !APPLICATION_URL.matcher(applicationUrl).matches())) {
                throw validation("SignInOptions.ApplicationUrl is invalid.");
            }
            signInOptions = new ApplicationSignInOptions(origin, applicationUrl);
        }
        return new ApplicationPortalOptions(visibility, signInOptions);
    }

    private static String optionalInstanceName(JsonNode request) {
        if (request == null || !request.has("Name") || request.get("Name").isNull()) {
            return null;
        }
        String name = text(request, "Name");
        if (name == null || name.length() > 255 || !INSTANCE_NAME.matcher(name).matches()) {
            throw validation("Name must be at most 255 characters and match [\\w+=,.@-]+.");
        }
        return name;
    }

    private static String optionalString(JsonNode request, String field, int min, int max) {
        if (request == null || !request.has(field) || request.get(field).isNull()) {
            return null;
        }
        String value = text(request, field);
        if (value == null || value.length() < min || value.length() > max) {
            throw validation(field + " length is invalid.");
        }
        return value;
    }

    private static Map<String, String> parseTags(JsonNode node) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return tags;
        }
        if (!node.isArray() || node.size() > 75) {
            throw validation("Tags must contain at most 75 entries.");
        }
        for (JsonNode tag : node) {
            if (!tag.isObject()) {
                throw validation("Each tag must be an object.");
            }
            String key = required(tag, "Key");
            JsonNode valueNode = tag.get("Value");
            String value = valueNode != null && valueNode.isTextual() ? valueNode.textValue() : null;
            if (key.length() > 128 || !TAG_VALUE.matcher(key).matches()
                    || value == null || value.length() > 256 || !TAG_VALUE.matcher(value).matches()
                    || key.regionMatches(true, 0, "aws:", 0, 4)) {
                throw validation("Tag key or value is invalid.");
            }
            if (tags.putIfAbsent(key, value) != null) {
                throw validation("Duplicate tag key: " + key);
            }
        }
        return tags;
    }

    private static int managedPolicyCount(PermissionSet permissionSet) {
        return permissionSet.managedPolicies().size() + permissionSet.customerManagedPolicies().size();
    }

    private static String customerManagedPolicyKey(String name, String path) {
        return name.toLowerCase(java.util.Locale.ROOT) + "\n" + path;
    }

    private static String validateRegionName(String value) {
        if (value == null || value.length() > 32 || !REGION_NAME.matcher(value).matches()) {
            throw validation("RegionName must be 1-32 characters and use an AWS Region name format.");
        }
        return value;
    }

    private static String validateAccountId(String value) {
        if (value == null || !value.matches("\\d{12}")) {
            throw validation("AWS account identifiers must contain 12 digits.");
        }
        return value;
    }

    private static String validatePrincipalId(String value) {
        if (value == null || !PRINCIPAL_ID.matcher(value).matches()) {
            throw validation("PrincipalId is invalid.");
        }
        return value;
    }

    private static String valueOr(JsonNode request, String field, String fallback) {
        String value = text(request, field);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static String shortId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    private static AwsException notFound(String message) { return new AwsException("ResourceNotFoundException", message, 400); }
    private static AwsException validation(String message) { return new AwsException("ValidationException", message, 400); }
    private static AwsException conflict(String message) { return new AwsException("ConflictException", message, 400); }
    private static AwsException quota(String message) { return new AwsException("ServiceQuotaExceededException", message, 400); }
    private static AwsException accessDenied(String message) { return new AwsException("AccessDeniedException", message, 400); }
    private SsoInstance requireInstance(String arn) {
        if (arn == null || arn.length() < 10 || arn.length() > 1224 || !INSTANCE_ARN_PATTERN.matcher(arn).matches()) {
            throw validation("InstanceArn is invalid.");
        }
        SsoInstance instance = findInstanceByArn(arn);
        if (instance == null) {
            throw notFound("IAM Identity Center instance not found: " + arn);
        }
        return instance;
    }

    private SsoInstance findInstanceByArn(String arn) {
        if (arn == null) {
            return null;
        }
        SsoInstance stored = instances.scan(key -> true).stream()
                .filter(instance -> arn.equals(instance.instanceArn()))
                .findFirst().orElse(null);
        return stored;
    }

    @Override
    public void clear() {
        permissionSets.clear();
        assignments.clear();
        assignmentOperations.clear();
        assignmentDeletionOperations.clear();
        regions.clear();
        applications.clear();
        applicationClientTokens.clear();
        applicationAssignments.clear();
        instances.clear();
        instanceClientTokens.clear();
        accessControlAttributeConfigurations.clear();
        trustedTokenIssuers.clear();
        trustedTokenIssuerClientTokens.clear();
        ensureBootstrapInstance(defaultAccountId, defaultRegion);
    }

}
