package com.example.utils;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.StringReader;

public class LLMClient {

    private static final String LLM_API_URL = "http://localhost:11434/api/chat"; // Update if needed

    public String getActionForStep(String dom, String stepText) throws Exception {
        String prompt = String.format(
                "You are an automation assistant.\n" +
                        "Given this HTML DOM:\n%s\n" +
                        "And this instruction:\n%s\n" +
                        "Return a JSON with keys 'action', 'locator', and 'value' (if applicable).",
                dom, stepText);

        String requestBody = String.format(
                "{\n" +
                        "  \"model\": \"mistral:7b\",\n" +
                        "  \"messages\": [\n" +
                        "    {\"role\": \"system\", \"content\": \"You are a helpful assistant for web automation.\"},\n" +
                        "    {\"role\": \"user\", \"content\": \"%s\"}\n" +
                        "  ]\n" +
                        "}", escapeJson(prompt));

        String response = Request.post(LLM_API_URL)
                .bodyString(requestBody, ContentType.APPLICATION_JSON)
                .execute()
                .returnContent()
                .asString();

        try {
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("choices")) {
                JSONObject choice = jsonResponse.getJSONArray("choices").getJSONObject(0);
                String content = choice.getJSONObject("message").getString("content").trim();
                return cleanOutput(content);
            }
        } catch (Exception e) {
            System.out.println("Failed to parse LLM response: " + e.getMessage());
            System.out.println("Raw response: " + response);
        }
        return response;
    }

    public String askForLocator(String dom, String description) throws Exception {
        String snippet = DomUtils.extractSnippetByDescription(dom, description);
        System.out.println("Extracted snippet length: " + snippet.length());

//        String prompt = String.format(
//                "You are a senior QA automation engineer specializing in Selenium locators.\n" +
//                "Given this HTML DOM snippet:\n%s\n\n" +
//                "Find the most reliable locator for the element described as: \"%s\".\n\n" +
//                "LOCATOR PRIORITY ORDER:\n" +
//                "1. ID attribute (e.g., #username, #login-button)\n" +
//                "2. Name attribute (e.g., [name='username'])\n" +
//                "3. Data attributes (e.g., [data-testid='username'], [data-qa='login'])\n" +
//                "4. Unique class combinations (e.g., .login-form .username-input)\n" +
//                "5. XPath with text content (e.g., //input[@placeholder='Username'])\n\n" +
//                "REQUIREMENTS:\n" +
//                "- Return ONLY valid JSON in this exact format:\n" +
//                "{ \"primary\": \"<best_css_selector>\", \"fallback\": \"<xpath_fallback_or_empty>\" }\n" +
//                "- Primary locator should be CSS selector when possible\n" +
//                "- Fallback should be XPath only if CSS is not reliable\n" +
//                "- Do NOT include any explanation, markdown, or extra text\n" +
//                "- Ensure the locator is specific enough to find only one element\n" +
//                "- For input fields, prefer attributes like id, name, placeholder\n" +
//                "- For buttons, prefer text content or unique attributes\n\n" +
//                "EXAMPLES:\n" +
//                "- Username field: { \"primary\": \"#username\", \"fallback\": \"//input[@name='username']\" }\n" +
//                "- Login button: { \"primary\": \"button[type='submit']\", \"fallback\": \"//button[contains(text(),'Login')]\" }\n" +
//                "- Password field: { \"primary\": \"#password\", \"fallback\": \"//input[@type='password']\" }",
//                snippet, description
//        );

        String prompt = String.format("You are a senior QA automation engineer specializing in Selenium locators. Find the most reliable and maintainable locator for the element described, treating the description as intent and not requiring exact tag matches. FIRST normalize the description: lowercase, trim, collapse spaces. EXTRACT label tokens by removing generic UI words: button, link, tab, icon, image, img, label, field, box, div, span, section, panel, menu, card, header, footer, item, option, tile. The remaining token(s) are the TARGET LABEL (e.g., \"start\" from \"Start Button\"). DO NOT replace the target label with any other word if an exact match exists in the DOM.\n" +
                        "Matching precedence (in order, stop at first unique hit):\n" +
                        "1) Exact attribute equals TARGET LABEL (case-insensitive) on aria-label, title, alt, placeholder, value, data-testid, data-qa, name, id. Prefer CSS attribute equals (e.g., [aria-label='Start']) and target the clickable element (e.g., a/button) if the label is on a child.\n" +
                        "2) Exact visible text equals TARGET LABEL (case-insensitive) using XPath with normalize-space() and translate(), selecting the CLICKABLE element if the text is in a child. Example pattern: //*[self::button or self::a or @role='button' or @role='link'][.//*/text() or text()][translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='start']\n" +
                        "3) Visible text contains TARGET LABEL as a whole word (case-insensitive) when (1) and (2) yield no unique match. Use XPath with word-boundary logic via spaces, e.g., contains(concat(' ', translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), ' '), ' start ')\n" +
                        "4) ONLY IF no candidates found by (1)-(3), try controlled synonyms for the TARGET LABEL and repeat steps (1)-(3). Allowed synonyms map (use ONLY these; do not invent others): logout↔sign out↔log off↔exit; login↔sign in↔log on; submit↔save↔apply↔confirm; cancel↔close↔dismiss; search↔find↔lookup; settings↔preferences↔options↔configuration; profile↔user info↔account settings; start↔begin↔get started↔start now↔launch; delete↔remove↔trash; accounts↔my accounts↔customer accounts↔account list; home↔dashboard↔start page. If an exact TARGET LABEL match exists, do NOT use a synonym.\n" +
                        "5) As a last resort ONLY (no text/attribute matches), consider stable structural/attribute combos (e.g., unique data-* on a nav item). NEVER default to generic types like button[type='submit'] unless the TARGET LABEL (or its allowed synonyms) is “submit”.\n" +
                        "Uniqueness and quality rules:\n" +
                        "- The locator must match EXACTLY ONE element in the provided DOM. If a selector could match multiple, refine with parent/ancestor or unique attributes.\n" +
                        "- Choose the actual clickable element (e.g., the <a> or <button>), not the inner <span>/<i>, unless the wrapper is not clickable.\n" +
                        "- Prefer CSS when attributes provide a stable hook. If only visible text uniquely identifies the element, PRIMARY MAY BE XPATH (allowed) because CSS in Selenium cannot match inner text.\n" +
                        "- HARD BANS: do not output comma-separated multi-selectors; do not guess unrelated labels (e.g., \"Login\" or \"Submit\" for “Start”); do not rely on dynamic class fragments or index-based selectors unless absolutely necessary.\n" +
                        "Output:\n" +
                        "Return ONLY valid JSON exactly in this format: { \"primary\": \"<best_selector>\", \"fallback\": \"<xpath_fallback_or_empty>\" }\n" +
                        "- primary: CSS when attribute-based; otherwise XPath if text-only is the most reliable.\n" +
                        "- fallback: provide a different reliable XPath or empty string if not needed.\n" +
                        "- No explanations or extra text."+
                         " HTML DOM Snippet: "+snippet+" Find Element "+description);
        System.out.println("Prompt is: " + prompt);
        String requestBody = String.format(
                "{\n" +
                "  \"model\": \"mistral:7b\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are a QA automation expert. Always return valid JSON locators for Selenium.\"},\n" +
                "    {\"role\": \"user\", \"content\": \"%s\"}\n" +
                "  ],\n" +
                "  \"temperature\": 0.1,\n" +
                "  \"max_tokens\": 200\n" +
                "}", escapeJson(prompt)
        );

        System.out.println("Sending request to LLM for locator generation...");
        String response = Request.post(LLM_API_URL)
                .bodyString(requestBody, ContentType.APPLICATION_JSON)
                .execute()
                .returnContent()
                .asString();

        System.out.println("Raw LLM response length: " + response.length());

        // Parse the response more robustly
        String content = parseLLMResponse(response);
        System.out.println("Parsed content: " + content);

        // Try parsing as JSON
        try {
            JSONObject locatorJson = new JSONObject(content);
            System.out.println("LLM JSON locator for '" + description + "': " + locatorJson.toString());
            
            // Validate the JSON structure
            if (locatorJson.has("primary") && !locatorJson.getString("primary").trim().isEmpty()) {
                return locatorJson.toString();
            } else {
                throw new Exception("Invalid JSON structure - missing or empty primary locator");
            }
        } catch (Exception e) {
            System.err.println("Failed to parse locator JSON: " + e.getMessage());
            System.err.println("Content: " + content);
            
            // Try to extract JSON from the content
            String extractedJson = extractJsonFromText(content);
            if (extractedJson != null) {
                try {
                    JSONObject json = new JSONObject(extractedJson);
                    if (json.has("primary") && !json.getString("primary").trim().isEmpty()) {
                        System.out.println("Successfully extracted JSON: " + json.toString());
                        return json.toString();
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to parse extracted JSON: " + ex.getMessage());
                }
            }
        }

        // Generate intelligent fallback based on description
        String fallback = generateIntelligentFallback(description, snippet);
        System.out.println("Using intelligent fallback: " + fallback);
        return fallback;
    }
    
    private String parseLLMResponse(String response) {
        try {
            // Try to parse as standard Ollama response
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("choices")) {
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    if (choice.has("message")) {
                        JSONObject message = choice.getJSONObject("message");
                        if (message.has("content")) {
                            return message.getString("content").trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Not a standard response, try to extract content
        }
        
        // Fallback: try to find JSON-like content in the response
        String content = response.replaceAll("```[a-zA-Z]*", "").replace("```", "").trim();
        content = content.replaceAll("^`|`$", "").trim();
        return content;
    }
    
    private String extractJsonFromText(String text) {
        // Look for JSON-like patterns
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        
        if (start >= 0 && end > start) {
            String jsonCandidate = text.substring(start, end + 1);
            try {
                // Validate it's actually JSON
                new JSONObject(jsonCandidate);
                return jsonCandidate;
            } catch (Exception e) {
                // Not valid JSON
            }
        }
        return null;
    }
    
    private String generateIntelligentFallback(String description, String domSnippet) {
        String lowerDesc = description.toLowerCase();
        
        // Generate fallback based on description type
        if (lowerDesc.contains("user") || lowerDesc.contains("username") || lowerDesc.contains("email")) {
            return "{ \"primary\": \"input[type='text'], input[name*='user'], input[placeholder*='user']\", \"fallback\": \"//input[@type='text' and (@name='username' or @placeholder='Username')]\" }";
        } else if (lowerDesc.contains("pass")) {
            return "{ \"primary\": \"input[type='password']\", \"fallback\": \"//input[@type='password']\" }";
        } else if (lowerDesc.contains("login") || lowerDesc.contains("submit") || lowerDesc.contains("button")) {
            return "{ \"primary\": \"button[type='submit'], input[type='submit']\", \"fallback\": \"//button[contains(text(),'Login') or contains(text(),'Submit')]\" }";
        } else {
            // Generic fallback
            String safeId = description.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
            return "{ \"primary\": \"#" + safeId + "\", \"fallback\": \"//*[contains(text(),'" + description + "') or @placeholder='" + description + "']\" }";
        }
    }


    // --- Helpers ---
    private String parseStreamingLocator(String ndjson) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(ndjson))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    JSONObject obj = new JSONObject(line);
                    if (obj.has("message")) {
                        JSONObject message = obj.getJSONObject("message");
                        if (message.has("content")) {
                            sb.append(message.getString("content"));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private boolean isValidLocator(String locator) {
        return locator.startsWith("#") || locator.startsWith("//") || locator.contains("[") || locator.contains(".");
    }

    private String cleanOutput(String text) {
        return text
                .replaceAll("```[a-zA-Z]*", "")
                .replace("```", "")
                .replaceAll("^`|`$", "")
                .replaceAll("^LOCATOR:\\s*", "")
                .trim();
    }

    private String findLocatorFromDom(String dom, String keyword) {
        if (dom.contains("id=\"" + keyword + "\"")) {
            return "#" + keyword;
        }
        if (dom.contains("name=\"" + keyword + "\"")) {
            return "*[name='" + keyword + "']";
        }
        if (dom.toLowerCase().contains("label for=\"" + keyword + "\"")) {
            return "label[for='" + keyword + "'] + *";
        }
        return "//input[contains(@id,'" + keyword + "') or contains(@name,'" + keyword + "')]";
    }

    private String fallbackLocator(String description) {
        return "id=" + description.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
