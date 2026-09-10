# AWS Marketplace

Floci emulates AWS Marketplace APIs under the shared `aws-marketplace` SigV4 signing scope.

## Marketplace Catalog

<!-- floci:actions:start -->
| Action | Description |
| --- | --- |
| `BatchDescribeEntities` | Describes up to 20 catalog entities in one request |
| `CancelChangeSet` | Cancels a change set while it is preparing or applying |
| `DeleteResourcePolicy` | Deletes an entity resource policy |
| `DescribeAssessment` | Returns an assessment and control results |
| `DescribeChangeSet` | Returns change set status and change details |
| `DescribeEntity` | Returns the current entity revision |
| `GetResourcePolicy` | Returns an entity resource policy |
| `ListAssessments` | Lists Marketplace Catalog assessments |
| `ListChangeSets` | Lists change sets with pagination |
| `ListEntities` | Lists catalog entities by entity type |
| `ListTagsForResource` | Lists tags on a Marketplace Catalog resource |
| `PutResourcePolicy` | Creates or replaces an entity resource policy |
| `StartChangeSet` | Starts an idempotent Marketplace Catalog change set |
| `TagResource` | Adds or replaces tags on a Marketplace Catalog resource |
| `UntagResource` | Removes tag keys from a Marketplace Catalog resource |
<!-- floci:actions:end -->

Catalog change sets use AWS states (`PREPARING`, `APPLYING`, `SUCCEEDED`, and `CANCELLED`). Floci applies supported entity mutations locally when a change set is observed and persists entities, change sets, tags, resource policies, and assessments through `StorageFactory`, isolated by AWS account.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_MARKETPLACE_ENABLED` | `true` | Enable or disable AWS Marketplace emulation |

See the [AWS Marketplace API Reference](https://docs.aws.amazon.com/marketplace/latest/APIReference/).
