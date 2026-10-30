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

        assertThat(client.getMonthlyPrice("Virtual Machines")).hasValueSatisfying(
                p -> assertThat(p).isCloseTo(36.5, within(0.001)));
    }

    @Test
    void queriesConfiguredRegionWithServiceFilter() {
        server.respond(200, "{\"Items\": [{\"productName\": \"SQL\", \"retailPrice\": 0.02, \"unitOfMeasure\": \"1 Hour\"}]}");

        client.getMonthlyPrice("SQL Database");

        String uri = URLDecoder.decode(server.requests().get(0).uri(), StandardCharsets.UTF_8);
        assertThat(uri).contains("serviceName eq 'SQL Database'").contains("armRegionName eq 'westeurope'");
    }

    @Test
    void cachesPricesBetweenCalls() {
        server.respond(200, "{\"Items\": [{\"productName\": \"Storage\", \"retailPrice\": 0.02, \"unitOfMeasure\": \"1 GB/Month\"}]}");

        assertThat(client.getMonthlyPrice("Blob Storage")).contains(2.0);
        assertThat(client.getMonthlyPrice("Blob Storage")).contains(2.0);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void emptyWhenApiFails() {
        server.respond(500, "");

        assertThat(client.getMonthlyPrice("Virtual Machines")).isEmpty();
    }

    @Test
    void emptyWhenNoUsablePrice() {
        server.respond(200, "{\"Items\": [{\"productName\": \"X\", \"retailPrice\": 0, \"unitOfMeasure\": \"1 Hour\"}]}");

        assertThat(client.getMonthlyPrice("Virtual Machines")).isEmpty();
    }

    @Test
    void emptyForUnknownServiceWithoutCallingApi() {
        assertThat(client.getMonthlyPrice("Quantum Computer")).isEmpty();
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void toMonthlyHandlesEachUnit() {
        assertThat(AzurePricingClient.toMonthly(0.1, "1 Hour")).isCloseTo(73.0, within(0.001));
        assertThat(AzurePricingClient.toMonthly(1.0, "1/Day")).isEqualTo(30.0);
        assertThat(AzurePricingClient.toMonthly(0.02, "1 GB/Month")).isCloseTo(2.0, within(0.001));
        assertThat(AzurePricingClient.toMonthly(50.0, "1/Month")).isEqualTo(50.0);
        assertThat(AzurePricingClient.toMonthly(0.01, "1 GB")).isCloseTo(1.0, within(0.001));
        assertThat(AzurePricingClient.toMonthly(1.0, "10K Transactions")).isNull();
    }
}
