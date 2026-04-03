package com.edwin.edwin_ai_agent.chatmemory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ManusConversationMemoryTest {

    @Test
    void loadHistoryKeepsConversationIsolationAndWindow() throws IOException {
        ManusConversationMemory memory = new ManusConversationMemory(createTestDir().toString(), 4);

        memory.appendTurn("chat-a", "What is cognitive science?", "It studies the mind.");
        memory.appendTurn("chat-a", "tell me more", "It combines psychology and AI.");
        memory.appendTurn("chat-a", "what did I ask just now?", "You asked me to tell you more.");
        memory.appendTurn("chat-b", "hello", "hi");

        List<Message> chatAHistory = memory.loadHistory("chat-a");
        List<Message> chatBHistory = memory.loadHistory("chat-b");

        assertEquals(4, chatAHistory.size());
        assertInstanceOf(UserMessage.class, chatAHistory.get(0));
        assertEquals("tell me more", ((UserMessage) chatAHistory.get(0)).getText());
        assertInstanceOf(AssistantMessage.class, chatAHistory.get(1));
        assertEquals("It combines psychology and AI.", ((AssistantMessage) chatAHistory.get(1)).getText());
        assertInstanceOf(UserMessage.class, chatAHistory.get(2));
        assertEquals("what did I ask just now?", ((UserMessage) chatAHistory.get(2)).getText());
        assertInstanceOf(AssistantMessage.class, chatAHistory.get(3));
        assertEquals("You asked me to tell you more.", ((AssistantMessage) chatAHistory.get(3)).getText());

        assertEquals(2, chatBHistory.size());
        assertEquals("hello", ((UserMessage) chatBHistory.get(0)).getText());
        assertEquals("hi", ((AssistantMessage) chatBHistory.get(1)).getText());
    }

    private Path createTestDir() throws IOException {
        Path dir = Path.of("target", "test-manus-memory-" + UUID.randomUUID());
        return Files.createDirectories(dir).toAbsolutePath();
    }
}
