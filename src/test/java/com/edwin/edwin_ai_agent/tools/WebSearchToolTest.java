package com.edwin.edwin_ai_agent.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebSearchToolTest {

    @Test
    void shouldNormalizeSingleResultFromTavilyResponse() {
        WebSearchTool tool = new WebSearchTool("test-key", (url, headers, requestBody) -> {
            JSONObject response = JSONUtil.createObj()
                    .set("query", "java")
                    .set("results", new JSONArray()
                            .put(JSONUtil.createObj()
                                    .set("title", "Java")
                                    .set("url", "https://example.com/java")
                                    .set("content", "Java content")
                                    .set("score", 0.99)));
            return JSONUtil.toJsonStr(response);
        });

        String result = tool.searchWeb("java");
        JSONObject resultJson = JSONUtil.parseObj(result);
        JSONArray results = resultJson.getJSONArray("results");

        assertEquals("java", resultJson.getStr("query"));
        assertEquals(1, results.size());
        assertEquals("Java", results.getJSONObject(0).getStr("title"));
        assertEquals("https://example.com/java", results.getJSONObject(0).getStr("url"));
        assertEquals("Java content", results.getJSONObject(0).getStr("content"));
        assertFalse(results.getJSONObject(0).containsKey("score"));
    }

    @Test
    void shouldReturnEmptyResultsWhenTavilyResultsMissing() {
        WebSearchTool tool = new WebSearchTool("test-key", (url, headers, requestBody) ->
                JSONUtil.toJsonStr(JSONUtil.createObj().set("query", "empty"))
        );

        String result = tool.searchWeb("empty");
        JSONObject resultJson = JSONUtil.parseObj(result);

        assertEquals("empty", resultJson.getStr("query"));
        assertEquals(0, resultJson.getJSONArray("results").size());
    }

    @Test
    void shouldReturnErrorMessageWhenHttpClientThrowsException() {
        WebSearchTool tool = new WebSearchTool("test-key", (url, headers, requestBody) -> {
            throw new RuntimeException("network down");
        });

        String result = tool.searchWeb("failure");

        assertEquals("Error searching web: network down", result);
    }

    @Test
    void shouldLimitResultsToFiveItems() {
        WebSearchTool tool = new WebSearchTool("test-key", (url, headers, requestBody) -> {
            JSONArray results = new JSONArray();
            for (int index = 0; index < 7; index++) {
                results.put(JSONUtil.createObj()
                        .set("title", "title-" + index)
                        .set("url", "https://example.com/" + index)
                        .set("content", "content-" + index));
            }
            return JSONUtil.toJsonStr(JSONUtil.createObj()
                    .set("query", "limit")
                    .set("results", results));
        });

        String result = tool.searchWeb("limit");
        JSONObject resultJson = JSONUtil.parseObj(result);
        JSONArray results = resultJson.getJSONArray("results");

        assertEquals(5, results.size());
        assertEquals("title-4", results.getJSONObject(4).getStr("title"));
    }
}
