package com.edwin.edwin_ai_agent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.stereotype.Component;

/**
 * Centralizes prompt steering and token budgets for all response-length modes.
 */
@Component
public class ResponseLengthStrategy {

    private static final int EDWIN_APP_SHORT_MAX_TOKENS = 512;
    private static final int EDWIN_APP_MEDIUM_MAX_TOKENS = 1024;
    private static final int EDWIN_APP_LONG_MAX_TOKENS = 2048;

    private static final int MANUS_SHORT_MAX_TOKENS = 768;
    private static final int MANUS_MEDIUM_MAX_TOKENS = 1280;
    private static final int MANUS_LONG_MAX_TOKENS = 2304;

    public ResponseLength resolve(String rawValue) {
        return ResponseLength.fromWireValue(rawValue);
    }

    public DashScopeChatOptions buildEdwinAppOptions(ResponseLength responseLength) {
        ResponseLength resolvedLength = normalize(responseLength);
        return DashScopeChatOptions.builder()
                .maxToken(resolveEdwinAppMaxTokens(resolvedLength))
                .build();
    }

    public DashScopeChatOptions buildManusOptions(ResponseLength responseLength) {
        ResponseLength resolvedLength = normalize(responseLength);
        return DashScopeChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .maxToken(resolveManusMaxTokens(resolvedLength))
                .build();
    }

    public String appendSystemPrompt(String basePrompt, ResponseLength responseLength) {
        return appendInstruction(basePrompt, buildSystemInstruction(normalize(responseLength)));
    }

    public String appendPlannerPrompt(String basePrompt, ResponseLength responseLength) {
        return appendInstruction(basePrompt, buildPlannerInstruction(normalize(responseLength)));
    }

    private ResponseLength normalize(ResponseLength responseLength) {
        return responseLength == null ? ResponseLength.MEDIUM : responseLength;
    }

    private int resolveEdwinAppMaxTokens(ResponseLength responseLength) {
        return switch (responseLength) {
            case SHORT -> EDWIN_APP_SHORT_MAX_TOKENS;
            case MEDIUM -> EDWIN_APP_MEDIUM_MAX_TOKENS;
            case LONG -> EDWIN_APP_LONG_MAX_TOKENS;
        };
    }

    private int resolveManusMaxTokens(ResponseLength responseLength) {
        return switch (responseLength) {
            case SHORT -> MANUS_SHORT_MAX_TOKENS;
            case MEDIUM -> MANUS_MEDIUM_MAX_TOKENS;
            case LONG -> MANUS_LONG_MAX_TOKENS;
        };
    }

    private String appendInstruction(String basePrompt, String instruction) {
        if (basePrompt == null || basePrompt.isBlank()) {
            return instruction;
        }
        return basePrompt.strip() + "\n\n" + instruction;
    }

    private String buildSystemInstruction(ResponseLength responseLength) {
        return switch (responseLength) {
            case SHORT -> """
                    Response length mode: short.
                    Keep the answer concise. Give the conclusion and only the necessary supporting points.
                    Do not add extra background or optional expansions unless they are required for correctness.
                    """.trim();
            case MEDIUM -> """
                    Response length mode: medium.
                    Keep the answer balanced. Cover the important points clearly without becoming overly brief or overly detailed.
                    """.trim();
            case LONG -> """
                    Response length mode: long.
                    Provide a detailed answer with short sections when helpful.
                    Include the necessary explanation and context, but do not add unrelated tangents or speculative extras.
                    """.trim();
        };
    }

    private String buildPlannerInstruction(ResponseLength responseLength) {
        return switch (responseLength) {
            case SHORT -> """
                    Final answer length mode: short.
                    When you are ready to answer, give only the conclusion and essential supporting points.
                    """.trim();
            case MEDIUM -> """
                    Final answer length mode: medium.
                    When you are ready to answer, keep the reply balanced and cover the important points clearly.
                    """.trim();
            case LONG -> """
                    Final answer length mode: long.
                    When you are ready to answer, organize the reply into short sections and provide the necessary detail without drifting into unrelated topics.
                    """.trim();
        };
    }
}
