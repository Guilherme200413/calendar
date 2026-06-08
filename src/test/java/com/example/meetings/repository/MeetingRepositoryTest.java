package com.example.meetings.repository;

import com.example.meetings.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DataJpaTest
public class MeetingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MeetingRepository meetingRepository;

    private User alice;
    private User bob;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        alice = entityManager.persist(new User("alice", "alice@example.com", "hash"));
        bob   = entityManager.persist(new User("bob",   "bob@example.com",   "hash"));
        entityManager.flush();

        start = Instant.parse("2099-01-10T10:00:00Z");
        end   = Instant.parse("2099-01-10T11:00:00Z");
    }

    // -------------------------------------------------------------------------
    // findCalendarMeetings()
    // -------------------------------------------------------------------------

    /**
     * Verifies that the organizer sees their own meeting in the calendar.
     * The organizer is always an ACCEPTED participant.
     */
    @Test
    void findCalendarMeetings_organizerSeesOwnMeeting() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);
        assertEquals(1, result.size());
        assertEquals("Standup", result.get(0).getTitle());
    }

    /**
     * Verifies that an ACCEPTED participant sees the meeting in their calendar.
     */
    @Test
    void findCalendarMeetings_acceptedParticipantSeesMeeting() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertEquals(1, result.size());
    }

    /**
     * Verifies that a PENDING participant sees the meeting in their calendar.
     * Pending invites block the time slot until the user responds.
     */
    @Test
    void findCalendarMeetings_pendingParticipantSeesMeeting() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));
        entityManager.persist(meeting);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertEquals(1, result.size());
    }

    /**
     * Verifies that a DECLINED participant does NOT see the meeting.
     * Declining frees up the slot on the user's calendar.
     * A bug that kept declined meetings visible would be caught here.
     */
    @Test
    void findCalendarMeetings_declinedParticipantDoesNotSeeMeeting() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.DECLINED));
        entityManager.persist(meeting);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that meetings are returned in ascending order of start time.
     * A bug in the ORDER BY clause would cause this test to fail.
     */
    @Test
    void findCalendarMeetings_sortedByStartTime() {
        Instant start2 = Instant.parse("2099-01-11T10:00:00Z");
        Instant end2   = Instant.parse("2099-01-11T11:00:00Z");

        Meeting m2 = new Meeting("Later",   "desc", start2, end2, alice);
        Meeting m1 = new Meeting("Earlier", "desc", start,  end,  alice);
        m2.addParticipant(new MeetingParticipant(m2, alice, InviteStatus.ACCEPTED));
        m1.addParticipant(new MeetingParticipant(m1, alice, InviteStatus.ACCEPTED));
        entityManager.persist(m2);
        entityManager.persist(m1);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);
        assertEquals("Earlier", result.get(0).getTitle());
        assertEquals("Later",   result.get(1).getTitle());
    }

    /**
     * Verifies that a user with no meetings gets an empty list.
     */
    @Test
    void findCalendarMeetings_emptyForUserWithNoMeetings() {
        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // findOverlapping()
    // -------------------------------------------------------------------------

    /**
     * Verifies that a meeting overlapping the query window is detected.
     */
    @Test
    void findOverlapping_detectsOverlap() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findOverlapping(alice, start, end);
        assertEquals(1, result.size());
    }

    /**
     * Verifies that a meeting outside the query window is not detected.
     */
    @Test
    void findOverlapping_noOverlap_returnsEmpty() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();

        Instant after    = Instant.parse("2099-01-10T12:00:00Z");
        Instant afterEnd = Instant.parse("2099-01-10T13:00:00Z");
        List<Meeting> result = meetingRepository.findOverlapping(alice, after, afterEnd);
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that a partial overlap is detected.
     * A query window starting before the meeting and ending during it must match.
     */
    @Test
    void findOverlapping_partialOverlap_detected() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();

        Instant before = Instant.parse("2099-01-10T09:30:00Z");
        List<Meeting> result = meetingRepository.findOverlapping(alice, before, end);
        assertEquals(1, result.size());
    }

    /**
     * Boundary test: a query window that starts exactly when the meeting ends
     * must NOT count as an overlap. The query uses strict operators ({@code <} and {@code >}).
     * A bug that used {@code <=}/{@code >=} would cause a false conflict here.
     */
    @Test
    void findOverlapping_windowTouchesExactlyEnd_notOverlap() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();

        Instant windowEnd = Instant.parse("2099-01-10T13:00:00Z");
        List<Meeting> result = meetingRepository.findOverlapping(alice, end, windowEnd);
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that a declined meeting is not counted as a conflict.
     * A user who declined a meeting has their slot freed up for new proposals.
     */
    @Test
    void findOverlapping_declinedMeeting_notCountedAsConflict() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.DECLINED));
        entityManager.persist(meeting);
        entityManager.flush();

        List<Meeting> result = meetingRepository.findOverlapping(bob, start, end);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Cascade and round-trip
    // -------------------------------------------------------------------------

    /**
     * Verifies that persisting a Meeting also persists its participants via cascade.
     * The entity manager is flushed and cleared to force a real DB read,
     * confirming the cascade annotation is correctly configured.
     */
    @Test
    void cascade_participantsPersistedWithMeeting() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));
        entityManager.persist(meeting);
        entityManager.flush();
        entityManager.clear();

        Meeting reloaded = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertEquals(2, reloaded.getParticipants().size());
    }

    /**
     * Verifies that the start and end Instant values survive a full round-trip
     * to and from the database without precision loss.
     */
    @Test
    void roundTrip_startAndEndTimePreserved() {
        Meeting meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        entityManager.persist(meeting);
        entityManager.flush();
        entityManager.clear();

        Meeting reloaded = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertEquals(start, reloaded.getStartTime());
        assertEquals(end,   reloaded.getEndTime());
    }
}