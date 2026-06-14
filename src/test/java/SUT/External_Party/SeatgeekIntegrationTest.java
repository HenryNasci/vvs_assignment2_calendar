package SUT.External_Party;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.SeatGeekProvider;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SeatGeekProvider} against a WireMock stand-in for the
 * SeatGeek Events API (https://api.seatgeek.com/2).
 */
class SeatGeekProviderIT {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private SeatGeekProvider providerWithClientId(String clientId) {
        return new SeatGeekProvider(clientId, "http://localhost:" + wireMock.port());
    }

    @Test
    @DisplayName("isConfigured - should return false when client-id is blank")
    void isConfiguredFalseWhenClientIdBlank() {
        SeatGeekProvider provider = providerWithClientId("");

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.name()).isEqualTo("SeatGeek");
    }

    @Test
    @DisplayName("search - should return an empty list and not call the SeatGeek API when not configured")
    void searchReturnsEmptyAndDoesNotCallApiWhenNotConfigured() {
        SeatGeekProvider provider = providerWithClientId(null);

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/events")));
    }

    @Test
    @DisplayName("search - should return parsed event with correct fields and interpret datetime_utc as UTC")
    void searchReturnsParsedEventsAndUsesUtcForDatetime() {
        SeatGeekProvider provider = providerWithClientId("test-client-id");

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("concert"))
                .withQueryParam("per_page", equalTo("20"))
                .withQueryParam("client_id", equalTo("test-client-id"))
                .willReturn(okJson("""
                    {
                      "events": [
                        {
                          "id": 555,
                          "title": "Rock Concert",
                          "short_title": "Rock",
                          "datetime_utc": "2026-08-15T20:00:00",
                          "url": "https://seatgeek.com/rock-concert",
                          "description": "A great rock concert.",
                          "venue": { "name": "MEO Arena" }
                        }
                      ]
                    }
                    """)));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.source()).isEqualTo("SeatGeek");
        assertThat(event.externalId()).isEqualTo("555");
        assertThat(event.title()).isEqualTo("Rock Concert");
        assertThat(event.description()).isEqualTo("A great rock concert.");
        assertThat(event.venue()).isEqualTo("MEO Arena");
        assertThat(event.url()).isEqualTo("https://seatgeek.com/rock-concert");
        assertThat(event.end()).isNull();

        Instant expected = LocalDateTime.parse("2026-08-15T20:00:00").toInstant(ZoneOffset.UTC);
        assertThat(event.start()).isEqualTo(expected);
    }

    @Test
    @DisplayName("search - should fall back to short_title when title is missing")
    void searchFallsBackToShortTitleWhenTitleMissing() {
        SeatGeekProvider provider = providerWithClientId("test-client-id");

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(okJson("""
                    {
                      "events": [
                        {
                          "id": 556,
                          "short_title": "Short Name",
                          "datetime_utc": "2026-09-01T19:30:00",
                          "url": "https://seatgeek.com/event-556",
                          "venue": { "name": "Coliseu" }
                        }
                      ]
                    }
                    """)));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Short Name");
    }

    @Test
    @DisplayName("search - should skip events with a missing datetime_utc")
    void searchSkipsEventsWithMissingDatetime() {
        SeatGeekProvider provider = providerWithClientId("test-client-id");

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(okJson("""
                    {
                      "events": [
                        {
                          "id": 557,
                          "title": "TBA Event",
                          "url": "https://seatgeek.com/event-557",
                          "venue": { "name": "Unknown" }
                        }
                      ]
                    }
                    """)));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should return an empty list when the SeatGeek API responds with a server error")
    void searchReturnsEmptyListOnServerError() {
        SeatGeekProvider provider = providerWithClientId("test-client-id");

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(serverError()));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should return an empty list when the response has no events field")
    void searchReturnsEmptyListWhenNoEventsField() {
        SeatGeekProvider provider = providerWithClientId("test-client-id");

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(okJson("{}")));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
    }
}