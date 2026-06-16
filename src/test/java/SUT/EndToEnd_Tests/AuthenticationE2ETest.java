package SUT.EndToEnd_Tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the registration, login, and logout flows, driven through a real
 * browser against the running application and its "e2e" test database.
 */
class AuthenticationE2ETest extends AbstractE2ETest {

    private static String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("register then login - should let a new user sign in and reach the calendar")
    void registerThenLoginReachesCalendar() {
        String username = uniqueUsername("user");

        register(username, username + "@example.com", "s3cret123");

        wait(driver).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success")));
        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(driver.findElement(By.cssSelector(".success")).getText())
                .contains("Account created");

        login(username, "s3cret123");

        assertThat(driver.getCurrentUrl()).endsWith("/calendar");
        wait(driver).until(ExpectedConditions.visibilityOfElementLocated(By.tagName("nav")));
        assertThat(driver.findElement(By.tagName("nav")).getText()).contains(username);
    }

    @Test
    @DisplayName("register - should reject a username that is already taken")
    void registerRejectsDuplicateUsername() {
        String username = uniqueUsername("user");
        register(username, username + "@example.com", "s3cret123");
        login(username, "s3cret123");
        logout();

        register(username, "another@example.com", "different123");

        assertThat(driver.getCurrentUrl()).contains("/register");
        assertThat(driver.findElement(By.cssSelector(".error")).getText())
                .contains("Username already taken");
    }

    @Test
    @DisplayName("login - should show an error message for unknown credentials")
    void loginWithUnknownUserShowsError() {
        login(uniqueUsername("no-such-user"), "whatever123");

        assertThat(driver.getCurrentUrl()).contains("?error");
        wait(driver).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));
        assertThat(driver.findElement(By.cssSelector(".error")).getText())
                .contains("Invalid username or password");
    }

    @Test
    @DisplayName("logout - should sign the user out and show a confirmation on the login page")
    void logoutShowsConfirmationOnLoginPage() {
        String username = uniqueUsername("user");
        register(username, username + "@example.com", "s3cret123");
        login(username, "s3cret123");

        logout();

        wait(driver).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success")));
        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(driver.findElement(By.cssSelector(".success")).getText())
                .containsIgnoringCase("signed out");
    }
}