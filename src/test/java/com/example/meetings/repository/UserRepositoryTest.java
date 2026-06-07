package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database integration tests for UserRepository.
 * Database Sandbox pattern: H2 in-memory isolates from production DB.
 * Transaction Rollback pattern: @DataJpaTest rolls back after each test.
 */
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

    // --- findByUsername ---

    @Test
    void findByUsername_existingUser_returnsUser() {
        Optional<User> result = userRepository.findByUsername("alice");
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    @Test
    void findByUsername_unknownUser_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("ghost");
        assertFalse(result.isPresent());
    }

    // --- existsByUsername ---

    @Test
    void existsByUsername_existing_returnsTrue() {
        assertTrue(userRepository.existsByUsername("alice"));
    }

    @Test
    void existsByUsername_unknown_returnsFalse() {
        assertFalse(userRepository.existsByUsername("ghost"));
    }

    // --- findByIcalToken ---

    @Test
    void findByIcalToken_validToken_returnsUser() {
        String token = alice.getIcalToken();
        Optional<User> result = userRepository.findByIcalToken(token);
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    @Test
    void findByIcalToken_invalidToken_returnsEmpty() {
        Optional<User> result = userRepository.findByIcalToken("invalid-token");
        assertFalse(result.isPresent());
    }

    // --- save ---

    @Test
    void save_persistsUser() {
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        entityManager.flush();
        entityManager.clear();

        Optional<User> result = userRepository.findByUsername("bob");
        assertTrue(result.isPresent());
        assertEquals("bob@example.com", result.get().getEmail());
    }

    // --- iCal token ---

    @Test
    void icalToken_isGeneratedAutomatically() {
        assertNotNull(alice.getIcalToken());
        assertFalse(alice.getIcalToken().isBlank());
    }

    @Test
    void twoUsers_haveDifferentIcalTokens() {
        User bob = entityManager.persist(new User("bob", "bob@example.com", "hash"));
        entityManager.flush();
        assertNotEquals(alice.getIcalToken(), bob.getIcalToken());
    }

    // --- Constraints da BD ---

    @Test
    void duplicateUsername_throwsDataIntegrityViolation() {
        entityManager.persist(new User("bob", "bob@example.com", "hash"));
        entityManager.flush();

        assertThrows(Exception.class, () -> {
            entityManager.persist(new User("bob", "other@example.com", "hash"));
            entityManager.flush();
        });
    }
}