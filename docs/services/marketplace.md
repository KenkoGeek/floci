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
| `PutDeploymentParameter` | - |
| `GetBuyerDashboard` | - |
| `AcceptAgreementCancellationRequest` | - |
| `AcceptAgreementPaymentRequest` | - |
| `AcceptAgreementRequest` | - |
| `BatchCreateBillingAdjustmentRequest` | - |
| `CancelAgreement` | - |
| `CancelAgreementCancellationRequest` | - |
| `CancelAgreementPaymentRequest` | - |
| `CreateAgreementRequest` | - |
| `DescribeAgreement` | - |
| `GetAgreementCancellationRequest` | - |
| `GetAgreementEntitlements` | - |
| `GetAgreementPaymentRequest` | - |
| `GetAgreementTerms` | - |
| `GetBillingAdjustmentRequest` | - |
| `ListAgreementCancellationRequests` | - |
| `ListAgreementCharges` | - |
| `ListAgreementInvoiceLineItems` | - |
| `ListAgreementPaymentRequests` | - |
| `ListBillingAdjustmentRequests` | - |
| `RejectAgreementCancellationRequest` | - |
| `RejectAgreementPaymentRequest` | - |
| `SearchAgreements` | - |
| `SendAgreementCancellationRequest` | - |
| `SendAgreementPaymentRequest` | - |
| `UpdatePurchaseOrders` | - |
<!-- floci:actions:end -->

Catalog change sets use AWS states (`PREPARING`, `APPLYING`, `SUCCEEDED`, and `CANCELLED`). Floci applies supported entity mutations locally when a change set is observed and persists entities, change sets, tags, resource policies, and assessments through `StorageFactory`, isolated by AWS account.

## Marketplace Agreement

The AWS JSON 1.0 Agreement endpoint supports all 25 current public operations: agreement request creation and acceptance, agreement description/search/cancellation, agreement terms and entitlements, cancellation requests, payment requests, billing adjustments, charges, invoice line items, and purchase order updates. Agreement request acceptance persists the resulting agreement and exposes it through subsequent read and search operations.

## Marketplace Entitlement

`GetEntitlements` is supported through the AWS JSON 1.1 wire contract used by the current AWS CLI and through Floci's Smithy RPC v2 CBOR dispatcher. The service validates the documented filter keys, enforces the mutual exclusion of customer identifier and customer AWS account ID filters, supports pagination, and follows AWS Marketplace's `us-east-1` regional restriction.

## Marketplace Deployment

The four Deployment Service operations are supported for Quick Launch workflows. `PutDeploymentParameter` creates or updates a parameter by catalog, product, agreement, and parameter name, preserves create-only tags on updates, supports client-token idempotency, and returns the documented DeploymentParameter ARN shape. Deployment parameter secret values are persisted for local emulation but are never returned by the API. Tag operations use the Deployment Service REST paths and status codes, including query-string `tagKeys` for `UntagResource`.

## Marketplace Discovery

All nine Discovery API operations from the 2026-02-05 public API are supported. Local Marketplace Catalog product, offer, and offer-set entities are projected into buyer-facing Discovery resources, so seller-side catalog changes can be exercised through `GetProduct`, `GetListing`, offer reads, purchase and fulfillment option lists, listing search, and facet search. Discovery requests enforce the documented service regions: `us-east-1`, `us-west-2`, and `eu-west-1`.

## Marketplace Reporting

`GetBuyerDashboard` validates the two documented procurement-insights dashboard ARN forms and one or two embedding domains, then returns an opaque local QuickSight-style embedding URL together with the requested dashboard and domains. AWS organization-management and delegated-administrator IAM authorization is outside Floci's protocol emulation boundary.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_MARKETPLACE_ENABLED` | `true` | Enable or disable AWS Marketplace emulation |

See the [AWS Marketplace API Reference](https://docs.aws.amazon.com/marketplace/latest/APIReference/).
