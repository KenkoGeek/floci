# AWS IAM Identity Center OIDC

**Protocol:** REST JSON

**Endpoint:** `http://localhost:4566`

Floci emulates the IAM Identity Center OIDC registration and token endpoints used by public OAuth 2.0 clients.

## Supported Actions

<!-- floci:actions:start -->
| Action | Description |
| --- | --- |
| `RegisterClient` | Registers a public OIDC client, persists its generated client credentials, and returns local authorization and token endpoints. |
<!-- floci:actions:end -->

`RegisterClient` supports the authorization-code, device-code, and refresh-token grant identifiers documented by AWS. Client registrations are persisted so later device authorization and token operations can authenticate the generated client ID and secret.

OIDC failures use the AWS response shape with `error` and `error_description`. The local emulator assigns a 90-day client-secret lifetime; AWS documents the expiration timestamp but does not publish a fixed lifetime for this operation.

See the [IAM Identity Center OIDC API Reference](https://docs.aws.amazon.com/singlesignon/latest/OIDCAPIReference/Welcome.html).

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_SSOOIDC_ENABLED` | `true` | Enable or disable IAM Identity Center OIDC |
