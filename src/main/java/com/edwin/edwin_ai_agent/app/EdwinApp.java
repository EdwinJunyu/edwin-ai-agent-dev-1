package com.edwin.edwin_ai_agent.app;

import com.edwin.edwin_ai_agent.advisor.MyLoggerAdvisor;
import com.edwin.edwin_ai_agent.chatmemory.FileBaseChatMemory;
import com.edwin.edwin_ai_agent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class EdwinApp {

    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = "扮演聊天大师，回答语言精简，适当倾听";
    // private static final Logger log = LoggerFactory.getLogger(LoveApp.class);
    // #NEW CODE#
    //private static final Logger log = LoggerFactory.getLogger(EdwinApp.class);

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    // =========================
    // 基于文件的对话记忆（启用）
    // =========================
    public EdwinApp(ChatModel dashscopeChatModel) {
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBaseChatMemory(fileDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .order(0)
                                .build())
                .build();
    }

    //AI 基础对话（支持多轮对话记忆）
    public String doChat(String message, String chatId) {
        String content = chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
        log.info("content: {}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    //AI XX报告功能
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次生成XX结果时，标题为{用户名}的XX报告，内容为列表")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("Report: {}", loveReport);
        return loveReport;
    }

    //AI 知识库问答功能
    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(
                        QuestionAnswerAdvisor.builder(loveAppVectorStore)
                                .searchRequest(
                                        SearchRequest.builder()
                                                .query(message)
                                                .topK(10)
                                                .build())
                                .build())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("Content: {}", content);
        return content;
    }

    public LoveReport doChatWithTools(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .entity(LoveReport.class);
        log.info("Report: {}", loveReport);
        return loveReport;
    }

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param(VectorStoreChatMemoryAdvisor.TOP_K, 10))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 基础对话 SSE 流式传输
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param(VectorStoreChatMemoryAdvisor.TOP_K, 10))
                .stream()
                .content();
    }
}
