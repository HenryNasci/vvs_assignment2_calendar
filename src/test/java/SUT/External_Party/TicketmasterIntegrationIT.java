package SUT.External_Party;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.TicketmasterProvider;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TicketmasterProvider} against a WireMock stand-in for the
 * Ticketmaster Discovery API (https://app.ticketmaster.com/discovery/v2).
 */
class TicketmasterIntegrationIT {

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

    private TicketmasterProvider providerWithApiKey(String apiKey) {
        return new TicketmasterProvider(apiKey, "PT", "http://localhost:" + wireMock.port());
    }

    @Test
    @DisplayName("isConfigured - should return false when api-key is blank")
    void isConfiguredFalseWhenApiKeyBlank() {
        TicketmasterProvider provider = providerWithApiKey("");

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.name()).isEqualTo("Ticketmaster");
    }

    @Test
    @DisplayName("search - should return an empty list and not call the Ticketmaster API when not configured")
    void searchReturnsEmptyAndDoesNotCallApiWhenNotConfigured() {
        TicketmasterProvider provider = providerWithApiKey(null);

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/events.json")));
    }

    @Test
    @DisplayName("search - should return parsed event with correct fields, venue, and countryCode query param")
    void searchReturnsParsedEventsWithVenueAndCountryCode() {
        TicketmasterProvider provider = providerWithApiKey("test-api-key");

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("concert"))
                .withQueryParam("size", equalTo("20"))
                .withQueryParam("apikey", equalTo("test-api-key"))
                .withQueryParam("countryCode", equalTo("PT"))
                .willReturn(okJson("""
                    {
                      "_embedded": {
                        "events": [
                          {
                            "id": "G5diZbbZbAaAa",
                            "name": "Rock in Rio",
                            "url": "https://www.ticketmaster.pt/event/rock-in-rio",
                            "info": "Outdoor festival",
                            "dates": {
                              "start": { "dateTime": "2026-07-01T19:00:00Z" }
                            },
                            "_embedded": {
                              "venues": [
                                { "name": "Parque da Bela Vista" }
                              ]
                            }
                          }
                        ]
                      }
                    }
                    """)));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.source()).isEqualTo("Ticketmaster");
        assertThat(event.externalId()).isEqualTo("G5diZbbZbAaAa");
        assertThat(event.title()).isEqualTo("Rock in Rio");
        assertThat(event.description()).isEqualTo("Outdoor festival");
        assertThat(event.venue()).isEqualTo("Parque da Bela Vista");
        assertThat(event.url()).isEqualTo("https://www.ticketmaster.pt/event/rock-in-rio");
        assertThat(event.end()).isNull();
        assertThat(event.start()).isEqualTo(Instant.parse("2026-07-01T19:00:00Z"));
    }

    @Test
    @DisplayName("search - should omit the countryCode query param when country-code is blank")
    void searchOmitsCountryCodeWhenBlank() {
        TicketmasterProvider provider = new TicketmasterProvider("test-api-key", "", "http://localhost:" + wireMock.port());

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .willReturn(okJson("""
                    {
                      "_embedded": {
                        "events": []
                      }
                    }
                    """)));

        provider.search("concert");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withoutQueryParam("countryCode"));
    }

    @Test
    @DisplayName("search - should skip events with a TBA (missing) start dateTime")
    void searchSkipsEventsWithTbaDates() {
        TicketmasterProvider provider = providerWithApiKey("test-api-key");

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .willReturn(okJson("""
                    {
                      "_embedded": {
                        "events": [
                          {
                            "id": "TBA1",
                            "name": "TBA Event",
                            "url": "https://www.ticketmaster.pt/event/tba",
                            "dates": {
                              "start": {}
                            }
                          }
                        ]
                      }
                    }
                    """)));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should return a null venue when the event has no embedded venues")
    void searchHandlesEventWithoutVenue() {
        TicketmasterProvider provider = providerWithApiKey("test-api-key");

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .willReturn(okJson("""
                    {
                      "_embedded": {
                        "events": [
                          {
                            "id": "EV2",
                            "name": "No Venue Event",
                            "url": "https://www.ticketmaster.pt/event/no-venue",
                            "dates": {
                              "start": { "dateTime": "2026-08-01T10:00:00Z" }
                            }
                          }
                        ]
                      }
                    }
                    """)));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).venue()).isNull();
    }

    @Test
    @DisplayName("search - should return an empty list when the response has no _embedded events")
    void searchReturnsEmptyListWhenNoEmbeddedEvents() {
        TicketmasterProvider provider = providerWithApiKey("test-api-key");

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .willReturn(okJson("{}")));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should return an empty list when the Ticketmaster API responds with a server error")
    void searchReturnsEmptyListOnServerError() {
        TicketmasterProvider provider = providerWithApiKey("test-api-key");

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .willReturn(serverError()));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).isEmpty();
    }
}