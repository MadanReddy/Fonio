# 🚀 Fonio - AI-Powered Web Automation Framework

**Fonio** is an intelligent web automation framework that combines **Cucumber BDD**, **Selenium WebDriver**, and **AI/LLM integration** to create smart, self-healing test automation. It automatically generates element locators using AI, making test maintenance significantly easier.

## ✨ Key Features

- 🤖 **AI-Powered Locator Generation** - Automatically finds elements using natural language descriptions
- 🧠 **LLM Integration** - Uses local Ollama models for intelligent element detection
- 🎯 **Smart DOM Filtering** - Automatically detects Salesforce Lightning vs generic websites
- 🧪 **BDD Testing** - Cucumber feature files with TestNG execution
- 🔄 **Self-Healing Tests** - Fallback locators when primary selectors fail
- 🌐 **Multi-Browser Support** - Chrome WebDriver with extensible architecture

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Cucumber     │    │   GenericSteps  │    │   DomUtils      │
│   Feature      │───▶│   (Step Defs)   │───▶│   (DOM Filter)  │
│   Files        │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │   LLMClient     │    │   Selenium      │
                       │   (AI Engine)   │    │   WebDriver     │
                       └─────────────────┘    └─────────────────┘
```

## 📁 Project Structure

```
fonio/
├── src/
│   ├── main/java/com/example/
│   │   └── App.java                          # Main application entry point
│   └── test/
│       ├── java/com/example/
│       │   ├── runner/
│       │   │   └── CucumberTest.java         # Test runner configuration
│       │   ├── steps/
│       │   │   └── GenericSteps.java         # Step definitions with AI integration
│       │   └── utils/
│       │       ├── LLMClient.java            # AI/LLM integration client
│       │       └── DomUtils.java             # Smart DOM filtering utilities
│       └── resources/features/
│           └── SalesforceLogin.feature       # BDD feature files
├── pom.xml                                    # Maven dependencies
└── README.md                                  # This file
```

## 🚀 Quick Start

### Prerequisites

- **Java 11+** (Maven 3.6+)
- **Chrome Browser** (ChromeDriver will be auto-managed)
- **Ollama** with Mistral model installed locally

### 1. Install Ollama & Mistral

```bash
# Install Ollama (macOS)
brew install ollama

# Pull Mistral model
ollama pull mistral:7b

# Start Ollama service
ollama serve
```

### 2. Clone & Build

```bash
git clone <repository-url>
cd fonio
mvn clean compile
```

### 3. Run Tests

```bash
# Run all tests
mvn clean test

# Run with specific profile
mvn clean test -Dcucumber.filter.tags="@smoke"
```

## 🧪 Test Examples

### Feature File (BDD)

```gherkin
Feature: Salesforce Login

  Scenario: Login with valid credentials
    Given I navigate to "https://login.salesforce.com"
    When I enter username "testuser@example.com"
    And I enter password "Pass@123"
    And I click on "Login"
    Then I should see "Home"
```

### How It Works

1. **Natural Language Processing**: The step "I enter username" automatically asks the AI to find the username field
2. **DOM Analysis**: `DomUtils` filters the page HTML, removing noise and keeping relevant UI elements
3. **AI Locator Generation**: `LLMClient` sends the filtered DOM to Mistral with a prompt to find the best locator
4. **Smart Execution**: Selenium uses the AI-generated locator to interact with the element
5. **Fallback Support**: If the primary locator fails, fallback locators are automatically tried

## 🔧 Configuration

### LLM Settings

```java
// LLMClient.java
private static final String LLM_API_URL = "http://localhost:11434/api/chat";
private static final String MODEL_NAME = "mistral:7b";
```

### Browser Settings

```java
// GenericSteps.java
driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
```

### DOM Filtering

```java
// DomUtils.java
private static final int MAX_OUTPUT_CHARS = 80_000;        // LLM prompt size limit
private static final int SNIPPET_PARENT_DEPTH = 3;         // Context depth for snippets
private static final int SNIPPET_SIBLING_LIMIT = 12;       // Max siblings per context
```

## 🛠️ Dependencies

### Core Dependencies

| Component | Version | Purpose |
|-----------|---------|---------|
| **Cucumber** | 7.15.0 | BDD testing framework |
| **TestNG** | 7.8.0 | Test execution engine |
| **Selenium** | 4.11.0 | Web automation |
| **JSoup** | 1.17.2 | HTML parsing & filtering |
| **Apache HttpClient** | 5.2.1 | LLM API communication |
| **JSON** | 20230227 | Response parsing |

### Test Dependencies

```xml
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <version>7.15.0</version>
    <scope>test</scope>
