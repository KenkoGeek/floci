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
