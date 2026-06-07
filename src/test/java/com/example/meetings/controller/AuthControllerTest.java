package com.example.meetings.controller;

import com.example.meetings.model.User;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    // --- GET / ---

    @Test
    void root_redirectsToCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    // --- GET /login ---

    @Test
    void loginPage_returnsOk() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
    }

    // --- GET /register ---

    @Test
    void registerPage_returnsOk() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create an account")));
    }

    // --- POST /register ---

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

    @Test
    void register_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());
    }
}