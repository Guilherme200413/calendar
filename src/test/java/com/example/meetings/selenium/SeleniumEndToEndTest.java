package com.example.meetings.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeleniumEndToEndTest {

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** The "Sign out" button is on every authenticated page; used as a "page ready" marker. */
    private void waitForAuthenticatedPage() {
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[normalize-space()='Sign out']")));
    }

    /** Sets an input's value via JS and fires input/change so any client model updates. */
    private void setValue(String name, String value) {
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(By.name(name)));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];" +
                "arguments[0].dispatchEvent(new Event('input',  { bubbles: true }));" +
                "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                el, value);
    }

    /** Submits the form that owns the named field, via JS (reliable in this Chrome build). */
    private void submitFormOf(String fieldName) {
        WebElement el = driver.findElement(By.name(fieldName));
        ((JavascriptExecutor) driver).executeScript(
                "var f = arguments[0].form || arguments[0].closest('form'); f.submit();", el);
    }

    private void register(String username, String email, String password) {
        driver.get(baseUrl() + "/register");
        setValue("username", username);
        setValue("email", email);
        setValue("password", password);
        submitFormOf("username");
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    private void login(String username, String password) {
        driver.get(baseUrl() + "/login");
        setValue("username", username);
        setValue("password", password);
        submitFormOf("username");
        wait.until(ExpectedConditions.urlContains("/calendar"));
        waitForAuthenticatedPage();
    }

    /**
     * Logs out by submitting the Spring Security logout form via JS (carries the
     * CSRF hidden input). Polls until the redirect to /login happens; falls back
     * to clicking "Sign out" if the button is not inside a <form>. Logout is
     * idempotent, so an extra attempt while the page navigates is harmless.
     */
    private void logout() {
        wait.until(d -> {
            if (d.getCurrentUrl().contains("/login")) {
                return true;
            }
            Boolean submitted = (Boolean) ((JavascriptExecutor) d).executeScript(
                    "var f = document.querySelector(\"form[action*='/logout']\");" +
                    "if (f) { f.submit(); return true; } return false;");
            if (Boolean.FALSE.equals(submitted)) {
                try {
                    d.findElement(By.xpath("//button[normalize-space()='Sign out']")).click();
                } catch (WebDriverException transitional) {
                    // page still in transition; retry on the next polling cycle
                }
            }
            return d.getCurrentUrl().contains("/login");
        });
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    /**
     * Fills and submits the new-meeting form, then waits for the calendar page.
     * Pass invitees = null (or empty) when there are no invitees.
     */
    private void proposeMeeting(String title, String description,
                                String start, String end, String invitees) {
        driver.get(baseUrl() + "/meetings/new");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("title")));

        setValue("title", title);
        if (description != null && !description.isEmpty()) {
            setValue("description", description);
        }
        setValue("start", start);
        setValue("end", end);
        if (invitees != null && !invitees.isEmpty()) {
            setValue("invitees", invitees);
        }

        submitFormOf("title");

        wait.until(ExpectedConditions.urlContains("/calendar"));
        waitForAuthenticatedPage();
    }

    /**
     * Responds to a pending invite (Accept/Decline).
     *
     * Accept and Decline are two separate forms posting to the same
     * /meetings/{id}/respond endpoint; the choice is carried by a hidden
     * "action" input, not by the button, so submitting the button's owning form
     * via JS (reliable in this Chrome build) preserves the accept/decline intent
     * and includes the CSRF token. The response reloads the page in place (the
     * URL stays /calendar), so instead of waiting for a URL change we wait for
     * the submitted button to go stale, then for the reloaded page to be ready.
     */
    private void respondToInvite(String buttonText) {
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[normalize-space()='" + buttonText + "']")));
        ((JavascriptExecutor) driver).executeScript(
                "var f = arguments[0].form || arguments[0].closest('form'); f.submit();", btn);
        wait.until(ExpectedConditions.stalenessOf(btn));
        waitForAuthenticatedPage();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    void registerAndLogin_success_reachesCalendar() {
        register("alice", "alice@example.com", "password123");
        assertTrue(driver.getCurrentUrl().contains("/login"));
        login("alice", "password123");
        assertTrue(driver.getCurrentUrl().contains("/calendar"));
    }

    @Test
    @Order(2)
    void login_invalidCredentials_showsError() {
        register("bob", "bob@example.com", "password123");

        driver.get(baseUrl() + "/login");
        setValue("username", "bob");
        setValue("password", "wrongpassword");
        submitFormOf("username");

        wait.until(ExpectedConditions.urlContains("/login?error"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    @Test
    @Order(3)
    void calendar_withoutLogin_redirectsToLogin() {
        driver.get(baseUrl() + "/calendar");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    @Test
    @Order(4)
    void logout_invalidatesSession() {
        register("carol", "carol@example.com", "password123");
        login("carol", "password123");

        logout();

        driver.get(baseUrl() + "/calendar");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    @Test
    @Order(5)
    void proposeMeeting_appearsOnCalendar() {
        register("dave", "dave@example.com", "password123");
        login("dave", "password123");

        proposeMeeting("Team Standup", "Daily sync",
                "2099-06-10T10:00", "2099-06-10T11:00", null);

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "Team Standup"));
    }

    @Test
    @Order(6)
    void invite_accepted_meetingAppearsOnCalendar() {
        register("eve", "eve@example.com", "password123");
        register("frank", "frank@example.com", "password123");

        // Frank proposes a meeting and invites Eve
        login("frank", "password123");
        proposeMeeting("Project Sync", null,
                "2099-06-11T14:00", "2099-06-11T15:00", "eve");
        logout();

        // Eve logs in and accepts the invite
        login("eve", "password123");
        respondToInvite("Accept");

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "Project Sync"));
    }

    @Test
    @Order(7)
    void invite_declined_meetingDisappearsFromCalendar() {
        register("grace", "grace@example.com", "password123");
        register("henry", "henry@example.com", "password123");

        // Henry proposes a meeting and invites Grace
        login("henry", "password123");
        proposeMeeting("Design Review", null,
                "2099-06-12T09:00", "2099-06-12T10:00", "grace");
        logout();

        // Grace logs in and declines the invite
        login("grace", "password123");
        respondToInvite("Decline");

        // The declined meeting must no longer be on Grace's calendar
        String body = driver.findElement(By.tagName("body")).getText();
        assertFalse(body.contains("Design Review"));
    }
}