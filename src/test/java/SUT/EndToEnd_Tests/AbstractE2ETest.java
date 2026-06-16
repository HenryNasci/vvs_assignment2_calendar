package SUT.EndToEnd_Tests;

import com.example.meetings.MeetingsApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import java.time.Duration;

/**
 * Base class for Selenium end-to-end tests.
 *
 * <p>Starts the real Spring MVC + Security + Thymeleaf application on a random port
 * ({@code webEnvironment = RANDOM_PORT}) against the "e2e" database profile (a throwaway
 * in-memory H2 instance — see {@code application-e2e.properties}), then drives it through a
 * headless Chrome browser.
 *
 * <p>{@code classes = MeetingsApplication.class} is required because these test classes live
 * outside the {@code com.example.meetings} package tree, so {@code @SpringBootTest} can't find
 * the {@code @SpringBootApplication} class by walking up the package hierarchy.
 */
@SpringBootTest(classes = MeetingsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
abstract class AbstractE2ETest {

    static final Duration WAIT = Duration.ofSeconds(10);

    @LocalServerPort
    protected int port;

    protected WebDriver driver;

    @BeforeEach
    void setUpDriver() {
        driver = newDriver();
        driver.get(url("/login"));
        wait(driver).until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
        driver.manage().deleteAllCookies();
    }

    @AfterEach
    void tearDownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox",
                "--disable-dev-shm-usage", "--window-size=1280,800");
        return new ChromeDriver(options);
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected WebDriverWait wait(WebDriver d) {
        return new WebDriverWait(d, WAIT);
    }

    /**
     * Fills and submits the registration form, then waits until the browser has left
     * the /register page (either to /login on success, or stays on /register on error).
     */
    protected void register(WebDriver d, String username, String email, String password) {
        d.get(url("/register"));
        d.manage().deleteAllCookies();
        d.get(url("/register"));
        wait(d).until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
        d.findElement(By.id("username")).sendKeys(username);
        d.findElement(By.id("email")).sendKeys(email);
        d.findElement(By.id("password")).sendKeys(password);
        d.findElement(By.cssSelector("form button[type=submit]")).click();
        wait(d).until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/login"),
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error"))
        ));
    }

    protected void register(String username, String email, String password) {
        register(driver, username, email, password);
    }

    /**
     * Fills and submits the login form, then waits until the browser has navigated to
     * /calendar (success) or the error message appears (failure).
     */
    protected void login(WebDriver d, String username, String password) {
        d.manage().deleteAllCookies();
        d.get(url("/login"));
        wait(d).until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
        d.findElement(By.id("username")).sendKeys(username);
        d.findElement(By.id("password")).sendKeys(password);
        d.findElement(By.cssSelector("form button[type=submit]")).click();
        wait(d).until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/calendar"),
                ExpectedConditions.urlContains("?error")
        ));
    }

    protected void login(String username, String password) {
        login(driver, username, password);
    }

    /**
     * Clicks the "Sign out" button in the nav bar, then waits until the browser
     * has reached the /login page.
     */
    protected void logout(WebDriver d) {
        wait(d).until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("nav form button[type=submit]")));
        d.findElement(By.cssSelector("nav form button[type=submit]")).click();
        wait(d).until(ExpectedConditions.urlContains("/login"));
    }

    protected void logout() {
        logout(driver);
    }
}