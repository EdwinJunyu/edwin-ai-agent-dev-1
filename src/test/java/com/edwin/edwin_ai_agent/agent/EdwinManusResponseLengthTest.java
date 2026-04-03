package com.edwin.edwin_ai_agent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EdwinManusResponseLengthTest {

    private final ResponseLengthStrategy responseLengthStrategy = new ResponseLengthStrategy();

    @Test
    void shortModeBindsPromptAndTokenBudgetTogether() {
        EdwinManus agent = new EdwinManus(
                new ToolCallback[0],
                mock(ChatModel.class),
                responseLengthStrategy,
                ResponseLength.SHORT
        );

        DashScopeChatOptions chatOptions = (DashScopeChatOptions) agent.getChatOptions();
        assertTrue(agent.getSystemPrompt().contains("Response length mode: short."));
        assertTrue(agent.getNextStepPrompt().contains("Final answer length mode: short."));
        assertEquals(768, chatOptions.getMaxTokens());
    }

    @Test
    void longModeAddsDetailedPromptInstructionAndBudget() {
        EdwinManus agent = new EdwinManus(
                new ToolCallback[0],
                mock(ChatModel.class),
                responseLengthStrategy,
                ResponseLength.LONG
        );

        DashScopeChatOptions chatOptions = (DashScopeChatOptions) agent.getChatOptions();
        assertTrue(agent.getSystemPrompt().contains("Response length mode: long."));
        assertTrue(agent.getSystemPrompt().contains("Provide a detailed answer"));
        assertTrue(agent.getNextStepPrompt().contains("Final answer length mode: long."));
        assertEquals(2304, chatOptions.getMaxTokens());
    }
}
