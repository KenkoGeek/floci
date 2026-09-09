# Amazon Bedrock

**Protocol:** REST JSON
**Signing name:** `bedrock`

Floci emulates the Amazon Bedrock control-plane API separately from the existing Bedrock Runtime data plane. The control plane is account-aware and persists mutable emulator state through `StorageFactory`.

The implementation follows the Amazon Bedrock API Reference and the AWS SDK for Java v2 Bedrock service model. Unsupported control-plane operations continue to return the normal unmatched-operation response instead of synthetic success.

## Supported Actions

<!-- floci:actions:start -->
| Action | Description |
| --- | --- |
| `GetUseCaseForModelAccess` | - |
| `PutUseCaseForModelAccess` | - |
| `GetFoundationModelAvailability` | - |
| `ListFoundationModelAgreementOffers` | - |
| `CreateFoundationModelAgreement` | - |
| `GetFoundationModel` | - |
| `ListFoundationModels` | - |
| `DeleteFoundationModelAgreement` | - |
| `GetModelInvocationLoggingConfiguration` | - |
| `PutModelInvocationLoggingConfiguration` | - |
| `DeleteModelInvocationLoggingConfiguration` | - |
| `ListTagsForResource` | - |
| `TagResource` | - |
| `UntagResource` | - |
| `GetResourcePolicy` | - |
| `PutResourcePolicy` | - |
| `DeleteResourcePolicy` | - |
| `GetAccountDataRetention` | - |
| `PutAccountDataRetention` | - |
| `CreateGuardrail` | - |
| `CreateGuardrailVersion` | - |
| `DeleteGuardrail` | - |
| `GetGuardrail` | - |
| `ListGuardrails` | - |
| `UpdateGuardrail` | - |
| `ListEnforcedGuardrailsConfiguration` | - |
| `PutEnforcedGuardrailConfiguration` | - |
| `DeleteEnforcedGuardrailConfiguration` | - |
| `CreateInferenceProfile` | - |
| `GetInferenceProfile` | - |
| `ListInferenceProfiles` | - |
| `DeleteInferenceProfile` | - |
| `CreateProvisionedModelThroughput` | - |
| `GetProvisionedModelThroughput` | - |
| `ListProvisionedModelThroughputs` | - |
| `UpdateProvisionedModelThroughput` | - |
| `DeleteProvisionedModelThroughput` | - |
| `CreateModelImportJob` | - |
| `GetModelImportJob` | - |
| `ListModelImportJobs` | - |
| `GetImportedModel` | - |
| `ListImportedModels` | - |
| `DeleteImportedModel` | - |
| `CreateCustomModel` | - |
| `GetCustomModel` | - |
| `ListCustomModels` | - |
| `DeleteCustomModel` | - |
| `CreateModelCustomizationJob` | - |
| `GetModelCustomizationJob` | - |
| `ListModelCustomizationJobs` | - |
| `StopModelCustomizationJob` | - |
| `CreateModelCopyJob` | - |
| `GetModelCopyJob` | - |
| `ListModelCopyJobs` | - |
| `CreateModelInvocationJob` | - |
| `GetModelInvocationJob` | - |
| `ListModelInvocationJobs` | - |
| `StopModelInvocationJob` | - |
| `CreateMarketplaceModelEndpoint` | - |
| `GetMarketplaceModelEndpoint` | - |
| `ListMarketplaceModelEndpoints` | - |
| `UpdateMarketplaceModelEndpoint` | - |
| `DeleteMarketplaceModelEndpoint` | - |
| `RegisterMarketplaceModelEndpoint` | - |
| `DeregisterMarketplaceModelEndpoint` | - |
| `CreatePromptRouter` | - |
<!-- floci:actions:end -->

### Cloud Launchpad model access flow

The five model-access operations used by Cloud Launchpad are `GetUseCaseForModelAccess`, `PutUseCaseForModelAccess`, `GetFoundationModelAvailability`, `ListFoundationModelAgreementOffers`, and `CreateFoundationModelAgreement`. The emulator preserves the documented success status codes, request validation, agreement state transition, and AWS SDK response shapes for this flow.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_BEDROCK_ENABLED` | `true` | Enable or disable the Bedrock control plane. |

## Compatibility notes

Bedrock control-plane and Bedrock Runtime requests both use the `bedrock` SigV4 signing scope in AWS SDK service models. Floci registers each JAX-RS controller as its own service resource class so service enablement and REST JSON error handling remain route-specific while both signing forms are recognized.
