package com.edwin.edwin_ai_agent.app;
import com.edwin.edwin_ai_agent.advisor.MyLoggerAdvisor;
import com.edwin.edwin_ai_agent.chatmemory.FileBaseChatMemory;
import com.edwin.edwin_ai_agent.rag.LoveAppRagCustomAdvisorFactory;
import com.edwin.edwin_ai_agent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import reactor.core.publisher.Flux;

import java.util.List;
@Component
public class LoveApp{
        private final ChatClient chatClient;
        private static final String SYSTEM_PROMPT = "扮演聊天大师，回答语言精简，适当倾听";
        private static final Logger log = LoggerFactory.getLogger(LoveApp.class);
        // =========================
        // 基于文件的对话记忆（启用）
        // =========================
     public LoveApp(ChatModel dashscopeChatModel){
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBaseChatMemory(fileDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .order(0)
                                .build()
                )
                .build();
    }



    //AI 基础对话（支持多轮对话记忆）
    public String doChat(String message, String chatId){
        String content = chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)) //CHAT_MEMORY_CONVERSATION_ID_KEY → ChatMemory.CONVERSATION_ID
                .call()
                .content();
        log.info("content: {}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggestions){}
    //AI XX报告功能
    public LoveReport doChatWithReport(String message, String chatId){
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次生成XX结果时，标题为{用户名}的XX报告，内容为列表")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)) //CHAT_MEMORY_CONVERSATION_ID_KEY → ChatMemory.CONVERSATION_ID
                .call()
                .entity(LoveReport.class);
        log.info("Report: {}", loveReport);
        return loveReport;
    }

    //AI 知识库问答功能
    @Resource
    private VectorStore loveAppVectorStore;
    @Resource
    private QueryRewriter queryRewriter;
    public String doChatWithRag(String message, String chatId) {  //和Spring Ai 1.0 代码非常不一样
        //查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                //使用查询后的查询
                .user(rewrittenMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(
                        QuestionAnswerAdvisor.builder(loveAppVectorStore)
                                .searchRequest(
                                        SearchRequest.builder()
                                                .query(message)
                                                .topK(10)
                                                .build()
                                )
                                .build()

                )
                //应用自定义的RAG 检索增强服务（文档查询器 + 上下文增强器）
                //.advisors(
                //        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore,"单身")
                //)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("Content: {}", content);
        return content;
    }
    //AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;
    //AI 报告功能（支持调用工具）
    public LoveReport doChatWithTools(String message, String chatId){
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)) //CHAT_MEMORY_CONVERSATION_ID_KEY → ChatMemory.CONVERSATION_ID
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools) //旧版本是 .tool()
                .call()
                .entity(LoveReport.class);
        log.info("Report: {}", loveReport);
        return loveReport;
    }

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param(VectorStoreChatMemoryAdvisor.TOP_K, 10))  //CHAT_MEMORY_RETRIEVE_SIZE_KEY -> VectorStoreChatMemoryAdvisor.TOP_K
                // 开启日志，便于观察效果
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
