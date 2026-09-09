package com.floci.test;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.marketplaceagreement.MarketplaceAgreementClient;
import software.amazon.awssdk.services.marketplacecatalog.MarketplaceCatalogClient;
import software.amazon.awssdk.services.marketplacedeployment.MarketplaceDeploymentClient;
import software.amazon.awssdk.services.marketplacediscovery.MarketplaceDiscoveryClient;
import software.amazon.awssdk.services.marketplaceentitlement.MarketplaceEntitlementClient;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacereporting.MarketplaceReportingClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceTest {

    @Test
    void marketplaceApiFamiliesUseAwsSdkWireContracts() {
        try (MarketplaceCatalogClient catalog = TestFixtures.marketplaceCatalogClient();
             MarketplaceAgreementClient agreement = TestFixtures.marketplaceAgreementClient();
             MarketplaceEntitlementClient entitlement = TestFixtures.marketplaceEntitlementClient();
             MarketplaceDeploymentClient deployment = TestFixtures.marketplaceDeploymentClient();
             MarketplaceDiscoveryClient discovery = TestFixtures.marketplaceDiscoveryClient();
             MarketplaceReportingClient reporting = TestFixtures.marketplaceReportingClient();
             MarketplaceMeteringClient metering = TestFixtures.marketplaceMeteringClient()) {

            var entities = catalog.listEntities(request -> request
                    .catalog("AWSMarketplace")
                    .entityType("SaaSProduct"));
            assertNotNull(entities.entitySummaryList());

            var agreements = agreement.searchAgreements(request -> { });
            assertNotNull(agreements.agreementViewSummaries());

            var entitlements = entitlement.getEntitlements(request -> request
                    .productCode("product-local"));
            assertNotNull(entitlements.entitlements());
            assertTrue(entitlements.entitlements().isEmpty());

            var deploymentParameter = deployment.putDeploymentParameter(request -> request
                    .catalog("AWSMarketplace")
                    .productId("prod-sdk-compat")
                    .agreementId("agr-sdk-compat")
                    .deploymentParameter(parameter -> parameter
                            .name("ApiKey")
                            .secretString("local-secret"))
                    .tags(java.util.Map.of("suite", "sdk-compat")));
            assertNotNull(deploymentParameter.deploymentParameterId());
            assertNotNull(deploymentParameter.resourceArn());
            assertEquals("sdk-compat", deploymentParameter.tags().get("suite"));

            var listings = discovery.searchListings(request -> request.searchText("sdk-compat"));
            assertTrue(listings.totalResults() >= 0);
            assertNotNull(listings.listingSummaries());

            String dashboardArn = "arn:aws:aws-marketplace::000000000000:AWSMarketplace/ReportingData/Agreement_V1/Dashboard/AgreementSummary_V1";
            var dashboard = reporting.getBuyerDashboard(request -> request
                    .dashboardIdentifier(dashboardArn)
                    .embeddingDomains("https://example.com"));
            assertEquals(dashboardArn, dashboard.dashboardIdentifier());
            assertFalse(dashboard.embedUrl().isBlank());

            var customer = metering.resolveCustomer(request -> request
                    .registrationToken("local-registration-token"));
            assertNotNull(customer.customerAWSAccountId());
            assertNotNull(customer.licenseArn());
        }
    }
}
