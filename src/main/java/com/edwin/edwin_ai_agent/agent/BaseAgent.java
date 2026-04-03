package com.edwin.edwin_ai_agent.agent;

import cn.hutool.core.util.StrUtil;
import com.edwin.edwin_ai_agent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base agent with state management, chat history, and step execution.
 */
@Data
@Slf4j
public abstract class BaseAgent {
    // Shared end marker lets the frontend stop EventSource before the browser retries.
    public static final String STREAM_DONE_MARKER = "[DONE]";

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState state = AgentState.IDLE;
    private int maxSteps = 10;
    private int currentStep = 0;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();

    // Centralize SSE termination so every handled completion path emits the same done marker.
    public static void completeEmitterStream(SseEmitter emitter, String payload) throws IOException {
        if (StrUtil.isNotBlank(payload)) {
            emitter.send(payload);
        }
        emitter.send(STREAM_DONE_MARKER);
        emitter.complete();
    }

    public static void completeEmitterStream(SseEmitter emitter) throws IOException {
        completeEmitterStream(emitter, null);
    }

    // History is seeded externally so multi-turn modes can restore prior user/assistant turns.
    public void seedMessageHistory(List<Message> history) {
        this.messageList = history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    /**
     * Run the agent synchronously.
     *
     * @param userPrompt user input
     * @return execution result
     */
    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {
            for (int index = 0; index < maxSteps && state != AgentState.FINISHED; index++) {
                int stepNumber = index + 1;
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);

                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }

            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            safeAfterRunComplete(userPrompt);
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "Execution error: " + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    /**
     * Execute one step.
     *
     * @return step result
     */
    public abstract String step();

    /**
     * Cleanup hook.
     */
    protected void cleanup() {
        // Subclasses can override this hook when cleanup is needed.
    }

    protected void afterRunComplete(String userPrompt) {
        // Subclasses can override this hook to persist user-visible conversation state.
    }

    protected void resetRunState() {
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.messageList = new ArrayList<>();
    }

    /**
     * Run the agent with SSE streaming.
     *
     * @param userPrompt user input
     * @return SSE emitter
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                // #NEW CODE#
                if (this.state != AgentState.IDLE) {
                    completeEmitterStream(emitter, "Error: agent cannot run from state " + this.state);
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    completeEmitterStream(emitter, "Error: empty prompt");
                    return;
                }

                state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));

                try {
                    for (int index = 0; index < maxSteps && state != AgentState.FINISHED; index++) {
                        int stepNumber = index + 1;
                        currentStep = stepNumber;
                        log.info("Executing step " + stepNumber + "/" + maxSteps);

                        String stepResult = step();

                        // Only forward visible payloads so the frontend does not render empty frames.
                        if (StrUtil.isNotBlank(stepResult)) {
                            emitter.send(stepResult);
                        }
                    }

                    if (currentStep >= maxSteps) {
                        state = AgentState.FINISHED;
                        safeAfterRunComplete(userPrompt);
                        completeEmitterStream(emitter, "Execution finished: reached max steps (" + maxSteps + ")");
                        return;
                    }
                    safeAfterRunComplete(userPrompt);
                    completeEmitterStream(emitter);
                } catch (Exception e) {
                    state = AgentState.ERROR;
                    log.error("Error executing agent", e);
                    try {
                        completeEmitterStream(emitter, "Execution error: " + e.getMessage());
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                } finally {
                    this.cleanup();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return emitter;
    }

    private void safeAfterRunComplete(String userPrompt) {
        try {
            afterRunComplete(userPrompt);
        } catch (Exception e) {
            log.warn("Post-run completion hook failed", e);
        }
    }
}
