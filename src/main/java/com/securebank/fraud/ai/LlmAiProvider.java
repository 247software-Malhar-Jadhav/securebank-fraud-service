package com.securebank.fraud.ai;

import com.securebank.fraud.config.AiProperties;
import com.securebank.fraud.support.SupportedLocale;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * ADAPTER #1 — adapts a remote Anthropic-style HTTP API (Claude) to the {@link AiProvider}
 * interface.
 *
 * <p><b>Model:</b> the whole platform standardises on Claude Opus 4.8. The model id is held
 * as the constant {@link #MODEL} ("claude-opus-4-8") and also configurable via
 * {@code securebank.ai.model} for forward-compatibility.</p>
 *
 * <p><b>Resilience (CIRCUIT BREAKER pattern):</b> the network call is wrapped with
 * Resilience4j {@code @CircuitBreaker} + {@code @Retry}. If the endpoint is slow or failing,
 * the breaker opens and short-circuits further calls; {@code @Retry} smooths over transient
 * blips. Both point at a {@code fallback} method that throws {@link LlmUnavailableException},
 * which the orchestrating services catch to switch to {@link DeterministicAiProvider}. This
 * is the mechanism behind "degrade gracefully".</p>
 *
 * <p>Note this bean ALWAYS exists, but the orchestrators only call it when
 * {@link AiProperties#isLive()} (feature enabled + non-blank key). With a blank key the
 * deterministic provider is used directly and this class is never hit.</p>
 */
@Slf4j
@Component
public class LlmAiProvider implements AiProvider {

    /** Platform-wide model constant. */
    public static final String MODEL = "claude-opus-4-8";

    /** Resilience4j instance name, referenced from application.yml config. */
    private static final String CB = "llmProvider";

    private final RestClient restClient;
    private final AiProperties props;

    public LlmAiProvider(RestClient anthropicRestClient, AiProperties props) {
        this.restClient = anthropicRestClient;
        this.props = props;
    }

    @Override
    @CircuitBreaker(name = CB, fallbackMethod = "answerFallback")
    @Retry(name = CB)
    public String answer(String question, SupportedLocale locale) {
        String system = "You are the SecureBank assistant. Answer concisely and accurately. "
                + "Reply ONLY in language: " + languageName(locale) + ".";
        return callMessages(system, question);
    }

    @Override
    @CircuitBreaker(name = CB, fallbackMethod = "summarizeFallback")
    @Retry(name = CB)
    public String summarize(String breakdownDescription, SupportedLocale locale) {
        String system = "You are a personal-finance assistant. Summarise the user's spending "
                + "in 2-3 friendly sentences. Reply ONLY in language: " + languageName(locale) + ".";
        return callMessages(system, "Spending breakdown: " + breakdownDescription);
    }

    @Override
    public boolean isLlm() {
        return true;
    }

    // ------------------------------------------------------------------
    // The actual HTTP call to the Anthropic-style /v1/messages endpoint.
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String callMessages(String system, String userContent) {
        // Anthropic Messages API request shape.
        Map<String, Object> body = Map.of(
                "model", resolveModel(),
                "max_tokens", 512,
                "system", system,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", userContent))
        );

        log.debug("Calling LLM endpoint model={} ...", resolveModel());
        Map<String, Object> response = restClient.post()
                .uri("/v1/messages")
                // Anthropic auth headers. The key comes from config (never logged).
                .header("x-api-key", props.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        // Response shape: { "content": [ { "type": "text", "text": "..." } ], ... }
        if (response == null) {
            throw new LlmUnavailableException("Empty LLM response");
        }
        Object content = response.get("content");
        if (content instanceof List<?> blocks && !blocks.isEmpty()
                && blocks.get(0) instanceof Map<?, ?> first) {
            Object text = first.get("text");
            if (text != null) {
                return text.toString();
            }
        }
        throw new LlmUnavailableException("Unexpected LLM response shape");
    }

    private String resolveModel() {
        // Prefer the configured model but default to the platform constant.
        String configured = props.getModel();
        return (configured == null || configured.isBlank()) ? MODEL : configured;
    }

    private String languageName(SupportedLocale locale) {
        return switch (locale) {
            case HI -> "Hindi (Devanagari)";
            case MR -> "Marathi (Devanagari)";
            case EN -> "English";
        };
    }

    // ------------------------------------------------------------------
    // Resilience4j fallbacks. Invoked when the breaker is OPEN or the call
    // ultimately fails. They translate any failure into LlmUnavailableException
    // so the orchestrating service can switch to the deterministic provider.
    // The fallback signature must match the original method + a Throwable arg.
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    private String answerFallback(String question, SupportedLocale locale, Throwable t) {
        log.warn("LLM answer() unavailable ({}); signalling fallback", t.toString());
        throw new LlmUnavailableException("LLM answer unavailable", t);
    }

    @SuppressWarnings("unused")
    private String summarizeFallback(String breakdownDescription, SupportedLocale locale, Throwable t) {
        log.warn("LLM summarize() unavailable ({}); signalling fallback", t.toString());
        throw new LlmUnavailableException("LLM summarize unavailable", t);
    }
}
