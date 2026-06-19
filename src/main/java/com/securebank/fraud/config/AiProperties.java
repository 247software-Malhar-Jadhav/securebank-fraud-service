package com.securebank.fraud.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code securebank.ai.*} configuration block.
 *
 * <p>These properties control how (and whether) the assistant and insights summary talk
 * to a real LLM:</p>
 * <ul>
 *   <li>{@code enabled}  - master switch. When false we never attempt the LLM.</li>
 *   <li>{@code baseUrl}  - the Anthropic-style HTTP endpoint base URL.</li>
 *   <li>{@code apiKey}   - the API key. BLANK BY DEFAULT. When blank the service degrades
 *                          gracefully to the deterministic provider (offline mode).</li>
 *   <li>{@code model}    - the model id. Defaults to the platform-wide "claude-opus-4-8".</li>
 * </ul>
 *
 * <p>Keeping these in one typed object (instead of scattered {@code @Value} reads) makes
 * the "graceful degradation" decision easy to centralise: see {@code AiProperties#isLive()}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "securebank.ai")
public class AiProperties {

    /** Master on/off switch for LLM usage. */
    private boolean enabled = true;

    /** Anthropic-style HTTP endpoint base URL (e.g. https://api.anthropic.com). */
    private String baseUrl = "https://api.anthropic.com";

    /**
     * API key. Intentionally blank by default so a fresh checkout runs fully offline
     * on the deterministic provider. Supplied via env / k8s secret in real deployments.
     */
    private String apiKey = "";

    /**
     * Model identifier. The whole platform standardises on Claude Opus 4.8.
     * Exposed as a constant on the provider too, but configurable for forward-compat.
     */
    private String model = "claude-opus-4-8";

    /**
     * The single source of truth for "should we even try the network LLM?".
     * We require the feature enabled AND a non-blank API key. If either fails we run
     * deterministically. This is what makes degradation automatic rather than an error.
     */
    public boolean isLive() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
