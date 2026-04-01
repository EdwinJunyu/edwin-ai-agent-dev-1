/* Previous implementation commented out per workspace rule. */
// #NEW CODE#
package com.edwin.edwin_ai_agent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.edwin.edwin_ai_agent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool-call agent with guardrails to reduce repeated thinking loops.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    static final String SEARCH_WEB_TOOL_NAME = "searchWeb";
    static final String TERMINATE_TOOL_NAME = "doTerminate";
    static final int SEARCH_WEB_MAX_EXECUTIONS = 2;
    private static final int TOOL_RESULT_SUMMARY_LIMIT = 320;
    private static final int TOOL_ARGUMENT_SUMMARY_LIMIT = 160;

    enum GuardAction {
        EXECUTE,
        RETRY_WITH_CORRECTION,
        FINISH
    }

    record ToolBatchGuardDecision(
            GuardAction action,
            String reason,
            String batchSignature,
            String searchQuerySignature
    ) {
    }

    private final ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;

    private String lastAssistantText = "";
    private boolean nextStepPromptInjected = false;
    private String lastToolBatchSignature = "";
    private String pendingToolBatchSignature = "";
    private String lastSearchQuerySignature = "";
    private String pendingSearchQuerySignature = "";
    private String lastEffectiveToolResultSummary = "";
    private String lastCompletionReason = "";
    private final Map<String, Integer> toolExecutionCounts = new LinkedHashMap<>();

    public ToolCallAgent(ToolCallback[] availableTools) {
        this(
                availableTools,
                ToolCallingManager.builder().build(),
                DashScopeChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .build()
        );
    }

    ToolCallAgent(ToolCallback[] availableTools, ToolCallingManager toolCallingManager, ChatOptions chatOptions) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = toolCallingManager;
        this.chatOptions = chatOptions;
    }

    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                setState(AgentState.FINISHED);
                return buildPayload(
                        buildBubble("thought", "\u601d\u8003\u8fc7\u7a0b", buildFinalThoughtContent()),
                        buildBubble("final", "\u6700\u7ec8\u56de\u590d", buildFinalReply())
                );
            }
            return act();
        } catch (Exception e) {
            log.error("Agent execution failed", e);
            setState(AgentState.ERROR);
            return buildPayload(
                    buildBubble("error", "\u6267\u884c\u5f02\u5e38", e.getMessage())
            );
        }
    }

    @Override
    public boolean think() {
        injectNextStepPromptOnce();
        lastCompletionReason = "";
        pendingToolBatchSignature = "";
        pendingSearchQuerySignature = "";
        return thinkInternal(false);
    }

    @Override
    public String act() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            return "";
        }

        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        String assistantText = normalizeWhitespace(assistantMessage.getText());
        if (StringUtils.hasText(assistantText)) {
            lastAssistantText = assistantText;
        }

        List<AssistantMessage.ToolCall> toolCalls = getSafeToolCalls(assistantMessage);
        String toolCallSummary = formatToolCalls(toolCalls);

        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // setMessageList(toolExecutionResult.conversationHistory());
        // #NEW CODE#
        setMessageList(new ArrayList<>(toolExecutionResult.conversationHistory()));

        ToolResponseMessage toolResponseMessage =
                (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> TERMINATE_TOOL_NAME.equals(response.name()));

        String toolResultSummary = summarizeToolResponses(toolResponseMessage.getResponses());
        rememberExecutedToolBatch(toolCalls, toolResultSummary);

        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
            lastCompletionReason = "The model called terminate because the current evidence is enough to finish.";
            String finalReply = StringUtils.hasText(assistantText)
                    ? assistantText
                    : buildFallbackFinalReply(lastCompletionReason);
            return buildPayload(
                    buildBubble("thought", "\u601d\u8003\u8fc7\u7a0b", buildFinalThoughtContent()),
                    buildBubble("final", "\u6700\u7ec8\u56de\u590d", finalReply)
            );
        }

        return buildPayload(
                buildBubble("thought", "\u601d\u8003\u8fc7\u7a0b", buildOngoingThoughtContent(toolCallSummary, toolResultSummary))
        );
    }

    @Override
    protected void cleanup() {
        // Reset run-scoped guard state so each request starts cleanly.
        nextStepPromptInjected = false;
        toolCallChatResponse = null;
        lastAssistantText = "";
        lastToolBatchSignature = "";
        pendingToolBatchSignature = "";
        lastSearchQuerySignature = "";
        pendingSearchQuerySignature = "";
        lastEffectiveToolResultSummary = "";
        lastCompletionReason = "";
        toolExecutionCounts.clear();
    }

    boolean shouldInjectNextStepPrompt() {
        return !nextStepPromptInjected && StringUtils.hasText(getNextStepPrompt());
    }

    String canonicalizeArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return "{}";
        }
        try {
            return canonicalizeJsonValue(JSONUtil.parse(arguments));
        } catch (Exception ignored) {
            return normalizeWhitespace(arguments).toLowerCase(Locale.ROOT);
        }
    }

    String buildToolBatchSignature(List<AssistantMessage.ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(toolCall -> toolCall.name() + ":" + canonicalizeArguments(toolCall.arguments()))
                .sorted()
                .collect(Collectors.joining("|"));
    }

    ToolBatchGuardDecision evaluateToolBatch(List<AssistantMessage.ToolCall> toolCalls, boolean correctionRetry) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return new ToolBatchGuardDecision(
                    GuardAction.FINISH,
                    "The model did not provide a new tool plan, so the agent will finish with current evidence.",
                    "",
                    ""
            );
        }

        String batchSignature = buildToolBatchSignature(toolCalls);
        if (StringUtils.hasText(lastToolBatchSignature) && lastToolBatchSignature.equals(batchSignature)) {
            return guardDecision(
                    correctionRetry,
                    "Detected the same tool batch again. Repeating it would only duplicate existing work.",
                    batchSignature,
                    ""
            );
        }

        List<String> searchQueries = extractSearchQueries(toolCalls);
        if (searchQueries.size() > 1) {
            return guardDecision(
                    correctionRetry,
                    "The model scheduled multiple searchWeb calls in the same step. The plan must be narrowed first.",
                    batchSignature,
                    ""
            );
        }

        if (!searchQueries.isEmpty()) {
            int executedSearchCount = toolExecutionCounts.getOrDefault(SEARCH_WEB_TOOL_NAME, 0);
            String searchQuerySignature = normalizeSearchQuery(searchQueries.get(0));
            if (executedSearchCount >= SEARCH_WEB_MAX_EXECUTIONS) {
                return guardDecision(
                        correctionRetry,
                        "searchWeb has already reached the per-request execution budget of 2.",
                        batchSignature,
                        searchQuerySignature
                );
            }
            if (executedSearchCount == 1 && !isRefinedSearch(lastSearchQuerySignature, searchQuerySignature)) {
                return guardDecision(
                        correctionRetry,
                        "The second searchWeb call must use a materially different refined query.",
                        batchSignature,
                        searchQuerySignature
                );
            }
            return new ToolBatchGuardDecision(GuardAction.EXECUTE, "", batchSignature, searchQuerySignature);
        }

        return new ToolBatchGuardDecision(GuardAction.EXECUTE, "", batchSignature, "");
    }

    String summarizeToolResponses(List<ToolResponseMessage.ToolResponse> responses) {
        return responses.stream()
                .filter(response -> !TERMINATE_TOOL_NAME.equals(response.name()))
                .map(response -> "- " + response.name() + ": " + summarizeResponseData(response.name(), response.responseData()))
                .collect(Collectors.joining("\n"));
    }

    private boolean thinkInternal(boolean correctionRetry) {
        Prompt prompt = new Prompt(getMessageList(), chatOptions);

        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();

            toolCallChatResponse = chatResponse;

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            String assistantText = normalizeWhitespace(assistantMessage.getText());
            if (StringUtils.hasText(assistantText)) {
                lastAssistantText = assistantText;
            }

            List<AssistantMessage.ToolCall> toolCalls = getSafeToolCalls(assistantMessage);
            log.info("{} thought result: {}", getName(), assistantText);
            log.info("{} current tool plan count: {}", getName(), toolCalls.size());

            if (toolCalls.isEmpty()) {
                getMessageList().add(assistantMessage);
                lastCompletionReason = correctionRetry
                        ? "The correction retry did not produce a new tool plan, so the agent will finish now."
                        : "The model decided it already has enough information to answer directly.";
                return false;
            }

            ToolBatchGuardDecision decision = evaluateToolBatch(toolCalls, correctionRetry);
            if (decision.action() == GuardAction.EXECUTE) {
                pendingToolBatchSignature = decision.batchSignature();
                pendingSearchQuerySignature = decision.searchQuerySignature();
                logToolCalls(toolCalls);
                return true;
            }

            if (decision.action() == GuardAction.RETRY_WITH_CORRECTION) {
                // Keep the rejected assistant turn so the retry has explicit context.
                getMessageList().add(assistantMessage);
                getMessageList().add(new UserMessage(buildCorrectionPrompt(decision.reason())));
                return thinkInternal(true);
            }

            lastCompletionReason = decision.reason();
            toolCallChatResponse = buildFinalOnlyResponse(buildFallbackFinalReply(decision.reason()));
            lastAssistantText = toolCallChatResponse.getResult().getOutput().getText();
            getMessageList().add(toolCallChatResponse.getResult().getOutput());
            return false;
        } catch (Exception e) {
            log.error("{} think stage failed: {}", getName(), e.getMessage(), e);
            lastAssistantText = "Thinking failed: " + e.getMessage();
            lastCompletionReason = "The think stage failed, so the agent stopped with the current state.";
            getMessageList().add(new AssistantMessage(lastAssistantText));
            return false;
        }
    }

    private void injectNextStepPromptOnce() {
        // Inject the steering prompt only once per run so it does not keep amplifying repeated reasoning.
        if (shouldInjectNextStepPrompt()) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
            nextStepPromptInjected = true;
        }
    }

    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        String toolCallInfo = toolCalls.stream()
                .map(toolCall -> toolCall.name() + " -> " + toolCall.arguments())
                .collect(Collectors.joining("\n"));
        if (StringUtils.hasText(toolCallInfo)) {
            log.info(toolCallInfo);
        }
    }

    private ToolBatchGuardDecision guardDecision(
            boolean correctionRetry,
            String reason,
            String batchSignature,
            String searchQuerySignature
    ) {
        GuardAction action = correctionRetry ? GuardAction.FINISH : GuardAction.RETRY_WITH_CORRECTION;
        return new ToolBatchGuardDecision(action, reason, batchSignature, searchQuerySignature);
    }

    private String buildCorrectionPrompt(String reason) {
        return """
                The previous tool plan is redundant or over budget: %s
                Do not repeat the same tool call or the same search query.
                Use the evidence already collected to answer now, or choose a materially different tool or refined arguments.
                If current evidence is sufficient, provide the final answer and call the `terminate` tool.
                Unless the user explicitly asked, do not proactively suggest additional next steps.
                """.formatted(reason).trim();
    }

    private List<AssistantMessage.ToolCall> getSafeToolCalls(AssistantMessage assistantMessage) {
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        return toolCalls == null ? List.of() : toolCalls;
    }

    private String buildOngoingThoughtContent(String toolCallSummary, String toolResultSummary) {
        return mergeBlocks(
                block("\u672c\u6b65\u5de5\u5177\u8c03\u7528", toolCallSummary),
                block("\u672c\u6b65\u7ed3\u679c\u6458\u8981", toolResultSummary)
        );
    }

    private String buildFinalThoughtContent() {
        String completionReason = StringUtils.hasText(lastCompletionReason)
                ? lastCompletionReason
                : "The current evidence is sufficient, so the agent will finish here.";
        return mergeBlocks(
                block("\u5b8c\u6210\u539f\u56e0", completionReason),
                block("\u6700\u540e\u4e00\u6b21\u6709\u6548\u5de5\u5177\u7ed3\u679c\u6458\u8981", lastEffectiveToolResultSummary)
        );
    }

    private String buildFinalReply() {
        if (StringUtils.hasText(lastAssistantText)) {
            return lastAssistantText;
        }
        return buildFallbackFinalReply(lastCompletionReason);
    }

    private String buildFallbackFinalReply(String reason) {
        if (StringUtils.hasText(lastEffectiveToolResultSummary)) {
            return "Here is the conclusion based on the effective tool output so far:\n" + lastEffectiveToolResultSummary;
        }
        if (StringUtils.hasText(reason)) {
            return reason;
        }
        return "There is no new effective tool result to continue from, so the agent stops here.";
    }

    private void rememberExecutedToolBatch(List<AssistantMessage.ToolCall> toolCalls, String toolResultSummary) {
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            toolExecutionCounts.merge(toolCall.name(), 1, Integer::sum);
        }

        if (StringUtils.hasText(pendingToolBatchSignature)) {
            lastToolBatchSignature = pendingToolBatchSignature;
        }
        if (StringUtils.hasText(pendingSearchQuerySignature)) {
            lastSearchQuerySignature = pendingSearchQuerySignature;
        }
        if (StringUtils.hasText(toolResultSummary)) {
            lastEffectiveToolResultSummary = toolResultSummary;
            log.info(toolResultSummary);
        }

        pendingToolBatchSignature = "";
        pendingSearchQuerySignature = "";
    }

    private String formatToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(this::formatToolCall)
                .collect(Collectors.joining("\n"));
    }

    private String formatToolCall(AssistantMessage.ToolCall toolCall) {
        if (SEARCH_WEB_TOOL_NAME.equals(toolCall.name())) {
            String query = extractSearchQuery(toolCall.arguments());
            if (StringUtils.hasText(query)) {
                return "- searchWeb(query=\"" + abbreviate(query, TOOL_ARGUMENT_SUMMARY_LIMIT) + "\")";
            }
        }

        String arguments = canonicalizeArguments(toolCall.arguments());
        if ("{}".equals(arguments)) {
            return "- " + toolCall.name() + "()";
        }
        return "- " + toolCall.name() + "(" + abbreviate(arguments, TOOL_ARGUMENT_SUMMARY_LIMIT) + ")";
    }

    private List<String> extractSearchQueries(List<AssistantMessage.ToolCall> toolCalls) {
        List<String> queries = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (SEARCH_WEB_TOOL_NAME.equals(toolCall.name())) {
                String query = extractSearchQuery(toolCall.arguments());
                if (StringUtils.hasText(query)) {
                    queries.add(query);
                }
            }
        }
        return queries;
    }

    private String extractSearchQuery(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return "";
        }
        try {
            Object parsed = JSONUtil.parse(arguments);
            if (parsed instanceof JSONObject jsonObject) {
                return normalizeWhitespace(jsonObject.getStr("query", ""));
            }
        } catch (Exception ignored) {
            // Fall through to plain-text normalization when arguments are not valid JSON.
        }
        return normalizeWhitespace(arguments);
    }

    private String normalizeSearchQuery(String query) {
        return normalizeWhitespace(query)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .trim();
    }

    private boolean isRefinedSearch(String previousQuerySignature, String currentQuerySignature) {
        if (!StringUtils.hasText(currentQuerySignature)) {
            return false;
        }
        if (!StringUtils.hasText(previousQuerySignature)) {
            return true;
        }
        return !previousQuerySignature.equals(currentQuerySignature);
    }

    private String summarizeResponseData(String toolName, String responseData) {
        if (SEARCH_WEB_TOOL_NAME.equals(toolName)) {
            return summarizeSearchWebResponse(responseData);
        }
        return abbreviate(normalizeWhitespace(responseData), TOOL_RESULT_SUMMARY_LIMIT);
    }

    private String summarizeSearchWebResponse(String responseData) {
        try {
            JSONObject resultObject = JSONUtil.parseObj(responseData);
            String query = normalizeWhitespace(resultObject.getStr("query", ""));
            JSONArray results = resultObject.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return StringUtils.hasText(query)
                        ? "Query \"" + query + "\" returned no usable results."
                        : "The search returned no usable results.";
            }

            List<String> topResults = new ArrayList<>();
            int limit = Math.min(results.size(), 3);
            for (int index = 0; index < limit; index++) {
                JSONObject item = results.getJSONObject(index);
                String title = normalizeWhitespace(item.getStr("title", ""));
                String url = normalizeWhitespace(item.getStr("url", ""));
                if (StringUtils.hasText(title) && StringUtils.hasText(url)) {
                    topResults.add(title + " (" + url + ")");
                } else if (StringUtils.hasText(title)) {
                    topResults.add(title);
                }
            }

            String header = StringUtils.hasText(query)
                    ? "Query \"" + query + "\" returned " + results.size() + " results."
                    : "The search returned " + results.size() + " results.";
            if (topResults.isEmpty()) {
                return header;
            }
            return abbreviate(header + " Top hits: " + String.join("; ", topResults), TOOL_RESULT_SUMMARY_LIMIT);
        } catch (Exception ignored) {
            return abbreviate(normalizeWhitespace(responseData), TOOL_RESULT_SUMMARY_LIMIT);
        }
    }

    private ChatResponse buildFinalOnlyResponse(String finalReply) {
        AssistantMessage assistantMessage = new AssistantMessage(finalReply);
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private String block(String title, String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return title + "\n" + content.trim();
    }

    private String mergeBlocks(String... blocks) {
        return Arrays.stream(blocks)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    @SafeVarargs
    private final String buildPayload(Map<String, Object>... bubbles) {
        List<Map<String, Object>> payload = Arrays.stream(bubbles)
                .filter(item -> item != null)
                .collect(Collectors.toList());
        if (payload.isEmpty()) {
            return "";
        }
        return JSONUtil.toJsonStr(payload);
    }

    private Map<String, Object> buildBubble(String kind, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Map<String, Object> bubble = new LinkedHashMap<>();
        bubble.put("kind", kind);
        bubble.put("step", getCurrentStep());
        bubble.put("title", title);
        bubble.put("content", content.trim());
        return bubble;
    }

    private String canonicalizeJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JSONObject jsonObject) {
            return jsonObject.keySet().stream()
                    .sorted()
                    .map(key -> JSONUtil.toJsonStr(key) + ":" + canonicalizeJsonValue(jsonObject.get(key)))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof JSONArray jsonArray) {
            List<String> items = new ArrayList<>();
            for (Object item : jsonArray) {
                items.add(canonicalizeJsonValue(item));
            }
            return items.stream().collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof CharSequence charSequence) {
            return JSONUtil.toJsonStr(normalizeWhitespace(charSequence.toString()));
        }
        return JSONUtil.toJsonStr(value);
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit - 3) + "...";
    }
}
