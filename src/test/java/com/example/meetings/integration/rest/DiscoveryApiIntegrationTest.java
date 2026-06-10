package com.example.meetings.integration.rest;

import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST API integration test for the discovery endpoints (task 3): the discover page and
 * copying a discovered event into the user's calendar.
 *
 * Full stack via MockMvc (real services + H2), no @MockBean. External providers are
 * disabled in the test configuration (no API keys / base-url pointing nowhere), so the
 * discover page renders with no results without any network call; the copy endpoint does
 * not depend on a provider (it receives the event data directly). @Transactional rolls
 * back each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DiscoveryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private MeetingRepository meetingRepository;

    @BeforeEach
    void seedUser() {
        userService.register("alice", "alice@example.com", "password123");
    }

    @Test
    void discover_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void discover_authenticated_returnsOk() throws Exception {
        mockMvc.perform(get("/discover").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    @Test
    void copy_validEvent_redirectsToCalendarAndPersists() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .with(user("alice")).with(csrf())
                        .param("source", "seatgeek")
                        .param("externalId", "evt-1")
                        .param("title", "Live Concert")
                        .param("start", "2099-06-10T20:00:00Z"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        assertThat(meetingRepository.findAll())
                .extracting(m -> m.getTitle())
                .containsExactly("Live Concert");
    }

    @Test
    void copy_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .with(user("alice"))
                        .param("source", "seatgeek")
                        .param("externalId", "evt-1")
                        .param("title", "Live Concert")
                        .param("start", "2099-06-10T20:00:00Z"))
                .andExpect(status().isForbidden());
    }
}