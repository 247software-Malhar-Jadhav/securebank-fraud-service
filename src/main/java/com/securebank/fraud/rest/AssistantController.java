package com.securebank.fraud.rest;

import com.securebank.fraud.ai.AiOrchestrator;
import com.securebank.fraud.ai.AssistantService;
import com.securebank.fraud.insights.InsightsService;
import com.securebank.fraud.support.MoneyMapper;
import com.securebank.fraud.support.SupportedLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * OPTIONAL REST surface mirroring the public routes the gateway exposes
 * ({@code /api/assistant/ask}, {@code /api/insights/spending}).
 *
 * <p>The canonical inter-service contract is gRPC; this REST layer exists for direct
 * testing and to document the same capabilities via springdoc/Swagger UI. It reuses the
 * exact same domain services as the gRPC endpoint, so behaviour (Strategy, Adapter,
 * circuit breaker, degradation) is identical.</p>
 *
 * <p>The gateway forwards the resolved user as {@code X-User-Id}; we read it where useful.</p>
 */
@RestController
// The gateway forwards the path unchanged (it does not strip /api), and the other
// services mount their controllers under /api, so this controller must too — otherwise
// /api/assistant/ask and /api/insights/spending 404 at the service.
@RequestMapping("/api")
@Tag(name = "Assistant & Insights",
        description = "Ask SecureBank assistant and spending insights (optional REST mirror of the gRPC API).")
public class AssistantController {

    private final AssistantService assistantService;
    private final InsightsService insightsService;

    public AssistantController(AssistantService assistantService, InsightsService insightsService) {
        this.assistantService = assistantService;
        this.insightsService = insightsService;
    }

    @PostMapping("/assistant/ask")
    @Operation(summary = "Ask the SecureBank assistant a localized question.")
    public AskResponse ask(@RequestBody AskBody body) {
        SupportedLocale locale = SupportedLocale.parse(body.getLocale());
        AiOrchestrator.Result r = assistantService.ask(body.getQuestion(), locale);
        return new AskResponse(r.getText(), r.isFromLlm());
    }

    @GetMapping("/insights/spending")
    @Operation(summary = "Spending category breakdown plus a localized natural-language summary.")
    public InsightsResponse spending(
            @RequestParam(name = "customerId") String customerId,
            @RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
            @RequestHeader(name = "X-User-Id", required = false) String userIdHeader) {
        // Prefer an explicit customerId; the gateway-supplied header is available as context.
        String resolved = (customerId != null && !customerId.isBlank()) ? customerId : userIdHeader;
        SupportedLocale loc = SupportedLocale.parse(locale);
        InsightsService.InsightsResult result = insightsService.insights(resolved, loc);

        List<CategoryDto> breakdown = result.getBreakdown().stream()
                .map(ct -> new CategoryDto(ct.getCategory(), ct.getTotal()))
                .toList();
        return new InsightsResponse(breakdown, result.getSummary());
    }

    // ------------------------------------------------------------------
    // DTOs (deliberately separate from gRPC/proto and domain types).
    // ------------------------------------------------------------------

    @Value
    public static class AskBody {
        @NotBlank
        String question;
        String locale;
    }

    @Value
    public static class AskResponse {
        String answer;
        boolean fromLlm;
    }

    @Value
    public static class CategoryDto {
        String category;
        BigDecimal total;
    }

    @Value
    public static class InsightsResponse {
        List<CategoryDto> breakdown;
        String summary;
    }
}
