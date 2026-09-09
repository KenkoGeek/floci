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
| `DescribeInstance` | Returns IAM Identity Center instance metadata, status, identity store ID, owner account, creation date, and permission-set availability. |
| `DeleteInstance` | Deletes an owned IAM Identity Center instance with AWS-compatible empty response semantics and local dependent-resource cleanup. |
| `CreateInstanceAccessControlAttributeConfiguration` | Enables ABAC and stores up to 50 IAM Identity Center access control attributes. |
| `DescribeInstanceAccessControlAttributeConfiguration` | Returns the IAM Identity Center ABAC attribute configuration and its creation status for an instance. |
| `DeleteInstanceAccessControlAttributeConfiguration` | Disables ABAC for an IAM Identity Center instance and removes its access control attribute configuration. |
| `CreateTrustedTokenIssuer` | Creates an OIDC JWT trusted token issuer with idempotency, tags, and AWS-compatible validation. |
| `DescribeTrustedTokenIssuer` | Returns the trusted token issuer name, ARN, OIDC JWT configuration, and issuer type. |
| `DeleteTrustedTokenIssuer` | Deletes a trusted token issuer, validates its AWS ARN, and clears local idempotency mappings. |
| `AddRegion` | Adds a Region to the local IAM Identity Center instance and reports the initial `ADDING` status. |
| `DescribeRegion` | Returns the enabled Region name, status, added date, and primary-Region flag for an IAM Identity Center instance. |
| `CreateApplication` | Creates a customer managed OAuth 2.0 application with AWS-compatible idempotency, portal options, status, and tags. |
| `DescribeApplication` | Returns the full persisted IAM Identity Center application metadata, including portal options, status, creation Region, and identity store ARN. |
| `CreateApplicationAssignment` | Grants direct application access to a user or group. |
| `DescribeApplicationAssignment` | Retrieves a direct user or group assignment to an IAM Identity Center application with AWS-compatible validation. |
| `DescribeApplicationProvider` | Returns the supported custom OAuth application provider metadata with AWS-compatible ARN validation and not-found behavior. |
| `ListApplicationAssignments` | Lists direct user and group assignments for an IAM Identity Center application with AWS-compatible pagination. |
| `ListApplicationAssignmentsForPrincipal` | Lists effective application access for a user or group, including group-derived user access, with instance-aware filtering and pagination. |
| `DeleteApplication` | Deletes the IAM Identity Center application association and its local assignment state. |
| `DeleteApplicationAccessScope` | Deletes an application access scope after validating its AWS scope name and application ARN. |
| `DeleteApplicationAssignment` | Revokes a direct user or group assignment from an IAM Identity Center application. |
| `DeleteApplicationAuthenticationMethod` | Deletes the IAM authentication method configured for an IAM Identity Center application. |
| `DeleteApplicationGrant` | Deletes a supported OAuth 2.0 grant configuration from an IAM Identity Center application. |
| `ListPermissionSets` | Lists permission sets with AWS-compatible pagination. |
| `CreatePermissionSet` | Creates a permission set. |
| `DeletePermissionSet` | Deletes a permission set and removes its local account-assignment and provisioning state. |
| `DescribePermissionSet` | Describes a permission set. |
| `UpdatePermissionSet` | Updates mutable permission-set settings. |
| `ListManagedPoliciesInPermissionSet` | Lists attached AWS managed policies with AWS-compatible pagination. |
| `AttachManagedPolicyToPermissionSet` | Attaches an AWS managed policy. |
| `AttachCustomerManagedPolicyReferenceToPermissionSet` | Attaches a customer managed IAM policy reference by name and path. |
| `DetachCustomerManagedPolicyReferenceFromPermissionSet` | Detaches a customer managed IAM policy reference from a permission set by name and path. |
| `ListCustomerManagedPolicyReferencesInPermissionSet` | Lists customer managed IAM policy references attached to a permission set with AWS-compatible pagination. |
| `DetachManagedPolicyFromPermissionSet` | Detaches an AWS managed policy. |
| `DeleteInlinePolicyFromPermissionSet` | Deletes the inline policy. |
| `DeletePermissionsBoundaryFromPermissionSet` | Removes the permissions boundary from a permission set and marks provisioned copies stale. |
| `GetInlinePolicyForPermissionSet` | Returns the inline IAM policy attached to a permission set, or an empty string when none is attached. |
| `GetPermissionsBoundaryForPermissionSet` | Returns the AWS managed or customer managed IAM policy configured as a permission-set permissions boundary. |
| `PutInlinePolicyToPermissionSet` | Creates or replaces the inline policy. |
| `PutPermissionsBoundaryToPermissionSet` | Attaches an AWS managed or customer managed IAM policy as the permissions boundary for a permission set. |
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
| `ListAccountAssignmentCreationStatus` | Lists account-assignment creation request metadata with optional operation-status filtering and AWS-compatible pagination. |
| `DescribeAccountAssignmentDeletionStatus` | Describes a persisted account-assignment deletion request by its AWS-compatible UUID request identifier. |
| `ListAccountAssignmentDeletionStatus` | Lists account-assignment deletion request metadata with optional operation-status filtering and AWS-compatible pagination. |
<!-- floci:actions:end -->

State is isolated by caller account through Floci storage.

## AWS-compatible failures and state

Permission-set names, ARNs, session durations, managed-policy ARNs, inline policies, account IDs, principal types, pagination, and duplicate assignments are validated before state is changed. Missing resources return `ResourceNotFoundException`; duplicate or incompatible state returns `ConflictException`; invalid input returns `ValidationException`; enforced local limits return `ServiceQuotaExceededException`.

Account-assignment creation returns an operation record that can be read with `DescribeAccountAssignmentCreationStatus`. Provider-side `InternalServerException` and `ThrottlingException` are part of the AWS model but are not injected artificially by Floci.

See the [AWS SSO Admin API Reference](https://docs.aws.amazon.com/singlesignon/latest/APIReference/welcome.html).
