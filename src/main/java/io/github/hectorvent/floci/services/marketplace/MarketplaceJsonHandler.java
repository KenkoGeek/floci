package io.github.hectorvent.floci.services.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class MarketplaceJsonHandler {
    private final MarketplaceAgreementService agreementService;

    @Inject
    public MarketplaceJsonHandler(MarketplaceAgreementService agreementService) {
        this.agreementService = agreementService;
    }

    public Response handle(String action, JsonNode request, String region) {
        JsonNode response = agreementService.handle(action, request, region);
        return response == null ? null : Response.ok(response).build();
    }
}
