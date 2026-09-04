# IAM Identity Center (SSO Admin)

Floci supports the JSON 1.1 SSO Admin operations used to manage IAM Identity Center permission sets and account assignments locally.

## Supported operations

`ListInstances`, `ListPermissionSets`, `CreatePermissionSet`, `DescribePermissionSet`, `UpdatePermissionSet`, `ListManagedPoliciesInPermissionSet`, `AttachManagedPolicyToPermissionSet`, `DetachManagedPolicyFromPermissionSet`, `DeleteInlinePolicyFromPermissionSet`, `PutInlinePolicyToPermissionSet`, `ListAccountAssignments`, `CreateAccountAssignment`, and `DescribeAccountAssignmentCreationStatus`.

State is isolated by caller account through Floci storage.
