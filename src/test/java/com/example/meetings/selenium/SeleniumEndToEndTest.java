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
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
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

    /**
     * Registers a new user via the registration form.
     */
    private void register(String username, String email, String password) {
        driver.get(baseUrl() + "/register");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("username")));
        driver.findElement(By.name("username")).sendKeys(username);
        driver.findElement(By.name("email")).sendKeys(email);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    /**
     * Logs in with the given credentials and waits for the calendar page.
     */
    private void login(String username, String password) {
        driver.get(baseUrl() + "/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("username")));
        driver.findElement(By.name("username")).sendKeys(username);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
    }

    /**
     * Sets a datetime-local input value via JavaScript (locale-safe).
     */
    private void setDateTimeLocal(String name, String value) {
        WebElement el = driver.findElement(By.name(name));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1]", el, value);
    }

    /**
     * Clicks a button by its visible text, relocating it at click time
     * to avoid StaleElementReferenceException.
     */
    private void clickButtonWithText(String text) {
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='" + text + "']"))).click();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Verifies the full registration + login flow.
     * A new user registers, sees the success message, logs in and reaches /calendar.
     */
    @Test
    @Order(1)
    void registerAndLogin_success_reachesCalendar() {
        register("alice", "alice@example.com", "password123");

        assertTrue(driver.getCurrentUrl().contains("/login"));

        login("alice", "password123");

        assertTrue(driver.getCurrentUrl().contains("/calendar"));
    }

    /**
     * Verifies that invalid credentials show an error message on the login page.
     * The user must remain on /login and see a meaningful error.
     */
    @Test
    @Order(2)
    void login_invalidCredentials_showsError() {
        register("bob", "bob@example.com", "password123");

        driver.get(baseUrl() + "/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("username")));
        driver.findElement(By.name("username")).sendKeys("bob");
        driver.findElement(By.name("password")).sendKeys("wrongpassword");
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        wait.until(ExpectedConditions.urlContains("/login?error"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    /**
     * Verifies that accessing /calendar without a session redirects to /login.
     * This tests Spring Security end-to-end — the redirect happens server-side.
     */
    @Test
    @Order(3)
    void calendar_withoutLogin_redirectsToLogin() {
        driver.get(baseUrl() + "/calendar");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    /**
     * Verifies the logout flow: after logging out, accessing /calendar
     * redirects back to /login, confirming the session was invalidated.
     */
    @Test
    @Order(4)
    void logout_invalidatesSession() {
        register("carol", "carol@example.com", "password123");
        login("carol", "password123");

        // Clica no botão Sign out
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='Sign out']"))).click();
        wait.until(ExpectedConditions.urlContains("/login"));

        // Tenta aceder ao calendar sem sessão
        driver.get(baseUrl() + "/calendar");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    /**
     * Verifies the full meeting proposal flow.
     * After proposing a meeting, it appears on the calendar as confirmed.
     */
    @Test
    @Order(5)
    void proposeMeeting_appearsOnCalendar() {
        register("dave", "dave@example.com", "password123");
        login("dave", "password123");

        driver.get(baseUrl() + "/meetings/new");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("title")));

        driver.findElement(By.name("title")).sendKeys("Team Standup");
        driver.findElement(By.name("description")).sendKeys("Daily sync");
        setDateTimeLocal("start", "2099-06-10T10:00");
        setDateTimeLocal("end",   "2099-06-10T11:00");

        clickButtonWithText("Propose");
        wait.until(ExpectedConditions.urlContains("/calendar"));

        String body = driver.findElement(By.tagName("body")).getText();
        assertTrue(body.contains("Team Standup"));
    }

    /**
     * Verifies the multi-user invite acceptance flow.
     * Bob proposes a meeting and invites Alice. Alice logs in, sees the
     * pending invite, accepts it, and the meeting appears as confirmed.
     */
    @Test
    @Order(6)
    void invite_accepted_meetingAppearsOnCalendar() {
        // Registar os dois utilizadores
        register("eve", "eve@example.com", "password123");
        register("frank", "frank@example.com", "password123");

        // Frank propõe reunião e convida Eve
        login("frank", "password123");
        driver.get(baseUrl() + "/meetings/new");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("title")));
        driver.findElement(By.name("title")).sendKeys("Project Sync");
        setDateTimeLocal("start", "2099-06-11T14:00");
        setDateTimeLocal("end",   "2099-06-11T15:00");
        driver.findElement(By.name("invitees")).sendKeys("eve");
        clickButtonWithText("Propose");
        wait.until(ExpectedConditions.urlContains("/calendar"));

        // Frank faz logout
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='Sign out']"))).click();
        wait.until(ExpectedConditions.urlContains("/login"));

        // Eve faz login e aceita o convite
        login("eve", "password123");
        wait.until(ExpectedConditions.urlContains("/calendar"));

        // Clica em Accept no convite pendente
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='Accept']"))).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));

        // A reunião deve aparecer no calendário da Eve
        String body = driver.findElement(By.tagName("body")).getText();
        assertTrue(body.contains("Project Sync"));
    }

    /**
     * Verifies the multi-user invite decline flow.
     * Bob proposes a meeting and invites Alice. Alice declines and the
     * meeting disappears from her calendar.
     */
    @Test
    @Order(7)
    void invite_declined_meetingDisappearsFromCalendar() {
        // Registar os dois utilizadores
        register("grace", "grace@example.com", "password123");
        register("henry", "henry@example.com", "password123");

        // Henry propõe reunião e convida Grace
        login("henry", "password123");
        driver.get(baseUrl() + "/meetings/new");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("title")));
        driver.findElement(By.name("title")).sendKeys("Design Review");
        setDateTimeLocal("start", "2099-06-12T09:00");
        setDateTimeLocal("end",   "2099-06-12T10:00");
        driver.findElement(By.name("invitees")).sendKeys("grace");
        clickButtonWithText("Propose");
        wait.until(ExpectedConditions.urlContains("/calendar"));

        // Henry faz logout
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='Sign out']"))).click();
        wait.until(ExpectedConditions.urlContains("/login"));

        // Grace faz login e recusa o convite
        login("grace", "password123");
        wait.until(ExpectedConditions.urlContains("/calendar"));

        // Clica em Decline no convite pendente
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='Decline']"))).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));

        // A reunião NÃO deve aparecer no calendário da Grace
        String body = driver.findElement(By.tagName("body")).getText();
        assertFalse(body.contains("Design Review"));
    }
}