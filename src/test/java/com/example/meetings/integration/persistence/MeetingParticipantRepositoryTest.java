package com.example.meetings.integration.persistence;

import com.example.meetings.model.*;
import com.example.meetings.repository.MeetingParticipantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import jakarta.persistence.PersistenceException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@DataJpaTest
public class MeetingParticipantRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    private User alice;
    private User bob;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        alice = entityManager.persist(new User("alice", "alice@example.com", "hash"));
        bob   = entityManager.persist(new User("bob",   "bob@example.com",   "hash"));

        Instant start = Instant.parse("2099-01-10T10:00:00Z");
        Instant end   = Instant.parse("2099-01-10T11:00:00Z");

        meeting = new Meeting("Standup", "desc", start, end, alice);
        meeting.addParticipant(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));
        entityManager.persist(meeting);
        entityManager.flush();
    }

    // -------------------------------------------------------------------------
    // findByUserAndStatus()
    // -------------------------------------------------------------------------

    /**
     * Verifies that pending invites for a user are correctly returned.
     * Used by the calendar view to show the user's pending invitations.
     */
    @Test
    void findByUserAndStatus_pendingInvites_returnsCorrect() {
        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(bob, InviteStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(bob.getUsername(), result.get(0).getUser().getUsername());
    }

    /**
     * Verifies that accepted invites for a user are correctly returned.
     */
    @Test
    void findByUserAndStatus_acceptedInvites_returnsCorrect() {
        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(alice, InviteStatus.ACCEPTED);

        assertEquals(1, result.size());
        assertEquals(alice.getUsername(), result.get(0).getUser().getUsername());
    }

    /**
     * Verifies that querying for a status the user does not have returns empty.
     * Alice has ACCEPTED, so querying PENDING for alice must return nothing.
     */
    @Test
    void findByUserAndStatus_noMatchingStatus_returnsEmpty() {
        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(alice, InviteStatus.PENDING);

        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that a user with no invites at all gets an empty list.
     */
    @Test
    void findByUserAndStatus_userWithNoInvites_returnsEmpty() {
        User carol = entityManager.persist(new User("carol", "carol@example.com", "hash"));
        entityManager.flush();

        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(carol, InviteStatus.PENDING);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // findByMeetingIdAndUserId()
    // -------------------------------------------------------------------------

    /**
     * Happy path: verifies that an existing participant is found by meeting and user ID.
     * Used by MeetingService.respond() to locate the invite to update.
     */
    @Test
    void findByMeetingIdAndUserId_existingParticipant_returnsIt() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), bob.getId());

        assertTrue(result.isPresent());
        assertEquals(InviteStatus.PENDING, result.get().getStatus());
    }

    /**
     * Verifies that a wrong meeting ID returns empty.
     * Prevents responding to a meeting the user was never invited to.
     */
    @Test
    void findByMeetingIdAndUserId_wrongMeetingId_returnsEmpty() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(999L, bob.getId());

        assertFalse(result.isPresent());
    }

    /**
     * Verifies that a wrong user ID returns empty.
     */
    @Test
    void findByMeetingIdAndUserId_wrongUserId_returnsEmpty() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), 999L);

        assertFalse(result.isPresent());
    }

    /**
     * Verifies that both wrong IDs returns empty.
     */
    @Test
    void findByMeetingIdAndUserId_bothWrong_returnsEmpty() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(999L, 999L);

        assertFalse(result.isPresent());
    }

    // -------------------------------------------------------------------------
    // Database constraints
    // -------------------------------------------------------------------------

    /**
     * Verifies that the database enforces the UNIQUE constraint on (meeting_id, user_id).
     * A user cannot appear twice in the same meeting's participant list.
     * This is a database-level test — it validates schema integrity directly.
     */
    @Test
    void duplicateParticipant_throwsException() {
        assertThrows(PersistenceException.class, () -> {
            entityManager.persist(new MeetingParticipant(meeting, bob, InviteStatus.ACCEPTED));
            entityManager.flush();
        });
    }
}