package com.edwin.edwin_ai_agent.app;

import com.edwin.edwin_ai_agent.agent.EdwinManus;
import com.edwin.edwin_ai_agent.chatmemory.ManusConversationMemory;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ManusApp {

    private final ToolCallback[] allTools;
    private final ChatModel dashscopeChatModel;
    private final ResponseLengthStrategy responseLengthStrategy;
    private final ManusConversationMemory manusConversationMemory;

    public ManusApp(
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel,
            ResponseLengthStrategy responseLengthStrategy,
            ManusConversationMemory manusConversationMemory
    ) {
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
        this.responseLengthStrategy = responseLengthStrategy;
        this.manusConversationMemory = manusConversationMemory;
    }

    public SseEmitter runStream(String message, String chatId, ResponseLength responseLength) {
        EdwinManus edwinManus = createAgent(responseLength);
        if (StringUtils.hasText(chatId)) {
            // Restore only prior user/assistant turns so follow-up prompts keep concise context.
            edwinManus.seedConversationHistory(manusConversationMemory.loadHistory(chatId));
            edwinManus.bindConversationMemory(chatId, manusConversationMemory);
        }
        return edwinManus.runStream(message);
    }

    protected EdwinManus createAgent(ResponseLength responseLength) {
        return new EdwinManus(allTools, dashscopeChatModel, responseLengthStrategy, responseLength);
    }
}
