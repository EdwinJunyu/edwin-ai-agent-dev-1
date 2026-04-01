package com.edwin.edwin_ai_agent.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@SpringBootTest
class WebSearchToolLiveTest {

    //    private String tavilyApiKey;
    // #NEW CODE#
    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Test
    void shouldSearchRealTavilyApiWhenApiKeyIsProvided() {
        String apiKey = resolveTavilyApiKey();
        // 只有在提供真实 Tavily Key 时才执行联网测试，避免占位配置导致误失败。
        assumeTrue(isRealApiKey(apiKey), "Missing real tavily.api-key / TAVILY_API_KEY");

        WebSearchTool tool = new WebSearchTool(apiKey);
        String result = tool.searchWeb("OpenAI latest API platform");

        assertNotNull(result);
        assertFalse(result.startsWith("Error searching web:"), "Live Tavily call returned error: " + result);

        JSONObject resultJson = JSONUtil.parseObj(result);
        JSONArray results = resultJson.getJSONArray("results");

        assertNotNull(results);
        assertFalse(results.isEmpty(), "Live Tavily search should return at least one result");
        assertTrue(results.size() <= 5, "Live Tavily search should keep max 5 results");
        assertFalse(results.getJSONObject(0).getStr("title", "").isBlank(), "First result title should not be blank");
        assertFalse(results.getJSONObject(0).getStr("url", "").isBlank(), "First result url should not be blank");
        assertFalse(resultJson.getJSONObject("strategy").isEmpty(), "Search strategy metadata should be present");
        assertFalse(results.getJSONObject(0).getStr("sourceType", "").isBlank(), "First result sourceType should not be blank");
        assertFalse(results.getJSONObject(0).getStr("verificationStatus", "").isBlank(), "First result verificationStatus should not be blank");
    }

    // 优先读取 Spring 配置，其次兼容环境变量和 JVM 参数，方便本地与 CI 复用。
    private String resolveTavilyApiKey() {
        // String apiKey = System.getenv("TAVILY_API_KEY");
        // #NEW CODE#
        if (isRealApiKey(tavilyApiKey)) {
            return tavilyApiKey;
        }
        String apiKey = System.getenv("TAVILY_API_KEY");
        if (isRealApiKey(apiKey)) {
            return apiKey;
        }
        apiKey = System.getProperty("tavily.api-key");
        return apiKey;
    }

    private boolean isRealApiKey(String apiKey) {
        return apiKey != null
                && !apiKey.isBlank()
                && !"YOUR_TAVILY_API_KEY".equals(apiKey);
    }
}
