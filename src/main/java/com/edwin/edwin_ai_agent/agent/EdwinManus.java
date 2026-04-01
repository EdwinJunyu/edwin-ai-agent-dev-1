/* Previous implementation commented out per workspace rule. */
// #NEW CODE#
package com.edwin.edwin_ai_agent.agent;

import com.edwin.edwin_ai_agent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class EdwinManus extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            You are EdwinManus, an all-capable AI assistant focused on finishing the user's task with the minimum necessary steps.
            You can use tools, but every tool call must materially improve the answer or artifact you are producing.
            """;

    private static final String NEXT_STEP_PROMPT = """
            Choose the minimum tool usage needed to finish the request.
            Only continue to another step when there is a clear missing fact or missing artifact.
            Do not repeat the same tool with the same arguments.
            For searchWeb, use at most two searches in one request, and the second search must be materially refined.
            If the current evidence is enough, answer directly and call the `terminate` tool.
            Unless the user explicitly asks, do not proactively suggest extra next steps.
            """;

    public EdwinManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("EdwinManus");
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(8);

        // Keep the existing advisor chain so request/response logging behavior does not change.
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
