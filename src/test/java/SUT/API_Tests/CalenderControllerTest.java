package SUT.API_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.config.SecurityConfig;
import com.example.meetings.controller.CalendarController;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * REST-API-level integration tests for {@link CalendarController}. {@link MeetingService} and
 * {@link UserService} are mocked; {@link SecurityConfig} is imported so the
 * "/calendar requires authentication" rule is genuinely enforced.
 *
 * <p>{@code app.base-url} (used to build the iCal links) comes from the real
 * {@code application.properties} on the test classpath ({@code http://localhost:8080}).
 */
@WebMvcTest(CalendarController.class)
@ContextConfiguration(classes = MeetingsApplication.class)
@Import(SecurityConfig.class)
class CalenderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /calendar - should redirect anonymous users to the login page")
    void calendarRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("GET /calendar - should expose the user's meetings, pending invites, and iCal subscription links")
    void calendarShowsMeetingsPendingInvitesAndIcalLinks() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        User bob = new User("bob", "bob@example.com", "hash");

        Meeting meeting = new Meeting("Standup", "Daily sync",
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T09:30:00Z"),
                testuser);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser, InviteStatus.ACCEPTED));

        MeetingParticipant pendingInvite = new MeetingParticipant(meeting, bob, InviteStatus.PENDING);

        when(userService.requireByUsername("testuser")).thenReturn(testuser);
        when(meetingService.calendarFor(testuser)).thenReturn(List.of(meeting));
        when(meetingService.pendingInvitesFor(testuser)).thenReturn(List.of(pendingInvite));

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("user", testuser))
                .andExpect(model().attribute("meetings", List.of(meeting)))
                .andExpect(model().attribute("pendingInvites", List.of(pendingInvite)))
                .andExpect(model().attribute("icalHttpUrl",
                        "http://localhost:8080/ical/" + testuser.getIcalToken() + ".ics"))
                .andExpect(model().attribute("icalWebcalUrl",
                        "webcal://localhost:8080/ical/" + testuser.getIcalToken() + ".ics"));
    }
}