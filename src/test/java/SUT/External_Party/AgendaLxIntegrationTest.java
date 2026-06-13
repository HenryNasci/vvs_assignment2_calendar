package SUT.External_Party;

import com.example.meetings.discover.AgendaLxProvider;
import com.example.meetings.discover.DiscoveredEvent;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AgendaLxProvider} against a WireMock stand-in for the
 * AgendaLx WordPress REST API (https://www.agendalx.pt/wp-json/agendalx/v1).
 *
 * These tests exercise the real HTTP client (RestClient), JSON,
 * and the provider's date/time/description parsing logic — without depending on the
 * live AgendaLx service.
 */
class AgendaLxProviderIT {

    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

    private WireMockServer wireMock;
    private AgendaLxProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        provider = new AgendaLxProvider("http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("isConfigured - should return true since AgendaLx is a public endpoint requiring no credentials")
    void isConfiguredIsAlwaysTrue() {
        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.name()).isEqualTo("Agenda Cultural de Lisboa");
    }

    @Test
    @DisplayName("search - should return parsed event with correct fields and send a browser-like User-Agent header")
    void searchReturnsParsedEventsAndSendsBrowserUserAgent() {
        String futureDate = LocalDate.now(LISBON).plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("fado"))
                .withQueryParam("per_page", equalTo("20"))
                .withHeader("User-Agent", matching("Mozilla.*"))
                .willReturn(okJson("""
                    [
                      {
                        "id": 123,
                        "title": { "rendered": "Noite de Fado" },
                        "description": ["<p>Uma noite de <b>fado</b> tradicional.</p>"],
                        "occurences": ["%s"],
                        "string_times": "qua: 21h30",
                        "link": "https://www.agendalx.pt/evento/noite-de-fado/",
                        "venue": {
                          "1": { "name": "Casa de Fado" }
                        }
                      }
                    ]
                    """.formatted(futureDate))));

        List<DiscoveredEvent> results = provider.search("fado");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.source()).isEqualTo("Agenda Cultural de Lisboa");
        assertThat(event.externalId()).isEqualTo("123");
        assertThat(event.title()).isEqualTo("Noite de Fado");
        assertThat(event.description()).isEqualTo("Uma noite de  fado  tradicional.");
        assertThat(event.venue()).isEqualTo("Casa de Fado");
        assertThat(event.url()).isEqualTo("https://www.agendalx.pt/evento/noite-de-fado/");
        assertThat(event.end()).isNull();

        ZonedDateTime expected = LocalDate.parse(futureDate).atTime(21, 30).atZone(LISBON);
        assertThat(event.start()).isEqualTo(expected.toInstant());
    }

    @Test
    @DisplayName("search - should fall back to 20:00 Lisbon time when string_times cannot be parsed")
    void searchFallsBackTo20hWhenStringTimesUnparseable() {
        String futureDate = LocalDate.now(LISBON).plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("exposicao"))
                .willReturn(okJson("""
                    [
                      {
                        "id": 456,
                        "title": { "rendered": "Exposição de Arte" },
                        "description": [],
                        "occurences": ["%s"],
                        "string_times": "todo o dia",
                        "link": "https://www.agendalx.pt/evento/exposicao/",
                        "venue": {}
                      }
                    ]
                    """.formatted(futureDate))));

        List<DiscoveredEvent> results = provider.search("exposicao");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.venue()).isNull();
        assertThat(event.description()).isNull();

        ZonedDateTime expected = LocalDate.parse(futureDate).atTime(LocalTime.of(20, 0)).atZone(LISBON);
        assertThat(event.start()).isEqualTo(expected.toInstant());
    }

    @Test
    @DisplayName("search - should skip events whose occurrences are all in the past")
    void searchSkipsEventsWithOnlyPastOccurrences() {
        String pastDate = LocalDate.now(LISBON).minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(okJson("""
                    [
                      {
                        "id": 789,
                        "title": { "rendered": "Evento Passado" },
                        "description": [],
                        "occurences": ["%s"],
                        "string_times": "21h00",
                        "link": "https://www.agendalx.pt/evento/passado/",
                        "venue": {}
                      }
                    ]
                    """.formatted(pastDate))));

        List<DiscoveredEvent> results = provider.search("anything");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should skip events with a blank title")
    void searchSkipsEventsWithBlankTitle() {
        String futureDate = LocalDate.now(LISBON).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(okJson("""
                    [
                      {
                        "id": 1,
                        "title": { "rendered": "" },
                        "description": [],
                        "occurences": ["%s"],
                        "string_times": "21h00",
                        "link": "https://www.agendalx.pt/evento/no-title/",
                        "venue": {}
                      }
                    ]
                    """.formatted(futureDate))));

        List<DiscoveredEvent> results = provider.search("anything");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should truncate descriptions longer than 600 characters and append an ellipsis")
    void searchTruncatesLongDescriptions() {
        String futureDate = LocalDate.now(LISBON).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String longText = "a".repeat(700);

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(okJson("""
                    [
                      {
                        "id": 2,
                        "title": { "rendered": "Evento Longo" },
                        "description": ["%s"],
                        "occurences": ["%s"],
                        "string_times": "21h00",
                        "link": "https://www.agendalx.pt/evento/longo/",
                        "venue": {}
                      }
                    ]
                    """.formatted(longText, futureDate))));

        List<DiscoveredEvent> results = provider.search("anything");

        assertThat(results).hasSize(1);
        String description = results.get(0).description();
        assertThat(description).hasSize(601); // 600 chars + ellipsis
        assertThat(description).endsWith("…");
    }

    @Test
    @DisplayName("search - should return an empty list when the AgendaLx API responds with a server error")
    void searchReturnsEmptyListOnServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(serverError()));

        List<DiscoveredEvent> results = provider.search("anything");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search - should return an empty list when the AgendaLx API responds with malformed JSON")
    void searchReturnsEmptyListOnMalformedJson() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(ok("not json").withHeader("Content-Type", "application/json")));

        List<DiscoveredEvent> results = provider.search("anything");

        assertThat(results).isEmpty();
    }
}