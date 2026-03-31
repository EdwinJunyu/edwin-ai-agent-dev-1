package com.edwin.edwin_ai_agent.controller;

import com.edwin.edwin_ai_agent.app.EdwinApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerRenameSmokeTest {

    private MockMvc mockMvc;
    private EdwinApp edwinApp;

    @BeforeEach
    void setUp() {
        AiController controller = new AiController();
        edwinApp = mock(EdwinApp.class);

        // Wire fields by name so the test matches the @Resource injection contract.
        ReflectionTestUtils.setField(controller, "edwinApp", edwinApp);
        ReflectionTestUtils.setField(controller, "allTools", new ToolCallback[0]);
        ReflectionTestUtils.setField(controller, "dashscopeChatModel", mock(ChatModel.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void syncEndpointKeepsLegacyPathWhileDelegatingToEdwinApp() throws Exception {
        when(edwinApp.doChat("hello", "chat-1")).thenReturn("ok");

        mockMvc.perform(get("/api/ai/love_app/chat/sync")
                        .contextPath("/api")
                        .param("message", "hello")
                        .param("chatId", "chat-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(edwinApp).doChat("hello", "chat-1");
    }

    @Test
    void sseEndpointKeepsLegacyPathWhileDelegatingToEdwinApp() throws Exception {
        when(edwinApp.doChatByStream("hello", "chat-2")).thenReturn(Flux.just("chunk-1", "chunk-2"));

        MvcResult result = mockMvc.perform(get("/api/ai/love_app/chat/sse/emitter")
                        .contextPath("/api")
                        .param("message", "hello")
                        .param("chatId", "chat-2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());

        verify(edwinApp).doChatByStream("hello", "chat-2");
    }
}
