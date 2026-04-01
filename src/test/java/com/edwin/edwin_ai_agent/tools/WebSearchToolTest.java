package com.edwin.edwin_ai_agent.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    @Test
    void shouldRemainBackwardCompatibleWithSingleQueryMethod() {
        List<String> requestBodies = new ArrayList<>();
        WebScrapingTool scrapingTool = new WebScrapingTool(url -> Jsoup.parse("""
                <html><head><title>Java Basics</title></head><body><main><p>Java basics for beginners.</p></main></body></html>
                """, url));
        WebSearchTool tool = new WebSearchTool("test-key", scrapingTool, (url, headers, requestBody) -> {
            requestBodies.add(requestBody);
            return tavilyResponse("Java programming basics",
                    result("Java Basics", "https://docs.example.com/java", "Java basics for beginners.", 0.92));
        });

        JSONObject result = JSONUtil.parseObj(tool.searchWeb("Java programming basics"));

        assertEquals(1, requestBodies.size());
        assertEquals("Java programming basics", result.getStr("query"));
        JSONObject firstItem = result.getJSONArray("results").getJSONObject(0);
        assertEquals("third_party", firstItem.getStr("sourceType"));
        assertEquals("not_requested", firstItem.getStr("verificationStatus"));
        assertTrue(firstItem.containsKey("score"));
        assertTrue(firstItem.containsKey("matchedDateHints"));
        assertTrue(firstItem.containsKey("verificationSnippet"));
    }

    @Test
    void shouldRunOfficialRoundThenFallbackAndVerifyTopCandidates() {
        List<String> requestBodies = new ArrayList<>();
        WebScrapingTool scrapingTool = new WebScrapingTool(url -> {
            if (url.contains("calendar")) {
                return Jsoup.parse("""
                        <html>
                          <head><title>Simon Fraser University Student Welcome Calendar</title></head>
                          <body><main><p>The student welcome social will take place on April 8, 2026 at Surrey campus.</p></main></body>
                        </html>
                        """, url);
            }
            return Jsoup.parse("""
                    <html>
                      <head><title>Community Roundup for SFU Students</title></head>
                      <body><main><p>A local roundup mentions SFU events and student services.</p></main></body>
                    </html>
                    """, url);
        });

        WebSearchTool tool = new WebSearchTool("test-key", scrapingTool, (url, headers, requestBody) -> {
            requestBodies.add(requestBody);
            if (requestBodies.size() == 1) {
                return tavilyResponse("SFU 2026 April student events official",
                        result("Academic Dates - Simon Fraser University", "https://www.sfu.ca/calendar/welcome", "Welcome Day for Spring 2026 students.", 0.74));
            }
            return tavilyResponse("SFU 2026 April student events",
                    result("Community Roundup", "https://community.example.com/sfu-roundup", "A roundup of student news and events.", 0.91));
        });

        JSONObject result = JSONUtil.parseObj(tool.searchWeb(
                "SFU 2026 April student events",
                List.of("sfu.ca"),
                List.of("instagram.com"),
                "2026-04",
                true,
                true,
                3
        ));

        assertEquals(2, requestBodies.size());

        JSONObject firstRequest = JSONUtil.parseObj(requestBodies.get(0));
        assertEquals("advanced", firstRequest.getStr("search_depth"));
        assertEquals("sfu.ca", firstRequest.getJSONArray("include_domains").getStr(0));
        assertFalse(firstRequest.containsKey("start_date"));
        assertFalse(firstRequest.containsKey("end_date"));
        assertTrue(firstRequest.getStr("query").toLowerCase().contains("official"));

        JSONObject secondRequest = JSONUtil.parseObj(requestBodies.get(1));
        assertFalse(secondRequest.containsKey("include_domains"));
        assertEquals("advanced", secondRequest.getStr("search_depth"));

        JSONObject strategy = result.getJSONObject("strategy");
        assertEquals(2, strategy.getInt("roundsUsed"));
        assertTrue(strategy.getBool("officialFirst"));
        assertTrue(strategy.getBool("needVerification"));
        assertTrue(strategy.getBool("evidenceThresholdMet"));
        assertEquals(1, strategy.getInt("thresholdQualifiedCount"));

        JSONArray results = result.getJSONArray("results");
        JSONObject topResult = results.getJSONObject(0);
        assertEquals("official", topResult.getStr("sourceType"));
        assertEquals("verified", topResult.getStr("verificationStatus"));
        assertFalse(topResult.getJSONArray("matchedDateHints").isEmpty());
        assertTrue(topResult.getStr("verificationSnippet").contains("April 8, 2026"));
    }

    @Test
    void shouldApplyRelativeTimeRangeForLatestNewsQueries() {
        List<String> requestBodies = new ArrayList<>();
        WebSearchTool tool = new WebSearchTool("test-key", new WebScrapingTool(url -> Jsoup.parse("<html></html>", url)),
                (url, headers, requestBody) -> {
                    requestBodies.add(requestBody);
                    return tavilyResponse("latest OpenAI API news");
                });

        tool.searchWeb("latest OpenAI API news", null, null, "month", false, false, 2);

        JSONObject request = JSONUtil.parseObj(requestBodies.get(0));
        assertEquals("news", request.getStr("topic"));
        assertEquals("month", request.getStr("time_range"));
        assertEquals("advanced", request.getStr("search_depth"));
    }

    @Test
    void shouldTreatWebinarRegistrationQueriesAsEventLike() {
        List<String> requestBodies = new ArrayList<>();
        WebSearchTool tool = new WebSearchTool("test-key", new WebScrapingTool(url -> Jsoup.parse("<html></html>", url)),
                (url, headers, requestBody) -> {
                    requestBodies.add(requestBody);
                    return tavilyResponse("OpenAI developer webinar registration",
                            result("Developer Webinar Registration", "https://events.example.com/openai-webinar", "Register for the upcoming developer webinar.", 0.88));
                });

        JSONObject result = JSONUtil.parseObj(tool.searchWeb("OpenAI developer webinar registration", null, null, null, null, false, 2));
        JSONObject strategy = result.getJSONObject("strategy");
        JSONObject firstRequest = JSONUtil.parseObj(requestBodies.get(0));

        assertTrue(strategy.getBool("officialFirst"));
        assertEquals("advanced", firstRequest.getStr("search_depth"));
        assertTrue(firstRequest.getStr("query").toLowerCase().contains("official"));
        assertTrue(firstRequest.getStr("query").toLowerCase().contains("events"));
    }

    @Test
    void shouldTreatPressReleaseQueriesAsNewsLike() {
        List<String> requestBodies = new ArrayList<>();
        WebSearchTool tool = new WebSearchTool("test-key", new WebScrapingTool(url -> Jsoup.parse("<html></html>", url)),
                (url, headers, requestBody) -> {
                    requestBodies.add(requestBody);
                    return tavilyResponse("FDA press release diabetes guidance",
                            result("FDA Press Release", "https://www.fda.gov/news-events/press-announcements/example", "FDA issued a press release about diabetes guidance.", 0.91));
                });

        tool.searchWeb("FDA press release diabetes guidance", null, null, null, null, false, 2);

        JSONObject request = JSONUtil.parseObj(requestBodies.get(0));
        assertEquals("news", request.getStr("topic"));
        assertEquals("advanced", request.getStr("search_depth"));
    }

    @Test
    void shouldTreatClinicQueriesAsInstitutionLike() {
        List<String> requestBodies = new ArrayList<>();
        WebSearchTool tool = new WebSearchTool("test-key", new WebScrapingTool(url -> Jsoup.parse("<html></html>", url)),
                (url, headers, requestBody) -> {
                    requestBodies.add(requestBody);
                    return tavilyResponse("Mayo clinic visiting hours",
                            result("Visiting Hours", "https://www.mayoclinic.org/visitor-info", "Visitor information and hours.", 0.84));
                });

        JSONObject result = JSONUtil.parseObj(tool.searchWeb("Mayo clinic visiting hours", null, null, null, null, false, 2));
        JSONObject strategy = result.getJSONObject("strategy");
        JSONObject request = JSONUtil.parseObj(requestBodies.get(0));

        assertTrue(strategy.getBool("officialFirst"));
        assertEquals("advanced", request.getStr("search_depth"));
        assertTrue(request.getStr("query").toLowerCase().contains("official"));
    }

    @Test
    void shouldTreatDividendForecastQueriesAsFinanceLike() {
        List<String> requestBodies = new ArrayList<>();
        WebSearchTool tool = new WebSearchTool("test-key", new WebScrapingTool(url -> Jsoup.parse("<html></html>", url)),
                (url, headers, requestBody) -> {
                    requestBodies.add(requestBody);
                    return tavilyResponse("Tesla dividend forecast",
                            result("Tesla dividend forecast", "https://finance.example.com/tesla-forecast", "Analyst dividend forecast for Tesla.", 0.87));
                });

        tool.searchWeb("Tesla dividend forecast", null, null, null, null, false, 2);

        JSONObject request = JSONUtil.parseObj(requestBodies.get(0));
        assertEquals("finance", request.getStr("topic"));
    }

    @Test
    void shouldMarkDataIntensiveQueriesAsBelowThresholdWhenResultMissesKeyIntent() {
        WebScrapingTool scrapingTool = new WebScrapingTool(url -> Jsoup.parse("""
                <html>
                  <head><title>Top 10 Market Cap Companies</title></head>
                  <body><main><p>Top 10 market cap companies in March 2026: Apple 3200, Microsoft 3100, Nvidia 2800.</p></main></body>
                </html>
                """, url));
        WebSearchTool tool = new WebSearchTool("test-key", scrapingTool, (url, headers, requestBody) ->
                tavilyResponse("Nasdaq March 2026 top 5 company performance",
                        result("Top 10 Market Cap Companies", "https://markets.example.com/top10", "Top 10 market cap companies in March 2026.", 0.93)));

        JSONObject result = JSONUtil.parseObj(tool.searchWeb(
                "Nasdaq March 2026 top 5 company performance",
                null,
                null,
                "2026-03",
                true,
                true,
                3
        ));

        JSONObject strategy = result.getJSONObject("strategy");
        assertTrue(strategy.getBool("dataIntensive"));
        assertFalse(strategy.getBool("evidenceThresholdMet"));
        assertEquals(0, strategy.getInt("thresholdQualifiedCount"));
    }

    @Test
    void shouldApplyEvidenceThresholdToGenericTopListQueriesNotOnlyFinance() {
        WebScrapingTool scrapingTool = new WebScrapingTool(url -> Jsoup.parse("""
                <html>
                  <head><title>Spring 2026 Student Events Calendar</title></head>
                  <body><main><p>The welcome fair runs on April 8, 2026 and the writing workshop runs on April 10, 2026.</p></main></body>
                </html>
                """, url));
        WebSearchTool tool = new WebSearchTool("test-key", scrapingTool, (url, headers, requestBody) ->
                tavilyResponse("2026 top 5 campus events attendance",
                        result("Spring 2026 Student Events Calendar", "https://campus.example.com/events", "Student events for spring 2026.", 0.9)));

        JSONObject result = JSONUtil.parseObj(tool.searchWeb(
                "2026 top 5 campus events attendance",
                null,
                null,
                "2026-04",
                true,
                true,
                3
        ));

        JSONObject strategy = result.getJSONObject("strategy");
        assertTrue(strategy.getBool("dataIntensive"));
        assertFalse(strategy.getBool("evidenceThresholdMet"));
        assertEquals(0, strategy.getInt("thresholdQualifiedCount"));
        assertTrue(strategy.getStr("evidenceThresholdReason").contains("精确"));
    }

    @Test
    void shouldReturnErrorMessageWhenHttpClientThrowsException() {
        WebSearchTool tool = new WebSearchTool("test-key", new WebScrapingTool(url -> Jsoup.parse("<html></html>", url)),
                (url, headers, requestBody) -> {
                    throw new RuntimeException("network down");
                });

        String result = tool.searchWeb("failure");

        assertEquals("Error searching web: network down", result);
    }

    private String tavilyResponse(String query, JSONObject... results) {
        JSONArray resultArray = new JSONArray();
        for (JSONObject result : results) {
            resultArray.add(result);
        }
        return JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("query", query)
                .set("results", resultArray));
    }

    private JSONObject result(String title, String url, String content, double score) {
        return JSONUtil.createObj()
                .set("title", title)
                .set("url", url)
                .set("content", content)
                .set("score", score);
    }
}
