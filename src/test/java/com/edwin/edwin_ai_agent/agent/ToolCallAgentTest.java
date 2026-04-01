package com.edwin.edwin_ai_agent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallAgentTest {

    private final AtomicInteger idCounter = new AtomicInteger();

    private ToolCallingManager toolCallingManager;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ToolCallAgent agent;

    @BeforeEach
    void setUp() {
        toolCallingManager = mock(ToolCallingManager.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        agent = new ToolCallAgent(
                new ToolCallback[0],
                toolCallingManager,
                DashScopeChatOptions.builder().internalToolExecutionEnabled(false).build()
        );
        agent.setName("test-agent");
        agent.setSystemPrompt("system prompt");
        agent.setNextStepPrompt("inject once");
        agent.setChatClient(chatClient);
        agent.getMessageList().add(new UserMessage("user request"));
    }

    @Test
    void thinkInjectsNextStepPromptOnlyOnceAcrossRounds() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("Need one search.", List.of(toolCall("searchWeb", "{\"query\":\"q1\"}"))),
                chatResponse("Final answer without tools.", List.of())
        );

        assertTrue(agent.think());
        assertFalse(agent.think());

        long injectedCount = agent.getMessageList().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .filter(message -> "inject once".equals(message.getText()))
                .count();

        assertEquals(1, injectedCount);
    }

    @Test
    void duplicateToolBatchTriggersCorrectionAndSkipsSecondExecution() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("Search first.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events\"}"))),
                chatResponse("Search first again.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events\"}"))),
                chatResponse("I will stop with the current evidence.", List.of())
        );
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(toolExecutionResult(toolResponse("searchWeb", searchResponse("sfu april events", "Page A", "https://example.com/a"))));

        assertTrue(agent.think());
        agent.act();
        assertFalse(agent.think());

        verify(toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
        assertEquals(1, agent.getToolExecutionCounts().get("searchWeb"));
        assertTrue(agent.getLastCompletionReason().contains("correction retry"));
    }

    @Test
    void thirdSearchWebRequestIsBlockedAfterTwoExecutions() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("Search first.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events\"}"))),
                chatResponse("Search refined.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events official site\"}"))),
                chatResponse("Search again.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events calendar\"}"))),
                chatResponse("Search again anyway.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events clubs\"}")))
        );
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(
                        toolExecutionResult(toolResponse("searchWeb", searchResponse("sfu april events", "Page A", "https://example.com/a"))),
                        toolExecutionResult(toolResponse("searchWeb", searchResponse("sfu april events official site", "Page B", "https://example.com/b")))
                );

        assertTrue(agent.think());
        agent.act();
        assertTrue(agent.think());
        agent.act();
        assertFalse(agent.think());

        verify(toolCallingManager, times(2)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
        assertEquals(2, agent.getToolExecutionCounts().get("searchWeb"));
        assertTrue(agent.getLastCompletionReason().contains("execution budget of 2"));
    }

    @Test
    void stepReturnsThoughtAndFinalWhenModelAnswersWithoutTools() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("Here is the final answer.", List.of())
        );

        String payload = agent.step();

        assertTrue(payload.contains("\"kind\":\"thought\""));
        assertTrue(payload.contains("\"kind\":\"final\""));
        verify(toolCallingManager, never()).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
    }

    @Test
    void ongoingThoughtPayloadOmitsAssistantNarration() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("I will search and then suggest the next step.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events\"}")))
        );
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(toolExecutionResult(toolResponse("searchWeb", searchResponse("sfu april events", "Page A", "https://example.com/a"))));

        assertTrue(agent.think());
        String payload = agent.act();

        assertTrue(payload.contains("\"kind\":\"thought\""));
        assertTrue(payload.contains("searchWeb(query="));
        assertTrue(payload.contains("Top hits"));
        assertFalse(payload.contains("I will search and then suggest the next step."));
        assertTrue(payload.contains("\\u5f53\\u524d\\u72b6\\u6001") || payload.contains("当前状态"));
    }

    private ChatResponse chatResponse(String content, List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content)
                .toolCalls(toolCalls)
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private AssistantMessage.ToolCall toolCall(String name, String arguments) {
        return new AssistantMessage.ToolCall("call-" + idCounter.incrementAndGet(), "function", name, arguments);
    }

    private ToolExecutionResult toolExecutionResult(ToolResponseMessage.ToolResponse response) {
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(response))
                .metadata(Map.of())
                .build();

        return ToolExecutionResult.builder()
                .conversationHistory(List.of(new UserMessage("user request"), toolResponseMessage))
                .build();
    }

    private ToolResponseMessage.ToolResponse toolResponse(String name, String responseData) {
        return new ToolResponseMessage.ToolResponse("tool-" + idCounter.incrementAndGet(), name, responseData);
    }

    private String searchResponse(String query, String title, String url) {
        return """
                {
                  "query": "%s",
                  "results": [
                    {
                      "title": "%s",
                      "url": "%s",
                      "content": "sample"
                    }
                  ]
                }
                """.formatted(query, title, url).trim();
    }
}
