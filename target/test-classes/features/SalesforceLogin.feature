Feature: Salesforce Login

  Scenario: Login with valid credentials
    Given I navigate to "https://login.test1.pc-rnd.salesforce.com"
    When I enter "mrhcagent@sf.com" in "UserName"
    When I enter "Summer2025" in "Password"
    And I click on "Login button"
    Then WaitFor {20000} seconds
    And I click on "Accounts link in navigation bar"
    Then WaitFor {20000} seconds
