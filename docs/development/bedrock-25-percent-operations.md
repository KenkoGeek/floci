# Bedrock 25% operation task ledger

Each task is checked against the Amazon Bedrock API Reference and the AWS SDK for Java v2 2.52.0 generated request marshaller before exposure. Full Maven test execution is intentionally omitted per project instruction for this worktree; source compilation is performed at milestones.

| Task | Operation | AWS method/path | Validation |
|---|---|---|---|
| BR-001 | `GetUseCaseForModelAccess` | `GET /use-case-for-model-access` | SDK marshaller contract, focused test added, compile, diff-check |
| BR-002 | `PutUseCaseForModelAccess` | `POST /use-case-for-model-access` | SDK marshaller binding verified; diff-check |
| BR-003 | `GetFoundationModelAvailability` | `GET /foundation-model-availability/{modelId}` | SDK marshaller binding verified; diff-check |
| BR-004 | `ListFoundationModelAgreementOffers` | `GET /list-foundation-model-agreement-offers/{modelId}` | SDK marshaller binding verified; diff-check |
| BR-005 | `CreateFoundationModelAgreement` | `POST /create-foundation-model-agreement` | SDK marshaller binding verified; diff-check |
| BR-006 | `GetFoundationModel` | `GET /foundation-models/{modelIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-007 | `ListFoundationModels` | `GET /foundation-models` | SDK marshaller binding verified; diff-check |
| BR-008 | `DeleteFoundationModelAgreement` | `POST /delete-foundation-model-agreement` | SDK marshaller binding verified; diff-check |
| BR-009 | `GetModelInvocationLoggingConfiguration` | `GET /logging/modelinvocations` | SDK marshaller binding verified; diff-check |
| BR-010 | `PutModelInvocationLoggingConfiguration` | `PUT /logging/modelinvocations` | SDK marshaller binding verified; diff-check |
| BR-011 | `DeleteModelInvocationLoggingConfiguration` | `DELETE /logging/modelinvocations` | SDK marshaller binding verified; diff-check |
| BR-012 | `ListTagsForResource` | `POST /listTagsForResource` | SDK marshaller binding verified; diff-check |
| BR-013 | `TagResource` | `POST /tagResource` | SDK marshaller binding verified; diff-check |
| BR-014 | `UntagResource` | `POST /untagResource` | SDK marshaller binding verified; diff-check |
| BR-015 | `GetResourcePolicy` | `GET /resource-policy/{resourceArn}` | SDK marshaller binding verified; diff-check |
| BR-016 | `PutResourcePolicy` | `POST /resource-policy` | SDK marshaller binding verified; diff-check |
| BR-017 | `DeleteResourcePolicy` | `DELETE /resource-policy/{resourceArn}` | SDK marshaller binding verified; diff-check |
| BR-018 | `GetAccountDataRetention` | `GET /data-retention` | SDK marshaller binding verified; diff-check |
| BR-019 | `PutAccountDataRetention` | `PUT /data-retention` | SDK marshaller binding verified; diff-check |
| BR-020 | `CreateGuardrail` | `POST /guardrails` | SDK marshaller binding verified; diff-check |
| BR-021 | `CreateGuardrailVersion` | `POST /guardrails/{guardrailIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-022 | `DeleteGuardrail` | `DELETE /guardrails/{guardrailIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-023 | `GetGuardrail` | `GET /guardrails/{guardrailIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-024 | `ListGuardrails` | `GET /guardrails` | SDK marshaller binding verified; diff-check |
| BR-025 | `UpdateGuardrail` | `PUT /guardrails/{guardrailIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-026 | `ListEnforcedGuardrailsConfiguration` | `GET /enforcedGuardrailsConfiguration` | SDK marshaller binding verified; diff-check |
| BR-027 | `PutEnforcedGuardrailConfiguration` | `PUT /enforcedGuardrailsConfiguration` | SDK marshaller binding verified; diff-check |
| BR-028 | `DeleteEnforcedGuardrailConfiguration` | `DELETE /enforcedGuardrailsConfiguration/{configId}` | SDK marshaller binding verified; diff-check |
| BR-029 | `CreateInferenceProfile` | `POST /inference-profiles` | SDK marshaller binding verified; diff-check |
| BR-030 | `GetInferenceProfile` | `GET /inference-profiles/{inferenceProfileIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-031 | `ListInferenceProfiles` | `GET /inference-profiles` | SDK marshaller binding verified; diff-check |
| BR-032 | `DeleteInferenceProfile` | `DELETE /inference-profiles/{inferenceProfileIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-033 | `CreateProvisionedModelThroughput` | `POST /provisioned-model-throughput` | SDK marshaller binding verified; diff-check |
| BR-034 | `GetProvisionedModelThroughput` | `GET /provisioned-model-throughput/{provisionedModelId}` | SDK marshaller binding verified; diff-check |
| BR-035 | `ListProvisionedModelThroughputs` | `GET /provisioned-model-throughputs` | SDK marshaller binding verified; diff-check |
| BR-036 | `UpdateProvisionedModelThroughput` | `PATCH /provisioned-model-throughput/{provisionedModelId}` | SDK marshaller binding verified; diff-check |
| BR-037 | `DeleteProvisionedModelThroughput` | `DELETE /provisioned-model-throughput/{provisionedModelId}` | SDK marshaller binding verified; diff-check |
| BR-038 | `CreateModelImportJob` | `POST /model-import-jobs` | SDK marshaller binding verified; diff-check |
| BR-039 | `GetModelImportJob` | `GET /model-import-jobs/{jobIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-040 | `ListModelImportJobs` | `GET /model-import-jobs` | SDK marshaller binding verified; diff-check |
| BR-041 | `GetImportedModel` | `GET /imported-models/{modelIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-042 | `ListImportedModels` | `GET /imported-models` | SDK marshaller binding verified; diff-check |
| BR-043 | `DeleteImportedModel` | `DELETE /imported-models/{modelIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-044 | `CreateCustomModel` | `POST /custom-models/create-custom-model` | SDK marshaller binding verified; diff-check |
| BR-045 | `GetCustomModel` | `GET /custom-models/{modelIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-046 | `ListCustomModels` | `GET /custom-models` | SDK marshaller binding verified; diff-check |
| BR-047 | `DeleteCustomModel` | `DELETE /custom-models/{modelIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-048 | `CreateModelCustomizationJob` | `POST /model-customization-jobs` | SDK marshaller binding verified; diff-check |
| BR-049 | `GetModelCustomizationJob` | `GET /model-customization-jobs/{jobIdentifier}` | SDK marshaller binding verified; diff-check |
| BR-050 | `ListModelCustomizationJobs` | `GET /model-customization-jobs` | SDK marshaller binding verified; diff-check |
