package com.example.meetings.repository;

import com.example.meetings.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database integration tests for MeetingParticipantRepository.
 * Database Sandbox pattern: H2 in-memory isolates from production DB.
 * Transaction Rollback pattern: @DataJpaTest rolls back after each test.
 */
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

    // --- findByUserAndStatus ---

    @Test
    void findByUserAndStatus_pendingInvites_returnsCorrect() {
        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(bob, InviteStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(bob.getUsername(), result.get(0).getUser().getUsername());
    }

    @Test
    void findByUserAndStatus_acceptedInvites_returnsCorrect() {
        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(alice, InviteStatus.ACCEPTED);

        assertEquals(1, result.size());
        assertEquals(alice.getUsername(), result.get(0).getUser().getUsername());
    }

    @Test
    void findByUserAndStatus_noMatchingStatus_returnsEmpty() {
        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(alice, InviteStatus.PENDING);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByUserAndStatus_userWithNoInvites_returnsEmpty() {
        User carol = entityManager.persist(new User("carol", "carol@example.com", "hash"));
        entityManager.flush();

        List<MeetingParticipant> result = participantRepository
                .findByUserAndStatus(carol, InviteStatus.PENDING);

        assertTrue(result.isEmpty());
    }

    // --- findByMeetingIdAndUserId ---

    @Test
    void findByMeetingIdAndUserId_existingParticipant_returnsIt() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), bob.getId());

        assertTrue(result.isPresent());
        assertEquals(InviteStatus.PENDING, result.get().getStatus());
    }

    @Test
    void findByMeetingIdAndUserId_wrongMeetingId_returnsEmpty() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(999L, bob.getId());

        assertFalse(result.isPresent());
    }

    @Test
    void findByMeetingIdAndUserId_wrongUserId_returnsEmpty() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), 999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByMeetingIdAndUserId_bothWrong_returnsEmpty() {
        Optional<MeetingParticipant> result = participantRepository
                .findByMeetingIdAndUserId(999L, 999L);

        assertFalse(result.isPresent());
    }

    // --- Constraints da BD ---

    @Test
    void duplicateParticipant_throwsDataIntegrityViolation() {
        assertThrows(Exception.class, () -> {
            entityManager.persist(new MeetingParticipant(meeting, bob, InviteStatus.ACCEPTED));
            entityManager.flush();
        });
    }
}
