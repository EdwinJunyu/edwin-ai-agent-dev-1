package com.edwin.edwin_ai_agent.chatmemory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ManusConversationMemory {

    private static final int HISTORY_WINDOW = 10;

    private final ChatMemory chatMemory;

    public ManusConversationMemory() {
        this(System.getProperty("user.dir") + "/tmp/manus-chat-memory", HISTORY_WINDOW);
    }

    public ManusConversationMemory(String dir, int historyWindow) {
        this.chatMemory = new FileBaseChatMemory(dir, historyWindow);
    }

    // Only user/final-assistant turns are persisted so the next prompt stays free of tool traces.
    public void appendTurn(String chatId, String userPrompt, String assistantReply) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userPrompt) || !StringUtils.hasText(assistantReply)) {
            return;
        }

        chatMemory.add(chatId, List.of(
                new UserMessage(userPrompt),
                new AssistantMessage(assistantReply)
        ));
    }

    public List<Message> loadHistory(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return List.of();
        }
        return new ArrayList<>(chatMemory.get(chatId));
    }

    public void clear(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return;
        }
        chatMemory.clear(chatId);
    }
}
