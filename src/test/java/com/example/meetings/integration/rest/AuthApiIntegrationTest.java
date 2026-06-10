package com.example.meetings.integration.rest;

import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST API integration test for the authentication endpoints (task 3): registration,
 * the login/register pages, the root redirect, form login, and CSRF protection.
 *
 * Runs the real security filter chain, controllers, services and H2 database through
 * MockMvc (no @MockBean). Form login + CSRF + session make a real socket fragile, so
 * MockMvc is the appropriate transport here (see ICalFeedIntegrationTest for the
 * socket-based test of the token-based feed). @Transactional rolls back each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void root_redirectsToCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    void loginPage_isPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void registerPage_isPublic() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void register_newUser_redirectsToLoginAndPersists() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "newuser")
                        .param("email", "new@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.findByUsername("newuser")).isPresent();
    }

    @Test
    void register_duplicateUsername_reRendersFormWithError() throws Exception {
        userService.register("taken", "taken@example.com", "password123");

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "taken")
                        .param("email", "other@example.com")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void register_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "nocsrf")
                        .param("email", "nocsrf@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_validCredentials_authenticatesAndRedirectsToCalendar() throws Exception {
        userService.register("alice", "alice@example.com", "password123");

        mockMvc.perform(formLogin("/login").user("alice").password("password123"))
                .andExpect(authenticated().withUsername("alice"))
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    void login_invalidCredentials_failsAuthentication() throws Exception {
        userService.register("alice", "alice@example.com", "password123");

        mockMvc.perform(formLogin("/login").user("alice").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }
}