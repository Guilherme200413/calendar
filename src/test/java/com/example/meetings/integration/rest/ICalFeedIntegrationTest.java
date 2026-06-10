package com.example.meetings.integration.rest;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ICalFeedIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserService userService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    private static final Instant START = Instant.parse("2099-06-10T10:00:00Z");
    private static final Instant END   = Instant.parse("2099-06-10T11:00:00Z");

    @BeforeEach
    void resetDatabase() {
        // Order matters for referential integrity: participants -> meetings -> users.
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String feedUrl(String token) {
        return "http://localhost:" + port + "/ical/" + token + ".ics";
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    /**
     * A valid token returns 200 with a text/calendar body that contains the real
     * rendered VCALENDAR. A solo meeting (organizer auto-accepts) is CONFIRMED.
     */
    @Test
    void validToken_returnsConfirmedMeetingInCalendar() {
        User alice = userService.register("alice", "alice@example.com", "password123");
        meetingService.propose(alice, "Team Standup", "Daily sync", START, END, List.of());

        ResponseEntity<String> response =
                rest.getForEntity(feedUrl(alice.getIcalToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("text/calendar");

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body)
                .contains("BEGIN:VCALENDAR")
                .contains("BEGIN:VEVENT")
                .contains("SUMMARY:Team Standup")
                .contains("STATUS:CONFIRMED")
                .contains("END:VCALENDAR");
    }

    /**
     * A meeting with an invitee who has not yet responded is rendered TENTATIVE,
     * proving the real {@code Meeting#isConfirmed()} logic flows through to the feed.
     */
    @Test
    void pendingInvite_rendersTentativeStatus() {
        User bob   = userService.register("bob", "bob@example.com", "password123");
        userService.register("carol", "carol@example.com", "password123");

        meetingService.propose(bob, "Project Sync", null, START, END, List.of("carol"));

        ResponseEntity<String> response =
                rest.getForEntity(feedUrl(bob.getIcalToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("SUMMARY:Project Sync")
                .contains("STATUS:TENTATIVE");
    }

    /**
     * The strongest integration assertion: it crosses the controller, the service
     * and the real JPQL query. When an invitee DECLINES, the meeting must vanish
     * from THEIR feed (slot freed) but remain on the ORGANIZER's feed.
     */
    @Test
    void declinedInvite_isExcludedFromInviteeFeedButNotOrganizerFeed() {
        User dave = userService.register("dave", "dave@example.com", "password123");
        User erin = userService.register("erin", "erin@example.com", "password123");

        Meeting meeting =
                meetingService.propose(dave, "Design Review", null, START, END, List.of("erin"));
        meetingService.respond(meeting.getId(), erin, InviteStatus.DECLINED);

        String erinFeed = rest.getForEntity(feedUrl(erin.getIcalToken()), String.class).getBody();
        String daveFeed = rest.getForEntity(feedUrl(dave.getIcalToken()), String.class).getBody();

        assertThat(erinFeed).doesNotContain("Design Review");
        assertThat(daveFeed).contains("SUMMARY:Design Review");
    }

    // -------------------------------------------------------------------------
    // Access control
    // -------------------------------------------------------------------------

    /**
     * An unknown token must not expose any calendar. The endpoint maps the missing
     * user to HTTP 404. TestRestTemplate does not throw on 4xx, so we assert directly.
     */
    @Test
    void unknownToken_returns404() {
        ResponseEntity<String> response =
                rest.getForEntity(feedUrl("does-not-exist"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}