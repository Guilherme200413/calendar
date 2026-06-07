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

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

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

    @Test
    void register_duplicateUsername_throwsException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                userService.register("alice", "alice@example.com", "pass123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordIsEncoded() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.register("alice", "alice@example.com", "pass123");

        verify(passwordEncoder).encode("pass123");
    }

    @Test
    void register_passwordNeverStoredInPlainText() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.register("alice", "alice@example.com", "pass123");

        assertNotEquals("pass123", result.getPasswordHash());
    }

    @Test
    void requireByUsername_existingUser_returnsUser() {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        User result = userService.requireByUsername("alice");

        assertEquals("alice", result.getUsername());
    }

    @Test
    void requireByUsername_unknownUser_throwsException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                userService.requireByUsername("ghost"));
    }

    @Test
    void requireByUsername_errorMessageContainsUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.requireByUsername("ghost"));

        assertTrue(ex.getMessage().contains("ghost"));
    }
}