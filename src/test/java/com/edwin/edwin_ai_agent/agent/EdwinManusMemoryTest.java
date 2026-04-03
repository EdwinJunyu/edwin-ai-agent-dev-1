package com.edwin.edwin_ai_agent.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.edwin.edwin_ai_agent.chatmemory.ManusConversationMemory;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdwinManusMemoryTest {

    private final ResponseLengthStrategy responseLengthStrategy = new ResponseLengthStrategy();

    @Test
    void followUpQuestionsReuseConversationHistoryAcrossRuns() throws IOException {
        ManusConversationMemory memory = new ManusConversationMemory(createTestDir().toString(), 10);
        String chatId = "chat-memory";

        runTurn(memory, chatId, "What is cognitive science?", "Cognitive science is the interdisciplinary study of mind and intelligence.");
        runTurn(memory, chatId, "tell me more", "It combines ideas from psychology, neuroscience, linguistics, philosophy, and AI.");

        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        EdwinManus thirdAgent = createAgentWithReply(
                "You just asked me to tell you more about cognitive science.",
                capturedPrompt
        );
        thirdAgent.seedConversationHistory(memory.loadHistory(chatId));
        thirdAgent.bindConversationMemory(chatId, memory);

        String payload = thirdAgent.run("what did I ask you just now?");
        List<Message> promptHistory = capturedPrompt.get().getInstructions();
        List<String> promptTexts = promptHistory.stream()
                .map(message -> ((AbstractMessage) message).getText())
                .filter(text -> text != null && !text.isBlank())
                .filter(text -> !text.equals(thirdAgent.getNextStepPrompt()))
                .toList();
        List<Message> storedHistory = memory.loadHistory(chatId);

        assertTrue(payload.contains("\"kind\":\"final\""));
        assertTrue(promptTexts.contains("What is cognitive science?"));
        assertTrue(promptTexts.contains("Cognitive science is the interdisciplinary study of mind and intelligence."));
        assertTrue(promptTexts.contains("tell me more"));
        assertTrue(promptTexts.contains("It combines ideas from psychology, neuroscience, linguistics, philosophy, and AI."));
        assertTrue(promptTexts.contains("what did I ask you just now?"));
        assertTrue(promptTexts.indexOf("tell me more") > promptTexts.indexOf("What is cognitive science?"));
        assertTrue(promptTexts.indexOf("what did I ask you just now?") > promptTexts.indexOf("tell me more"));

        assertEquals(6, storedHistory.size());
        assertEquals("what did I ask you just now?", ((UserMessage) storedHistory.get(4)).getText());
        assertEquals("You just asked me to tell you more about cognitive science.",
                ((AssistantMessage) storedHistory.get(5)).getText());
    }

    private void runTurn(ManusConversationMemory memory, String chatId, String userPrompt, String assistantReply) {
        EdwinManus agent = createAgentWithReply(assistantReply, new AtomicReference<>());
        agent.seedConversationHistory(memory.loadHistory(chatId));
        agent.bindConversationMemory(chatId, memory);
        agent.run(userPrompt);
    }

    private EdwinManus createAgentWithReply(String assistantReply, AtomicReference<Prompt> capturedPrompt) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt(any(Prompt.class))).thenAnswer(invocation -> {
            capturedPrompt.set(invocation.getArgument(0));
            return requestSpec;
        });
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse(assistantReply));

        EdwinManus agent = new EdwinManus(
                new ToolCallback[0],
                mock(ChatModel.class),
                responseLengthStrategy,
                ResponseLength.MEDIUM
        );
        agent.setChatClient(chatClient);
        return agent;
    }

    private ChatResponse chatResponse(String assistantReply) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(assistantReply)
                .toolCalls(List.of())
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private Path createTestDir() throws IOException {
        Path dir = Path.of("target", "test-manus-agent-memory-" + UUID.randomUUID());
        return Files.createDirectories(dir).toAbsolutePath();
    }
}
