package com.example.meetings.integration.external;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.SeatGeekProvider;
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

public class SeatGeekProviderTest {

    private StubServer server;
    private SeatGeekProvider provider;

    private static final String RESPONSE_ONE_EVENT = """
            {
              "events": [{
                "id": 42,
                "title": "Rock Night",
                "short_title": "Rock",
                "datetime_utc": "2099-06-10T20:00:00",
                "url": "http://seatgeek.com/42",
                "description": "Great night",
                "venue": { "name": "Campo Pequeno" }
              }]
            }
            """;

    @BeforeEach
    void setUp() {
        server = new StubServer().run();
        provider = new SeatGeekProvider(
                "test-client-id",
                "http://localhost:" + server.getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    /**
     * Verifies that a provider with a non-empty client ID reports itself as configured.
     */
    @Test
    void isConfigured_withClientId_returnsTrue() {
        assertTrue(provider.isConfigured());
    }

    /**
     * Verifies that a provider with an empty client ID reports itself as not configured.
     */
    @Test
    void isConfigured_emptyClientId_returnsFalse() {
        SeatGeekProvider unconfigured = new SeatGeekProvider(
                "", "http://localhost:" + server.getPort());
        assertFalse(unconfigured.isConfigured());
    }

    /**
     * Happy path: verifies that a valid API response is parsed correctly,
     * returning events with the expected title and venue.
     */
    @Test
    void search_returnsEvents() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(RESPONSE_ONE_EVENT));

        List<DiscoveredEvent> results = provider.search("rock");

        assertEquals(1, results.size());
        assertEquals("Rock Night", results.get(0).title());
        assertEquals("Campo Pequeno", results.get(0).venue());
    }

    /**
     * Verifies that the provider sends the correct query parameters:
     * the client ID and the search query. This validates the HTTP contract.
     */
    @Test
    void search_sendsClientIdAndQuery() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(RESPONSE_ONE_EVENT));

        provider.search("rock");

        verifyHttp(server).once(
                method(Method.GET),
                startsWithUri("/events"),
                parameter("client_id", "test-client-id"),
                parameter("q", "rock")
        );
    }

    /**
     * Verifies that when a title is absent, the provider falls back to short_title.
     * Prevents events from appearing with null or empty titles.
     */
    @Test
    void search_titleFallsBackToShortTitle() {
        String noTitle = """
                {
                  "events": [{
                    "id": 43, "short_title": "Rock",
                    "datetime_utc": "2099-06-10T20:00:00",
                    "url": "http://seatgeek.com/43"
                  }]
                }
                """;
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(noTitle));

        List<DiscoveredEvent> results = provider.search("rock");

        assertEquals("Rock", results.get(0).title());
    }

    /**
     * Verifies that a server error (HTTP 500) results in an empty list
     * rather than an exception, implementing the best-effort contract.
     */
    @Test
    void search_serverError_returnsEmptyList() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events"))
                .then(status(HttpStatus.INTERNAL_SERVER_ERROR_500));

        List<DiscoveredEvent> results = provider.search("rock");

        assertTrue(results.isEmpty());
    }

    /**
     * Verifies that an unconfigured provider never contacts the server.
     */
    @Test
    void search_unconfigured_neverContactsServer() {
        SeatGeekProvider unconfigured = new SeatGeekProvider(
                "", "http://localhost:" + server.getPort());

        unconfigured.search("rock");

        assertTrue(server.getCalls().isEmpty());
    }

    /**
     * Verifies that events without a datetime_utc field are discarded.
     * Including undated events would create meetings with null start times.
     */
    @Test
    void search_eventWithoutDatetime_isSkipped() {
        String noDate = """
                {
                  "events": [{
                    "id": 44, "title": "TBA",
                    "url": "http://seatgeek.com/44"
                  }]
                }
                """;
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events"))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(noDate));

        List<DiscoveredEvent> results = provider.search("rock");

        assertTrue(results.isEmpty());
    }
}