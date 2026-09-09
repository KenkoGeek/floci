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