</dependency>
```

## 🧠 AI Integration Details

### How Locator Generation Works

1. **DOM Filtering**: `DomUtils.filterRelevantHtml()` removes unnecessary elements and attributes
2. **Context Extraction**: `DomUtils.extractSnippetByDescription()` finds relevant HTML around target elements
3. **AI Prompting**: Structured prompts ask the LLM for reliable CSS/XPath selectors
4. **Response Parsing**: JSON responses are parsed to extract primary and fallback locators
5. **Validation**: Locators are validated before use in Selenium

### Example AI Prompt

```
You are a senior QA automation engineer.
Given this filtered HTML DOM: [filtered HTML]
Find the most reliable locator for the element described as: "username field".

Requirements:
- Always prefer a short, stable CSS selector for the primary locator if possible.
- Provide an XPath fallback only if necessary.
- Return ONLY valid JSON in this format:
{ "primary": "<locator>", "fallback": "<locator or empty string>" }
```

## 🔍 DOM Filtering Intelligence

### Salesforce Lightning Detection

- **SLDS Classes**: Automatically detects Salesforce Lightning Design System
- **Aura Attributes**: Identifies Lightning/Aura framework elements
- **Chrome Removal**: Strips navigation, headers, and non-essential UI
- **Attribute Preservation**: Keeps crucial attributes like `data-testid`, `aria-*`, etc.

### Generic Website Support

- **Semantic HTML**: Preserves meaningful structure and content
- **Hidden Element Removal**: Strips CSS-hidden and assistive elements
- **Attribute Optimization**: Keeps only relevant attributes for automation

## 📊 Test Reports

### Cucumber HTML Reports

```bash
# Reports are generated automatically
open target/cucumber-report.html
```

### TestNG Reports

```bash
# TestNG execution reports
open target/surefire-reports/
```

## 🚨 Troubleshooting

### Common Issues

1. **LLM Connection Failed**
   ```bash
   # Ensure Ollama is running
   ollama serve
   
   # Check if Mistral model is available
   ollama list
   ```

2. **ChromeDriver Issues**
   ```bash
   # Selenium 4.11+ auto-manages ChromeDriver
   # Ensure Chrome browser is installed
   ```

3. **DOM Filtering Errors**
   ```bash
   # Check JSoup dependency in pom.xml
   # Verify HTML content is valid
   ```

### Debug Mode

```java
// Enable detailed logging in GenericSteps
System.out.println("LLM suggested locator: " + locator);
System.out.println("Raw DOM snippet: " + domSnippet);
```

## 🔮 Future Enhancements

- [ ] **Multi-LLM Support** - OpenAI, Anthropic, local models
- [ ] **Visual AI Integration** - Screenshot-based element detection
- [ ] **Cross-Browser Support** - Firefox, Safari, Edge
- [ ] **Mobile Testing** - Appium integration
- [ ] **Performance Testing** - Load testing capabilities
- [ ] **CI/CD Integration** - GitHub Actions, Jenkins, GitLab CI

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Cucumber** - BDD testing framework
- **Selenium** - Web automation library
- **JSoup** - HTML parsing utilities
- **Ollama** - Local LLM hosting
- **Mistral AI** - Open-source language model

---

**Fonio** - Making web automation intelligent, one test at a time! 🎯

