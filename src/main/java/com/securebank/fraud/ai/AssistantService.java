package com.securebank.fraud.ai;

import com.securebank.fraud.support.SupportedLocale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * The "Ask SecureBank" assistant facade used by the gRPC layer.
 *
 * <p>It delegates to {@link AiOrchestrator} (which decides LLM vs deterministic) and caches
 * the result in Redis via Spring's cache abstraction. Caching the assistant answer keyed by
 * (question, locale) keeps repeated identical questions cheap and shields the LLM/circuit
 * breaker from duplicate load.</p>
 *
 * <p>We cache the {@link AiOrchestrator.Result} so the {@code from_llm} flag is preserved on
 * a cache hit. Cache TTL is configured on the "assistant" cache in application.yml.</p>
 */
@Slf4j
@Service
public class AssistantService {

    private final AiOrchestrator orchestrator;

    public AssistantService(AiOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Answer a question, with a Redis-backed cache keyed by locale + question.
     * On a miss the orchestrator runs (LLM or deterministic) and the result is stored.
     */
    @Cacheable(cacheNames = "assistant", key = "#locale.name() + '|' + #question")
    public AiOrchestrator.Result ask(String question, SupportedLocale locale) {
        log.debug("Assistant cache miss; computing answer for locale={}", locale);
        return orchestrator.answer(question, locale);
    }
}
