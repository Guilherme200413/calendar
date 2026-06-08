package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = entityManager.persist(new User("alice", "alice@example.com", "hash"));
        entityManager.flush();
    }

    // -------------------------------------------------------------------------
    // findByUsername()
    // -------------------------------------------------------------------------

    /**
     * Verifies that an existing user can be found by username.
     */
    @Test
    void findByUsername_existingUser_returnsUser() {
        Optional<User> result = userRepository.findByUsername("alice");
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    /**
     * Verifies that querying a non-existent username returns an empty Optional,
     * preventing NullPointerExceptions in callers.
     */
    @Test
    void findByUsername_unknownUser_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("ghost");
        assertFalse(result.isPresent());
    }

    // -------------------------------------------------------------------------
    // existsByUsername()
    // -------------------------------------------------------------------------

    /**
     * Verifies that existsByUsername returns true for an existing username.
     * Used by UserService to check for duplicates before registration.
     */
    @Test
    void existsByUsername_existing_returnsTrue() {
        assertTrue(userRepository.existsByUsername("alice"));
    }

    /**
     * Verifies that existsByUsername returns false for a non-existent username.
     */
    @Test
    void existsByUsername_unknown_returnsFalse() {
        assertFalse(userRepository.existsByUsername("ghost"));
    }

    // -------------------------------------------------------------------------
    // findByIcalToken()
    // -------------------------------------------------------------------------

    /**
     * Verifies that a user can be found by their iCal token.
     * This is the lookup used by the iCal feed endpoint to identify the owner.
     */
    @Test
    void findByIcalToken_validToken_returnsUser() {
        String token = alice.getIcalToken();
        Optional<User> result = userRepository.findByIcalToken(token);
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    /**
     * Verifies that an invalid iCal token returns an empty Optional.
     * The iCal controller uses this to return 404 for unknown tokens.
     */
    @Test
    void findByIcalToken_invalidToken_returnsEmpty() {
        Optional<User> result = userRepository.findByIcalToken("invalid-token");
        assertFalse(result.isPresent());
    }

    // -------------------------------------------------------------------------
    // save() and persistence
    // -------------------------------------------------------------------------

    /**
     * Verifies that saving a user persists it to the database.
     * The entity manager is flushed and cleared to force a real DB read,
     * confirming the data was actually written and not just cached.
     */
    @Test
    void save_persistsUser() {
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        entityManager.flush();
        entityManager.clear();

        Optional<User> result = userRepository.findByUsername("bob");
        assertTrue(result.isPresent());
        assertEquals("bob@example.com", result.get().getEmail());
    }

    // -------------------------------------------------------------------------
    // iCal token invariants
    // -------------------------------------------------------------------------

    /**
     * Verifies that a new user always has a non-null, non-blank iCal token.
     * The token is required for the iCal feed URL to be functional.
     */
    @Test
    void icalToken_isGeneratedAutomatically() {
        assertNotNull(alice.getIcalToken());
        assertFalse(alice.getIcalToken().isBlank());
    }

    /**
     * Verifies that two different users have different iCal tokens.
     * Tokens must be unique to prevent one user from accessing another's calendar.
     */
    @Test
    void twoUsers_haveDifferentIcalTokens() {
        User bob = entityManager.persist(new User("bob", "bob@example.com", "hash"));
        entityManager.flush();
        assertNotEquals(alice.getIcalToken(), bob.getIcalToken());
    }

    // -------------------------------------------------------------------------
    // Database constraints
    // -------------------------------------------------------------------------

    /**
     * Verifies that the database enforces the UNIQUE constraint on username.
     * This is a database-level test — it validates that integrity is guaranteed
     * by the schema itself, not only by the application service layer.
     */
    @Test
    void duplicateUsername_throwsException() {
        entityManager.persist(new User("bob", "bob@example.com", "hash"));
        entityManager.flush();

        assertThrows(Exception.class, () -> {
            entityManager.persist(new User("bob", "other@example.com", "hash"));
            entityManager.flush();
        });
    }
}