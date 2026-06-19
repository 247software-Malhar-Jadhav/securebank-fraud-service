package com.securebank.fraud.ai;

import com.securebank.fraud.support.SupportedLocale;

/**
 * ADAPTER pattern — a stable, provider-agnostic interface the rest of the service depends
 * on for "generate some natural language".
 *
 * <p>The application code (assistant, insights) only ever talks to this interface. Behind
 * it sit two adapters with very different mechanics:</p>
 * <ul>
 *   <li>{@code LlmAiProvider} — adapts a remote Anthropic-style HTTP API (Claude,
 *       model "claude-opus-4-8") to this interface, wrapped in a circuit breaker + retry.</li>
 *   <li>{@code DeterministicAiProvider} — a fully offline, template/keyword based adapter
 *       that needs no network. It is the graceful-degradation fallback.</li>
 * </ul>
 *
 * <p>Because both implement the same contract, the orchestrating
 * {@code AssistantService}/{@code InsightsService} can swap between "live" and "fallback"
 * with no knowledge of which is in play, and the choice can flip at runtime (key blank,
 * circuit open, request failed).</p>
 */
public interface AiProvider {

    /**
     * Answer a free-text customer question ("Ask SecureBank"), localized to {@code locale}.
     *
     * @return the answer text; never null.
     */
    String answer(String question, SupportedLocale locale);

    /**
     * Produce a short natural-language summary of a spending breakdown, localized.
     *
     * @param breakdownDescription a compact, machine-built description of the spend
     *                             categories (the deterministic facts to summarise)
     * @return the summary text; never null.
     */
    String summarize(String breakdownDescription, SupportedLocale locale);

    /**
     * @return true if THIS adapter is the real LLM (so callers can set the
     *         {@code from_llm} flag on AskReply correctly).
     */
    boolean isLlm();
}
