package com.cloudrec.service.pricing;

import com.cloudrec.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AzurePricingClientTest {

    private StubHttpServer server;
    private AzurePricingClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        client = new AzurePricingClient("westeurope", server.url("/prices"), false);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void convertsHourlyPriceToMonthlyAndSkipsWindowsSkus() {
        server.respond(200, """
                {"Items": [
                  {"productName": "Virtual Machines BS Series Windows", "retailPrice": 0.09, "unitOfMeasure": "1 Hour"},
                  {"productName": "Virtual Machines BS Series", "retailPrice": 0.05, "unitOfMeasure": "1 Hour"}
                ]}
                """);

        assertThat(client.getUnitPrice("Virtual Machines")).hasValueSatisfying(
                p -> assertThat(p).isCloseTo(36.5, within(0.001)));
    }

    @Test
    void queriesConfiguredRegionWithServiceFilter() {
        server.respond(200, "{\"Items\": [{\"productName\": \"SQL\", \"retailPrice\": 0.02, \"unitOfMeasure\": \"1 Hour\"}]}");

        client.getUnitPrice("SQL Database");

        String uri = URLDecoder.decode(server.requests().get(0).uri(), StandardCharsets.UTF_8);
        assertThat(uri).contains("serviceName eq 'SQL Database'").contains("armRegionName eq 'westeurope'");
    }

    @Test
    void cachesPricesBetweenCalls() {
        server.respond(200, "{\"Items\": [{\"productName\": \"Storage\", \"retailPrice\": 0.0184, \"unitOfMeasure\": \"1 GB/Month\"}]}");

        assertThat(client.getUnitPrice("Blob Storage")).contains(0.0184);
        assertThat(client.getUnitPrice("Blob Storage")).contains(0.0184);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void emptyWhenApiFails() {
        server.respond(500, "");

        assertThat(client.getUnitPrice("Virtual Machines")).isEmpty();
    }

    @Test
    void emptyWhenNoUsablePrice() {
        server.respond(200, "{\"Items\": [{\"productName\": \"X\", \"retailPrice\": 0, \"unitOfMeasure\": \"1 Hour\"}]}");

        assertThat(client.getUnitPrice("Virtual Machines")).isEmpty();
    }

    @Test
    void emptyForUnknownServiceWithoutCallingApi() {
        assertThat(client.supports("Quantum Computer")).isFalse();
        assertThat(client.supports("Functions")).as("no meter matches its per-request unit").isFalse();
        assertThat(client.supports("Virtual Machines")).isTrue();
        assertThat(client.getUnitPrice("Quantum Computer")).isEmpty();
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void toUnitPriceHandlesEachUnit() {
        assertThat(AzurePricingClient.toUnitPrice(0.1, "1 Hour")).isCloseTo(73.0, within(0.001));
        assertThat(AzurePricingClient.toUnitPrice(1.0, "1/Day")).isEqualTo(30.0);
        assertThat(AzurePricingClient.toUnitPrice(0.02, "1 GB/Month")).isEqualTo(0.02);
        assertThat(AzurePricingClient.toUnitPrice(50.0, "1/Month")).isEqualTo(50.0);
        assertThat(AzurePricingClient.toUnitPrice(0.081, "1 GB")).isEqualTo(0.081);
        assertThat(AzurePricingClient.toUnitPrice(1.0, "10K Transactions")).isNull();
    }
}
