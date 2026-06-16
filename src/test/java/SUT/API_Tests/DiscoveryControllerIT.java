package SUT.API_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.config.SecurityConfig;
import com.example.meetings.controller.DiscoveryController;
import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * REST-API-level integration tests for {@link DiscoveryController}. {@link DiscoveryService},
 * {@link MeetingService}, and {@link UserService} are mocked; {@link SecurityConfig} is imported
 * so authentication/CSRF rules are genuinely enforced.
 */
@WebMvcTest(DiscoveryController.class)
@ContextConfiguration(classes = MeetingsApplication.class)
@Import(SecurityConfig.class)
class DiscoveryControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscoveryService discoveryService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /discover - should redirect anonymous users to the login page")
    void discoverRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /discover - should list providers and return no results when no query is given")
    void discoverWithoutQueryReturnsNoResults() throws Exception {
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));

        mockMvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("providers", List.of(provider)))
                .andExpect(model().attribute("anyConfigured", true))
                .andExpect(model().attribute("q", ""))
                .andExpect(model().attribute("results", List.of()));

        verify(discoveryService, never()).search(any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /discover?q=... - should search and return results when at least one provider is configured")
    void discoverWithQuerySearchesWhenProviderConfigured() throws Exception {
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));

        DiscoveredEvent event = new DiscoveredEvent("Ticketmaster", "1", "Concert",
                "A great show", Instant.parse("2026-07-01T19:00:00Z"), null,
                "https://example.com/concert", "Arena");
        when(discoveryService.search("concert")).thenReturn(List.of(event));

        mockMvc.perform(get("/discover").param("q", "concert"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("q", "concert"))
                .andExpect(model().attribute("results", List.of(event)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /discover?q=... - should return no results without searching when no provider is configured")
    void discoverWithQuerySkipsSearchWhenNoProviderConfigured() throws Exception {
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(false);
        when(discoveryService.providers()).thenReturn(List.of(provider));

        mockMvc.perform(get("/discover").param("q", "concert"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("anyConfigured", false))
                .andExpect(model().attribute("results", List.of()));

        verify(discoveryService, never()).search(any());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /discover/copy - should copy the discovered event to the user's calendar and redirect to /calendar")
    void copyDiscoveredEventRedirectsToCalendar() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        when(userService.requireByUsername("testuser")).thenReturn(testuser);

        mockMvc.perform(post("/discover/copy")
                        .param("source", "Ticketmaster")
                        .param("externalId", "evt-1")
                        .param("title", "Concert")
                        .param("description", "Great show")
                        .param("start", "2026-07-01T19:00:00Z")
                        .param("end", "2026-07-01T21:00:00Z")
                        .param("url", "https://example.com/concert")
                        .param("venue", "Arena")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).copyFromDiscovered(eq(testuser), eq(new DiscoveredEvent(
                "Ticketmaster", "evt-1", "Concert", "Great show",
                Instant.parse("2026-07-01T19:00:00Z"),
                Instant.parse("2026-07-01T21:00:00Z"),
                "https://example.com/concert", "Arena")));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /discover/copy - should treat a blank end parameter as no end time")
    void copyDiscoveredEventWithBlankEndTime() throws Exception {
        User testuser = new User("testuser", "testuser@example.com", "hash");
        when(userService.requireByUsername("testuser")).thenReturn(testuser);

        mockMvc.perform(post("/discover/copy")
                        .param("source", "Agenda Cultural de Lisboa")
                        .param("externalId", "evt-2")
                        .param("title", "Exhibition")
                        .param("start", "2026-08-01T10:00:00Z")
                        .param("end", "")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).copyFromDiscovered(eq(testuser), eq(new DiscoveredEvent(
                "Agenda Cultural de Lisboa", "evt-2", "Exhibition", null,
                Instant.parse("2026-08-01T10:00:00Z"),
                null, null, null)));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /discover/copy - should be rejected with 403 Forbidden when the CSRF token is missing")
    void copyWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .param("source", "Ticketmaster")
                        .param("externalId", "evt-1")
                        .param("title", "Concert")
                        .param("start", "2026-07-01T19:00:00Z"))
                .andExpect(status().isForbidden());
    }
}