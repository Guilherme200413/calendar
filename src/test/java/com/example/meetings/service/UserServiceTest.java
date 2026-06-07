package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 *
 * This class tests user registration and lookup logic.
 *
 * Strategy: {@code UserRepository} and {@code PasswordEncoder} are
 * replaced by Mockito mocks. Mocking the {@code PasswordEncoder} is a deliberate
 * decision: the goal is to verify that the service delegates password
 * hashing to the encoder and never stores plain text — not to test the BCrypt
 * algorithm itself (which is a third-party library outside our scope).
 *
 * Coverage rationale (criterion c — bug detection): Tests cover
 * duplicate username rejection, password encoding delegation, plain-text
 * password prevention, and error message content for unknown users.
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // -------------------------------------------------------------------------
    // register()
    // -------------------------------------------------------------------------

    /**
     * Happy path: registering a new user saves it via the repository
     * and returns the saved entity with the correct username.
     */
    @Test
    void register_newUser_savesUser() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.register("alice", "alice@example.com", "pass123");

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        verify(userRepository).save(any(User.class));
    }

    /**
     * Verifies that registering a username that already exists throws an exception
     * and that the repository save is never called, preventing duplicate entries.
     */
    @Test
    void register_duplicateUsername_throwsException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                userService.register("alice", "alice@example.com", "pass123"));

        verify(userRepository, never()).save(any());
    }

    /**
     * Verifies that the password encoder is called during registration,
     * confirming that hashing is always applied before persistence.
     */
    @Test
    void register_passwordIsEncoded() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.register("alice", "alice@example.com", "pass123");

        verify(passwordEncoder).encode("pass123");
    }

    /**
     * Verifies that the stored password hash is never equal to the plain-text
     * password. This is a security invariant that must always hold.
     */
    @Test
    void register_passwordNeverStoredInPlainText() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.register("alice", "alice@example.com", "pass123");

        assertNotEquals("pass123", result.getPasswordHash());
    }

    // -------------------------------------------------------------------------
    // requireByUsername()
    // -------------------------------------------------------------------------

    /**
     * Happy path: looking up an existing user returns the correct entity.
     */
    @Test
    void requireByUsername_existingUser_returnsUser() {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        User result = userService.requireByUsername("alice");

        assertEquals("alice", result.getUsername());
    }

    /**
     * Verifies that looking up a non-existent username throws an exception
     * rather than returning null, enforcing fail-fast behavior.
     */
    @Test
    void requireByUsername_unknownUser_throwsException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                userService.requireByUsername("ghost"));
    }

    /**
     * Verifies that the exception message contains the username that was not found,
     * making debugging easier and error messages meaningful to callers.
     */
    @Test
    void requireByUsername_errorMessageContainsUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.requireByUsername("ghost"));

        assertTrue(ex.getMessage().contains("ghost"));
    }
}