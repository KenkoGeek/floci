package io.github.hectorvent.floci.services.ssooidc.model;

public record AuthorizationCode(
        String code,
        String clientId,
        String redirectUri,
        String codeChallenge,
        long expiresAtEpochSeconds,
        String principalId
) {}
