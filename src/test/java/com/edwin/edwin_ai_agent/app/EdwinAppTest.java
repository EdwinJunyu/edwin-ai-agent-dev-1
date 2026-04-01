package com.edwin.edwin_ai_agent.app;

import java.beans.Introspector;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EdwinAppTest {
    @Resource
    private EdwinApp edwinApp;

    @Test
    void beanNameMatchesResourceInjectionField() {
        // This locks the rename contract used by @Resource in AiController.
        assertEquals("edwinApp", Introspector.decapitalize(EdwinApp.class.getSimpleName()));
    }

    @Test
    void loveReportShapeRemainsAvailableAfterRename() {
        EdwinApp.LoveReport report = new EdwinApp.LoveReport("title", List.of("suggestion"));
        assertEquals("title", report.title());
        assertEquals(List.of("suggestion"), report.suggestions());
    }
    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        EdwinApp.LoveReport report = edwinApp.doChatWithTools(message,chatId);
        Assertions.assertNotNull(report);
    }
    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？必须调用 WebSearchTool!");
    }
}
