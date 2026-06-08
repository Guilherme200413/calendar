package com.example.meetings.controller;

import com.example.meetings.model.User;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    // -------------------------------------------------------------------------
    // GET /
    // -------------------------------------------------------------------------

    /**
     * Verifies that the root path redirects to /calendar.
     * Users who access the app root should be sent to their calendar.
     */
    @Test
    void root_redirectsToCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    // -------------------------------------------------------------------------
    // GET /login
    // -------------------------------------------------------------------------

    /**
     * Verifies that the login page is accessible without authentication
     * and contains the expected sign-in form.
     */
    @Test
    void loginPage_returnsOk() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
    }

    /**
     * Verifies that an authenticated user can still access the login page.
     * Spring Security in this app does not force-redirect authenticated users
     * away from /login — the page is simply shown.
     */
    @Test
    @WithMockUser(username = "alice")
    void login_alreadyAuthenticated_pageLoads() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // POST /login
    // -------------------------------------------------------------------------

    /**
     * Verifies that a POST to /login with invalid credentials redirects to /login?error.
     * This validates our Spring Security configuration (failure URL).
     */
    @Test
    void login_invalidCredentials_redirectsToLoginError() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "alice")
                        .param("password", "wrongpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    /**
     * Verifies that a POST to /login without a CSRF token is rejected with 403.
     * This validates that Spring Security's CSRF protection is active on the login form.
     */
    @Test
    void login_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /register
    // -------------------------------------------------------------------------

    /**
     * Verifies that the registration page is accessible without authentication
     * and contains the expected account creation form.
     */
    @Test
    void registerPage_returnsOk() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create an account")));
    }

    // -------------------------------------------------------------------------
    // POST /register
    // -------------------------------------------------------------------------

    /**
     * Happy path: registering a new user redirects to /login?registered.
     * The redirect signals success to the login page which can show a confirmation.
     */
    @Test
    void register_validUser_redirectsToLogin() throws Exception {
        User user = new User("alice", "alice@example.com", "hash");
        when(userService.register(anyString(), anyString(), anyString())).thenReturn(user);

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
    }

    /**
     * Verifies that registering a duplicate username re-renders the form (200)
     * with an error message. The user stays on the page to correct the input.
     * A bug that redirected instead of re-rendering would be caught here.
     */
    @Test
    void register_duplicateUsername_showsError() throws Exception {
        when(userService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Username already taken"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Username already taken")));
    }

    /**
     * Verifies that a POST to /register without a CSRF token is rejected with 403.
     * This validates that Spring Security's CSRF protection is active.
     */
    @Test
    void register_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());
    }
}