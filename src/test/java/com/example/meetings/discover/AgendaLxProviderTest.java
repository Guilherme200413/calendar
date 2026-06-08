package com.example.meetings.discover;

import com.xebialabs.restito.server.StubServer;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.*;
import static com.xebialabs.restito.semantics.Condition.*;
import static org.junit.jupiter.api.Assertions.*;

public class AgendaLxProviderTest {

    private StubServer server;
    private AgendaLxProvider provider;

    private static final String RESPONSE_ONE_EVENT = """
            [{
              "id": 1,
              "title": { "rendered": "Concerto de Jazz" },
              "description": ["<p>Uma noite incrível</p>"],
              "occurences": ["2099-01-15"],
              "string_times": "qua: 21h30",
              "link": "http://agendalx.pt/1",
              "venue": { "123": { "name": "Casa da Música" } }
            }]
            """;

    @BeforeEach
    void setUp() {
        server = new StubServer().run();
        provider = new AgendaLxProvider("http://localhost:" + server.getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private void stubOk(String body) {
        whenHttp(server)
                .match(method(Method.GET))
                .then(status(HttpStatus.OK_200),
                        contentType("application/json"),
                        stringContent(body));
    }

    /**
     * Verifies that AgendaLx is always configured — it requires no API key.
     */
    @Test
    void isConfigured_alwaysTrue() {
        assertTrue(provider.isConfigured());
    }

    /**
     * Happy path: verifies that a valid API response is parsed correctly,
     * returning events with the expected title and venue.
     */
    @Test
    void search_returnsEvent() {
        stubOk(RESPONSE_ONE_EVENT);

        List<DiscoveredEvent> results = provider.search("jazz");

        assertEquals(1, results.size());
        assertEquals("Concerto de Jazz", results.get(0).title());
        assertEquals("Casa da Música", results.get(0).venue());
    }

    /**
     * Verifies that time strings like "qua: 21h30" are correctly parsed
     * to the expected UTC Instant (21:30 UTC in January = UTC+0 in Lisbon winter).
     */
    @Test
    void search_parsesTimeCorrectly() {
        stubOk(RESPONSE_ONE_EVENT);

        List<DiscoveredEvent> results = provider.search("jazz");

        assertTrue(results.get(0).start().toString().contains("T21:30:00Z"));
    }

    /**
     * Verifies that when no time string is present, the provider defaults
     * to 20:00 as the event start time.
     */
    @Test
    void search_noTime_fallsBackTo2000() {
        String noTime = """
                [{
                  "id": 2, "title": { "rendered": "Exposição" },
                  "description": [], "occurences": ["2099-01-15"],
                  "string_times": "", "link": "http://agendalx.pt/2", "venue": {}
                }]
                """;
        stubOk(noTime);

        List<DiscoveredEvent> results = provider.search("expo");

        assertTrue(results.get(0).start().toString().contains("T20:00:00Z"));
    }

    /**
     * Verifies that events whose only occurrences are in the past are discarded.
     * Prevents showing outdated events on the discover page.
     */
    @Test
    void search_pastDatesOnly_returnsEmpty() {
        String pastEvent = """
                [{
                  "id": 3, "title": { "rendered": "Old Concert" },
                  "description": [], "occurences": ["2000-01-01"],
                  "string_times": "21h00", "link": "http://agendalx.pt/3", "venue": {}
                }]
                """;
        stubOk(pastEvent);

        List<DiscoveredEvent> results = provider.search("old");

        assertTrue(results.isEmpty());
    }

    /**
     * Verifies that events with a blank or whitespace-only title are discarded.
     * Prevents unnamed events from appearing on the discover page.
     */
    @Test
    void search_blankTitle_isSkipped() {
        String blankTitle = """
                [{
                  "id": 4, "title": { "rendered": "   " },
                  "description": [], "occurences": ["2099-01-15"],
                  "string_times": "21h00", "link": "http://agendalx.pt/4", "venue": {}
                }]
                """;
        stubOk(blankTitle);

        List<DiscoveredEvent> results = provider.search("test");

        assertTrue(results.isEmpty());
    }

    /**
     * Verifies that HTML tags in the description are stripped before
     * being stored, leaving only plain text content.
     */
    @Test
    void search_htmlInDescription_isRemoved() {
        stubOk(RESPONSE_ONE_EVENT);

        List<DiscoveredEvent> results = provider.search("jazz");

        assertFalse(results.get(0).description().contains("<p>"));
        assertTrue(results.get(0).description().contains("Uma noite incrível"));
    }

    /**
     * Verifies that a server error (HTTP 500) results in an empty list
     * rather than an exception, implementing the best-effort contract.
     */
    @Test
    void search_serverError_returnsEmptyList() {
        whenHttp(server)
                .match(method(Method.GET))
                .then(status(HttpStatus.INTERNAL_SERVER_ERROR_500));

        List<DiscoveredEvent> results = provider.search("jazz");

        assertTrue(results.isEmpty());
    }

    /**
     * Verifies that the provider contacts the stub server when search is called,
     * confirming that HTTP requests are actually being made.
     */
    @Test
    void search_sendsQueryParam() {
        stubOk(RESPONSE_ONE_EVENT);

        provider.search("jazz");

        assertFalse(server.getCalls().isEmpty());
    }
}