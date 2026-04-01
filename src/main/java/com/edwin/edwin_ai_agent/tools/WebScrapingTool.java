/* Previous implementation commented out per workspace rule. */
// #NEW CODE#
package com.edwin.edwin_ai_agent.tools;

import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured web scraping used by search verification and direct tool calls.
 */
public class WebScrapingTool {

    private static final int FETCH_TIMEOUT_MS = 12000;
    private static final int MAX_PARAGRAPHS = 4;
    private static final int MAX_SUMMARY_LENGTH = 900;
    private static final int MAX_SNIPPET_LENGTH = 260;
    private static final List<Pattern> DATE_PATTERNS = List.of(
            Pattern.compile("\\b20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}\\b"),
            Pattern.compile("\\b20\\d{2}[-/]\\d{1,2}\\b"),
            Pattern.compile("\\b(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\s+\\d{1,2},\\s*20\\d{2}\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b\\d{1,2}\\s+(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\s+20\\d{2}\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("20\\d{2}年\\d{1,2}月(?:\\d{1,2}日)?")
    );

    private final DocumentFetcher documentFetcher;

    public WebScrapingTool() {
        this(url -> Jsoup.connect(url)
                .userAgent("EdwinAI-Agent/1.0")
                .timeout(FETCH_TIMEOUT_MS)
                .get());
    }

    WebScrapingTool(DocumentFetcher documentFetcher) {
        this.documentFetcher = documentFetcher;
    }

    @Tool(description = "Scrape a web page and return structured readable content for verification")
    public String scrapeWebPage(
            @ToolParam(description = "URL of the web page to scrape") String url) {
        return JSONUtil.toJsonStr(scrapePage(url).toMap());
    }

    ScrapeResult scrapePage(String url) {
        try {
            Document document = documentFetcher.fetch(url);
            return summarizeDocument(url, document);
        } catch (Exception e) {
            return ScrapeResult.error(url, e.getMessage());
        }
    }

    // Extract readable text and evidence hints instead of returning raw HTML to the agent.
    private ScrapeResult summarizeDocument(String url, Document document) {
        String title = normalizeWhitespace(document.title());
        String readableText = extractReadableText(document);
        String summary = abbreviate(readableText, MAX_SUMMARY_LENGTH);
        List<String> matchedDateHints = extractDateHints(title + "\n" + readableText);
        String verificationSnippet = buildVerificationSnippet(title, readableText, matchedDateHints);

        String verificationStatus;
        if (!StringUtils.hasText(summary)) {
            verificationStatus = "insufficient_content";
        } else if (!matchedDateHints.isEmpty()) {
            verificationStatus = "verified";
        } else {
            verificationStatus = "content_found";
        }

        return new ScrapeResult(
                url,
                title,
                summary,
                matchedDateHints,
                verificationSnippet,
                verificationStatus
        );
    }

    private String extractReadableText(Document document) {
        Elements paragraphElements = document.select("article p, main p, [role=main] p, .content p, .post p, .entry-content p, p");
        List<String> paragraphs = new ArrayList<>();

        for (Element paragraphElement : paragraphElements) {
            String paragraph = normalizeWhitespace(paragraphElement.text());
            if (paragraph.length() < 40) {
                continue;
            }
            paragraphs.add(paragraph);
            if (paragraphs.size() >= MAX_PARAGRAPHS) {
                break;
            }
        }

        if (!paragraphs.isEmpty()) {
            return String.join("\n", paragraphs);
        }

        Element body = document.body();
        return body == null ? "" : normalizeWhitespace(body.text());
    }

    private List<String> extractDateHints(String text) {
        LinkedHashSet<String> dateHints = new LinkedHashSet<>();
        String normalizedText = normalizeWhitespace(text);

        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedText);
            while (matcher.find() && dateHints.size() < 5) {
                dateHints.add(matcher.group());
            }
            if (dateHints.size() >= 5) {
                break;
            }
        }

        return new ArrayList<>(dateHints);
    }

    private String buildVerificationSnippet(String title, String readableText, List<String> matchedDateHints) {
        if (!StringUtils.hasText(readableText)) {
            return abbreviate(title, MAX_SNIPPET_LENGTH);
        }

        if (!matchedDateHints.isEmpty()) {
            String firstDateHint = matchedDateHints.get(0);
            int index = readableText.toLowerCase().indexOf(firstDateHint.toLowerCase());
            if (index >= 0) {
                int start = Math.max(0, index - 80);
                int end = Math.min(readableText.length(), index + firstDateHint.length() + 120);
                return abbreviate(readableText.substring(start, end).trim(), MAX_SNIPPET_LENGTH);
            }
        }

        String combined = StringUtils.hasText(title) ? title + "\n" + readableText : readableText;
        return abbreviate(combined, MAX_SNIPPET_LENGTH);
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit - 3) + "...";
    }

    @FunctionalInterface
    interface DocumentFetcher {
        Document fetch(String url) throws IOException;
    }

    record ScrapeResult(
            String url,
            String title,
            String summary,
            List<String> matchedDateHints,
            String verificationSnippet,
            String verificationStatus
    ) {
        static ScrapeResult error(String url, String errorMessage) {
            return new ScrapeResult(
                    url,
                    "",
                    "",
                    List.of(),
                    errorMessage == null ? "" : errorMessage,
                    "fetch_error"
            );
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "url", url,
                    "title", title,
                    "summary", summary,
                    "matchedDateHints", matchedDateHints,
                    "verificationSnippet", verificationSnippet,
                    "verificationStatus", verificationStatus
            );
        }
    }
}
