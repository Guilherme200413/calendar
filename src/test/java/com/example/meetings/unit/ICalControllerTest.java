package com.example.meetings.unit;

import com.example.meetings.model.*;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class ICalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private ICalService icalService;

    /**
     * Happy path: a valid iCal token returns HTTP 200 with Content-Type text/calendar
     * and a body containing the BEGIN:VCALENDAR marker.
     */
    @Test
    void ical_validToken_returnsOk() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userRepository.findByIcalToken(alice.getIcalToken()))
                .thenReturn(Optional.of(alice));
        when(meetingService.calendarFor(alice)).thenReturn(List.of());
        when(icalService.render(alice, List.of()))
                .thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        mockMvc.perform(get("/ical/" + alice.getIcalToken() + ".ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")));
    }

    /**
     * An invalid iCal token returns HTTP 404, preventing access to another
     * user's calendar feed.
     */
    @Test
    void ical_invalidToken_returns404() throws Exception {
        when(userRepository.findByIcalToken("invalid-token"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/ical/invalid-token.ics"))
                .andExpect(status().isNotFound());
    }
}