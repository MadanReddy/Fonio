package com.example.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Pattern;

/**
 * DOM filtering utilities for Fonio.
 * - Auto-detects Salesforce Lightning vs generic sites.
 * - Keeps actionable, visible UI and crucial attributes.
 * - Optional: extracts a compact snippet around a natural-language description.
 */
public class DomUtils {

    // ---------- Config ----------
    private static final int SNIPPET_PARENT_DEPTH = 3;     // how far up to climb for snippet context
    private static final int SNIPPET_SIBLING_LIMIT = 12;   // max siblings to include per context node
    private static final int MAX_OUTPUT_CHARS = 200_000;    // safety cap for LLM prompts

    private static final Set<String> KEEP_TAGS = new HashSet<>(Arrays.asList(
            "a","button","input","select","option","textarea","label",
            "form","fieldset","legend",
            "table","thead","tbody","tfoot","tr","th","td",
            "ul","ol","li",
            "div","span","section","article","aside","nav","main","header","footer",
            "h1","h2","h3","h4","h5","h6","p","small","strong","em"
    ));

    private static final Set<String> STRIP_TAGS = new HashSet<>(Arrays.asList(
            "script","style","noscript","template","svg","canvas","video","audio","source",
            "iframe","object","embed","picture","track","map","area","meta","link"
    ));

    // Common “hidden” class names across sites
    private static final Set<String> HIDDEN_CLASSES = new HashSet<>(Arrays.asList(
            "hidden","is-hidden","visually-hidden","sr-only","srOnly","a11y-hidden",
            "slds-hide","slds-assistive-text","uiInput--hidden","hide","d-none"
    ));

    // Salesforce-specific IDs/classes to drop (chrome, header, footer, etc.)
    private static final Set<String> SF_REMOVE_IDS = new HashSet<>(Arrays.asList(
            "oneHeader","navMenu","tabBar","globalHeader","appNav","publisherFooter","footer"
    ));

    private static final Set<String> SF_REMOVE_CLASSES = new HashSet<>(Arrays.asList(
            "slds-global-header","slds-context-bar","branding-header","utilityBar","appLauncher",
            "slds-page-header","slds-global-actions","forceHeader","navigationMenu"
    ));

    // Attribute allow-list (others get dropped to reduce prompt noise)
    private static final Set<String> KEEP_ATTRS_COMMON = new HashSet<>(Arrays.asList(
            "id","name","type","value","placeholder","title","role","href","for"
    ));
    private static final Set<String> KEEP_ATTR_PREFIXES = new HashSet<>(Arrays.asList(
            "aria-","data-","data-testid","data-test","data-qa"
    ));

    // ---------- Public API ----------




    public static String filterRelevantHtml(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            System.err.println("Warning: Input HTML is null or empty");
            return "";
        }

