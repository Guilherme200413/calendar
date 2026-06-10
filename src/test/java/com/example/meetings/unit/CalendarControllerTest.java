package com.example.meetings.unit;

import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer test for {@link com.example.meetings.controller.CalendarController},
 * with the service layer mocked.
 *
 * Complements ICalControllerTest's calendar cases by asserting what the controller
 * actually puts in the model — in particular the iCal subscription URLs. The
 * webcal:// URL is derived from the https URL via a scheme rewrite that no other
 * test exercised; pinning it here means a regression in that derivation (which would
 * break "subscribe in Apple/Google Calendar") fails the build.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    @Test
    void calendar_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "alice")
    void calendar_authenticated_rendersCalendarViewWithModel() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        Meeting meeting = new Meeting("Team Standup", "Daily sync",
                Instant.parse("2099-06-10T10:00:00Z"),
                Instant.parse("2099-06-10T11:00:00Z"), alice);
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(meetingService.calendarFor(alice)).thenReturn(List.of(meeting));
        when(meetingService.pendingInvitesFor(alice)).thenReturn(List.of());

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("user", alice))
                .andExpect(model().attribute("meetings", List.of(meeting)))
                .andExpect(model().attribute("pendingInvites", List.of()));
    }

    @Test
    @WithMockUser(username = "alice")
    void calendar_exposesIcalHttpAndWebcalUrls() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(meetingService.calendarFor(alice)).thenReturn(List.of());
        when(meetingService.pendingInvitesFor(alice)).thenReturn(List.of());

        // app.base-url in the test properties is http://localhost:8080
        String token = alice.getIcalToken();
        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("icalHttpUrl",
                        is("http://localhost:8080/ical/" + token + ".ics")))
                .andExpect(model().attribute("icalWebcalUrl",
                        is("webcal://localhost:8080/ical/" + token + ".ics")));
    }
}