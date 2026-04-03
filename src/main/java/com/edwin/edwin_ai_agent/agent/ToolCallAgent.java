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
    static final String SCRAPE_WEB_PAGE_TOOL_NAME = "scrapeWebPage";
    static final String TERMINATE_TOOL_NAME = "doTerminate";
    static final int SEARCH_WEB_MAX_EXECUTIONS = 2;
    private static final int TOOL_RESULT_SUMMARY_LIMIT = 320;
    private static final int TOOL_ARGUMENT_SUMMARY_LIMIT = 160;
    private static final String STATUS_SEARCHING = "\u6b63\u5728\u68c0\u7d22";
    private static final String STATUS_VERIFYING = "\u6b63\u5728\u9a8c\u8bc1\u6765\u6e90";
    // private static final String STATUS_DRAFTING = "\u6b63\u5728\u6574\u7406\u6700\u7ec8\u56de\u590d";
    // #NEW CODE#
    private static final String STATUS_DRAFTING = "\u6b63\u5728\u6574\u7406\u6700\u7ec8\u7b54\u590d";

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

    record SearchEvidenceDecision(
            boolean hasSearchEvidence,
            boolean evidenceThresholdMet,
            String reason
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
    private boolean lastSearchEvidenceSeen = false;
    private boolean lastSearchEvidenceThresholdMet = true;
    private String lastSearchEvidenceReason = "";
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
        SearchEvidenceDecision searchEvidenceDecision = evaluateSearchEvidenceDecision(toolResponseMessage.getResponses());
        String ongoingStatusContent = resolveOngoingStatusContent(toolCalls, toolResponseMessage.getResponses(), searchEvidenceDecision);
        rememberExecutedToolBatch(toolCalls, toolResultSummary, searchEvidenceDecision);

        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
            // lastCompletionReason = "The model called terminate because the current evidence is enough to finish.";
            // String finalReply = StringUtils.hasText(assistantText)
            //         ? assistantText
            //         : buildFallbackFinalReply(lastCompletionReason);
            // #NEW CODE#
            boolean blockedByEvidenceThreshold = shouldBlockUnsupportedFinalAnswer(searchEvidenceDecision);
            lastCompletionReason = blockedByEvidenceThreshold
                    ? searchEvidenceDecision.reason()
                    : "\u6a21\u578b\u5df2\u8c03\u7528 terminate\uff0c\u56e0\u4e3a\u5f53\u524d\u8bc1\u636e\u5df2\u8db3\u4ee5\u5b8c\u6210\u4efb\u52a1\u3002";
            String finalReply = blockedByEvidenceThreshold
                    ? buildEvidenceThresholdFallback(searchEvidenceDecision.reason())
                    : (StringUtils.hasText(assistantText) ? assistantText : buildFallbackFinalReply(lastCompletionReason));
            // #NEW CODE#
            // Persist the exact final bubble text rather than the raw model draft.
            lastAssistantText = finalReply;
            return buildPayload(
                    buildBubble("thought", "\u601d\u8003\u8fc7\u7a0b", buildFinalThoughtContent()),
                    buildBubble("final", "\u6700\u7ec8\u56de\u590d", finalReply)
            );
        }

        return buildPayload(
                buildBubble("thought", "\u601d\u8003\u8fc7\u7a0b", buildOngoingThoughtContent(toolCallSummary, toolResultSummary, ongoingStatusContent))
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
        lastSearchEvidenceSeen = false;
        lastSearchEvidenceThresholdMet = true;
        lastSearchEvidenceReason = "";
        toolExecutionCounts.clear();
        resetRunState();
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
                    "\u6a21\u578b\u672a\u63d0\u4f9b\u65b0\u7684\u5de5\u5177\u8ba1\u5212\uff0c\u7cfb\u7edf\u5c06\u57fa\u4e8e\u5f53\u524d\u8bc1\u636e\u7ed3\u675f\u3002",
                    "",
                    ""
            );
        }

        String batchSignature = buildToolBatchSignature(toolCalls);
        if (StringUtils.hasText(lastToolBatchSignature) && lastToolBatchSignature.equals(batchSignature)) {
            return guardDecision(
                    correctionRetry,
                    "\u68c0\u6d4b\u5230\u76f8\u540c\u7684\u5de5\u5177\u6279\u6b21\u518d\u6b21\u51fa\u73b0\uff0c\u91cd\u590d\u6267\u884c\u53ea\u4f1a\u590d\u5236\u5df2\u6709\u5de5\u4f5c\u3002",
                    batchSignature,
                    ""
            );
        }

        List<String> searchQueries = extractSearchQueries(toolCalls);
        if (searchQueries.size() > 1) {
            return guardDecision(
                    correctionRetry,
                    "\u6a21\u578b\u5728\u540c\u4e00\u6b65\u4e2d\u5b89\u6392\u4e86\u591a\u4e2a searchWeb \u8c03\u7528\uff0c\u9700\u8981\u5148\u6536\u7a84\u8ba1\u5212\u3002",
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
                        "searchWeb \u5df2\u8fbe\u5230\u5355\u6b21\u8bf7\u6c42 2 \u6b21\u7684\u6267\u884c\u9884\u7b97\u3002",
                        batchSignature,
                        searchQuerySignature
                );
            }
            if (executedSearchCount == 1 && !isRefinedSearch(lastSearchQuerySignature, searchQuerySignature)) {
                return guardDecision(
                        correctionRetry,
                        "\u7b2c\u4e8c\u6b21 searchWeb \u5fc5\u987b\u4f7f\u7528\u5b9e\u8d28\u4e0d\u540c\u7684 refined query\u3002",
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
                SearchEvidenceDecision searchEvidenceDecision = getLastSearchEvidenceDecision();
                if (shouldBlockUnsupportedFinalAnswer(searchEvidenceDecision) && !correctionRetry) {
                    getMessageList().add(new UserMessage(buildEvidenceThresholdPrompt(searchEvidenceDecision.reason())));
                    return thinkInternal(true);
                }
                // lastCompletionReason = correctionRetry
                //         ? "The correction retry did not produce a new tool plan, so the agent will finish now."
                //         : "The model decided it already has enough information to answer directly.";
                // #NEW CODE#
                if (shouldBlockUnsupportedFinalAnswer(searchEvidenceDecision)) {
                    lastCompletionReason = searchEvidenceDecision.reason();
                    lastAssistantText = buildEvidenceThresholdFallback(searchEvidenceDecision.reason());
                } else {
                    lastCompletionReason = correctionRetry
                            ? "\u7ea0\u504f\u91cd\u8bd5\u540e\u672a\u4ea7\u751f\u65b0\u7684\u5de5\u5177\u8ba1\u5212\uff0c\u7cfb\u7edf\u5c06\u7acb\u5373\u7ed3\u675f\u3002"
                            : "\u6a21\u578b\u5224\u65ad\u5f53\u524d\u4fe1\u606f\u5df2\u8db3\u591f\u76f4\u63a5\u4f5c\u7b54\u3002";
                }
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
            // lastAssistantText = "Thinking failed: " + e.getMessage();
            // lastCompletionReason = "The think stage failed, so the agent stopped with the current state.";
            // #NEW CODE#
            lastAssistantText = "\u601d\u8003\u9636\u6bb5\u6267\u884c\u5931\u8d25\uff1a" + e.getMessage();
            lastCompletionReason = "\u601d\u8003\u9636\u6bb5\u5931\u8d25\uff0c\u7cfb\u7edf\u5df2\u57fa\u4e8e\u5f53\u524d\u72b6\u6001\u505c\u6b62\u3002";
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
        // return """
        //         The previous tool plan is redundant or over budget: %s
        //         Do not repeat the same tool call or the same search query.
        //         Use the evidence already collected to answer now, or choose a materially different tool or refined arguments.
        //         If current evidence is sufficient, provide the final answer and call the `terminate` tool.
        //         Unless the user explicitly asked, do not proactively suggest additional next steps.
        //         """.formatted(reason).trim();
        // #NEW CODE#
        return """
                \u4e0a\u4e00\u8f6e\u5de5\u5177\u8ba1\u5212\u5b58\u5728\u91cd\u590d\u6216\u9884\u7b97\u95ee\u9898\uff1a%s
                \u4e0d\u8981\u91cd\u590d\u76f8\u540c\u7684\u5de5\u5177\u8c03\u7528\u6216\u76f8\u540c\u7684\u641c\u7d22\u8bcd\u3002
                \u8bf7\u8981\u4e48\u57fa\u4e8e\u5df2\u6709\u8bc1\u636e\u76f4\u63a5\u4f5c\u7b54\uff0c\u8981\u4e48\u9009\u62e9\u5b9e\u8d28\u4e0d\u540c\u7684\u5de5\u5177\u6216 refined arguments\u3002
                \u53ea\u6709\u5728\u5f53\u524d\u8bc1\u636e\u5df2\u8db3\u591f\u7684\u60c5\u51b5\u4e0b\uff0c\u624d\u8f93\u51fa\u6700\u7ec8\u7b54\u6848\u5e76\u8c03\u7528 `terminate` \u5de5\u5177\u3002
                \u9664\u975e\u7528\u6237\u660e\u786e\u8981\u6c42\uff0c\u4e0d\u8981\u4e3b\u52a8\u5efa\u8bae\u989d\u5916\u7684\u4e0b\u4e00\u6b65\u3002
                """.formatted(reason).trim();
    }

    private List<AssistantMessage.ToolCall> getSafeToolCalls(AssistantMessage assistantMessage) {
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        return toolCalls == null ? List.of() : toolCalls;
    }

    private String buildOngoingThoughtContent(String toolCallSummary, String toolResultSummary, String ongoingStatusContent) {
        // return mergeBlocks(
        //         block("\u672c\u6b65\u5de5\u5177\u8c03\u7528", toolCallSummary),
        //         block("\u672c\u6b65\u7ed3\u679c\u6458\u8981", toolResultSummary)
        // );
        // #NEW CODE#
        // Add an explicit progress status so the user can see the agent is still consolidating evidence instead of hanging.
        return mergeBlocks(
                block("\u672c\u6b65\u5de5\u5177\u8c03\u7528", toolCallSummary),
                block("\u672c\u6b65\u7ed3\u679c\u6458\u8981", toolResultSummary),
                block("\u5f53\u524d\u72b6\u6001", ongoingStatusContent)
        );
    }

    private String buildFinalThoughtContent() {
        String completionReason = StringUtils.hasText(lastCompletionReason)
                ? lastCompletionReason
                // : "The current evidence is sufficient, so the agent will finish here.";
                // #NEW CODE#
                : "\u5f53\u524d\u8bc1\u636e\u5df2\u8db3\u591f\uff0c\u7cfb\u7edf\u5728\u6b64\u7ed3\u675f\u3002";
        return mergeBlocks(
                block("\u5b8c\u6210\u539f\u56e0", completionReason),
                block("\u6700\u540e\u4e00\u6b21\u6709\u6548\u5de5\u5177\u7ed3\u679c\u6458\u8981", lastEffectiveToolResultSummary)
        );
    }

    // Keep the status phase specific so the frontend shows whether the agent is still searching, verifying, or composing.
    private String resolveOngoingStatusContent(
            List<AssistantMessage.ToolCall> toolCalls,
            List<ToolResponseMessage.ToolResponse> responses,
            SearchEvidenceDecision searchEvidenceDecision
    ) {
        if (containsToolCall(toolCalls, SEARCH_WEB_TOOL_NAME)) {
            return resolveSearchWebStatusContent(responses, searchEvidenceDecision);
        }
        if (containsToolCall(toolCalls, SCRAPE_WEB_PAGE_TOOL_NAME)) {
            return buildStatusContent(
                    STATUS_VERIFYING,
                    "\u5df2\u83b7\u53d6\u5019\u9009\u9875\u9762\uff0c\u6b63\u5728\u68c0\u67e5\u5b83\u662f\u5426\u5c5e\u4e8e\u5b98\u65b9/\u6743\u5a01\u6765\u6e90\uff0c\u4ee5\u53ca\u4e0e\u95ee\u9898\u7684\u65f6\u95f4\u548c\u5b9e\u4f53\u662f\u5426\u5339\u914d\u3002"
            );
        }
        return buildStatusContent(
                STATUS_DRAFTING,
                "\u5f53\u524d\u5de5\u5177\u7ed3\u679c\u5df2\u7ecf\u5230\u4f4d\uff0c\u6b63\u5728\u5408\u5e76\u6709\u6548\u8bc1\u636e\u5e76\u6574\u7406\u6210\u6700\u7ec8\u56de\u590d\u3002"
        );
    }

    private String resolveSearchWebStatusContent(List<ToolResponseMessage.ToolResponse> responses, SearchEvidenceDecision searchEvidenceDecision) {
        boolean hasSearchResults = false;
        boolean hasAuthoritativeResult = false;
        boolean hasVerifiedResult = false;
        boolean hasContentFoundResult = false;
        boolean needVerification = false;

        for (ToolResponseMessage.ToolResponse response : responses) {
            if (!SEARCH_WEB_TOOL_NAME.equals(response.name())) {
                continue;
            }
            try {
                JSONObject responseObject = JSONUtil.parseObj(response.responseData());
                JSONObject strategyObject = responseObject.getJSONObject("strategy");
                if (strategyObject != null) {
                    needVerification = needVerification || Boolean.TRUE.equals(strategyObject.getBool("needVerification"));
                }

                JSONArray results = responseObject.getJSONArray("results");
                if (results == null) {
                    continue;
                }

                for (int index = 0; index < results.size(); index++) {
                    JSONObject item = results.getJSONObject(index);
                    if (item == null) {
                        continue;
                    }
                    hasSearchResults = true;
                    String sourceType = normalizeWhitespace(item.getStr("sourceType", ""));
                    String verificationStatus = normalizeWhitespace(item.getStr("verificationStatus", ""));
                    if ("official".equalsIgnoreCase(sourceType) || "authoritative".equalsIgnoreCase(sourceType)) {
                        hasAuthoritativeResult = true;
                    }
                    if ("verified".equalsIgnoreCase(verificationStatus)) {
                        hasVerifiedResult = true;
                    }
                    if ("content_found".equalsIgnoreCase(verificationStatus)) {
                        hasContentFoundResult = true;
                    }
                }
            } catch (Exception ignored) {
                hasSearchResults = true;
            }
        }

        if (hasVerifiedResult && !shouldBlockUnsupportedFinalAnswer(searchEvidenceDecision)) {
            return buildStatusContent(
                    STATUS_DRAFTING,
                    "\u5df2\u7ecf\u62ff\u5230\u901a\u8fc7\u6838\u9a8c\u7684\u6709\u6548\u8bc1\u636e\uff0c\u6b63\u5728\u5408\u5e76\u5173\u952e\u4fe1\u606f\u5e76\u6574\u7406\u6700\u7ec8\u56de\u590d\u3002"
            );
        }
        if (hasAuthoritativeResult || hasContentFoundResult || needVerification || shouldBlockUnsupportedFinalAnswer(searchEvidenceDecision)) {
            return buildStatusContent(
                    STATUS_VERIFYING,
                    "\u5df2\u627e\u5230\u5019\u9009\u7ed3\u679c\uff0c\u6b63\u5728\u786e\u8ba4\u5b83\u4eec\u662f\u5426\u5c5e\u4e8e\u5b98\u65b9/\u6743\u5a01\u6765\u6e90\uff0c\u5e76\u68c0\u67e5\u65f6\u95f4\u7ebf\u7d22\u662f\u5426\u7b26\u5408\u95ee\u9898\u8981\u6c42\u3002"
            );
        }
        if (hasSearchResults) {
            return buildStatusContent(
                    STATUS_SEARCHING,
                    "\u5df2\u5b8c\u6210\u4e00\u8f6e\u7f51\u9875\u68c0\u7d22\uff0c\u6b63\u5728\u7ee7\u7eed\u7b5b\u9009\u66f4\u76f8\u5173\u7684\u9875\u9762\u548c\u65f6\u95f4\u7ebf\u7d22\u3002"
            );
        }
        return buildStatusContent(
                STATUS_SEARCHING,
                "\u6b63\u5728\u6536\u96c6\u5019\u9009\u7ed3\u679c\uff0c\u7a0d\u540e\u4f1a\u6839\u636e\u68c0\u7d22\u547d\u4e2d\u60c5\u51b5\u7ee7\u7eed\u7f29\u5c0f\u8303\u56f4\u3002"
        );
    }

    private boolean containsToolCall(List<AssistantMessage.ToolCall> toolCalls, String toolName) {
        return toolCalls.stream().anyMatch(toolCall -> toolName.equals(toolCall.name()));
    }

    private String buildStatusContent(String statusLabel, String description) {
        return statusLabel + "\n" + description;
    }

    private String buildFinalReply() {
        if (StringUtils.hasText(lastAssistantText)) {
            return lastAssistantText;
        }
        return buildFallbackFinalReply(lastCompletionReason);
    }

    protected String getFinalReplyForPersistence() {
        return buildFinalReply();
    }

    private String buildFallbackFinalReply(String reason) {
        if (StringUtils.hasText(lastEffectiveToolResultSummary)) {
            return "\u4ee5\u4e0b\u662f\u57fa\u4e8e\u76ee\u524d\u6709\u6548\u5de5\u5177\u8f93\u51fa\u7684\u7ed3\u8bba\uff1a\n" + lastEffectiveToolResultSummary;
        }
        if (StringUtils.hasText(reason)) {
            return reason;
        }
        // return "There is no new effective tool result to continue from, so the agent stops here.";
        // #NEW CODE#
        return "\u76ee\u524d\u6ca1\u6709\u65b0\u7684\u6709\u6548\u5de5\u5177\u7ed3\u679c\u53ef\u4ee5\u7ee7\u7eed\u652f\u6491\uff0c\u7cfb\u7edf\u5728\u6b64\u505c\u6b62\u3002";
    }

    private String buildEvidenceThresholdPrompt(String reason) {
        return """
                \u5f53\u524d\u641c\u7d22\u8bc1\u636e\u8fd8\u6ca1\u6709\u8fbe\u5230\u53ef\u4ee5\u7a33\u5b9a\u6536\u5c3e\u7684\u95e8\u69db\uff1a%s
                \u5982\u679c\u8fd8\u6709\u53ef\u4ee5\u5b9e\u8d28\u63d0\u5347\u8bc1\u636e\u7684\u5de5\u5177\u64cd\u4f5c\uff0c\u8bf7\u4f7f\u7528\u66f4\u76f4\u63a5\u7684 refined search \u6216\u66f4\u6743\u5a01\u7684\u9875\u9762\u3002
                \u5982\u679c\u76ee\u524d\u65e0\u6cd5\u62ff\u5230\u66f4\u5f3a\u7684\u8bc1\u636e\uff0c\u8bf7\u660e\u786e\u8bf4\u51fa\u8bc1\u636e\u4e0d\u8db3\uff0c\u4e0d\u8981\u8865\u5168\u672a\u88ab\u5de5\u5177\u8bc1\u636e\u652f\u6301\u7684\u5177\u4f53\u6570\u503c\u6216\u6392\u540d\u3002
                \u53ea\u6709\u5728\u8bc1\u636e\u8db3\u4ee5\u652f\u6491\u7ed3\u8bba\u65f6\uff0c\u624d\u53ef\u4ee5\u8c03\u7528 `terminate`\u3002
                """.formatted(reason).trim();
    }

    private String buildEvidenceThresholdFallback(String reason) {
        String summaryBlock = StringUtils.hasText(lastEffectiveToolResultSummary)
                ? "\n\n\u76ee\u524d\u53ef\u4ee5\u786e\u8ba4\u7684\u68c0\u7d22\u7ed3\u679c\uff1a\n" + lastEffectiveToolResultSummary
                : "";
        return "\u5f53\u524d\u8bc1\u636e\u8fd8\u4e0d\u8db3\u4ee5\u652f\u6301\u7cbe\u786e\u7ed3\u8bba\uff0c\u7cfb\u7edf\u4e0d\u4f1a\u8f93\u51fa\u672a\u88ab\u5de5\u5177\u8bc1\u636e\u652f\u6301\u7684\u5177\u4f53\u6570\u636e\u6216\u5217\u8868\u3002"
                + (StringUtils.hasText(reason) ? "\n\n\u539f\u56e0\uff1a" + reason : "")
                + summaryBlock;
    }

    // private void rememberExecutedToolBatch(List<AssistantMessage.ToolCall> toolCalls, String toolResultSummary) {
    //     for (AssistantMessage.ToolCall toolCall : toolCalls) {
    //         toolExecutionCounts.merge(toolCall.name(), 1, Integer::sum);
    //     }
    //
    //     if (StringUtils.hasText(pendingToolBatchSignature)) {
    //         lastToolBatchSignature = pendingToolBatchSignature;
    //     }
    //     if (StringUtils.hasText(pendingSearchQuerySignature)) {
    //         lastSearchQuerySignature = pendingSearchQuerySignature;
    //     }
    //     if (StringUtils.hasText(toolResultSummary)) {
    //         lastEffectiveToolResultSummary = toolResultSummary;
    //         log.info(toolResultSummary);
    //     }
    //
    //     pendingToolBatchSignature = "";
    //     pendingSearchQuerySignature = "";
    // }
    // #NEW CODE#
    private void rememberExecutedToolBatch(
            List<AssistantMessage.ToolCall> toolCalls,
            String toolResultSummary,
            SearchEvidenceDecision searchEvidenceDecision
    ) {
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
        if (searchEvidenceDecision != null && searchEvidenceDecision.hasSearchEvidence()) {
            lastSearchEvidenceSeen = true;
            lastSearchEvidenceThresholdMet = searchEvidenceDecision.evidenceThresholdMet();
            lastSearchEvidenceReason = normalizeWhitespace(searchEvidenceDecision.reason());
        }

        pendingToolBatchSignature = "";
        pendingSearchQuerySignature = "";
    }

    private SearchEvidenceDecision evaluateSearchEvidenceDecision(List<ToolResponseMessage.ToolResponse> responses) {
        boolean hasSearchEvidence = false;
        boolean evidenceThresholdMet = true;
        String reason = "";

        for (ToolResponseMessage.ToolResponse response : responses) {
            if (!SEARCH_WEB_TOOL_NAME.equals(response.name())) {
                continue;
            }
            hasSearchEvidence = true;
            try {
                JSONObject responseObject = JSONUtil.parseObj(response.responseData());
                JSONObject strategyObject = responseObject.getJSONObject("strategy");
                if (strategyObject == null) {
                    continue;
                }

                Boolean thresholdMet = strategyObject.getBool("evidenceThresholdMet");
                if (thresholdMet != null) {
                    evidenceThresholdMet = thresholdMet;
                }

                String thresholdReason = normalizeWhitespace(strategyObject.getStr("evidenceThresholdReason", ""));
                if (StringUtils.hasText(thresholdReason)) {
                    reason = thresholdReason;
                }
            } catch (Exception ignored) {
                // Preserve backward compatibility with older or non-JSON search tool payloads.
            }
        }

        if (!hasSearchEvidence) {
            return new SearchEvidenceDecision(false, true, "");
        }
        if (!StringUtils.hasText(reason) && !evidenceThresholdMet) {
            reason = "\u5f53\u524d\u641c\u7d22\u7ed3\u679c\u8fd8\u4e0d\u8db3\u4ee5\u652f\u6301\u76f4\u63a5\u7ed9\u51fa\u7ed3\u8bba\u3002";
        }
        return new SearchEvidenceDecision(hasSearchEvidence, evidenceThresholdMet, reason);
    }

    private SearchEvidenceDecision getLastSearchEvidenceDecision() {
        if (!lastSearchEvidenceSeen) {
            return new SearchEvidenceDecision(false, true, "");
        }
        return new SearchEvidenceDecision(
                true,
                lastSearchEvidenceThresholdMet,
                normalizeWhitespace(lastSearchEvidenceReason)
        );
    }

    private boolean shouldBlockUnsupportedFinalAnswer(SearchEvidenceDecision searchEvidenceDecision) {
        return searchEvidenceDecision != null
                && searchEvidenceDecision.hasSearchEvidence()
                && !searchEvidenceDecision.evidenceThresholdMet();
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
                String sourceType = normalizeWhitespace(item.getStr("sourceType", ""));
                String verificationStatus = normalizeWhitespace(item.getStr("verificationStatus", ""));
                List<String> labels = new ArrayList<>();
                if (StringUtils.hasText(sourceType)) {
                    labels.add(sourceType);
                }
                if (StringUtils.hasText(verificationStatus) && !"not_requested".equalsIgnoreCase(verificationStatus)) {
                    labels.add(verificationStatus);
                }
                String decoratedTitle = labels.isEmpty()
                        ? title
                        : title + " [" + String.join(", ", labels) + "]";
                if (StringUtils.hasText(title) && StringUtils.hasText(url)) {
                    topResults.add(decoratedTitle + " (" + url + ")");
                } else if (StringUtils.hasText(title)) {
                    topResults.add(decoratedTitle);
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
