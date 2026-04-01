package com.edwin.edwin_ai_agent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EdwinManusSearchPolicyTest {

    @Test
    void shouldDescribeOfficialAndVerifiedSearchPolicyInPrompt() {
        EdwinManus agent = new EdwinManus(new ToolCallback[0], mock(ChatModel.class));

        assertEquals(8, agent.getMaxSteps());
        assertTrue(agent.getNextStepPrompt().contains("officialFirst=true"));
        assertTrue(agent.getNextStepPrompt().contains("needVerification=true"));
        assertTrue(agent.getNextStepPrompt().contains("preferredDomains"));
        assertTrue(agent.getNextStepPrompt().contains("timeHint"));
    }
}
