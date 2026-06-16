package SUT.API_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.config.SecurityConfig;
import com.example.meetings.controller.AuthController;
import com.example.meetings.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * REST-API-level integration tests for {@link AuthController}, driven through MockMvc with the
 * real {@link SecurityConfig} applied (so the actual permitAll/CSRF rules are exercised).
 * {@link UserService} is mocked since the business logic is covered by unit tests.
 */
@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = MeetingsApplication.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /login - should return the login view without requiring authentication")
    void loginPageIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("GET /register - should return the registration form without requiring authentication")
    void registerFormIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    @DisplayName("GET / - should redirect to /calendar")
    void rootRedirectsToCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    @DisplayName("POST /register - should register the user and redirect to /login?registered on success")
    void registerSuccessRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "testuser")
                        .param("email", "testuser@example.com")
                        .param("password", "s3cret")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(userService).register("testuser", "testuser@example.com", "s3cret");
    }

    @Test
    @DisplayName("POST /register - should re-render the form with an error when the username is already taken")
    void registerFailureRendersFormWithError() throws Exception {
        when(userService.register("testuser", "testuser@example.com", "s3cret"))
                .thenThrow(new IllegalArgumentException("Username already taken"));

        mockMvc.perform(post("/register")
                        .param("username", "testuser")
                        .param("email", "testuser@example.com")
                        .param("password", "s3cret")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", "Username already taken"))
                .andExpect(model().attribute("username", "testuser"))
                .andExpect(model().attribute("email", "testuser@example.com"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /register - should be rejected with 403 Forbidden when the CSRF token is missing")
    void registerWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "testuser")
                        .param("email", "testuser@example.com")
                        .param("password", "s3cret"))
                .andExpect(status().isForbidden());
    }
}