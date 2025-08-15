Feature: Generic Web Application Demo

  Scenario: Form interaction and validation
    Given I navigate to "https://the-internet.herokuapp.com/login"
    When I click on "Username field"
    And I enter "tomsmith" in "Username field"
    And I click on "Password field"
    And I enter "SuperSecretPassword!" in "Password field"
    And I click on "Login button"
    Then WaitFor {2000} seconds
    And I should see "Secure Area"
    When I click on "Logout"
    Then WaitFor {2000} seconds
    And I should see "Login Page"

  Scenario: Dynamic content and navigation
    Given I navigate to "https://the-internet.herokuapp.com/dynamic_loading/1"
    When I click on "Start"
    Then WaitFor {5000} seconds
    And I should see "Hello World!"
    When I click on "Back to examples link"
    Then WaitFor {2000} seconds
    And I should see "Dynamic Loading"
    When I click on "Example 2 link"
    Then WaitFor {2000} seconds
    And I should see "Example 2"
    When I click on "Start button"
    Then WaitFor {3000} seconds
    And I should see "Hello World!"
