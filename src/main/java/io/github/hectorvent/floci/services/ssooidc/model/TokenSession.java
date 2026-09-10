package io.github.hectorvent.floci.services.ssooidc.model;

import java.util.List;

public record TokenSession(
        String accessToken,
        String refreshToken,
        String clientId,
        List<String> scopes,
        long accessTokenExpiresAtEpochSeconds,
        long refreshTokenExpiresAtEpochSeconds
) {
    public TokenSession {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
