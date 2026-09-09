# Amazon Bedrock

**Protocol:** REST JSON
**Signing name:** `bedrock`

Floci emulates the Amazon Bedrock control-plane API separately from the existing Bedrock Runtime data plane. The control plane is account-aware and persists mutable emulator state through `StorageFactory`.

The implementation follows the Amazon Bedrock API Reference and the AWS SDK for Java v2 Bedrock service model. Unsupported control-plane operations continue to return the normal unmatched-operation response instead of synthetic success.

## Supported Operations

| Operation | Endpoint | Notes |
|-----------|----------|-------|
| `GetUseCaseForModelAccess` | `GET /use-case-for-model-access` | Returns the account model-access use-case form data or `ResourceNotFoundException` when it has not been configured. |

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_BEDROCK_ENABLED` | `true` | Enable or disable the Bedrock control plane. |

## Compatibility notes

Bedrock control-plane and Bedrock Runtime requests both use the `bedrock` SigV4 signing scope in AWS SDK service models. Floci registers each JAX-RS controller as its own service resource class so service enablement and REST JSON error handling remain route-specific while both signing forms are recognized.
