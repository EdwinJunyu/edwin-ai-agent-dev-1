package com.edwin.edwin_ai_agent.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScrapingToolTest {

    @Test
    void shouldExtractStructuredSummaryAndDateHints() {
        WebScrapingTool tool = new WebScrapingTool(url -> Jsoup.parse("""
                <html>
                  <head><title>Spring 2026 International Student Welcome Social</title></head>
                  <body>
                    <main>
                      <p>The welcome social for new students will be held on April 8, 2026 at the Surrey campus.</p>
                      <p>Students can meet advisors, learn about support services, and join the event calendar mailing list.</p>
                    </main>
                  </body>
                </html>
                """, url));

        JSONObject result = JSONUtil.parseObj(tool.scrapeWebPage("https://www.sfu.ca/events/welcome"));

        assertEquals("verified", result.getStr("verificationStatus"));
        assertTrue(result.getStr("summary").contains("April 8, 2026"));
        assertFalse(result.getJSONArray("matchedDateHints").isEmpty());
        assertTrue(result.getStr("verificationSnippet").contains("April 8, 2026"));
    }

    @Test
    void shouldReturnContentFoundWhenNoDateIsPresent() {
        WebScrapingTool tool = new WebScrapingTool(url -> Jsoup.parse("""
                <html>
                  <head><title>Student Services and Campus Support</title></head>
                  <body>
                    <main>
                      <p>This page explains student services, support options, and campus resources for new students.</p>
                    </main>
                  </body>
                </html>
                """, url));

        JSONObject result = JSONUtil.parseObj(tool.scrapeWebPage("https://www.sfu.ca/student-services"));

        assertEquals("content_found", result.getStr("verificationStatus"));
        assertTrue(result.getJSONArray("matchedDateHints").isEmpty());
        assertTrue(result.getStr("summary").contains("student services"));
    }

    @Test
    void shouldReturnFetchErrorWhenFetcherFails() {
        WebScrapingTool tool = new WebScrapingTool(url -> {
            throw new RuntimeException("network down");
        });

        JSONObject result = JSONUtil.parseObj(tool.scrapeWebPage("https://example.com/failure"));

        assertEquals("fetch_error", result.getStr("verificationStatus"));
        assertTrue(result.getStr("verificationSnippet").contains("network down"));
    }
}
