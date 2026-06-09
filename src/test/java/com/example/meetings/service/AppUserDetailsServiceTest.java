package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    // -------------------------------------------------------------------------
    // loadUserByUsername()
    // -------------------------------------------------------------------------

    /**
     * Happy path: verifies that a known username returns a populated UserDetails
     * object with the correct username.
     */
    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        User alice = new User("alice", "alice@example.com", "hashedpassword");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserDetails result = appUserDetailsService.loadUserByUsername("alice");

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
    }

    /**
     * Verifies that the password hash from the User entity is used as the
     * password in UserDetails — never the plain-text password.
     */
    @Test
    void loadUserByUsername_usesPasswordHash() {
        User alice = new User("alice", "alice@example.com", "hashedpassword");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserDetails result = appUserDetailsService.loadUserByUsername("alice");

        assertEquals("hashedpassword", result.getPassword());
    }

    /**
     * Verifies that the returned UserDetails has the ROLE_USER authority.
     * All users in this application have the same role.
     */
    @Test
    void loadUserByUsername_hasRoleUser() {
        User alice = new User("alice", "alice@example.com", "hashedpassword");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserDetails result = appUserDetailsService.loadUserByUsername("alice");

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    /**
     * Verifies that the returned UserDetails has exactly one authority.
     * No extra roles should be assigned beyond ROLE_USER.
     */
    @Test
    void loadUserByUsername_hasExactlyOneAuthority() {
        User alice = new User("alice", "alice@example.com", "hashedpassword");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserDetails result = appUserDetailsService.loadUserByUsername("alice");

        assertEquals(1, result.getAuthorities().size());
    }

    /**
     * Verifies that a non-existent username throws {@link UsernameNotFoundException}.
     * Spring Security catches this exception and redirects to /login?error.
     */
    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                appUserDetailsService.loadUserByUsername("ghost"));
    }

    /**
     * Verifies that the exception message contains the unknown username,
     * making authentication failure logs easier to diagnose.
     */
    @Test
    void loadUserByUsername_unknownUser_exceptionMessageContainsUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class, () ->
                appUserDetailsService.loadUserByUsername("ghost"));

        assertTrue(ex.getMessage().contains("ghost"));
    }
}