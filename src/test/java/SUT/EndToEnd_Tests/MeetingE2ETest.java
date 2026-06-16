package SUT.EndToEnd_Tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for proposing meetings and responding to invites, driven through real
 * browser sessions against the running application and its "e2e" test database.
 *
 * <p>The invite/accept/decline scenarios use a second {@link WebDriver} session to simulate a
 * second user, since each browser session has its own cookie jar / Spring Security session.
 */
class MeetingE2ETest extends AbstractE2ETest {

    private static final DateTimeFormatter DATETIME_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private WebDriver secondDriver;

    @AfterEach
    void tearDownSecondDriver() {
        if (secondDriver != null) {
            secondDriver.quit();
        }
    }

    private static String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String inDays(int days, int hour) {
        return LocalDateTime.now().plusDays(days).withHour(hour).withMinute(0).withSecond(0).withNano(0)
                .format(DATETIME_LOCAL);
    }

    @Test
    @DisplayName("propose a meeting with no invitees - should appear on the calendar as confirmed")
    void proposeMeetingWithoutInviteesIsConfirmed() {
        String username = uniqueUsername("organizer");
        register(username, username + "@example.com", "s3cret123");
        login(username, "s3cret123");

        String title = "Solo planning " + UUID.randomUUID().toString().substring(0, 6);
        proposeMeeting(driver, title, "Plan the week", inDays(1, 10), inDays(1, 11), "");

        assertThat(driver.getCurrentUrl()).endsWith("/calendar");

        WebElement meeting = findMeetingByTitle(driver, title);
        assertThat(meeting.findElement(By.cssSelector(".badge.confirmed")).getText())
                .isEqualToIgnoringCase("confirmed");
    }

    @Test
    @DisplayName("propose a meeting - should reject an end time before the start time")
    void proposeMeetingRejectsInvalidTimeRange() {
        String username = uniqueUsername("organizer");
        register(username, username + "@example.com", "s3cret123");
        login(username, "s3cret123");

        proposeMeeting(driver, "Backwards meeting", "", inDays(1, 11), inDays(1, 10), "");

        assertThat(driver.getCurrentUrl()).contains("/meetings/new");
        assertThat(driver.findElement(By.cssSelector(".error")).getText())
                .contains("End time must be after start time");
    }

    @Test
    @DisplayName("invite a user - meeting is tentative for the organizer until the invitee accepts")
    void invitedUserCanAcceptAndMeetingBecomesConfirmedForBoth() {
        String organizer = uniqueUsername("organizer");
        String invitee = uniqueUsername("invitee");
        register(organizer, organizer + "@example.com", "s3cret123");
        register(invitee, invitee + "@example.com", "s3cret123");

        login(organizer, "s3cret123");
        String title = "Sync " + UUID.randomUUID().toString().substring(0, 6);
        proposeMeeting(driver, title, "Catch up", inDays(2, 14), inDays(2, 15), invitee);

        assertThat(driver.getCurrentUrl()).endsWith("/calendar");
        assertThat(findMeetingByTitle(driver, title).findElement(By.cssSelector(".badge.tentative")).getText())
                .isEqualToIgnoringCase("tentative");

        secondDriver = newDriver();
        login(secondDriver, invitee, "s3cret123");

        WebElement pendingInvite = secondDriver.findElement(By.cssSelector(".invite"));
        assertThat(pendingInvite.getText()).contains(title);
        pendingInvite.findElement(By.cssSelector("button[type=submit]")).click();

        assertThat(findMeetingByTitle(secondDriver, title).findElement(By.cssSelector(".badge.confirmed")).getText())
                .isEqualToIgnoringCase("confirmed");
        assertThat(secondDriver.findElements(By.cssSelector(".invite"))).isEmpty();

        driver.get(url("/calendar"));
        assertThat(findMeetingByTitle(driver, title).findElement(By.cssSelector(".badge.confirmed")).getText())
                .isEqualToIgnoringCase("confirmed");
    }

    @Test
    @DisplayName("invite a user - declining removes the meeting from the invitee's calendar")
    void invitedUserCanDeclineAndMeetingDisappearsFromTheirCalendar() {
        String organizer = uniqueUsername("organizer");
        String invitee = uniqueUsername("invitee");
        register(organizer, organizer + "@example.com", "s3cret123");
        register(invitee, invitee + "@example.com", "s3cret123");

        login(organizer, "s3cret123");
        String title = "Optional sync " + UUID.randomUUID().toString().substring(0, 6);
        proposeMeeting(driver, title, "", inDays(3, 9), inDays(3, 10), invitee);

        secondDriver = newDriver();
        login(secondDriver, invitee, "s3cret123");

        WebElement pendingInvite = secondDriver.findElement(By.cssSelector(".invite"));
        assertThat(pendingInvite.getText()).contains(title);
        List<WebElement> buttons = pendingInvite.findElements(By.cssSelector("button[type=submit]"));
        buttons.get(1).click(); // second button is Decline

        assertThat(secondDriver.findElements(By.cssSelector(".invite"))).isEmpty();
        boolean stillOnCalendar = secondDriver.findElements(By.cssSelector(".meeting")).stream()
                .anyMatch(m -> m.getText().contains(title));
        assertThat(stillOnCalendar).isFalse();
    }

    private void proposeMeeting(WebDriver d, String title, String description, String start, String end, String invitees) {
        d.get(url("/meetings/new"));
        wait(d).until(ExpectedConditions.visibilityOfElementLocated(By.id("title")));
        d.findElement(By.id("title")).sendKeys(title);
        if (description != null && !description.isBlank()) {
            d.findElement(By.id("description")).sendKeys(description);
        }
        setDateTimeLocal(d, By.id("start"), start);
        setDateTimeLocal(d, By.id("end"), end);
        if (invitees != null && !invitees.isBlank()) {
            d.findElement(By.id("invitees")).sendKeys(invitees);
        }

        d.findElement(By.cssSelector("form[action='/meetings/new'] button[type=submit]")).click();
        wait(d).until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/calendar"),
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error"))
        ));
    }

    /**
     * {@code datetime-local} inputs don't reliably accept {@code sendKeys} with ISO-formatted
     * text across browsers/locales, so set the value via JS and fire the events the page expects.
     */
    private static void setDateTimeLocal(WebDriver d, By locator, String value) {
        WebElement element = d.findElement(locator);
        ((JavascriptExecutor) d).executeScript(
                "arguments[0].value = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input'));" +
                        "arguments[0].dispatchEvent(new Event('change'));",
                element, value);
    }

    private static WebElement findMeetingByTitle(WebDriver d, String title) {
        return d.findElements(By.cssSelector(".meeting")).stream()
                .filter(m -> m.getText().contains(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No meeting found with title: " + title));
    }
}