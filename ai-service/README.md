# ai-service

Spring AI 2.0 microservice providing LLM-powered content enrichment for the platform's event-driven notification pipeline.

**Port:** `8083`  
**Database:** `ai_db` on the shared PostgreSQL instance (PGVector extension)  
**LLM backend:** Ollama (local, port `11434`)

---

## What It Does

When a new user is created, `notification-service` calls this service with the user's details. The service generates a personalized 2-sentence welcome message using a local LLM (`llama3.2` via Ollama) and returns it.

The call is **non-fatal by design**: if this service is unavailable, times out, or returns an error, the caller falls back to a static welcome message. Kafka consumer processing is never blocked by AI unavailability.

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/ai/enrich` | Generate enriched notification content |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/health/liveness` | Kubernetes liveness probe |
| `GET` | `/actuator/health/readiness` | Kubernetes readiness probe |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape |

### Request — `POST /api/v1/ai/enrich`

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "userName": "Jane Doe",
  "userEmail": "jane@example.com",
  "eventType": "USER_CREATED",
  "context": {}
}
```

### Response

```json
{
  "success": true,
  "message": "Enrichment successful",
  "data": {
    "enrichedMessage": "Welcome, Jane! Your account is now active and ready — we're excited to have you on board.",
    "modelUsed": "llama3.2",
    "processingTimeMs": 1842
  }
}
```

---

## Running Locally

### Prerequisites

1. **Install schemas** (required once before any build):
   ```bash
   cd ../common-schema && mvn clean install
   ```

2. **Start Ollama** (separate install — [ollama.ai](https://ollama.ai)):
   ```bash
   ollama serve              # starts the runtime at localhost:11434
   ollama pull llama3.2      # ~2.0 GB — pull once, cached locally
   ollama pull nomic-embed-text  # ~274 MB — for future embedding features
   ```

3. **Start PostgreSQL** with the PGVector extension. The easiest way is via Docker:
   ```bash
   docker run -d --name pgvector -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 pgvector/pgvector:pg17
   # Then create the database:
   docker exec -it pgvector psql -U postgres -c "CREATE DATABASE ai_db;"
   ```
   Or use the full `docker/docker-compose.yml` stack which includes postgres.

### Start the service

```bash
./gradlew bootRun
```

The service starts on port `8083`. Flyway will create the schema automatically on first start.

### Test the endpoint

```bash
curl -X POST http://localhost:8083/api/v1/ai/enrich \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "00000000-0000-0000-0000-000000000001",
    "userName": "Test User",
    "userEmail": "test@example.com",
    "eventType": "USER_CREATED"
  }'
```

---

## Configuration

All settings have sensible defaults for local development. Override via environment variables for Docker/Kubernetes.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama runtime URL |
| `spring.ai.ollama.chat.model` | — | `llama3.2` | Chat model |
| `spring.ai.ollama.embedding.model` | — | `nomic-embed-text` | Embedding model |
| `spring.datasource.url` | `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost:5432/ai_db` | PGVector database |
| `spring.datasource.username` | `DB_USERNAME` | `postgres` | DB username |
| `spring.datasource.password` | `DB_PASSWORD` | `postgres` | DB password |
| `otel.exporter.otlp.endpoint` | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | Tempo OTLP endpoint |

---

## Running in Kubernetes

The service is included in the Kustomize base manifests at `k8s/base/ai-service/`.

### Resource requirements

| Mode | Node RAM needed |
|------|----------------|
| ai-service only (Ollama not running) | 256MB — starts fine, every enrichment call falls back |
| ai-service + Ollama + llama3.2 | ≥ 8GB — llama3.2 loads ~2.5GB into RAM |

The Ollama StatefulSet manifests (`k8s/base/ollama/`) are committed but require `docker desktop` with ≥ 8GB memory to apply successfully. On a 4GB kind node, ai-service will stay in `Init:2/3` (waiting for Ollama at `ollama:11434`) — this is intentional and correct behavior. The notification-service falls back to the static message in the meantime.

### Apply (with Ollama — requires 8GB)

```bash
kubectl apply -k k8s/base/

# Pull models (runs as a one-shot Kubernetes Job)
# The model-pull Job handles this automatically on first apply.
# Check status:
kubectl get job ollama-model-pull -n microservices
```

### Apply (without Ollama — 4GB node)

Edit `k8s/base/kustomization.yaml` and comment out the three Ollama entries before applying:

```yaml
# - ollama/statefulset.yaml
# - ollama/service.yaml
# - ollama/model-pull-job.yaml
```

ai-service will start but every enrichment call will time out and fall back. The notification pipeline works end-to-end using the fallback message.

---

## Docker Image

```bash
# Build the JAR first
./gradlew bootJar

# Build the Docker image
docker build -t ai-platform/ai-service:1.0.0 .

# Load into kind (bypasses multi-arch import issue)
docker save ai-platform/ai-service:1.0.0 | docker exec -i ai-platform-control-plane \
  ctr --namespace=k8s.io images import --digests --snapshotter=overlayfs -
```

The image is built with:
- Base: `eclipse-temurin:21-jre` (layered Spring Boot 4 tools-jarmode extraction)
- OTel Java Agent 2.28.1 baked in (`/otel/opentelemetry-javaagent.jar`)
- Non-root user `spring` (uid 1001)
- `MaxRAMPercentage=75.0`

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 4, Java 21, Virtual Threads |
| AI | Spring AI 2.0 (`spring-ai-starter-model-ollama`) |
| Vector store | Spring AI PGVector (`spring-ai-starter-vector-store-pgvector`) |
| LLM | Ollama + llama3.2 (chat), nomic-embed-text (embeddings) |
| Database | PostgreSQL 17 with PGVector extension |
| Migrations | Flyway |
| Observability | Micrometer + Prometheus, OpenTelemetry Java Agent |
| Build | Gradle (independent build, no root project) |
