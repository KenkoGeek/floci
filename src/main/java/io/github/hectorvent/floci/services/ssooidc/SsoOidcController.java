package io.github.hectorvent.floci.services.ssooidc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.services.ssooidc.model.RegisteredClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
