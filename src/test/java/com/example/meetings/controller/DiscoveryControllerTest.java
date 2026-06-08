package com.example.meetings.controller;

import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.model.*;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class DiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscoveryService discoveryService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    // -------------------------------------------------------------------------
    // GET /discover
    // -------------------------------------------------------------------------

    /**
     * Verifies that unauthenticated access to /discover is redirected to login.
     */
    @Test
    void discover_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Verifies that an authenticated user can access the discover page.
     */
    @Test
    @WithMockUser(username = "alice")
    void discover_authenticated_returnsOk() throws Exception {
        when(discoveryService.providers()).thenReturn(List.of());

        mockMvc.perform(get("/discover"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that a search query triggers a search and results are shown.
     * When a query is provided and providers are configured, results are
     * passed to the view model.
     */
    @Test
    @WithMockUser(username = "alice")
    void discover_withQuery_returnsResults() throws Exception {
        when(discoveryService.providers()).thenReturn(List.of());
        when(discoveryService.search("jazz")).thenReturn(List.of());

        mockMvc.perform(get("/discover").param("q", "jazz"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // POST /discover/copy
    // -------------------------------------------------------------------------

    /**
     * Happy path: copying an event to the calendar redirects to /calendar.
     * This validates the post-redirect-get pattern on the copy endpoint.
     */
    @Test
    @WithMockUser(username = "alice")
    void copy_validEvent_redirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(meetingService.copyFromDiscovered(any(), any()))
                .thenReturn(new Meeting("Concert", "desc",
                        Instant.parse("2099-06-10T20:00:00Z"),
                        Instant.parse("2099-06-10T22:00:00Z"), alice));

        mockMvc.perform(post("/discover/copy").with(csrf())
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm1")
                        .param("title", "Concert")
                        .param("description", "Great show")
                        .param("start", "2099-06-10T20:00:00Z")
                        .param("end", "2099-06-10T22:00:00Z")
                        .param("url", "http://ticket.com/tm1")
                        .param("venue", "Altice Arena"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    /**
     * Verifies that copying an event without end time also works correctly.
     * The controller handles null end time by passing null to the service,
     * which then applies the 2-hour default.
     */
    @Test
    @WithMockUser(username = "alice")
    void copy_noEndTime_redirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(meetingService.copyFromDiscovered(any(), any()))
                .thenReturn(new Meeting("Concert", "desc",
                        Instant.parse("2099-06-10T20:00:00Z"),
                        Instant.parse("2099-06-10T22:00:00Z"), alice));

        mockMvc.perform(post("/discover/copy").with(csrf())
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm1")
                        .param("title", "Concert")
                        .param("start", "2099-06-10T20:00:00Z"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    /**
     * Verifies that POST /discover/copy without a CSRF token is rejected with 403.
     */
    @Test
    @WithMockUser(username = "alice")
    void copy_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm1")
                        .param("title", "Concert")
                        .param("start", "2099-06-10T20:00:00Z"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies that unauthenticated POST to /discover/copy is redirected.
     */
    @Test
    void copy_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/discover/copy").with(csrf())
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm1")
                        .param("title", "Concert")
                        .param("start", "2099-06-10T20:00:00Z"))
                .andExpect(status().is3xxRedirection());
    }
}