# AWS IAM Identity Center Access Portal

**Protocol:** REST JSON

**Endpoint:** `http://localhost:4566`

Floci emulates the IAM Identity Center access portal API used by authenticated workforce users to discover their assigned AWS accounts and roles and obtain local role credentials.

## Supported Actions

<!-- floci:actions:start -->
| Action | Description |
| --- | --- |
| `ListAccounts` | Lists the AWS accounts effectively assigned to the user represented by the OIDC access token, including assignments inherited through Identity Store groups. |
<!-- floci:actions:end -->

`ListAccounts` accepts the bearer token in the AWS-compatible `x-amz-sso_bearer_token` header and supports the documented `max_result` and `next_token` query parameters. The access token must have been completed through Floci's local OIDC authorization flow with a user principal. Direct user assignments and group assignments are resolved from SSO Admin and Identity Store state. Account names and email addresses are populated from Organizations when that account exists in the owning organization; these fields are optional in the AWS `AccountInfo` model.

Portal errors use the AWS service exception names such as `UnauthorizedException` and `InvalidRequestException`, rather than the OAuth error envelope used by the OIDC API.

See the [IAM Identity Center Access Portal API Reference](https://docs.aws.amazon.com/singlesignon/latest/PortalAPIReference/Welcome.html).

## Configuration

The access portal shares IAM Identity Center's `sso` namespace and the SSO Admin enablement flag.

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_SSOADMIN_ENABLED` | `true` | Enable or disable the IAM Identity Center `sso` namespace, including access portal routes |
