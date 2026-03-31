package com.edwin.edwin_ai_agent.app;

import java.beans.Introspector;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// class LoveAppTest {
// #NEW CODE#
class EdwinAppTest {

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
}
