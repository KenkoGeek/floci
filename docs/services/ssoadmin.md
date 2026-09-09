# IAM Identity Center (SSO Admin)

**Protocol:** JSON 1.1 (`X-Amz-Target: SWBExternalService.*`)
**Signing name:** `sso`

Floci supports the SSO Admin operations used to manage IAM Identity Center permission sets and account assignments locally.

## Supported Actions

<!-- floci:actions:start -->
| Action | Description |
| --- | --- |
| `ListInstances` | Lists the local IAM Identity Center instance. |
| `CreateInstance` | Creates a standalone account instance with AWS-compatible singleton, idempotency, tags, and instance metadata. |
| `DeleteInstance` | Deletes an owned IAM Identity Center instance with AWS-compatible empty response semantics and local dependent-resource cleanup. |
| `CreateInstanceAccessControlAttributeConfiguration` | Enables ABAC and stores up to 50 IAM Identity Center access control attributes. |
| `CreateTrustedTokenIssuer` | Creates an OIDC JWT trusted token issuer with idempotency, tags, and AWS-compatible validation. |
| `AddRegion` | Adds a Region to the local IAM Identity Center instance and reports the initial `ADDING` status. |
| `CreateApplication` | Creates a customer managed OAuth 2.0 application with AWS-compatible idempotency, portal options, status, and tags. |
| `CreateApplicationAssignment` | Grants direct application access to a user or group. |
| `DeleteApplication` | Deletes the IAM Identity Center application association and its local assignment state. |
| `DeleteApplicationAccessScope` | Deletes an application access scope after validating its AWS scope name and application ARN. |
| `DeleteApplicationAssignment` | Revokes a direct user or group assignment from an IAM Identity Center application. |
| `DeleteApplicationAuthenticationMethod` | Deletes the IAM authentication method configured for an IAM Identity Center application. |
| `DeleteApplicationGrant` | Deletes a supported OAuth 2.0 grant configuration from an IAM Identity Center application. |
| `ListPermissionSets` | Lists permission sets with AWS-compatible pagination. |
| `CreatePermissionSet` | Creates a permission set. |
| `DescribePermissionSet` | Describes a permission set. |
| `UpdatePermissionSet` | Updates mutable permission-set settings. |
| `ListManagedPoliciesInPermissionSet` | Lists attached AWS managed policies with AWS-compatible pagination. |
| `AttachManagedPolicyToPermissionSet` | Attaches an AWS managed policy. |
| `AttachCustomerManagedPolicyReferenceToPermissionSet` | Attaches a customer managed IAM policy reference by name and path. |
| `DetachManagedPolicyFromPermissionSet` | Detaches an AWS managed policy. |
| `DeleteInlinePolicyFromPermissionSet` | Deletes the inline policy. |
| `PutInlinePolicyToPermissionSet` | Creates or replaces the inline policy. |
| `ListAccountAssignments` | Lists account assignments with AWS-compatible pagination. |
| `ListAccountAssignmentsForPrincipal` | Lists the AWS account and permission set assignments for a user or group, with AccountId filtering and AWS-compatible pagination. |
| `ProvisionPermissionSet` | Provisions a permission set to one AWS account or refreshes all previously provisioned accounts, returning an AWS-compatible provisioning status. |
| `DescribePermissionSetProvisioningStatus` | Describes a persisted permission-set provisioning request by its AWS-compatible UUID request identifier. |
| `ListPermissionSetProvisioningStatus` | Lists permission-set provisioning request metadata with optional operation-status filtering and AWS-compatible pagination. |
| `ListPermissionSetsProvisionedToAccount` | Lists permission sets provisioned to an AWS account, including current/stale provisioning status filters and AWS-compatible pagination. |
| `ListAccountsForProvisionedPermissionSet` | Lists AWS accounts where a permission set is provisioned, including current/stale provisioning status filters and AWS-compatible pagination. |
| `CreateAccountAssignment` | Creates an account assignment and operation record. |
| `DeleteAccountAssignment` | Deletes an account assignment and returns a persisted deletion operation status. |
| `DescribeAccountAssignmentCreationStatus` | Describes account-assignment creation status. |
<!-- floci:actions:end -->

State is isolated by caller account through Floci storage.

## AWS-compatible failures and state

Permission-set names, ARNs, session durations, managed-policy ARNs, inline policies, account IDs, principal types, pagination, and duplicate assignments are validated before state is changed. Missing resources return `ResourceNotFoundException`; duplicate or incompatible state returns `ConflictException`; invalid input returns `ValidationException`; enforced local limits return `ServiceQuotaExceededException`.

Account-assignment creation returns an operation record that can be read with `DescribeAccountAssignmentCreationStatus`. Provider-side `InternalServerException` and `ThrottlingException` are part of the AWS model but are not injected artificially by Floci.

See the [AWS SSO Admin API Reference](https://docs.aws.amazon.com/singlesignon/latest/APIReference/welcome.html).
