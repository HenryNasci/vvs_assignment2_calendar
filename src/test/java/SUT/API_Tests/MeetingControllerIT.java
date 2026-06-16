package SUT.API_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.config.SecurityConfig;
import com.example.meetings.controller.MeetingController;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * REST-API-level integration tests for {@link MeetingController}. {@link MeetingService} and
 * {@link UserService} are mocked; {@link SecurityConfig} is imported so authentication/CSRF
 * rules are genuinely enforced.
 */
@WebMvcTest(MeetingController.class)
@ContextConfiguration(classes = MeetingsApplication.class)
@Import(SecurityConfig.class)
class MeetingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /meetings/new - should redirect anonymous users to the login page")
    void proposeFormRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /meetings/new - should return the propose form for an authenticated user")
    void proposeFormRendersForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /meetings/new - should propose the meeting and redirect to /calendar on success")
    void proposeMeetingRedirectsToCalendarOnSuccess() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        when(userService.requireByUsername("testuser")).thenReturn(testuser);
        when(meetingService.propose(any(), any(), any(), any(), any(), any()))
                .thenReturn(new Meeting("Standup", "Daily sync",
                        Instant.parse("2026-07-01T09:00:00Z"),
                        Instant.parse("2026-07-01T09:30:00Z"), testuser));

        mockMvc.perform(post("/meetings/new")
                        .param("title", "Standup")
                        .param("description", "Daily sync")
                        .param("start", "2026-07-01T09:00")
                        .param("end", "2026-07-01T09:30")
                        .param("invitees", "bob, carol")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).propose(eq(testuser), eq("Standup"), eq("Daily sync"),
                any(Instant.class), any(Instant.class), eq(List.of("bob", "carol")));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /meetings/new - should re-render the propose form with an error when the meeting is invalid")
    void proposeMeetingRendersFormWithErrorOnFailure() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        when(userService.requireByUsername("testuser")).thenReturn(testuser);
        when(meetingService.propose(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("End time must be after start time"));

        mockMvc.perform(post("/meetings/new")
                        .param("title", "Standup")
                        .param("description", "Daily sync")
                        .param("start", "2026-07-01T09:30")
                        .param("end", "2026-07-01T09:00")
                        .param("invitees", "")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"))
                .andExpect(model().attribute("error", "End time must be after start time"))
                .andExpect(model().attribute("title", "Standup"))
                .andExpect(model().attribute("description", "Daily sync"))
                .andExpect(model().attribute("start", "2026-07-01T09:30"))
                .andExpect(model().attribute("end", "2026-07-01T09:00"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /meetings/{id}/respond - should accept the invite and redirect to /calendar")
    void respondAcceptRedirectsToCalendar() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        when(userService.requireByUsername("testuser")).thenReturn(testuser);

        mockMvc.perform(post("/meetings/42/respond")
                        .param("action", "accept")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).respond(42L, testuser, InviteStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /meetings/{id}/respond - should decline the invite for any non-accept action and redirect to /calendar")
    void respondNonAcceptActionDeclinesInviteAndRedirectsToCalendar() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        when(userService.requireByUsername("testuser")).thenReturn(testuser);

        mockMvc.perform(post("/meetings/42/respond")
                        .param("action", "decline")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).respond(42L, testuser, InviteStatus.DECLINED);
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /meetings/{id}/respond - should be rejected with 403 Forbidden when the CSRF token is missing")
    void respondWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/meetings/42/respond")
                        .param("action", "accept"))
                .andExpect(status().isForbidden());
    }
}
