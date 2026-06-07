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

    // --- propose() ---

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

    @Test
    void propose_endBeforeStart_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                meetingService.propose(organizer, "Bad", "desc", end, start, List.of()));
    }

    @Test
    void propose_endEqualsStart_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                meetingService.propose(organizer, "Bad", "desc", start, start, List.of()));
    }

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

    @Test
    void propose_withUnknownInvitee_throwsException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                meetingService.propose(organizer, "Meeting", "desc",
                        start, end, List.of("ghost")));
    }

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

    @Test
    void propose_organizerNotAddedAsInvitee() {
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Solo", "desc",
                start, end, List.of("alice"));

        assertEquals(1, result.getParticipants().size());
    }

    // --- respond() ---

    @Test
    void respond_accept_updatesStatus() {
        MeetingParticipant participant = new MeetingParticipant(null, organizer, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, organizer.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, organizer, InviteStatus.ACCEPTED);

        assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
    }

    @Test
    void respond_decline_updatesStatus() {
        MeetingParticipant participant = new MeetingParticipant(null, organizer, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, organizer.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, organizer, InviteStatus.DECLINED);

        assertEquals(InviteStatus.DECLINED, participant.getStatus());
    }

    @Test
    void respond_invalidStatus_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                meetingService.respond(1L, organizer, InviteStatus.PENDING));
    }

    @Test
    void respond_noInviteFound_throwsException() {
        when(participantRepository.findByMeetingIdAndUserId(anyLong(), any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                meetingService.respond(99L, organizer, InviteStatus.ACCEPTED));
    }

    // --- calendarForIcalToken() ---

    @Test
    void calendarForIcalToken_invalidToken_throwsException() {
        when(userRepository.findByIcalToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                meetingService.calendarForIcalToken("bad-token"));
    }

    @Test
    void calendarForIcalToken_validToken_returnsMeetings() {
        when(userRepository.findByIcalToken("valid")).thenReturn(Optional.of(organizer));
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of());

        List<Meeting> result = meetingService.calendarForIcalToken("valid");

        assertNotNull(result);
        verify(meetingRepository).findCalendarMeetings(organizer);
    }

    // --- copyFromDiscovered() ---

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

    @Test
    void copyFromDiscovered_noEndTime_defaultsTwoHours() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", "Great show",
                start, null, "http://ticket.com/1", "Altice Arena");
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertEquals(start.plus(Duration.ofHours(2)), result.getEndTime());
    }

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

    @Test
    void copyFromDiscovered_noVenueNoUrl_descriptionHasSource() {
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "1", "Concert", null,
                start, null, null, null);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertTrue(result.getDescription().contains("Ticketmaster"));
    }

    // --- calendarFor() e pendingInvitesFor() ---

    @Test
    void calendarFor_delegatesToRepository() {
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of());

        meetingService.calendarFor(organizer);

        verify(meetingRepository).findCalendarMeetings(organizer);
    }

    @Test
    void pendingInvitesFor_delegatesToRepository() {
        when(participantRepository.findByUserAndStatus(organizer, InviteStatus.PENDING))
                .thenReturn(List.of());

        meetingService.pendingInvitesFor(organizer);

        verify(participantRepository).findByUserAndStatus(organizer, InviteStatus.PENDING);
    }
}