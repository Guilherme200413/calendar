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

    @Test
    void isConfigured_withClientId_returnsTrue() {
        assertTrue(provider.isConfigured());
    }

    @Test
    void isConfigured_emptyClientId_returnsFalse() {
        SeatGeekProvider unconfigured = new SeatGeekProvider(
                "", "http://localhost:" + server.getPort());
        assertFalse(unconfigured.isConfigured());
    }

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

    @Test
    void search_serverError_returnsEmptyList() {
        whenHttp(server)
                .match(method(Method.GET), startsWithUri("/events"))
                .then(status(HttpStatus.INTERNAL_SERVER_ERROR_500));

        List<DiscoveredEvent> results = provider.search("rock");

        assertTrue(results.isEmpty());
    }

    @Test
    void search_unconfigured_neverContactsServer() {
        SeatGeekProvider unconfigured = new SeatGeekProvider(
                "", "http://localhost:" + server.getPort());

        unconfigured.search("rock");

        assertTrue(server.getCalls().isEmpty());
    }

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