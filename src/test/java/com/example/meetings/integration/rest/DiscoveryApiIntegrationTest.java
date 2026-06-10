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

    /** The discover page requires authentication. */
    @Test
    void discover_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    /** An authenticated user can open the discover page. */
    @Test
    void discover_authenticated_returnsOk() throws Exception {
        mockMvc.perform(get("/discover").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    /** Copying a discovered event persists it as a meeting and redirects to the calendar. */
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

    /** A copy POST without a CSRF token is rejected. */
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