package com.example.steps;

import com.example.utils.DomUtils;
import io.cucumber.java.*;
import io.cucumber.java.en.*;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.Assert;
import com.example.utils.LLMClient;

import java.time.Duration;

public class GenericSteps {

    private static WebDriver driver;
    private static LLMClient llmClient;

    @Before
    public void setup() {
        if (driver == null) {
            // Make sure ChromeDriver is in PATH or set system property
            driver = new ChromeDriver();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
        if (llmClient == null) {
            llmClient = new LLMClient();
        }
    }

    @After
    public void teardown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    @Given("I navigate to {string}")
    public void i_navigate_to(String url) {
        System.out.println("Navigating to: " + url);
        driver.get(url);
    }



    @When("I enter username {string}")
    public void i_enter_username(String username) throws Exception {
        String locator = getLocatorForElement("username field");
        LocatorHolder locators = parseLocator(locator);
        try {
            driver.findElement(locators.primary).sendKeys(username);
        } catch (NoSuchElementException e) {
            if (locators.fallback != null) {
                driver.findElement(locators.fallback).sendKeys(username);
            } else {
                throw e;
            }
        }
    }

    @When("I enter password {string}")
    public void i_enter_password(String password) throws Exception {
        String locator = getLocatorForElement("password field");
        LocatorHolder locators = parseLocator(locator);
        try {
            driver.findElement(locators.primary).sendKeys(password);
        } catch (NoSuchElementException e) {
            if (locators.fallback != null) {
                driver.findElement(locators.fallback).sendKeys(password);
            } else {
                throw e;
            }
        }
    }

    @When("I click on {string}")
    public void i_click_on(String buttonText) throws Exception {
        String locator = getLocatorForElement( buttonText );
        LocatorHolder locators = parseLocator(locator);
        try {

            WebElement element = driver.findElement(locators.primary);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);

        } catch (NoSuchElementException e) {
            if (locators.fallback != null) {
                WebElement element = driver.findElement(locators.fallback);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);

            } else {
                throw e;
            }
        }

    }

    @Then("I should see {string}")
    public void i_should_see(String expectedText) {
        String pageSource = driver.getPageSource();
        Assert.assertTrue(pageSource.contains(expectedText),
                "Expected to see text: " + expectedText);
    }

    /**
     * Helper method to get locator for an element and print it
     */
    private String getLocatorForElement(String elementDescription) throws Exception {
        String dom = DomUtils.filterRelevantHtml(driver.getPageSource());
        String locator = llmClient.askForLocator(dom, elementDescription);
        System.out.println("LLM suggested locator for '" + elementDescription + "': " + locator);
        return locator;
    }

    private LocatorHolder parseLocator(String locatorResponse) {
        if (locatorResponse == null || locatorResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Locator response is null or empty");
        }

        locatorResponse = locatorResponse.trim();

        try {
            // 🔹 If LLM returned JSON format { "primary": "...", "fallback": "..." }
            JSONObject obj = new JSONObject(locatorResponse);
            By primary = parseSingleLocator(obj.getString("primary"));
            By fallback = obj.has("fallback") && !obj.getString("fallback").isBlank()
                    ? parseSingleLocator(obj.getString("fallback"))
                    : null;
            return new LocatorHolder(primary, fallback);
        } catch (Exception e) {
            // 🔹 Not JSON → treat it as a single locator string
            return new LocatorHolder(parseSingleLocator(locatorResponse), null);
        }
    }

    private By parseSingleLocator(String locator) {
        if (locator == null || locator.trim().isEmpty()) {
            throw new IllegalArgumentException("Locator string is null or empty");
        }

        locator = locator.trim();

        // Handle Selenium-style prefixes
        if (locator.toLowerCase().startsWith("id=")) {
            return By.id(locator.substring(3).trim());
        }
        if (locator.toLowerCase().startsWith("name=")) {
            return By.name(locator.substring(5).trim());
        }
        if (locator.toLowerCase().startsWith("css=")) {
            return By.cssSelector(locator.substring(4).trim());
        }
        if (locator.toLowerCase().startsWith("xpath=")) {
            return By.xpath(locator.substring(6).trim());
        }

        // Auto-detect XPath
        if (locator.startsWith("/") || locator.startsWith("(")) {
            return By.xpath(locator);
        }

        // Default to CSS selector
        return By.cssSelector(locator);
    }

    @Then("WaitFor \\{{int}} seconds")
    public void waitforSeconds(int seconds  ) {
        try{
            Thread.sleep(seconds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @When("I enter {string} in {string}")
    public void iEnterIn(String arg0, String arg1) throws Exception {
        String locator = getLocatorForElement(arg1);
        LocatorHolder locators = parseLocator(locator);
        try {
            driver.findElement(locators.primary).sendKeys(arg0);
        } catch (NoSuchElementException e) {
            if (locators.fallback != null) {
                driver.findElement(locators.fallback).sendKeys(arg0);
            } else {
                throw e;
            }
        }
    }
}
