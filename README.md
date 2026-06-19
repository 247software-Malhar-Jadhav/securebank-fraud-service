# securebank-fraud-service

The **AI brain** of the SecureBank microservices platform. A gRPC server that:

- **Scores** transfers for fraud risk (Strategy pattern: rule-based + statistical).
- Answers **"Ask SecureBank"** assistant questions (Adapter pattern over an LLM, wrapped
  in a circuit breaker, degrading gracefully to a deterministic provider).
- Produces **spending insights** (category breakdown + a localized natural-language summary).

| | |
|---|---|
| Root package | `com.securebank.fraud` |
| HTTP port | **8084** (actuator, optional REST, Swagger UI) |
| gRPC port | **9094** (`FraudService`: `Score` / `Ask` / `Insights`) |
| Relational DB | **none** — Redis only (cache) |
| LLM model | **`claude-opus-4-8`** (Claude Opus 4.8) |
| Stack | Java 21 + virtual threads, Spring Boot 3.3.x, Maven, Lombok, springdoc, Micrometer/Prometheus, Resilience4j, Spring Data Redis, `net.devh` gRPC server |

## Design patterns used

- **Strategy** — `FraudStrategy` with `RuleBasedFraudStrategy` + `StatisticalFraudStrategy`,
  blended by `FraudScoringService`.
- **Adapter** — `AiProvider` with `LlmAiProvider` (remote Claude HTTP API) and
  `DeterministicAiProvider` (offline fallback).
- **Circuit Breaker** — Resilience4j (`@CircuitBreaker` + `@Retry`) around the LLM call,
  with graceful degradation to the deterministic provider.

See [`docs/fraud-service.md`](docs/fraud-service.md) for the full design, diagrams and rationale.

## gRPC contract

`FraudService` (from `fraud.proto`, package `securebank.fraud.v1`):

```proto
rpc Score    (ScoreRequest)    returns (ScoreResult);   // 0..1 score + ALLOW|REVIEW|BLOCK + reasons
rpc Ask      (AskRequest)      returns (AskReply);       // assistant answer + from_llm flag
rpc Insights (InsightsRequest) returns (InsightsReply);  // category breakdown + localized summary
```

**Proto strategy:** this repo **vendors** `common.proto` + `fraud.proto` into
`src/main/proto/` and generates stubs locally via `protobuf-maven-plugin` + `os-maven-plugin`
(generated package `com.securebank.contracts.fraud.v1`). It does not depend on the
`securebank-contracts` jar, so it builds standalone.

## Graceful degradation (important)

The service runs **fully offline by default**. `securebank.ai.api-key` is **blank**, so the
assistant and the insights summary use `DeterministicAiProvider`. Set a real key to enable
the live LLM path; if the LLM endpoint is disabled, unconfigured, failing, or the circuit
breaker is open, the service automatically falls back to the deterministic provider and sets
`from_llm=false` on `AskReply`.

## No database / insights data source

There is **no relational DB**. Spending insights and the scoring history are computed from a
small in-memory demo dataset (`DemoDataStore`). **In production these would be computed by
querying transaction-service's ledger read-model over gRPC** — `DemoDataStore` mirrors that
shape so swapping in a real client is a localized change.

## Localization

All assistant answers and insights summaries are localized to **en / hi / mr**
(English / Hindi / Marathi, real Devanagari) based on the request locale.

## Build & run

```bash
# Build (generates gRPC stubs, runs tests, produces the executable jar)
mvn -DskipTests package

# Run locally (needs a Redis on localhost:6379; AI stays deterministic with a blank key)
mvn spring-boot:run
# HTTP:    http://localhost:8084/actuator/health
# Swagger: http://localhost:8084/swagger-ui.html
# gRPC:    localhost:9094
```

### Enable the live LLM

```bash
export SECUREBANK_AI_API_KEY='sk-ant-...'   # supplies a key => live Claude path
export SECUREBANK_AI_MODEL='claude-opus-4-8' # default
mvn spring-boot:run
```

## Docker

```bash
docker build -t securebank/fraud-service:1.0.0 .
docker run -p 8084:8084 -p 9094:9094 \
  -e REDIS_HOST=host.docker.internal \
  securebank/fraud-service:1.0.0
```

## Kubernetes

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml      # AI key (blank => deterministic). Replace to go live.
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## Optional REST surface

Mirrors the gateway's public routes, for direct testing:

```
POST /assistant/ask           { "question": "...", "locale": "en|hi|mr" }
GET  /insights/spending?customerId=cust-001&locale=en
```

## Observability

- `GET /actuator/health` (incl. liveness/readiness groups + circuit-breaker health)
- `GET /actuator/prometheus` (Micrometer metrics, incl. Resilience4j circuit-breaker metrics)
- `GET /actuator/circuitbreakers`
