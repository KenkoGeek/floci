package io.github.hectorvent.floci.services.cloudwatch.logs;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.cloudwatch.logs.model.AccountPolicy;
import io.github.hectorvent.floci.services.cloudwatch.logs.model.LogDestination;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CloudWatchLogsCrossAccountService {
    private final StorageBackend<String, LogDestination> destinations;
    private final StorageBackend<String, AccountPolicy> accountPolicies;
    private final RegionResolver regionResolver;

    @Inject
    public CloudWatchLogsCrossAccountService(StorageFactory storageFactory, RegionResolver regionResolver) {
        this(
                storageFactory.create("cloudwatchlogs", "cwlogs-destinations.json",
                        new TypeReference<Map<String, LogDestination>>() {}),
                storageFactory.create("cloudwatchlogs", "cwlogs-account-policies.json",
                        new TypeReference<Map<String, AccountPolicy>>() {}),
                regionResolver);
    }

    CloudWatchLogsCrossAccountService() {
        this(new InMemoryStorage<>(), new InMemoryStorage<>(), new RegionResolver("us-east-1", "000000000000"));
    }

    CloudWatchLogsCrossAccountService(StorageBackend<String, LogDestination> destinations,
                                      StorageBackend<String, AccountPolicy> accountPolicies,
                                      RegionResolver regionResolver) {
        this.destinations = destinations;
        this.accountPolicies = accountPolicies;
        this.regionResolver = regionResolver;
    }

    public synchronized LogDestination putDestination(String destinationName, String targetArn,
                                                       String roleArn, String region) {
        requireText(destinationName, "destinationName");
        requireText(targetArn, "targetArn");
        requireText(roleArn, "roleArn");
        LogDestination destination = destinations.get(destinationName).orElseGet(LogDestination::new);
        destination.setDestinationName(destinationName);
        destination.setTargetArn(targetArn);
        destination.setRoleArn(roleArn);
        destination.setArn("arn:aws:logs:" + region + ":" + regionResolver.getAccountId() + ":destination:" + destinationName);
        if (destination.getCreationTime() == 0) {
            destination.setCreationTime(System.currentTimeMillis());
        }
        destinations.put(destinationName, destination);
        return destination;
    }

    public synchronized void putDestinationPolicy(String destinationName, String accessPolicy) {
        requireText(destinationName, "destinationName");
        requireText(accessPolicy, "accessPolicy");
        LogDestination destination = destinations.get(destinationName)
                .orElseThrow(() -> new AwsException("ResourceNotFoundException",
                        "The specified destination does not exist.", 400));
        destination.setAccessPolicy(accessPolicy);
        destinations.put(destinationName, destination);
    }

    public synchronized AccountPolicy putAccountPolicy(String policyName, String policyDocument,
                                                        String policyType, String selectionCriteria, String scope) {
        requireText(policyName, "policyName");
        requireText(policyDocument, "policyDocument");
        requireText(policyType, "policyType");
        AccountPolicy policy = accountPolicies.get(policyKey(policyType, policyName)).orElseGet(AccountPolicy::new);
        policy.setPolicyName(policyName);
        policy.setPolicyDocument(policyDocument);
        policy.setPolicyType(policyType);
        policy.setSelectionCriteria(selectionCriteria);
        policy.setScope(scope == null || scope.isBlank() ? "ALL" : scope);
        policy.setLastUpdatedTime(System.currentTimeMillis());
        accountPolicies.put(policyKey(policyType, policyName), policy);
        return policy;
    }

    public List<AccountPolicy> describeAccountPolicies(String policyType, String policyName) {
        return accountPolicies.scan(key -> policyType == null || key.startsWith(policyType + "::")).stream()
                .filter(policy -> policyName == null || policyName.equals(policy.getPolicyName()))
                .sorted(Comparator.comparing(AccountPolicy::getPolicyName))
                .toList();
    }

    private static String policyKey(String policyType, String policyName) {
        return policyType + "::" + policyName;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AwsException("InvalidParameterException", field + " is required.", 400);
        }
    }
}
