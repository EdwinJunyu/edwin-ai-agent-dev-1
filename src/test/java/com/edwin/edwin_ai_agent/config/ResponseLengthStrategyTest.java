package com.edwin.edwin_ai_agent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResponseLengthStrategyTest {

    private final ResponseLengthStrategy strategy = new ResponseLengthStrategy();

    @Test
    void resolveFallsBackToMediumForBlankOrUnknownWireValues() {
        assertEquals(ResponseLength.MEDIUM, strategy.resolve(null));
        assertEquals(ResponseLength.MEDIUM, strategy.resolve(""));
        assertEquals(ResponseLength.MEDIUM, strategy.resolve("unexpected"));
        assertEquals(ResponseLength.SHORT, strategy.resolve("short"));
        assertEquals(ResponseLength.LONG, strategy.resolve("LONG"));
    }

    @Test
    void edwinAppTokenBudgetMatchesTheSharedPlan() {
        DashScopeChatOptions shortOptions = strategy.buildEdwinAppOptions(ResponseLength.SHORT);
        DashScopeChatOptions mediumOptions = strategy.buildEdwinAppOptions(ResponseLength.MEDIUM);
        DashScopeChatOptions longOptions = strategy.buildEdwinAppOptions(ResponseLength.LONG);

        assertEquals(512, shortOptions.getMaxTokens());
        assertEquals(1024, mediumOptions.getMaxTokens());
        assertEquals(2048, longOptions.getMaxTokens());
    }

    @Test
    void manusTokenBudgetMatchesTheSharedPlan() {
        DashScopeChatOptions shortOptions = strategy.buildManusOptions(ResponseLength.SHORT);
        DashScopeChatOptions mediumOptions = strategy.buildManusOptions(ResponseLength.MEDIUM);
        DashScopeChatOptions longOptions = strategy.buildManusOptions(ResponseLength.LONG);

        assertEquals(768, shortOptions.getMaxTokens());
        assertEquals(1280, mediumOptions.getMaxTokens());
        assertEquals(2304, longOptions.getMaxTokens());
        assertFalse(Boolean.TRUE.equals(shortOptions.getInternalToolExecutionEnabled()));
    }
}
