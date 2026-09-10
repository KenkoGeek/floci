package io.github.hectorvent.floci.services.ssooidc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.services.ssooidc.model.AuthorizationCode;
import io.github.hectorvent.floci.services.ssooidc.model.DeviceAuthorization;
import io.github.hectorvent.floci.services.ssooidc.model.RegisteredClient;
import io.github.hectorvent.floci.services.ssooidc.model.TokenSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class SsoOidcController {
    private final SsoOidcService service;
    private final ObjectMapper objectMapper;

    @Inject
    public SsoOidcController(SsoOidcService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @POST
    @Path("/client/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerClient(String body) {
        try {
            RegisteredClient client = service.registerClient(readTree(body));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("clientId", client.clientId());
            response.put("clientSecret", client.clientSecret());
            response.put("clientIdIssuedAt", client.clientIdIssuedAt());
            response.put("clientSecretExpiresAt", client.clientSecretExpiresAt());
            response.put("authorizationEndpoint", service.authorizationEndpoint());
            response.put("tokenEndpoint", service.tokenEndpoint());
            return Response.ok(response).build();
        } catch (SsoOidcException e) {
            return oidcError(e.status(), e.error(), e.getMessage());
        } catch (RuntimeException e) {
            return oidcError(500, "server_error", "The request could not be processed");
        }
    }

    @POST
    @Path("/device_authorization")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response startDeviceAuthorization(String body) {
        try {
            DeviceAuthorization authorization = service.startDeviceAuthorization(readTree(body));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("deviceCode", authorization.deviceCode());
            response.put("userCode", authorization.userCode());
            response.put("verificationUri", service.verificationUri());
            response.put("verificationUriComplete", service.verificationUriComplete(authorization));
            response.put("expiresIn", (int) (authorization.expiresAtEpochSeconds()
                    - System.currentTimeMillis() / 1000L));
            response.put("interval", authorization.intervalSeconds());
            return Response.ok(response).build();
        } catch (SsoOidcException e) {
            return oidcError(e.status(), e.error(), e.getMessage());
        } catch (RuntimeException e) {
            return oidcError(500, "server_error", "The request could not be processed");
        }
    }

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createToken(String body) {
        try {
            TokenSession session = service.createToken(readTree(body));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("accessToken", session.accessToken());
            response.put("refreshToken", session.refreshToken());
            response.put("expiresIn", (int) (session.accessTokenExpiresAtEpochSeconds()
                    - System.currentTimeMillis() / 1000L));
            response.put("tokenType", "Bearer");
            return Response.ok(response).build();
        } catch (SsoOidcException e) {
            return oidcError(e.status(), e.error(), e.getMessage());
        } catch (RuntimeException e) {
            return oidcError(500, "server_error", "The request could not be processed");
        }
    }

    @GET
    @Path("/device")
    public Response authorizeDevice(@QueryParam("user_code") String userCode) {
        try {
            DeviceAuthorization authorization = service.authorizeDevice(userCode);
            return Response.ok(objectMapper.createObjectNode()
                    .put("status", "authorized")
                    .put("userCode", authorization.userCode())).build();
        } catch (SsoOidcException e) {
            return oidcError(e.status(), e.error(), e.getMessage());
        }
    }

    @GET
    @Path("/authorize")
    public Response authorizeCode(@QueryParam("response_type") String responseType,
                                  @QueryParam("client_id") String clientId,
                                  @QueryParam("redirect_uri") String redirectUri,
                                  @QueryParam("code_challenge") String codeChallenge,
                                  @QueryParam("code_challenge_method") String codeChallengeMethod,
                                  @QueryParam("state") String state) {
        try {
            if (!"code".equals(responseType)) {
                throw new SsoOidcException("invalid_request", "response_type must be code", 400);
            }
            if (!"S256".equals(codeChallengeMethod)) {
                throw new SsoOidcException("invalid_request", "code_challenge_method must be S256", 400);
            }
            AuthorizationCode authorization = service.createAuthorizationCode(clientId, redirectUri, codeChallenge);
            String separator = redirectUri.contains("?") ? "&" : "?";
            String location = redirectUri + separator + "code="
                    + URLEncoder.encode(authorization.code(), StandardCharsets.UTF_8);
            if (state != null) {
                location += "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
            }
            return Response.seeOther(URI.create(location)).build();
        } catch (SsoOidcException e) {
            return oidcError(e.status(), e.error(), e.getMessage());
        }
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception e) {
            throw new SsoOidcException("invalid_request", "Request body must be valid JSON", 400);
        }
    }

    private Response oidcError(int status, String error, String description) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", error);
        response.put("error_description", description);
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(response).build();
    }
}
