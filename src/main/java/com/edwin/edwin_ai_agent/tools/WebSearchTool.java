package com.edwin.edwin_ai_agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

// Web search tool
public class WebSearchTool {

    // Tavily official search endpoint.
    private static final String TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final String apiKey;
    private final WebSearchHttpClient webSearchHttpClient;

    public WebSearchTool(String apiKey) {
        this(apiKey, (url, headers, requestBody) -> {
            HttpRequest request = HttpUtil.createPost(url);
            headers.forEach(request::header);
            return request.body(requestBody).execute().body();
        });
    }

    // Small HTTP abstraction to keep unit tests independent from real network calls.
    WebSearchTool(String apiKey, WebSearchHttpClient webSearchHttpClient) {
        this.apiKey = apiKey;
        this.webSearchHttpClient = webSearchHttpClient;
    }

    @Tool(description = "Search for information from the web with Tavily")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        try {
            JSONObject requestBody = JSONUtil.createObj()
                    .set("query", query)
                    .set("topic", "general")
                    .set("search_depth", "basic")
                    .set("max_results", DEFAULT_MAX_RESULTS)
                    .set("include_answer", false);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("Content-Type", "application/json");

            String response = webSearchHttpClient.post(
                    TAVILY_SEARCH_API_URL,
                    headers,
                    JSONUtil.toJsonStr(requestBody)
            );
            JSONObject responseJson = JSONUtil.parseObj(response);
            JSONObject normalizedResponse = JSONUtil.createObj()
                    .set("query", responseJson.getStr("query", query))
                    // Only expose stable fields to reduce coupling with Tavily raw payloads.
                    .set("results", normalizeResults(responseJson.getJSONArray("results")));
            return JSONUtil.toJsonStr(normalizedResponse);
        } catch (Exception e) {
            return "Error searching web: " + e.getMessage();
        }
    }

    // Normalize Tavily results into a fixed response shape.
    private JSONArray normalizeResults(JSONArray tavilyResults) {
        JSONArray normalizedResults = new JSONArray();
        if (tavilyResults == null) {
            return normalizedResults;
        }
        int resultCount = Math.min(tavilyResults.size(), DEFAULT_MAX_RESULTS);
        for (int index = 0; index < resultCount; index++) {
            JSONObject tavilyResult = tavilyResults.getJSONObject(index);
            JSONObject normalizedResult = JSONUtil.createObj()
                    .set("title", tavilyResult.getStr("title", ""))
                    .set("url", tavilyResult.getStr("url", ""))
                    .set("content", tavilyResult.getStr("content", ""));
            normalizedResults.add(normalizedResult);
        }
        return normalizedResults;
    }

    @FunctionalInterface
    interface WebSearchHttpClient {
        String post(String url, Map<String, String> headers, String requestBody);
    }
}