        try {
            System.out.println("Starting DOM filtering... Input length: " + rawHtml.length());
            
            Document doc = Jsoup.parse(rawHtml);
            if (doc == null || doc.body() == null) {
                System.err.println("Error: Jsoup failed to parse HTML");
                return rawHtml.length() > MAX_OUTPUT_CHARS ? rawHtml.substring(0, MAX_OUTPUT_CHARS) : rawHtml;
            }
            
            boolean isSalesforce = isSalesforceLightning(doc);
            System.out.println("Detected Salesforce Lightning: " + isSalesforce);

            baseStrip(doc);
            System.out.println("Base strip completed");
            
            stripHidden(doc, isSalesforce);
            System.out.println("Hidden elements stripped");
            
            if (isSalesforce) {
                stripSalesforceChromeSafe(doc);
                System.out.println("Salesforce chrome stripped");
            }

            unwrapNonKeptTags(doc);
            System.out.println("Non-kept tags unwrapped");
            
            pruneAttributesSafe(doc);
            System.out.println("Attributes pruned");
            
            removeEmptyNodes(doc);
            System.out.println("Empty nodes removed");

            String out = compact(doc.body().html());
            if (out == null || out.isEmpty()) {
                System.err.println("Warning: Filtered HTML is empty, returning original");
                return rawHtml.length() > MAX_OUTPUT_CHARS ? rawHtml.substring(0, MAX_OUTPUT_CHARS) : rawHtml;
            }
            
            String result = cap(out, MAX_OUTPUT_CHARS);
            System.out.println("DOM filtering completed. Output length: " + result.length());
            return result;

        } catch (Exception e) {
            System.err.println("Error filtering HTML: " + e.getMessage());
            System.err.println("Stack trace: " + e.toString());
            e.printStackTrace();
            
            // Return a simplified version of the HTML if processing fails
            if (rawHtml.length() > MAX_OUTPUT_CHARS) {
                return rawHtml.substring(0, MAX_OUTPUT_CHARS);
            }
            return rawHtml;
        }
    }

    /** Detects if this is a Salesforce Lightning DOM. */
    private static boolean isSalesforceLightning(Document doc) {
        // More specific Salesforce detection
        return doc.select("body.auraBody, #auraLoadingBox, .slds-global-header, [data-aura-class]").size() > 0 ||
               doc.select("div[class*='slds-'], div[class*='force'], div[class*='ui']").size() > 5;
    }

    /** Removes scripts, styles, meta, link, and other non-UI noise. */
    private static void baseStrip(Document doc) {
        // More selective removal - keep some meta elements that might be useful
        doc.select("script, style, noscript, svg, canvas, video, audio, source, track").remove();
        // Keep link and meta but remove non-essential ones
        doc.select("link[rel='stylesheet'], link[rel='icon']").remove();
        doc.select("meta[name='viewport'], meta[charset]").remove();
    }

    /** Removes truly hidden elements. */
    private static void stripHidden(Document doc, boolean isSalesforce) {
        for (Element el : doc.select("*")) {
            String style = el.attr("style").toLowerCase();
            String hiddenAttr = el.attr("hidden");
            String ariaHidden = el.attr("aria-hidden");

            boolean isHidden = (style.contains("display:none") || 
                              style.contains("visibility:hidden") || 
                              !hiddenAttr.isEmpty() ||
                              "true".equals(ariaHidden));
            
            if (isHidden) {
                // In SF, skip removing if it contains interactive elements (might be lazy-loaded)
                if (isSalesforce && el.select("a, button, input, select, textarea").size() > 0) continue;
                // Don't remove if it has important attributes for identification
                if (el.hasAttr("id") || el.hasAttr("name") || el.hasAttr("data-testid")) continue;
                el.remove();
            }
        }
    }

    /** Strips only known irrelevant Salesforce chrome, keeps utility bar and functional UI. */
    private static void stripSalesforceChromeSafe(Document doc) {
        // More conservative Salesforce chrome removal
        Elements chromeParts = doc.select(
                ".slds-global-header," +
                ".slds-context-bar," +
                ".forceBrandBand," +
                ".slds-nav-vertical," +
                ".slds-page-header"
        );
        for (Element e : chromeParts) {
            // Keep elements that contain interactive elements or have important attributes
            if (e.select("a, button, input, select, textarea").size() > 0 ||
                e.hasAttr("id") || e.hasAttr("data-testid") || e.hasAttr("data-qa")) {
                continue;
            }
            // Only remove if it's purely decorative
            if (e.text().trim().isEmpty() && e.select("img").isEmpty()) {
                e.remove();
            }
        }
    }

    /** Unwrap tags that are not semantically important, but preserve content. */
    private static void unwrapNonKeptTags(Document doc) {
        // More selective unwrapping - only unwrap if content is preserved
        Elements spans = doc.select("span");
        for (Element span : spans) {
            // Don't unwrap spans with important attributes
            if (span.hasAttr("id") || span.hasAttr("class") || span.hasAttr("data-testid")) {
                continue;
            }
            // Only unwrap if it doesn't break the structure
            if (span.parent() != null && span.children().size() <= 1) {
                try {
                    span.unwrap();
                } catch (Exception e) {
                    // If unwrapping fails, keep the span
                    System.err.println("Failed to unwrap span: " + e.getMessage());
                }
            }
        }
        
        // Remove purely decorative tags
        doc.select("font, b, i, u").forEach(el -> {
            if (el.parent() != null && !el.hasAttr("id") && !el.hasAttr("class")) {
                try {
                    el.unwrap();
                } catch (Exception e) {
                    // Keep if unwrapping fails
                }
            }
        });
    }

    /** Keep only functional attributes that are crucial for element identification. */
    private static void pruneAttributesSafe(Document doc) {
        for (Element el : doc.getAllElements()) {
            // Create a modifiable copy of attributes to avoid UnsupportedOperationException
            List<Attribute> attributesToRemove = new ArrayList<>();
            
            for (Attribute attr : el.attributes()) {
                String key = attr.getKey();
                String value = attr.getValue();
                
                // Check if this attribute should be removed
                boolean shouldRemove = true;
                
                // Keep crucial attributes for element identification
                if (key.equals("id") || key.equals("name") || key.equals("type") || 
                    key.equals("value") || key.equals("placeholder") || key.equals("title") ||
                    key.equals("href") || key.equals("src") || key.equals("alt") ||
                    key.equals("class") || key.equals("role") || key.equals("for")) {
                    shouldRemove = false;
                }
                
                // Keep accessibility and testing attributes
                if (key.startsWith("aria-") || key.startsWith("data-") || 
                    key.startsWith("data-test") || key.startsWith("data-qa")) {
                    shouldRemove = false;
                }
                
                // Keep Salesforce-specific attributes
                if (key.startsWith("force-") || key.startsWith("aura-") || 
                    key.startsWith("lightning-") || key.startsWith("slds-")) {
                    shouldRemove = false;
                }
                
                if (shouldRemove) {
                    attributesToRemove.add(attr);
                }
            }
            
            // Remove the attributes that should be removed
            for (Attribute attr : attributesToRemove) {
                el.removeAttr(attr.getKey());
            }
        }
    }

    /** Removes tags with no text and no children. */
    private static void removeEmptyNodes(Document doc) {
        for (Element el : doc.select("*")) {
            if (el.children().isEmpty() && el.text().trim().isEmpty()) {
                el.remove();
            }
        }
    }

    /** Collapses excessive whitespace. */
    private static String compact(String html) {
        return html.replaceAll("\\s{2,}", " ").trim();
    }

    /** Truncate to size limit. */
    private static String cap(String text, int maxChars) {
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    public static String extractSnippetByDescription(String dom, String description) {
        if (dom == null || dom.isEmpty()) {
            System.err.println("Warning: DOM is null or empty for snippet extraction");
            return "";
        }
        
        try {
            Document doc = Jsoup.parse(dom);
            String lowerDesc = description.toLowerCase();
            System.out.println("Extracting snippet for description: " + description);

            // Search elements with matching text, labels, or attributes
            Elements candidates = doc.select("*:matchesOwn((?i)" + Pattern.quote(lowerDesc) + ")");
            System.out.println("Found " + candidates.size() + " text-matching candidates");

            if (candidates.isEmpty()) {
                // Try searching for label "for" attribute matching description words
                Elements labels = doc.select("label");
                for (Element label : labels) {
                    if (label.text().toLowerCase().contains(lowerDesc)) {
                        String forAttr = label.attr("for");
                        if (!forAttr.isEmpty()) {
                            Element input = doc.getElementById(forAttr);
                            if (input != null) {
                                System.out.println("Found input via label association");
                                return buildContextSnippet(input).outerHtml();
                            }
                        }
                    }
                }
                
                // Try searching by common input attributes
                Elements inputs = doc.select("input, textarea, select");
                for (Element input : inputs) {
                    String placeholder = input.attr("placeholder").toLowerCase();
                    String name = input.attr("name").toLowerCase();
                    String id = input.attr("id").toLowerCase();
                    String type = input.attr("type").toLowerCase();
                    
                    if (placeholder.contains(lowerDesc) || name.contains(lowerDesc) || 
                        id.contains(lowerDesc) || (type.equals("text") && lowerDesc.contains("user"))) {
                        System.out.println("Found input via attribute matching");
                        return buildContextSnippet(input).outerHtml();
                    }
                }
                
                System.out.println("No candidates found, returning filtered DOM");
                return filterRelevantHtml(dom);
            }

            // Return outer HTML of first candidate with more context
            Element el = candidates.first();
            Element snippetRoot = el;
            
            // Go up more levels for better context (3-4 levels)
            for (int i = 0; i < 3; i++) {
                if (snippetRoot.parent() != null) {
                    snippetRoot = snippetRoot.parent();
                }
            }
            
            // Also include siblings for better context
            Element parent = snippetRoot.parent();
            if (parent != null) {
                Elements siblings = parent.children();
                if (siblings.size() <= 5) { // Only if not too many siblings
                    snippetRoot = parent;
                }
            }
            
            System.out.println("Built snippet with context, size: " + snippetRoot.outerHtml().length());
            return snippetRoot.outerHtml();
            
        } catch (Exception e) {
            System.err.println("Error extracting snippet: " + e.getMessage());
            e.printStackTrace();
            return filterRelevantHtml(dom);
        }
    }
    
    /**
     * Build a context snippet around a seed element
     */
    private static Element buildContextSnippet(Element seed) {
        Element context = seed;
        
        // Go up 2-3 levels for context
        for (int i = 0; i < 2; i++) {
            if (context.parent() != null) {
                context = context.parent();
            }
        }
        
        return context;
    }


    /**
     * Filter HTML and return a small snippet focused around a human description.
     * Useful to feed to the LLM for precise locator generation.
     */
    public static String filterRelevantHtml(String rawHtml, String description) {
        if (rawHtml == null || rawHtml.isEmpty()) return "";
        if (description == null || description.isBlank()) return filterRelevantHtml(rawHtml);

        try {
            Document doc = Jsoup.parse(rawHtml);
            boolean isSalesforce = isSalesforceLightning(doc);

            baseStrip(doc);
            stripHidden(doc, isSalesforce);
            if (isSalesforce) stripSalesforceChrome(doc);

            // Find seed elements likely matching the description
            Elements seeds = findSeeds(doc, description);

            // If no seeds found, fallback to global filter
            if (seeds.isEmpty()) {
                unwrapNonKeptTags(doc);
                pruneAttributes(doc, isSalesforce);
                removeEmptyNodes(doc);
                String out = compact(doc.body().html());
                return cap(out, MAX_OUTPUT_CHARS);
            }

            // Build a focused context snippet around seeds
            Document snippetDoc = buildContextSnippet(seeds);

            // Clean & compress snippet
            unwrapNonKeptTags(snippetDoc);
            pruneAttributes(snippetDoc, isSalesforce);
            removeEmptyNodes(snippetDoc);

            String out = compact(snippetDoc.body().html());
            return cap(out, MAX_OUTPUT_CHARS);
        } catch (Exception e) {
            System.err.println("Error filtering HTML with description: " + e.getMessage());
            // Return a simplified version of the HTML if processing fails
            return rawHtml.length() > MAX_OUTPUT_CHARS ? rawHtml.substring(0, MAX_OUTPUT_CHARS) : rawHtml;
        }
    }

    /**
     * Only text (no tags) after filtering.
     */
    public static String extractRelevantText(String rawHtml) {
        return Jsoup.parse(filterRelevantHtml(rawHtml)).text();
    }

    // ---------- Heuristics ----------




    private static void stripSalesforceChrome(Document doc) {
        for (String id : SF_REMOVE_IDS) doc.select("#" + id).remove();
        for (String cls : SF_REMOVE_CLASSES) doc.select("." + cls).remove();
        // Unwrap aura/ltng technical containers
        doc.select("*").forEach(el -> {
            String tag = el.tagName();
            if (tag.startsWith("aura") || tag.startsWith("ltng") || tag.equals("one-app") || tag.equals("one-appnav")) {
                el.unwrap();
            }
        });
    }

    private static void pruneAttributes(Document doc, boolean isSalesforce) {
        for (Element el : doc.getAllElements()) {
            if ("html".equals(el.tagName()) || "body".equals(el.tagName())) continue;

            Attributes attrs = el.attributes();
            // Copy to avoid concurrent mutation
            List<Attribute> toCheck = new ArrayList<>(attrs.asList());

            // First remove everything
            for (Attribute a : toCheck) {
                el.removeAttr(a.getKey());
            }

            // Then re-add only the allowed/common attributes
            for (Attribute a : toCheck) {
                String k = a.getKey();
                String v = a.getValue();

                if (KEEP_ATTRS_COMMON.contains(k)) {
                    el.attr(k, v);
                    continue;
                }
                // prefixes
                for (String p : KEEP_ATTR_PREFIXES) {
                    if (k.startsWith(p)) {
                        el.attr(k, v);
                        break;
                    }
                }
            }

            // For anchors, keep href if short/meaningful
            if ("a".equals(el.tagName())) {
                String href = el.hasAttr("href") ? el.attr("href") : null;
                if (href != null && href.length() <= 300 && !href.startsWith("javascript:")) {
                    el.attr("href", href);
                }
            }

            // For inputs: keep key attributes if present
            if ("input".equals(el.tagName()) || "textarea".equals(el.tagName()) || "select".equals(el.tagName())) {
                keepIfPresent(el, "id","name","type","placeholder","title","aria-label","data-testid","data-test","data-qa","value");
            }

            // For SF, SLDS classes might be useful to infer role
            if (isSalesforce) {
                String cls = el.className();
                if (cls != null && (cls.contains("slds-") || cls.contains("uiInput") || cls.contains("force"))) {
                    el.attr("class", cls);
                }
            } else {
                // For generic sites, keep classes only if short & likely semantic
                String cls = el.className();
                if (cls != null && !cls.isBlank()) {
                    String shortCls = shortenClasses(cls, 5, 40);
                    if (!shortCls.isBlank()) el.attr("class", shortCls);
                }
            }
        }
    }

    private static void keepIfPresent(Element el, String... keys) {
        for (String k : keys) if (el.hasAttr(k)) el.attr(k, el.attr(k));
    }

    private static String shortenClasses(String cls, int maxTokens, int maxLen) {
        String[] parts = cls.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String p : parts) {
            if (p.length() > 2 && count < maxTokens && (sb.length() + p.length() + 1) <= maxLen) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p);
                count++;
            }
        }
        return sb.toString();
    }


    // ---------- Snippet extraction ----------

    private static Elements findSeeds(Document doc, String description) {
        Elements seeds = new Elements();

        String q = description.trim();
        if (q.isEmpty()) return seeds;

        // Normalize quotes
        q = q.replaceAll("[“”]", "\"").replaceAll("[‘’]", "'");

        // Heuristic tokenization
        String lowered = q.toLowerCase(Locale.ROOT);

        // Common roles to map from description
        boolean looksLikeUser = lowered.contains("user") || lowered.contains("username") || lowered.contains("email");
        boolean looksLikePass = lowered.contains("pass");
        boolean looksLikeBtn  = lowered.contains("button") || lowered.startsWith("click") || lowered.contains("sign in") || lowered.contains("login");

        // 1) Direct text matches for labels/buttons/links
        seeds.addAll(doc.select("label:matchesOwn((?i)" + Pattern.quote(q) + ")"));
        seeds.addAll(doc.select("button:matchesOwn((?i)" + Pattern.quote(q) + ")"));
        seeds.addAll(doc.select("a:matchesOwn((?i)" + Pattern.quote(q) + ")"));
        seeds.addAll(doc.select("*:matchesOwn((?i)^\\s*" + Pattern.quote(q) + "\\s*$)"));

        // 2) Attribute-based matches (placeholder, aria-label, title, name, id)
        seeds.addAll(doc.select("[placeholder~=(?i)" + Pattern.quote(q) + "]"));
        seeds.addAll(doc.select("[aria-label~=(?i)" + Pattern.quote(q) + "]"));
        seeds.addAll(doc.select("[title~=(?i)" + Pattern.quote(q) + "]"));
        seeds.addAll(doc.select("[name~=(?i)" + Pattern.quote(q) + "]"));
        seeds.addAll(doc.select("[id~=(?i)" + Pattern.quote(q) + "]"));

        // 3) Heuristic fallbacks for common fields
        if (looksLikeUser) {
            seeds.addAll(doc.select("input[type=text],input[type=email],input[name*=user i],input[name*=email i],input[id*=user i],input[id*=email i]"));
            seeds.addAll(doc.select("label:matchesOwn((?i)user|email) + input"));
        }
        if (looksLikePass) {
            seeds.addAll(doc.select("input[type=password],input[name*=pass i],input[id*=pass i]"));
        }
        if (looksLikeBtn) {
            seeds.addAll(doc.select("button, input[type=submit], a[role=button]"));
            // Filter those by visible text contains words from q
            seeds = filterByTextContains(seeds, Arrays.asList("login","sign in","submit","continue","next","ok","search","save","apply"), lowered);
        }

        // Deduplicate
        return dedupe(seeds);
    }

    private static Elements filterByTextContains(Elements elements, List<String> keywords, String descriptionLower) {
        Elements out = new Elements();
        for (Element e : elements) {
            String t = (e.text() + " " + e.attr("value") + " " + e.attr("aria-label") + " " + e.attr("title")).toLowerCase(Locale.ROOT);
            boolean keep = false;
            for (String k : keywords) {
                if (descriptionLower.contains(k) || t.contains(k)) { keep = true; break; }
            }
            if (keep) out.add(e);
        }
        return out;
    }

    private static Elements dedupe(Elements in) {
        Set<String> seen = new HashSet<>();
        Elements out = new Elements();
        for (Element e : in) {
            String key = e.cssSelector();
            if (seen.add(key)) out.add(e);
        }
        return out;
    }

    private static Document buildContextSnippet(Elements seeds) {
        Document snippet = Document.createShell("");
        Element root = snippet.body();

        // For each seed, climb parents to a reasonable container and copy a trimmed subtree
        for (Element seed : seeds) {
            Element container = climbParents(seed, SNIPPET_PARENT_DEPTH);
            Element copy = shallowCloneWithLimitedSiblings(container, SNIPPET_SIBLING_LIMIT);
            // Avoid duplicates by CSS key
            String key = copy.cssSelector();
            if (root.select(key).isEmpty()) {
                root.appendChild(copy);
            }
        }
        return snippet;
    }

    private static Element climbParents(Element el, int depth) {
        Element cur = el;
        for (int i = 0; i < depth && cur.parent() != null; i++) {
            cur = cur.parent();
        }
        return cur;
    }

    /**
     * Clone an element but limit number of siblings per level,
     * and keep children fully only for paths that include the original seed.
     */
    private static Element shallowCloneWithLimitedSiblings(Element container, int siblingLimit) {
        // We'll copy container and its children, but trim siblings per parent to keep snippet small.
        Element cloned = container.clone();
        // Trim excess siblings at each level by keeping first/last few around matching nodes.
        trimSiblingsRecursively(cloned, siblingLimit);
        return cloned;
    }

    private static void trimSiblingsRecursively(Element node, int siblingLimit) {
        if (node.children().isEmpty()) return;

        // If too many children, keep a window around “interesting” ones (those with inputs/buttons/labels or non-empty text)
        Elements children = node.children();
        if (children.size() > siblingLimit) {
            List<Element> interesting = new ArrayList<>();
            for (Element ch : children) {
                if (isInteractive(ch) || hasUsefulText(ch)) interesting.add(ch);
            }
            // If still too many, keep head/tail slices
            List<Element> keep = new ArrayList<>();
            if (!interesting.isEmpty()) {
                keep.addAll(interesting.subList(0, Math.min(interesting.size(), siblingLimit)));
            } else {
                keep.addAll(children.subList(0, Math.min(children.size(), siblingLimit)));
            }
            // Remove others
            for (Element ch : new ArrayList<>(children)) {
                if (!keep.contains(ch)) ch.remove();
            }
        }

        // Recurse
        for (Element ch : node.children()) {
            trimSiblingsRecursively(ch, siblingLimit);
        }
    }

    private static boolean isInteractive(Element e) {
        String tag = e.tagName();
        if ("input".equals(tag) || "button".equals(tag) || "select".equals(tag) || "textarea".equals(tag)) return true;
        if ("a".equals(tag) && ("button".equals(e.attr("role")) || !e.attr("href").isBlank())) return true;
        return false;
    }

    private static boolean hasUsefulText(Element e) {
        String t = e.ownText();
        if (t != null && t.trim().length() >= 2) return true;
        // Also consider label text/value/aria-label/title
        String meta = e.attr("value") + e.attr("aria-label") + e.attr("title") + e.attr("placeholder");
        return meta != null && meta.trim().length() >= 2;
    }

    // ---------- Utilities ----------


}
