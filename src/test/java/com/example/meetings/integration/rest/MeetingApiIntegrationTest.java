package com.example.meetings.integration.rest;

import com.example.meetings.model.Meeting;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeetingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private MeetingRepository meetingRepository;

    @BeforeEach
    void seedUsers() {
        // Real write path, so requireByUsername(...) in the controllers finds real rows.
        userService.register("alice", "alice@example.com", "password123");
        userService.register("bob", "bob@example.com", "password123");
    }

    /** Proposing a meeting persists it and shows it on the organizer's calendar. */
    @Test
    void proposeMeeting_persistsAndShowsOnOrganizerCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(user("alice")).with(csrf())
                        .param("title", "Sprint Planning")
                        .param("description", "Plan the sprint")
                        .param("start", "2099-06-10T10:00")
                        .param("end", "2099-06-10T11:00")
                        .param("invitees", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        mockMvc.perform(get("/calendar").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sprint Planning")));
    }

    /** When an invitee accepts, the meeting appears on their calendar. */
    @Test
    void invitedUser_acceptsInvite_meetingAppearsOnTheirCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(user("alice")).with(csrf())
                        .param("title", "Project Sync")
                        .param("start", "2099-06-11T14:00")
                        .param("end", "2099-06-11T15:00")
                        .param("invitees", "bob"))
                .andExpect(status().is3xxRedirection());

        Long meetingId = meetingRepository.findAll().get(0).getId();

        mockMvc.perform(post("/meetings/" + meetingId + "/respond")
                        .with(user("bob")).with(csrf())
                        .param("action", "accept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        mockMvc.perform(get("/calendar").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Project Sync")));
    }

    /** When an invitee declines, the meeting does not appear on their calendar. */
    @Test
    void invitedUser_declinesInvite_meetingNotOnTheirCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(user("alice")).with(csrf())
                        .param("title", "Design Review")
                        .param("start", "2099-06-12T09:00")
                        .param("end", "2099-06-12T10:00")
                        .param("invitees", "bob"))
                .andExpect(status().is3xxRedirection());

        Long meetingId = meetingRepository.findAll().get(0).getId();

        mockMvc.perform(post("/meetings/" + meetingId + "/respond")
                        .with(user("bob")).with(csrf())
                        .param("action", "decline"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        mockMvc.perform(get("/calendar").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Design Review"))));
    }

    /** An end time before the start re-renders the form with an error (validation branch). */
    @Test
    void proposeMeeting_endBeforeStart_reRendersFormWithError() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(user("alice")).with(csrf())
                        .param("title", "Bad Meeting")
                        .param("start", "2099-06-10T11:00")
                        .param("end", "2099-06-10T10:00")
                        .param("invitees", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"))
                .andExpect(model().attributeExists("error"));
    }

    /** The propose form loads for an authenticated user. */
    @Test
    void proposeForm_isReachableWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/meetings/new").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"));
    }

    /** The calendar page exposes the webcal:// subscription URL (scheme rewrite). */
    @Test
    void calendar_exposesWebcalSubscriptionUrl() throws Exception {
        // The calendar page derives a webcal:// URL from the https one (scheme rewrite);
        // assert it is rendered so a regression in that derivation is detected.
        mockMvc.perform(get("/calendar").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("webcal://")));
    }

    /** Proposing a meeting without a CSRF token is rejected. */
    @Test
    void proposeMeeting_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(user("alice"))
                        .param("title", "No CSRF")
                        .param("start", "2099-06-10T10:00")
                        .param("end", "2099-06-10T11:00")
                        .param("invitees", ""))
                .andExpect(status().isForbidden());
    }

    /** Responding to an invite without a CSRF token is rejected. */
    @Test
    void respondMeeting_withoutCsrf_isForbidden() throws Exception {
        // CSRF is rejected by the filter before the controller runs, so no real meeting id
        // is needed.
        mockMvc.perform(post("/meetings/1/respond")
                        .with(user("bob"))
                        .param("action", "accept"))
                .andExpect(status().isForbidden());
    }
}