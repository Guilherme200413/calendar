package com.example.meetings.discover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiscoveryServiceTest {

    @Mock
    private EventProvider providerA;
    @Mock
    private EventProvider providerB;

    private DiscoveryService discoveryService;

    @Test
    void search_nullQuery_returnsEmpty() {
        discoveryService = new DiscoveryService(List.of(providerA));

        List<DiscoveredEvent> result = discoveryService.search(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(providerA);
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        discoveryService = new DiscoveryService(List.of(providerA));

        List<DiscoveredEvent> result = discoveryService.search("   ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(providerA);
    }

    @Test
    void search_unconfiguredProvider_isSkipped() {
        when(providerA.isConfigured()).thenReturn(false);
        discoveryService = new DiscoveryService(List.of(providerA));

        List<DiscoveredEvent> result = discoveryService.search("concert");

        assertTrue(result.isEmpty());
        verify(providerA, never()).search(any());
    }

    @Test
    void search_configuredProvider_returnsResults() {
        DiscoveredEvent event = makeEvent("providerA", "1", "Concert", "http://a.com/1");
        when(providerA.isConfigured()).thenReturn(true);
        when(providerA.search("concert")).thenReturn(List.of(event));
        discoveryService = new DiscoveryService(List.of(providerA));

        List<DiscoveredEvent> result = discoveryService.search("concert");

        assertEquals(1, result.size());
        assertEquals("Concert", result.get(0).title());
    }

    @Test
    void search_deduplicatesByUrl() {
        DiscoveredEvent e1 = makeEvent("providerA", "1", "Concert", "http://same.com");
        DiscoveredEvent e2 = makeEvent("providerB", "2", "Concert", "http://same.com");
        when(providerA.isConfigured()).thenReturn(true);
        when(providerB.isConfigured()).thenReturn(true);
        when(providerA.search("concert")).thenReturn(List.of(e1));
        when(providerB.search("concert")).thenReturn(List.of(e2));
        discoveryService = new DiscoveryService(List.of(providerA, providerB));

        List<DiscoveredEvent> result = discoveryService.search("concert");

        assertEquals(1, result.size());
    }

    @Test
    void search_sortedByStartTime() {
        Instant t1 = Instant.parse("2025-07-01T10:00:00Z");
        Instant t2 = Instant.parse("2025-06-01T10:00:00Z");
        DiscoveredEvent later   = new DiscoveredEvent("A", "1", "Later",   null, t1, null, "http://a.com/1", null);
        DiscoveredEvent earlier = new DiscoveredEvent("A", "2", "Earlier", null, t2, null, "http://a.com/2", null);
        when(providerA.isConfigured()).thenReturn(true);
        when(providerA.search("music")).thenReturn(List.of(later, earlier));
        discoveryService = new DiscoveryService(List.of(providerA));

        List<DiscoveredEvent> result = discoveryService.search("music");

        assertEquals("Earlier", result.get(0).title());
        assertEquals("Later",   result.get(1).title());
    }

    @Test
    void search_mergesResultsFromMultipleProviders() {
        DiscoveredEvent e1 = makeEvent("providerA", "1", "Event A", "http://a.com/1");
        DiscoveredEvent e2 = makeEvent("providerB", "2", "Event B", "http://b.com/2");
        when(providerA.isConfigured()).thenReturn(true);
        when(providerB.isConfigured()).thenReturn(true);
        when(providerA.search("music")).thenReturn(List.of(e1));
        when(providerB.search("music")).thenReturn(List.of(e2));
        discoveryService = new DiscoveryService(List.of(providerA, providerB));

        List<DiscoveredEvent> result = discoveryService.search("music");

        assertEquals(2, result.size());
    }

    @Test
    void search_deduplicatesBySourceAndIdWhenNoUrl() {
        DiscoveredEvent e1 = new DiscoveredEvent("providerA", "42", "Show", null, Instant.now(), null, null, null);
        DiscoveredEvent e2 = new DiscoveredEvent("providerA", "42", "Show", null, Instant.now(), null, null, null);
        when(providerA.isConfigured()).thenReturn(true);
        when(providerA.search("show")).thenReturn(List.of(e1, e2));
        discoveryService = new DiscoveryService(List.of(providerA));

        List<DiscoveredEvent> result = discoveryService.search("show");

        assertEquals(1, result.size());
    }

    @Test
    void providers_returnsConfiguredList() {
        discoveryService = new DiscoveryService(List.of(providerA, providerB));

        assertEquals(2, discoveryService.providers().size());
    }

    private DiscoveredEvent makeEvent(String source, String id, String title, String url) {
        return new DiscoveredEvent(source, id, title, null, Instant.now(), null, url, null);
    }
}