package com.example.meetings.unit;

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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class MeetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    // -------------------------------------------------------------------------
    // GET /meetings/new
    // -------------------------------------------------------------------------

    /**
     * Verifies that unauthenticated access to the new meeting form is redirected.
     * The exact redirect target depends on Spring Security configuration.
     */
    @Test
    void meetingsNew_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Verifies that an authenticated user can access the new meeting form.
     */
    @Test
    @WithMockUser(username = "alice")
    void meetingsNew_authenticated_returnsOk() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // POST /meetings/new
    // -------------------------------------------------------------------------

    /**
     * Happy path: proposing a valid meeting redirects to /calendar.
     * This confirms the post-redirect-get pattern is correctly implemented.
     */
    @Test
    @WithMockUser(username = "alice")
    void proposeMeeting_validInput_redirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(meetingService.propose(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new Meeting("Standup", "desc",
                        Instant.parse("2025-06-10T10:00:00Z"),
                        Instant.parse("2025-06-10T11:00:00Z"), alice));

        mockMvc.perform(post("/meetings/new").with(csrf())
                        .param("title", "Standup")
                        .param("description", "desc")
                        .param("start", "2025-06-10T10:00")
                        .param("end", "2025-06-10T11:00")
                        .param("invitees", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    /**
     * Verifies that proposing a meeting with an unknown invitee re-renders
     * the form (200) with an error message. A bug that redirected instead
     * of re-rendering would be caught here.
     */
    @Test
    @WithMockUser(username = "alice")
    void proposeMeeting_invalidInvitee_showsError() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(meetingService.propose(any(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unknown invitee: ghost"));

        mockMvc.perform(post("/meetings/new").with(csrf())
                        .param("title", "Standup")
                        .param("description", "desc")
                        .param("start", "2025-06-10T10:00")
                        .param("end", "2025-06-10T11:00")
                        .param("invitees", "ghost"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unknown invitee: ghost")));
    }

    /**
     * Verifies that a POST to /meetings/new without a CSRF token is rejected with 403.
     */
    @Test
    @WithMockUser(username = "alice")
    void proposeMeeting_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .param("title", "Standup")
                        .param("description", "desc")
                        .param("start", "2025-06-10T10:00")
                        .param("end", "2025-06-10T11:00")
                        .param("invitees", ""))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // POST /meetings/{id}/respond
    // -------------------------------------------------------------------------

    /**
     * Verifies that accepting a meeting invite redirects to /calendar.
     */
    @Test
    @WithMockUser(username = "alice")
    void respondMeeting_accept_redirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        doNothing().when(meetingService).respond(anyLong(), any(), any());

        mockMvc.perform(post("/meetings/1/respond").with(csrf())
                        .param("action", "accept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    /**
     * Verifies that declining a meeting invite redirects to /calendar.
     */
    @Test
    @WithMockUser(username = "alice")
    void respondMeeting_decline_redirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        doNothing().when(meetingService).respond(anyLong(), any(), any());

        mockMvc.perform(post("/meetings/1/respond").with(csrf())
                        .param("action", "decline"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    /**
     * Verifies that a POST to /meetings/{id}/respond without a CSRF token
     * is rejected with 403.
     */
    @Test
    @WithMockUser(username = "alice")
    void respondMeeting_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/meetings/1/respond")
                        .param("action", "accept"))
                .andExpect(status().isForbidden());
    }
}