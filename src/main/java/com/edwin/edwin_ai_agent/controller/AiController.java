package com.edwin.edwin_ai_agent.controller;

import com.edwin.edwin_ai_agent.agent.BaseAgent;
import com.edwin.edwin_ai_agent.agent.EdwinManus;
// import com.edwin.edwin_ai_agent.app.LoveApp;
// #NEW CODE#
import com.edwin.edwin_ai_agent.app.EdwinApp;
import com.edwin.edwin_ai_agent.config.ResponseLength;
import com.edwin.edwin_ai_agent.config.ResponseLengthStrategy;
import jakarta.annotation.Resource;
import java.io.IOException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    // private LoveApp loveApp;
    // #NEW CODE#
    private EdwinApp edwinApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ResponseLengthStrategy responseLengthStrategy;

    // @GetMapping("/edwin_app/chat/sync")
    // public String doChatWithLoveAppSync(String message, String chatId) {
    //     return edwinApp.doChat(message, chatId);
    // }
    // #NEW CODE#
    @GetMapping({"/edwin_app/chat/sync", "/love_app/chat/sync"})
    public String doChatWithLoveAppSync(String message, String chatId, String responseLength) {
        return edwinApp.doChat(message, chatId, resolveResponseLength(responseLength));
    }

    // @GetMapping("/edwin_app/chat/sse/emitter")
    // public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId) {
    //     SseEmitter emitter = new SseEmitter(180000L);
    //     edwinApp.doChatByStream(message, chatId)
    //             .subscribe(
    //                     chunk -> {
    //                         try {
    //                             emitter.send(chunk);
    //                         } catch (IOException e) {
    //                             emitter.completeWithError(e);
    //                         }
    //                     },
    //                     emitter::completeWithError,
    //                     // emitter::complete);
    //                     // #NEW CODE#
    //                     () -> {
    //                         try {
    //                             BaseAgent.completeEmitterStream(emitter);
    //                         } catch (IOException e) {
    //                             emitter.completeWithError(e);
    //                         }
    //                     });
    //     return emitter;
    // }
    // #NEW CODE#
    @GetMapping({"/edwin_app/chat/sse/emitter", "/love_app/chat/sse/emitter"})
    public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId, String responseLength) {
        SseEmitter emitter = new SseEmitter(180000L);
        edwinApp.doChatByStream(message, chatId, resolveResponseLength(responseLength))
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        // emitter::complete);
                        // #NEW CODE#
                        () -> {
                            try {
                                BaseAgent.completeEmitterStream(emitter);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        });
        return emitter;
    }

    // @GetMapping("/manus/chat")
    // public SseEmitter doChatWithManus(String message) {
    //     EdwinManus edwinManus = new EdwinManus(allTools, dashscopeChatModel);
    //     return edwinManus.runStream(message);
    // }
    // #NEW CODE#
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String responseLength) {
        ResponseLength resolvedLength = resolveResponseLength(responseLength);
        EdwinManus edwinManus = new EdwinManus(allTools, dashscopeChatModel, responseLengthStrategy, resolvedLength);
        return edwinManus.runStream(message);
    }

    private ResponseLength resolveResponseLength(String responseLength) {
        return responseLengthStrategy.resolve(responseLength);
    }
}
