# Identity Store

**Protocol:** JSON 1.1 (`X-Amz-Target: AWSIdentityStore.*`)

**Signing name:** `identitystore`

Floci emulates the AWS Identity Store management API used by IAM Identity Center. Identity store resources are keyed by `IdentityStoreId` rather than the caller account, matching AWS behavior that allows authorized member accounts to access the same identity store.

## Supported Actions

<!-- floci:actions:start -->
| Action | Description |
| --- | --- |
| `CreateGroup` | - |
| `DeleteGroup` | - |
| `DescribeGroup` | - |
| `GetGroupId` | - |
| `ListGroups` | - |
| `UpdateGroup` | - |
| `CreateUser` | - |
| `DeleteUser` | - |
| `DescribeUser` | - |
| `GetUserId` | - |
| `ListUsers` | - |
| `UpdateUser` | - |
| `CreateGroupMembership` | - |
| `DeleteGroupMembership` | - |
| `DescribeGroupMembership` | - |
| `GetGroupMembershipId` | - |
| `IsMemberInGroups` | - |
| `ListGroupMemberships` | - |
| `ListGroupMembershipsForMember` | - |
<!-- floci:actions:end -->

## Behavior

Groups, users, and group memberships are persisted through `StorageFactory` and implement the complete AWS SDK Java v2 Identity Store operation surface. New resource identifiers use the normal `1234567890-UUID` format for `d-1234567890` identity stores, while legacy UUID-form identity store identifiers use UUID resource identifiers.

`ListGroups`, `ListUsers`, `ListGroupMemberships`, and `ListGroupMembershipsForMember` support AWS-style pagination with a maximum `MaxResults` of 100. The deprecated `ListGroups` and `ListUsers` filters remain supported for SDK compatibility. `GetGroupId` and `GetUserId` support alternate identifiers, including the documented unique attribute paths and external identifiers when those values are present in the stored resource.

`UpdateGroup` and `UpdateUser` apply `AttributeOperation` updates, including removals when `AttributeValue` is omitted. User updates support nested attributes and the `aws:identitystore:enterprise` extension. User extensions are returned by `DescribeUser` and `ListUsers` only when requested through `Extensions`, as in AWS.

Deleting a user or group removes its related local group memberships so subsequent membership queries do not retain dangling references.

## IAM Identity Center SCIM

Floci also accepts IAM Identity Center SCIM v2 requests under `/{tenant_id}/scim/v2`. SCIM requests require the AWS-supported `Authorization: Bearer <token>` authentication shape. Because Floci does not provision real IAM Identity Center access tokens, any non-empty bearer token is accepted locally; the tenant still must resolve to an existing IAM Identity Center identity store.

`CreateGroup` is supported through `POST /{tenant_id}/scim/v2/Groups`. It requires `displayName`, accepts `externalId`, supports up to 100 user members in one request, returns the AWS SCIM `201` group representation, and persists the group and memberships into the same Identity Store state used by the JSON 1.1 API. A tenant ID beginning with a ten-character identity-store prefix resolves to `d-<prefix>`; legacy UUID-form identity stores may use the UUID directly. Invalid tenants and missing bearer authorization return SCIM `401` errors.

`CreateUser` is supported through `POST /{tenant_id}/scim/v2/Users`. It enforces the IAM Identity Center SCIM requirements for `givenName`, `familyName`, `userName`, and `displayName`; allows only one value for multi-value attributes such as `emails`, `addresses`, `phoneNumbers`, and `roles`; requires the email value to be primary; rejects `groups` during creation and the AWS-documented unsupported attributes/subattributes; and persists the resulting user into the shared Identity Store state. The response uses the AWS SCIM `201` user representation, including the enterprise extension when supplied.

`DeleteGroup` is supported through `DELETE /{tenant_id}/scim/v2/Groups/{id}` and returns HTTP `204` with an empty body. Deletion removes the shared Identity Store group and its local membership records. Missing groups return the SCIM-documented HTTP `404` `ResourceNotFoundException` mapping.

`DeleteUser` is supported through `DELETE /{tenant_id}/scim/v2/Users/{id}` and returns HTTP `204` with an empty body. Deletion removes the shared Identity Store user and any local group memberships that reference it. Missing users return HTTP `404`.

`GetGroup` is supported through `GET /{tenant_id}/scim/v2/Groups/{id}`. It returns the SCIM group representation and, matching IAM Identity Center, does not expand group members in this response. Missing groups return HTTP `404`.

SCIM validation failures use the standard `urn:ietf:params:scim:api:messages:2.0:Error` response shape. See the [IAM Identity Center SCIM implementation](https://docs.aws.amazon.com/singlesignon/latest/developerguide/what-is-scim.html) and [CreateGroup](https://docs.aws.amazon.com/singlesignon/latest/developerguide/creategroup.html) documentation.

## AWS-compatible failures

Floci validates identity store and resource identifier formats, filter shapes, pagination bounds, membership references, alternate-identifier unions, duplicate user/group names, duplicate memberships, reserved names, operation counts, and local user/group quotas. Deterministic failures use modeled AWS errors including `ValidationException`, `ConflictException`, `ResourceNotFoundException`, and `ServiceQuotaExceededException`.

AWS also models provider-side errors such as `AccessDeniedException`, `InternalServerException`, and `ThrottlingException`. Floci does not synthesize those failures without a request or emulator-state condition that causes them.

See the [AWS Identity Store API Reference](https://docs.aws.amazon.com/singlesignon/latest/IdentityStoreAPIReference/welcome.html).

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_IDENTITYSTORE_ENABLED` | `true` | Enable or disable Identity Store |
