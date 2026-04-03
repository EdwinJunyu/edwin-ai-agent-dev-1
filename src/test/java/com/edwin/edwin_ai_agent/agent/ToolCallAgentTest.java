/* Previous implementation commented out per workspace rule. */
// #NEW CODE#
package com.edwin.edwin_ai_agent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.edwin.edwin_ai_agent.agent.model.AgentState;
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

import java.lang.reflect.Method;
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
        // assertTrue(agent.getLastCompletionReason().contains("correction retry"));
        // #NEW CODE#
        assertTrue(agent.getLastCompletionReason().contains("\u7ea0\u504f\u91cd\u8bd5\u540e\u672a\u4ea7\u751f\u65b0\u7684\u5de5\u5177\u8ba1\u5212"));
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
        // assertTrue(agent.getLastCompletionReason().contains("execution budget of 2"));
        // #NEW CODE#
        assertTrue(agent.getLastCompletionReason().contains("\u6267\u884c\u9884\u7b97"));
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
        assertTrue(payload.contains("\u5f53\u524d\u72b6\u6001"));
        assertTrue(payload.contains("\u6b63\u5728\u68c0\u7d22"));
    }

    @Test
    void ongoingThoughtShowsVerificationStatusForAuthoritativeButUnverifiedSearchResults() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("I will verify the source next.", List.of(toolCall("searchWeb", "{\"query\":\"sfu april events official\"}")))
        );
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(toolExecutionResult(toolResponse("searchWeb",
                        searchResponseWithMetadata(
                                "sfu april events official",
                                "Official Events Page",
                                "https://www.sfu.ca/events",
                                "official",
                                "content_found",
                                true
                        ))));

        assertTrue(agent.think());
        String payload = agent.act();

        assertTrue(payload.contains("\u6b63\u5728\u9a8c\u8bc1\u6765\u6e90"));
    }

    @Test
    void verifiedSearchResultsMapToDraftingStatus() throws Exception {
        ToolResponseMessage.ToolResponse response = toolResponse("searchWeb",
                searchResponseWithMetadata(
                        "sfu april events official",
                        "Official Events Page",
                        "https://www.sfu.ca/events",
                        "official",
                        "verified",
                        true
                ));

        // String statusContent = invokeSearchStatusContent(List.of(response));
        // #NEW CODE#
        String statusContent = invokeSearchStatusContent(
                List.of(response),
                new ToolCallAgent.SearchEvidenceDecision(
                        true,
                        true,
                        "\u5f53\u524d\u5df2\u83b7\u53d6\u5230\u8db3\u4ee5\u652f\u6491\u7ed3\u8bba\u7684\u76f4\u63a5\u8bc1\u636e\u3002"
                )
        );

        assertTrue(statusContent.contains("\u6b63\u5728\u6574\u7406\u6700\u7ec8\u7b54\u590d"));
    }

    @Test
    void verifiedSearchResultsStayInVerificationWhenEvidenceThresholdNotMet() throws Exception {
        ToolResponseMessage.ToolResponse response = toolResponse("searchWeb",
                searchResponseWithMetadata(
                        "nasdaq march 2026 top 5 company performance",
                        "Top 10 Market Cap Companies",
                        "https://markets.example.com/top10",
                        "authoritative",
                        "verified",
                        true,
                        false,
                        "\u5f53\u524d\u68c0\u7d22\u7ed3\u679c\u8fd8\u4e0d\u8db3\u4ee5\u7a33\u5b9a\u652f\u6301\u7cbe\u786e\u7684\u6570\u636e/\u6392\u540d/\u6da8\u8dcc\u7c7b\u7ed3\u8bba\u3002"
                ));

        String statusContent = invokeSearchStatusContent(
                List.of(response),
                new ToolCallAgent.SearchEvidenceDecision(
                        true,
                        false,
                        "\u5f53\u524d\u68c0\u7d22\u7ed3\u679c\u8fd8\u4e0d\u8db3\u4ee5\u7a33\u5b9a\u652f\u6301\u7cbe\u786e\u7684\u6570\u636e/\u6392\u540d/\u6da8\u8dcc\u7c7b\u7ed3\u8bba\u3002"
                )
        );

        assertTrue(statusContent.contains("\u6b63\u5728\u9a8c\u8bc1\u6765\u6e90"));
        assertFalse(statusContent.contains("\u6b63\u5728\u6574\u7406\u6700\u7ec8\u7b54\u590d"));
    }

    @Test
    void insufficientSearchEvidenceFallsBackInsteadOfClaimingUnsupportedData() {
        when(callResponseSpec.chatResponse()).thenReturn(
                chatResponse("Search first.", List.of(toolCall("searchWeb", "{\"query\":\"nasdaq march 2026 top 5 company performance\"}"))),
                chatResponse("Here are the exact top 5 changes.", List.of()),
                chatResponse("I still do not have stronger evidence.", List.of())
        );
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(toolExecutionResult(toolResponse("searchWeb",
                        searchResponseWithMetadata(
                                "nasdaq march 2026 top 5 company performance",
                                "Top 10 Market Cap Companies",
                                "https://markets.example.com/top10",
                                "authoritative",
                                "verified",
                                true,
                                false,
                                "\u5f53\u524d\u68c0\u7d22\u7ed3\u679c\u8fd8\u4e0d\u8db3\u4ee5\u7a33\u5b9a\u652f\u6301\u7cbe\u786e\u7684\u6570\u636e/\u6392\u540d/\u6da8\u8dcc\u7c7b\u7ed3\u8bba\u3002"
                        ))));

        assertTrue(agent.think());
        agent.act();
        assertFalse(agent.think());

        assertTrue(agent.getLastCompletionReason().contains("\u5f53\u524d\u68c0\u7d22\u7ed3\u679c\u8fd8\u4e0d\u8db3\u4ee5"));
        assertTrue(agent.getLastAssistantText().contains("\u5f53\u524d\u8bc1\u636e\u8fd8\u4e0d\u8db3\u4ee5\u652f\u6301\u7cbe\u786e\u7ed3\u8bba"));
        assertTrue(agent.getLastAssistantText().contains("\u4e0d\u4f1a\u8f93\u51fa\u672a\u88ab\u5de5\u5177\u8bc1\u636e\u652f\u6301\u7684\u5177\u4f53\u6570\u636e\u6216\u5217\u8868"));
    }

    @Test
    void cleanupResetsStateAndConversationHistory() {
        agent.setState(AgentState.RUNNING);
        agent.setCurrentStep(3);
        agent.getMessageList().add(new AssistantMessage("partial reply"));
        agent.getToolExecutionCounts().put("searchWeb", 2);

        agent.cleanup();

        assertEquals(AgentState.IDLE, agent.getState());
        assertEquals(0, agent.getCurrentStep());
        assertTrue(agent.getMessageList().isEmpty());
        assertTrue(agent.getToolExecutionCounts().isEmpty());
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

    private String searchResponseWithMetadata(
            String query,
            String title,
            String url,
            String sourceType,
            String verificationStatus,
            boolean needVerification
    ) {
        return searchResponseWithMetadata(query, title, url, sourceType, verificationStatus, needVerification, null, null);
    }

    private String searchResponseWithMetadata(
            String query,
            String title,
            String url,
            String sourceType,
            String verificationStatus,
            boolean needVerification,
            Boolean evidenceThresholdMet,
            String evidenceThresholdReason
    ) {
        return """
                {
                  "query": "%s",
                  "strategy": {
                    "needVerification": %s%s%s
                  },
                  "results": [
                    {
                      "title": "%s",
                      "url": "%s",
                      "content": "sample",
                      "sourceType": "%s",
                      "verificationStatus": "%s"
                    }
                  ]
                }
                """.formatted(
                query,
                needVerification,
                evidenceThresholdMet == null ? "" : ",\n                    \"evidenceThresholdMet\": " + evidenceThresholdMet,
                evidenceThresholdReason == null ? "" : ",\n                    \"evidenceThresholdReason\": \"" + evidenceThresholdReason + "\"",
                title,
                url,
                sourceType,
                verificationStatus
        ).trim();
    }

    // private String invokeSearchStatusContent(List<ToolResponseMessage.ToolResponse> responses) throws Exception {
    //     Method method = ToolCallAgent.class.getDeclaredMethod("resolveSearchWebStatusContent", List.class);
    //     method.setAccessible(true);
    //     return (String) method.invoke(agent, responses);
    // }
    // #NEW CODE#
    private String invokeSearchStatusContent(
            List<ToolResponseMessage.ToolResponse> responses,
            ToolCallAgent.SearchEvidenceDecision searchEvidenceDecision
    ) throws Exception {
        Method method = ToolCallAgent.class.getDeclaredMethod(
                "resolveSearchWebStatusContent",
                List.class,
                ToolCallAgent.SearchEvidenceDecision.class
        );
        method.setAccessible(true);
        return (String) method.invoke(agent, responses, searchEvidenceDecision);
    }
}
