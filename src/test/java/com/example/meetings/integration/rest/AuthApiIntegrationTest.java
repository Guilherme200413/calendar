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

    /** The application root redirects to the calendar. */
    @Test
    void root_redirectsToCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    /** The login page is reachable without authentication. */
    @Test
    void loginPage_isPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    /** The registration page is reachable without authentication. */
    @Test
    void registerPage_isPublic() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    /** Registering a new user persists it and redirects to the login page. */
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

    /** Registering an already-taken username re-renders the form with an error. */
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

    /** A registration POST without a CSRF token is rejected. */
    @Test
    void register_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "nocsrf")
                        .param("email", "nocsrf@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());
    }

    /** Valid credentials authenticate the user and redirect to the calendar. */
    @Test
    void login_validCredentials_authenticatesAndRedirectsToCalendar() throws Exception {
        userService.register("alice", "alice@example.com", "password123");

        mockMvc.perform(formLogin("/login").user("alice").password("password123"))
                .andExpect(authenticated().withUsername("alice"))
                .andExpect(redirectedUrl("/calendar"));
    }

    /** Invalid credentials leave the user unauthenticated and redirect to the error page. */
    @Test
    void login_invalidCredentials_failsAuthentication() throws Exception {
        userService.register("alice", "alice@example.com", "password123");

        mockMvc.perform(formLogin("/login").user("alice").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }
}