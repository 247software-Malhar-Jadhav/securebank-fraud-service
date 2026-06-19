package com.securebank.fraud.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.securebank.fraud.config.AiProperties;
import com.securebank.fraud.support.SupportedLocale;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The single decision point for "use the LLM or degrade to deterministic".
 *
 * <p>Both the assistant (Ask) and insights (summary) go through here so the
 * graceful-degradation policy lives in exactly one place:</p>
 * <ol>
 *   <li>If {@link AiProperties#isLive()} is false (feature off OR blank api-key — the
 *       default), skip the network entirely and use the deterministic provider.</li>
 *   <li>Otherwise try the {@link LlmAiProvider}. If it throws {@link LlmUnavailableException}
 *       (circuit open / failure), catch it and fall back to deterministic.</li>
 * </ol>
 *
 * <p>The returned {@link Result} carries both the text and a {@code fromLlm} flag so the
 * gRPC layer can set {@code AskReply.from_llm} correctly.</p>
 */
@Slf4j
@Service
public class AiOrchestrator {

    private final LlmAiProvider llm;
    private final DeterministicAiProvider deterministic;
    private final AiProperties props;

    public AiOrchestrator(LlmAiProvider llm,
                          DeterministicAiProvider deterministic,
                          AiProperties props) {
        this.llm = llm;
        this.deterministic = deterministic;
        this.props = props;
    }

    /** Answer a question, preferring the LLM, degrading to deterministic. */
    public Result answer(String question, SupportedLocale locale) {
        if (!props.isLive()) {
            log.debug("AI not live (enabled={}, key blank={}); using deterministic answer",
                    props.isEnabled(), props.getApiKey() == null || props.getApiKey().isBlank());
            return new Result(deterministic.answer(question, locale), false);
        }
        try {
            return new Result(llm.answer(question, locale), true);
        } catch (LlmUnavailableException ex) {
            log.warn("Falling back to deterministic answer: {}", ex.getMessage());
            return new Result(deterministic.answer(question, locale), false);
        }
    }

    /** Summarise a spending breakdown, preferring the LLM, degrading to deterministic. */
    public Result summarize(String breakdownDescription, SupportedLocale locale) {
        if (!props.isLive()) {
            return new Result(deterministic.summarize(breakdownDescription, locale), false);
        }
        try {
            return new Result(llm.summarize(breakdownDescription, locale), true);
        } catch (LlmUnavailableException ex) {
            log.warn("Falling back to deterministic summary: {}", ex.getMessage());
            return new Result(deterministic.summarize(breakdownDescription, locale), false);
        }
    }

    /**
     * Text + provenance of an AI response. Annotated for Jackson so it can be cached in
     * Redis (JSON) and deserialised back on a cache hit, preserving the {@code fromLlm} flag.
     */
    @Value
    public static class Result {
        String text;
        boolean fromLlm;

        @JsonCreator
        public Result(@JsonProperty("text") String text,
                      @JsonProperty("fromLlm") boolean fromLlm) {
            this.text = text;
            this.fromLlm = fromLlm;
        }
    }
}
