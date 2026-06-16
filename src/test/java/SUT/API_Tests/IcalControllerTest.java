package SUT.API_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.config.SecurityConfig;
import com.example.meetings.controller.ICalController;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST-API-level integration tests for {@link ICalController}. {@link UserRepository},
 * {@link MeetingService}, and {@link ICalService} are mocked. {@link SecurityConfig} is imported
 * so the "/ical/** is publicly accessible" rule is genuinely enforced.
 */
@WebMvcTest(ICalController.class)
@ContextConfiguration(classes = MeetingsApplication.class)
@Import(SecurityConfig.class)
class IcalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private ICalService icalService;

    @Test
    @DisplayName("GET /ical/{token}.ics - should return the rendered VCALENDAR feed for a known token, with no authentication")
    void feedReturnsCalendarForKnownToken() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        String token = testuser.getIcalToken();
        Meeting meeting = new Meeting("Standup", null,
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T09:30:00Z"),
                testuser);
        String ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(testuser));
        when(meetingService.calendarFor(testuser)).thenReturn(List.of(meeting));
        when(icalService.render(testuser, List.of(meeting))).thenReturn(ics);

        // No @WithMockUser / authentication of any kind — exercises the permitAll rule on /ical/**.
        mockMvc.perform(get("/ical/" + token + ".ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"meetings.ics\""))
                .andExpect(content().bytes(ics.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("GET /ical/{token}.ics - should return 404 Not Found for an unknown token")
    void feedReturns404ForUnknownToken() throws Exception {
        when(userRepository.findByIcalToken("does-not-exist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/ical/does-not-exist.ics"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /ical/{token}.ics - should render an empty VCALENDAR when the user has no meetings")
    void feedHandlesUserWithNoMeetings() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        String token = testuser.getIcalToken();
        String ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(testuser));
        when(meetingService.calendarFor(testuser)).thenReturn(List.of());
        when(icalService.render(testuser, List.of())).thenReturn(ics);

        mockMvc.perform(get("/ical/" + token + ".ics"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(ics.getBytes(StandardCharsets.UTF_8)));
    }
}