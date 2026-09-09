# Amazon Bedrock

**Protocol:** REST JSON
**Signing name:** `bedrock`

Floci emulates the Amazon Bedrock control-plane API separately from the existing Bedrock Runtime data plane. The control plane is account-aware and persists mutable emulator state through `StorageFactory`.

The implementation follows the Amazon Bedrock API Reference and the AWS SDK for Java v2 Bedrock service model. Unsupported control-plane operations continue to return the normal unmatched-operation response instead of synthetic success.

## Supported Operations

| Operation | Endpoint | Notes |
|-----------|----------|-------|
| `GetUseCaseForModelAccess` | `GET /use-case-for-model-access` | Returns the account model-access use-case form data or `ResourceNotFoundException` when it has not been configured. |
| `PutUseCaseForModelAccess` | `POST /use-case-for-model-access` | Validates base64 form data and AWS 10-16384 byte limits; returns HTTP 201. |
| `GetFoundationModelAvailability` | `GET /foundation-model-availability/{modelId}` | Returns agreement, authorization, entitlement, and regional availability state. |
| `ListFoundationModelAgreementOffers` | `GET /list-foundation-model-agreement-offers/{modelId}` | Returns a deterministic local public offer and opaque offer token. |
| `CreateFoundationModelAgreement` | `POST /create-foundation-model-agreement` | Validates the offer token, persists agreement state, and returns HTTP 202. |
| `GetFoundationModel` | `GET /foundation-models/{modelIdentifier}` | AWS-compatible control-plane emulation. |
| `ListFoundationModels` | `GET /foundation-models` | AWS-compatible control-plane emulation. |
| `DeleteFoundationModelAgreement` | `POST /delete-foundation-model-agreement` | AWS-compatible control-plane emulation. |
| `GetModelInvocationLoggingConfiguration` | `GET /logging/modelinvocations` | AWS-compatible control-plane emulation. |
| `PutModelInvocationLoggingConfiguration` | `PUT /logging/modelinvocations` | AWS-compatible control-plane emulation. |
| `DeleteModelInvocationLoggingConfiguration` | `DELETE /logging/modelinvocations` | AWS-compatible control-plane emulation. |
| `ListTagsForResource` | `POST /listTagsForResource` | AWS-compatible control-plane emulation. |
| `TagResource` | `POST /tagResource` | AWS-compatible control-plane emulation. |
| `UntagResource` | `POST /untagResource` | AWS-compatible control-plane emulation. |
| `GetResourcePolicy` | `GET /resource-policy/{resourceArn}` | AWS-compatible control-plane emulation. |
| `PutResourcePolicy` | `POST /resource-policy` | AWS-compatible control-plane emulation. |
| `DeleteResourcePolicy` | `DELETE /resource-policy/{resourceArn}` | AWS-compatible control-plane emulation. |
| `GetAccountDataRetention` | `GET /data-retention` | AWS-compatible control-plane emulation. |
| `PutAccountDataRetention` | `PUT /data-retention` | AWS-compatible control-plane emulation. |
| `CreateGuardrail` | `POST /guardrails` | AWS-compatible control-plane emulation. |
| `CreateGuardrailVersion` | `POST /guardrails/{guardrailIdentifier}` | AWS-compatible control-plane emulation. |
| `DeleteGuardrail` | `DELETE /guardrails/{guardrailIdentifier}` | AWS-compatible control-plane emulation. |
| `GetGuardrail` | `GET /guardrails/{guardrailIdentifier}` | AWS-compatible control-plane emulation. |
| `ListGuardrails` | `GET /guardrails` | AWS-compatible control-plane emulation. |
| `UpdateGuardrail` | `PUT /guardrails/{guardrailIdentifier}` | AWS-compatible control-plane emulation. |
| `ListEnforcedGuardrailsConfiguration` | `GET /enforcedGuardrailsConfiguration` | AWS-compatible control-plane emulation. |
| `PutEnforcedGuardrailConfiguration` | `PUT /enforcedGuardrailsConfiguration` | AWS-compatible control-plane emulation. |
| `DeleteEnforcedGuardrailConfiguration` | `DELETE /enforcedGuardrailsConfiguration/{configId}` | AWS-compatible control-plane emulation. |
| `CreateInferenceProfile` | `POST /inference-profiles` | AWS-compatible control-plane emulation. |
| `GetInferenceProfile` | `GET /inference-profiles/{inferenceProfileIdentifier}` | AWS-compatible control-plane emulation. |
| `ListInferenceProfiles` | `GET /inference-profiles` | AWS-compatible control-plane emulation. |
| `DeleteInferenceProfile` | `DELETE /inference-profiles/{inferenceProfileIdentifier}` | AWS-compatible control-plane emulation. |

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_BEDROCK_ENABLED` | `true` | Enable or disable the Bedrock control plane. |

## Compatibility notes

Bedrock control-plane and Bedrock Runtime requests both use the `bedrock` SigV4 signing scope in AWS SDK service models. Floci registers each JAX-RS controller as its own service resource class so service enablement and REST JSON error handling remain route-specific while both signing forms are recognized.
