package com.example.runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.Test;

@CucumberOptions(
    features = "src/test/resources/features",
    glue = "com.example.steps",
    plugin = {"pretty", "html:target/cucumber-report.html"}
)
@Test
public class CucumberTest extends AbstractTestNGCucumberTests {
}
