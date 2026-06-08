package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.*;
import com.example.meetings.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private MeetingParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MeetingService meetingService;

    private User organizer;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        organizer = new User("alice", "alice@example.com", "hash");
        start = Instant.parse("2025-06-10T10:00:00Z");
        end   = Instant.parse("2025-06-10T11:00:00Z");
    }

    // -------------------------------------------------------------------------
    // propose()
    // -------------------------------------------------------------------------

    /**
     * Happy path: a valid meeting is saved and returned with the correct title.
     * Verifies that the repository save is called exactly once.
     */
    @Test
    void propose_validMeeting_returnsSavedMeeting() {
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Standup", "Daily sync",
                start, end, List.of());

        assertNotNull(result);
        assertEquals("Standup", result.getTitle());
        verify(meetingRepository).save(any(Meeting.class));
    }

    /**
     * Boundary: end time strictly before start time must be rejected.
     * Protects against meetings with negative duration.
     */
    @Test
    void propose_endBeforeStart_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                meetingService.propose(organizer, "Bad", "desc", end, start, List.of()));
    }

    /**
     * Boundary: end time equal to start time must be rejected.
     * A zero-duration meeting is meaningless and should not be allowed.
     */
    @Test
    void propose_endEqualsStart_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                meetingService.propose(organizer, "Bad", "desc", start, start, List.of()));
    }

    /**
     * Verifies that a valid invitee is resolved from the repository and
     * added as a participant alongside the organizer.
     */
    @Test
    void propose_withValidInvitee_addsParticipant() {
        User bob = new User("bob", "bob@example.com", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Meeting", "desc",
                start, end, List.of("bob"));

        assertEquals(2, result.getParticipants().size());
    }

    /**
     * Verifies that inviting a username that does not exist throws an exception.
     * Prevents silent data loss where an invite would be created for nobody.
     */
    @Test
    void propose_withUnknownInvitee_throwsException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                meetingService.propose(organizer, "Meeting", "desc",
                        start, end, List.of("ghost")));
    }

    /**
     * Verifies that the same invitee listed twice results in only one participant entry.
     * Prevents duplicate participation records in the database.
     */
    @Test
    void propose_duplicateInvitee_addedOnlyOnce() {
        User bob = new User("bob", "bob@example.com", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Meeting", "desc",
                start, end, List.of("bob", "bob"));

        assertEquals(2, result.getParticipants().size());
    }

    /**
     * Verifies that the organizer is automatically added as an ACCEPTED participant.
     * This is a core business rule: the proposer always attends their own meeting.
     */
    @Test
    void propose_organizerAutoAccepted() {
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Solo", "desc",
                start, end, List.of());

        MeetingParticipant op = result.getParticipants().stream()
                .filter(p -> p.getUser().getUsername().equals("alice"))
                .findFirst().orElseThrow();
        assertEquals(InviteStatus.ACCEPTED, op.getStatus());
    }

    /**
     * Verifies that including the organizer's own username in the invitee list
     * does not result in a duplicate participant entry.
     */
    @Test
    void propose_organizerNotAddedAsInvitee() {
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Solo", "desc",
                start, end, List.of("alice"));

        assertEquals(1, result.getParticipants().size());
    }

    // -------------------------------------------------------------------------
    // respond()
    // -------------------------------------------------------------------------

    /**
     * Verifies that accepting an invite updates the participant status to ACCEPTED.
     */
    @Test
    void respond_accept_updatesStatus() {
        MeetingParticipant participant = new MeetingParticipant(null, organizer, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, organizer.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, organizer, InviteStatus.ACCEPTED);

        assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
    }

    /**
     * Verifies that declining an invite updates the participant status to DECLINED.
     */
    @Test
    void respond_decline_updatesStatus() {
        MeetingParticipant participant = new MeetingParticipant(null, organizer, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, organizer.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, organizer, InviteStatus.DECLINED);

        assertEquals(InviteStatus.DECLINED, participant.getStatus());
    }

    /**
     * Verifies that attempting to respond with PENDING status throws an exception.
     * PENDING is not a valid response — only ACCEPTED or DECLINED are allowed.
     */
    @Test
    void respond_invalidStatus_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                meetingService.respond(1L, organizer, InviteStatus.PENDING));
    }

    /**
     * Verifies that responding to a non-existent invite throws an exception.
     * Prevents silent failures where a response would have no effect.
     */
    @Test
    void respond_noInviteFound_throwsException() {
        when(participantRepository.findByMeetingIdAndUserId(anyLong(), any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                meetingService.respond(99L, organizer, InviteStatus.ACCEPTED));
    }

    // -------------------------------------------------------------------------
    // calendarForIcalToken()
    // -------------------------------------------------------------------------

    /**
     * Verifies that an invalid iCal token throws an exception.
     * Prevents exposing calendar data to unauthenticated requests.
     */
    @Test
    void calendarForIcalToken_invalidToken_throwsException() {
        when(userRepository.findByIcalToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                meetingService.calendarForIcalToken("bad-token"));
    }

    /**
     * Verifies that a valid iCal token resolves to the correct user's meetings.
     * Confirms the delegation chain: token → user → meetings.
     */
    @Test
    void calendarForIcalToken_validToken_returnsMeetings() {
        when(userRepository.findByIcalToken("valid")).thenReturn(Optional.of(organizer));
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of());

        List<Meeting> result = meetingService.calendarForIcalToken("valid");

        assertNotNull(result);
        verify(meetingRepository).findCalendarMeetings(organizer);
    }

    // -------------------------------------------------------------------------
    // copyFromDiscovered()
    // -------------------------------------------------------------------------

    /**
     * Verifies that when an external event has an explicit end time,
     * that end time is used as the meeting end time.
     */
    @Test
    void copyFromDiscovered_withEndTime_usesEventEndTime() {
        Instant evEnd = Instant.parse("2025-06-10T12:00:00Z");
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", "Great show",
                start, evEnd, "http://ticket.com/1", "Altice Arena");
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertEquals(evEnd, result.getEndTime());
    }

    /**
     * Verifies that when an external event has no end time, a default duration
     * of 2 hours is applied. This prevents open-ended meetings.
     */
    @Test
    void copyFromDiscovered_noEndTime_defaultsTwoHours() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", "Great show",
                start, null, "http://ticket.com/1", "Altice Arena");
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertEquals(start.plus(Duration.ofHours(2)), result.getEndTime());
    }

    /**
     * Verifies that the user who copies an event is automatically added
     * as an ACCEPTED participant — they are self-inviting.
     */
    @Test
    void copyFromDiscovered_participantAutoAccepted() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", "desc",
                start, null, "http://ticket.com/1", null);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        MeetingParticipant p = result.getParticipants().iterator().next();
        assertEquals(InviteStatus.ACCEPTED, p.getStatus());
    }

    /**
     * Verifies that the meeting description includes the venue name and URL
     * from the external event, giving the user context about where to go.
     */
    @Test
    void copyFromDiscovered_descriptionContainsVenueAndUrl() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", "Great show",
                start, null, "http://ticket.com/1", "Altice Arena");
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertTrue(result.getDescription().contains("Altice Arena"));
        assertTrue(result.getDescription().contains("http://ticket.com/1"));
    }

    /**
     * Verifies that when venue and URL are absent, the description still
     * contains at least the source provider name for traceability.
     */
    @Test
    void copyFromDiscovered_noVenueNoUrl_descriptionHasSource() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", null,
                start, null, null, null);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertTrue(result.getDescription().contains("Ticketmaster"));
    }

    // -------------------------------------------------------------------------
    // calendarFor() and pendingInvitesFor()
    // -------------------------------------------------------------------------

    /**
     * Verifies that calendarFor delegates to the correct repository method.
     * The actual query logic is tested in the database integration tests.
     */
    @Test
    void calendarFor_delegatesToRepository() {
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of());

        meetingService.calendarFor(organizer);

        verify(meetingRepository).findCalendarMeetings(organizer);
    }

    /**
     * Verifies that pendingInvitesFor delegates to the correct repository method
     * with the PENDING status filter.
     */
    @Test
    void pendingInvitesFor_delegatesToRepository() {
        when(participantRepository.findByUserAndStatus(organizer, InviteStatus.PENDING))
                .thenReturn(List.of());

        meetingService.pendingInvitesFor(organizer);

        verify(participantRepository).findByUserAndStatus(organizer, InviteStatus.PENDING);
    }
}