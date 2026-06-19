package com.securebank.fraud.grpc;

import com.securebank.contracts.fraud.v1.AskReply;
import com.securebank.contracts.fraud.v1.AskRequest;
import com.securebank.contracts.fraud.v1.CategorySpend;
import com.securebank.contracts.fraud.v1.FraudServiceGrpc;
import com.securebank.contracts.fraud.v1.InsightsReply;
import com.securebank.contracts.fraud.v1.InsightsRequest;
import com.securebank.contracts.fraud.v1.ScoreRequest;
import com.securebank.contracts.fraud.v1.ScoreResult;
import com.securebank.fraud.ai.AiOrchestrator;
import com.securebank.fraud.ai.AssistantService;
import com.securebank.fraud.insights.DemoDataStore;
import com.securebank.fraud.insights.InsightsService;
import com.securebank.fraud.scoring.FraudDecision;
import com.securebank.fraud.scoring.FraudScoringService;
import com.securebank.fraud.scoring.ScoringContext;
import com.securebank.fraud.support.MoneyMapper;
import com.securebank.fraud.support.SupportedLocale;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

/**
 * gRPC SERVER endpoint implementing {@code FraudService} (Score / Ask / Insights).
 *
 * <p>{@link GrpcService} (net.devh) registers this bean on the in-process gRPC server bound
 * to port 9094. This class is a thin ADAPTER between the protobuf wire types and the domain
 * services — it maps requests in, delegates to the right service, and maps results back out.
 * All real logic (Strategy scoring, Adapter/circuit-breaker AI, insights) lives in the
 * injected services.</p>
 */
@Slf4j
@GrpcService
public class FraudGrpcService extends FraudServiceGrpc.FraudServiceImplBase {

    private final FraudScoringService scoringService;
    private final AssistantService assistantService;
    private final InsightsService insightsService;
    private final DemoDataStore demoData;

    public FraudGrpcService(FraudScoringService scoringService,
                            AssistantService assistantService,
                            InsightsService insightsService,
                            DemoDataStore demoData) {
        this.scoringService = scoringService;
        this.assistantService = assistantService;
        this.insightsService = insightsService;
        this.demoData = demoData;
    }

    // ------------------------------------------------------------------
    // Score: STRATEGY pattern (rule-based + statistical) -> 0..1 + decision.
    // ------------------------------------------------------------------
    @Override
    public void score(ScoreRequest request, StreamObserver<ScoreResult> responseObserver) {
        BigDecimal amount = MoneyMapper.toBigDecimal(request.getAmount());
        String currency = request.getAmount().getCurrency();
        String customerId = request.getCustomerId();

        // Enrich the request with the small amount of history the strategies need. In
        // production this enrichment would query the ledger read-model; here it comes from
        // the in-memory demo store.
        var recent = demoData.recentAmounts(customerId);
        boolean newPayee = !demoData.isKnownPayee(customerId, request.getToAccountId());

        ScoringContext ctx = ScoringContext.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .customerId(customerId)
                .amount(amount)
                .currency(currency == null || currency.isBlank() ? "INR" : currency)
                .newPayee(newPayee)
                .recentTransferCount(recent.size())
                .recentAmounts(recent)
                .build();

        FraudDecision decision = scoringService.score(ctx);

        ScoreResult result = ScoreResult.newBuilder()
                .setScore(decision.getScore())
                .setDecision(decision.getDecision())
                .addAllReasons(decision.getReasons())
                .build();

        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }

    // ------------------------------------------------------------------
    // Ask: AiProvider ADAPTER + circuit breaker, with graceful degradation.
    // ------------------------------------------------------------------
    @Override
    public void ask(AskRequest request, StreamObserver<AskReply> responseObserver) {
        SupportedLocale locale = SupportedLocale.parse(request.getLocale());
        AiOrchestrator.Result answer = assistantService.ask(request.getQuestion(), locale);

        AskReply reply = AskReply.newBuilder()
                .setAnswer(answer.getText())
                // from_llm=false signals the deterministic fallback was used.
                .setFromLlm(answer.isFromLlm())
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    // ------------------------------------------------------------------
    // Insights: category breakdown + localized NL summary.
    // ------------------------------------------------------------------
    @Override
    public void insights(InsightsRequest request, StreamObserver<InsightsReply> responseObserver) {
        SupportedLocale locale = SupportedLocale.parse(request.getLocale());
        InsightsService.InsightsResult result =
                insightsService.insights(request.getCustomerId(), locale);

        InsightsReply.Builder reply = InsightsReply.newBuilder()
                .setSummary(result.getSummary());

        for (InsightsService.CategoryTotal ct : result.getBreakdown()) {
            // Insights are computed in INR in the demo dataset.
            reply.addBreakdown(CategorySpend.newBuilder()
                    .setCategory(ct.getCategory())
                    .setTotal(MoneyMapper.toMoney("INR", ct.getTotal()))
                    .build());
        }

        responseObserver.onNext(reply.build());
        responseObserver.onCompleted();
    }
}
