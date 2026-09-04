# Cloud Launchpad Go runner coverage

This document tracks the AWS API surface required by the Cloud Launchpad Go native reconciler in `backend/internal/awsfoundation`.

Scope is intentionally limited to AWS SDK calls issued by the Go runner for Core, Enterprise, Account Automation, verification, retry, removal, and decommission. Terraform-only resources and unrelated Cloud Launchpad binaries are excluded.

## Runner substrate

Cloud Launchpad itself uses DynamoDB, S3, and Secrets Manager for runner state, approved-plan evidence, and provider identity. These are already implemented by Floci and are not Cloud Launchpad customer-account coverage gaps.

## Existing Floci services used by the runner

The following Floci services already exist and still require operation-level compatibility validation against the Cloud Launchpad runner contract:

- Backup
- CloudFormation and StackSets
- CloudWatch Logs
- Control Tower
- EC2
- GuardDuty
- IAM
- KMS
- Organizations
- Route 53 Resolver
- S3
- Secrets Manager
- Service Catalog
- Service Quotas
- SSM
- IAM Identity Center Admin (`ssoadmin`)
- STS

## Missing Floci services required by the runner

| Service | Runner operations required |
| --- | --- |
| IAM Access Analyzer | `CreateAnalyzer`, `DeleteAnalyzer`, `ListAnalyzers` |
| AWS Account Management | `GetAlternateContact`, `PutAlternateContact` |
| AWS Budgets | `CreateBudget`, `CreateNotification`, `DeleteBudget`, `DeleteNotification`, `DescribeBudget`, `DescribeNotificationsForBudget`, `DescribeSubscribersForNotification`, `ListTagsForResource`, `UpdateBudget` |
| AWS Control Catalog | `GetControl` |
| Amazon Detective | `CreateMembers`, `DescribeOrganizationConfiguration`, `EnableOrganizationAdminAccount`, `ListGraphs`, `ListMembers`, `ListOrganizationAdminAccounts`, `StartMonitoringMember`, `UpdateOrganizationConfiguration` |
| AWS Identity Store | `CreateGroup`, `CreateGroupMembership`, `CreateUser`, `DeleteGroup`, `DeleteGroupMembership`, `DeleteUser`, `IsMemberInGroups`, `ListGroupMembershipsForMember`, `ListGroups`, `ListUsers` |
| Amazon Inspector2 | `BatchGetAccountStatus`, `DescribeOrganizationConfiguration`, `DisableDelegatedAdminAccount`, `Enable`, `EnableDelegatedAdminAccount`, `ListDelegatedAdminAccounts`, `UpdateOrganizationConfiguration` |
| Amazon Macie2 | `DisableMacie`, `DisableOrganizationAdminAccount`, `EnableMacie`, `EnableOrganizationAdminAccount`, `GetMacieSession`, `ListOrganizationAdminAccounts`, `UpdateOrganizationConfiguration` |
| CloudWatch Observability Access Manager | `CreateLink`, `CreateSink`, `DeleteLink`, `DeleteSink`, `ListAttachedLinks`, `ListLinks`, `ListSinks`, `PutSinkPolicy`, `UpdateLink` |
| Route 53 Profiles | `AssociateProfile`, `DisassociateProfile`, `ListProfileAssociations`, `ListProfiles` |
| S3 Control | `GetPublicAccessBlock`, `PutPublicAccessBlock` |
| AWS Security Hub | `CreateConfigurationPolicy`, `CreateFindingAggregator`, `DeleteConfigurationPolicy`, `DescribeHub`, `DescribeOrganizationConfiguration`, `DisableOrganizationAdminAccount`, `DisableSecurityHub`, `EnableOrganizationAdminAccount`, `EnableSecurityHub`, `GetConfigurationPolicy`, `GetConfigurationPolicyAssociation`, `GetFindingAggregator`, `ListConfigurationPolicies`, `ListConfigurationPolicyAssociations`, `ListFindingAggregators`, `ListOrganizationAdminAccounts`, `ListTagsForResource`, `StartConfigurationPolicyAssociation`, `StartConfigurationPolicyDisassociation`, `UpdateConfigurationPolicy`, `UpdateFindingAggregator`, `UpdateOrganizationConfiguration` |

## Protocols

AWS SDK generated clients and AWS API references establish the protocol families that Floci must preserve:

- AWS JSON 1.1: Budgets, Identity Store.
- REST JSON: Access Analyzer, Account Management, Control Catalog, Detective, Inspector2, Macie2, OAM, Route 53 Profiles, Security Hub.
- REST XML: S3 Control.

## Compatibility contract

Coverage is complete only when the AWS SDK for Go v2 can execute the operations above against Floci with AWS-compatible request routing, response shapes, identifiers, state transitions, not-found/conflict behavior, list semantics, and idempotent retry behavior expected by Cloud Launchpad.

Cloud Launchpad-specific convenience endpoints are not allowed. The Go runner must use the same AWS SDK calls it uses against AWS.
