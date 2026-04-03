package com.edwin.edwin_ai_agent.controller;

import com.edwin.edwin_ai_agent.agent.BaseAgent;
import com.edwin.edwin_ai_agent.app.EdwinApp;
import com.edwin.edwin_ai_agent.app.ManusApp;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    private ManusApp manusApp;

    @BeforeEach
    void setUp() {
        AiController controller = new AiController();
        edwinApp = mock(EdwinApp.class);
        manusApp = mock(ManusApp.class);

        ReflectionTestUtils.setField(controller, "edwinApp", edwinApp);
        // ReflectionTestUtils.setField(controller, "allTools", new ToolCallback[0]);
        // ReflectionTestUtils.setField(controller, "dashscopeChatModel", mock(ChatModel.class));
        // #NEW CODE#
        ReflectionTestUtils.setField(controller, "manusApp", manusApp);
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

    @Test
    void manusEndpointPassesChatIdToManusApp() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(manusApp.runStream("hello", "chat-42", ResponseLength.LONG))
                .thenReturn(emitter);

        MvcResult result = mockMvc.perform(get("/ai/manus/chat")
                        .param("message", "hello")
                        .param("chatId", "chat-42")
                        .param("responseLength", "long"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.complete();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());

        verify(manusApp).runStream("hello", "chat-42", ResponseLength.LONG);
    }

    @Test
    void manusEndpointAllowsMissingChatIdForCompatibility() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(manusApp.runStream("hello", null, ResponseLength.MEDIUM))
                .thenReturn(emitter);

        MvcResult result = mockMvc.perform(get("/ai/manus/chat")
                        .param("message", "hello"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.complete();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());

        verify(manusApp).runStream("hello", null, ResponseLength.MEDIUM);
    }
}
