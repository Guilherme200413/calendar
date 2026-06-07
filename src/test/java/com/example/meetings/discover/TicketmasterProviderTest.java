package com.example.meetings.discover;

import com.xebialabs.restito.server.StubServer;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.builder.verify.VerifyHttp.verifyHttp;
import static com.xebialabs.restito.semantics.Action.*;
import static com.xebialabs.restito.semantics.Condition.*;
import static org.junit.jupiter.api.Assertions.*;

public class TicketmasterProviderTest {

    private StubServer server;
    private TicketmasterProvider provider;

    private static final String RESPONSE_ONE_EVENT = """
            {
              "_embedded": {
                "events": [{
                  "id": "tm1",
                  "name": "Concert",
                  "url": "http://ticket.com/tm1",
                  "info": "Great show",
                  "dates": { "start": { "dateTime": "2099-06-10T20:00:00Z" } },
                  "_embedded": { "venues": [{ "name": "Altice Arena" }] }
                }]
              }
            }
            """;

    @BeforeEach
    void setUp() {
        server = new StubServer().run();
        provider = new TicketmasterProvider(
                "test-api-key", "PT",
                "http://localhost:" + server.getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void isConfigured_withApiKey_returnsTrue() {
        assertTrue(provider.isConfigured());
    }

    @Test
    void isConfigured_emptyApiKey_returnsFalse() {
        TicketmasterProvider unconfigured = new TicketmasterProvider(
                "", "PT", "http://localhost:" + server.getPort());
        assertFalse(unconfigured.isConfigured());
    }

    @Test
    void search_returnsEvents() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events.json"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(RESPONSE_ONE_EVENT));

        List<DiscoveredEvent> results = provider.search("concert");

        assertEquals(1, results.size());
        assertEquals("Concert", results.get(0).title());
        assertEquals("Altice Arena", results.get(0).venue());
    }

    @Test
    void search_sendsApiKeyAndKeyword() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events.json"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(RESPONSE_ONE_EVENT));

        provider.search("concert");

        verifyHttp(server).once(
                method(Method.GET),
                startsWithUri("/events.json"),
                parameter("apikey", "test-api-key"),
                parameter("keyword", "concert")
        );
    }

    @Test
    void search_serverError_returnsEmptyList() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events.json"))
                .then(status(HttpStatus.INTERNAL_SERVER_ERROR_500));

        List<DiscoveredEvent> results = provider.search("concert");

        assertTrue(results.isEmpty());
    }

    @Test
    void search_eventWithoutDateTime_isSkipped() {
        String noDate = """
                {
                  "_embedded": {
                    "events": [{
                      "id": "tm2", "name": "TBA Concert", "url": "http://ticket.com/tm2",
                      "dates": { "start": {} }
                    }]
                  }
                }
                """;
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events.json"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(noDate));

        List<DiscoveredEvent> results = provider.search("concert");

        assertTrue(results.isEmpty());
    }

    @Test
    void search_unconfigured_neverContactsServer() {
        TicketmasterProvider unconfigured = new TicketmasterProvider(
                "", "PT", "http://localhost:" + server.getPort());

        unconfigured.search("concert");

        assertTrue(server.getCalls().isEmpty());
    }
}