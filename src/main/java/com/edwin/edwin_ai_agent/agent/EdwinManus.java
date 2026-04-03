/* Previous implementation commented out per workspace rule. */
// #NEW CODE#
package com.edwin.edwin_ai_agent.agent;

import com.edwin.edwin_ai_agent.advisor.MyLoggerAdvisor;
import com.edwin.edwin_ai_agent.chatmemory.ManusConversationMemory;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EdwinManus extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            You are EdwinManus, an all-capable AI assistant focused on finishing the user's task with the minimum necessary steps.
            You can use tools, but every tool call must materially improve the answer or artifact you are producing.
            """;

    // private static final String NEXT_STEP_PROMPT = """
    //         Choose the minimum tool usage needed to finish the request.
    //         Only continue to another step when there is a clear missing fact or missing artifact.
    //         Do not repeat the same tool with the same arguments.
    //         For searchWeb, use at most two searches in one request, and the second search must be materially refined.
    //         If the current evidence is enough, answer directly and call the `terminate` tool.
    //         Unless the user explicitly asks, do not proactively suggest extra next steps.
    //         """;
    // #NEW CODE#
    private static final String NEXT_STEP_PROMPT = """
            Choose the minimum tool usage needed to finish the request.
            Only continue to another step when there is a clear missing fact or missing artifact.
            Do not repeat the same tool with the same arguments.
            For searchWeb, use at most two searches in one request, and the second search must be materially refined.
            For institution, policy, calendar, event, or time-sensitive queries, prefer `officialFirst=true` and `needVerification=true`.
            If you know likely official domains, pass them through `preferredDomains`; if some noisy domains keep showing up, use `excludedDomains`.
            Use `timeHint` when the user gives a concrete month, year, or relative timeframe.
            Final answers should rely on verified search results rather than raw search snippets whenever verification is available.
            If the current evidence is enough, answer directly and call the `terminate` tool.
            Unless the user explicitly asks, do not proactively suggest extra next steps.
            """;

    private String memoryConversationId = "";
    private ManusConversationMemory conversationMemory;

    @Autowired
    public EdwinManus(
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel,
            ResponseLengthStrategy responseLengthStrategy
    ) {
        this(allTools, dashscopeChatModel, responseLengthStrategy, ResponseLength.MEDIUM);
    }

    public EdwinManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        this(allTools, dashscopeChatModel, new ResponseLengthStrategy(), ResponseLength.MEDIUM);
    }

    // Key prompt and token settings are derived from one shared mode so the answer size stays predictable.
    public EdwinManus(
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel,
            ResponseLengthStrategy responseLengthStrategy,
            ResponseLength responseLength
    ) {
        super(
                allTools,
                ToolCallingManager.builder().build(),
                responseLengthStrategy.buildManusOptions(responseLength)
        );
        this.setName("EdwinManus");
        this.setSystemPrompt(responseLengthStrategy.appendSystemPrompt(SYSTEM_PROMPT, responseLength));
        this.setNextStepPrompt(responseLengthStrategy.appendPlannerPrompt(NEXT_STEP_PROMPT, responseLength));
        this.setMaxSteps(8);

        // Keep the existing advisor chain so request/response logging behavior does not change.
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    public void seedConversationHistory(List<Message> history) {
        seedMessageHistory(history);
    }

    public void bindConversationMemory(String chatId, ManusConversationMemory conversationMemory) {
        this.memoryConversationId = chatId;
        this.conversationMemory = conversationMemory;
    }

    @Override
    protected void afterRunComplete(String userPrompt) {
        if (!StringUtils.hasText(memoryConversationId) || conversationMemory == null) {
            return;
        }

        // Only persist the final user-visible answer so the next turn keeps clean dialogue context.
        String assistantReply = getFinalReplyForPersistence();
        if (!StringUtils.hasText(assistantReply)) {
            return;
        }
        conversationMemory.appendTurn(memoryConversationId, userPrompt, assistantReply);
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        memoryConversationId = "";
        conversationMemory = null;
    }
}
