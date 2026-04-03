package com.edwin.edwin_ai_agent.controller;

import com.edwin.edwin_ai_agent.agent.BaseAgent;
import com.edwin.edwin_ai_agent.app.EdwinApp;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerSseDoneTest {

    private MockMvc mockMvc;
    private EdwinApp edwinApp;

    @BeforeEach
    void setUp() {
        AiController controller = new AiController();
        edwinApp = mock(EdwinApp.class);

        ReflectionTestUtils.setField(controller, "edwinApp", edwinApp);
        ReflectionTestUtils.setField(controller, "allTools", new ToolCallback[0]);
        ReflectionTestUtils.setField(controller, "dashscopeChatModel", mock(ChatModel.class));
        ReflectionTestUtils.setField(controller, "responseLengthStrategy", new ResponseLengthStrategy());

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    // void edwinAppSseEndpointAppendsDoneMarkerBeforeCompleting() throws Exception {
    //     when(edwinApp.doChatByStream("hello", "chat-9"))
    //             .thenReturn(Flux.just("chunk-1", "chunk-2"));
    //
    //     MvcResult result = mockMvc.perform(get("/ai/edwin_app/chat/sse/emitter")
    //                     .param("message", "hello")
    //                     .param("chatId", "chat-9"))
    //             .andExpect(request().asyncStarted())
    //             .andReturn();
    //
    //     mockMvc.perform(asyncDispatch(result))
    //             .andExpect(status().isOk())
    //             .andExpect(content().string(containsString("chunk-1")))
    //             .andExpect(content().string(containsString("chunk-2")))
    //             .andExpect(content().string(containsString(BaseAgent.STREAM_DONE_MARKER)));
    //
    //     verify(edwinApp).doChatByStream("hello", "chat-9");
    // }
    // #NEW CODE#
    void edwinAppSseEndpointAppendsDoneMarkerBeforeCompleting() throws Exception {
        when(edwinApp.doChatByStream("hello", "chat-9", ResponseLength.MEDIUM))
                .thenReturn(Flux.just("chunk-1", "chunk-2"));

        MvcResult result = mockMvc.perform(get("/ai/edwin_app/chat/sse/emitter")
                        .param("message", "hello")
                        .param("chatId", "chat-9"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chunk-1")))
                .andExpect(content().string(containsString("chunk-2")))
                .andExpect(content().string(containsString(BaseAgent.STREAM_DONE_MARKER)));

        verify(edwinApp).doChatByStream("hello", "chat-9", ResponseLength.MEDIUM);
    }

    @Test
    void invalidResponseLengthFallsBackToMedium() throws Exception {
        when(edwinApp.doChatByStream("hello", "chat-10", ResponseLength.MEDIUM))
                .thenReturn(Flux.just("chunk-1"));

        MvcResult result = mockMvc.perform(get("/ai/edwin_app/chat/sse/emitter")
                        .param("message", "hello")
                        .param("chatId", "chat-10")
                        .param("responseLength", "invalid-mode"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(BaseAgent.STREAM_DONE_MARKER)));

        verify(edwinApp).doChatByStream("hello", "chat-10", ResponseLength.MEDIUM);
    }
}
