package io.github.hectorvent.floci.services.marketplace;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MarketplaceDiscoveryIntegrationTest {
    @BeforeAll static void configure() { RestAssuredJsonUtils.configureAwsContentTypes(); }

    @Test
    void catalogProductIsDiscoverableThroughBuyerApis() {
        String productId = createProduct("Discovery product");
        given().contentType("application/json").header("Authorization", auth())
                .body("{\"productId\":\"" + productId + "\"}")
                .post("/2026-02-05/getProduct").then().statusCode(200)
                .body("productId", equalTo(productId)).body("productName", equalTo("Discovery product"));
        given().contentType("application/json").header("Authorization", auth())
                .body("{\"listingId\":\"" + productId + "\"}")
                .post("/2026-02-05/getListing").then().statusCode(200)
                .body("listingId", equalTo(productId)).body("publisher.displayName", notNullValue());
        given().contentType("application/json").header("Authorization", auth())
                .body("{\"searchText\":\"Discovery product\"}")
                .post("/2026-02-05/searchListings").then().statusCode(200)
                .body("totalResults", greaterThanOrEqualTo(1));
    }

    @Test
    void unsupportedDiscoveryRegionIsRejected() {
        given().contentType("application/json").header("Authorization", auth("ap-southeast-2"))
                .body("{\"searchText\":\"anything\"}")
                .post("/2026-02-05/searchListings").then().statusCode(400);
    }

    private static String createProduct(String title) {
        String body = "{\"Catalog\":\"AWSMarketplace\",\"ChangeSet\":[{\"ChangeType\":\"CreateProduct\",\"Entity\":{\"Type\":\"SaaSProduct@1.0\",\"Identifier\":\"@1\"},\"DetailsDocument\":{\"ProductTitle\":\"" + title + "\",\"ShortDescription\":\"Local product\",\"FulfillmentType\":\"SAAS\"}}]}";
        String changeSetId = given().contentType("application/json").header("Authorization", auth()).body(body)
                .post("/StartChangeSet").then().statusCode(200).extract().path("ChangeSetId");
        return given().header("Authorization", auth())
                .get("/DescribeChangeSet?catalog=AWSMarketplace&changeSetId=" + changeSetId)
                .then().statusCode(200).extract().path("ChangeSet[0].Entity.Identifier");
    }

    private static String auth() { return auth("us-east-1"); }
    private static String auth(String region) { return "AWS4-HMAC-SHA256 Credential=000000000000/20260908/" + region + "/aws-marketplace/aws4_request"; }
}
