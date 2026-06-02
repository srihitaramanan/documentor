# Architecture

This document describes DocuMentor's architecture in depth: components, runtime behavior, deployment topology, and how the system is designed to evolve.

---

## 1. Architectural Style

DocuMentor is a **modular monolith**. All bounded contexts live in one deployable artifact, but each is isolated by package boundaries and exposes a narrow API to others.

```mermaid
graph TB
    subgraph App["DocuMentor (Single Spring Boot Process)"]
        direction TB
        subgraph Bounded["Bounded Contexts"]
            A[Auth]
            U[User]
            D[Document]
            I[Ingestion]
            C[Chunk + Vector Search]
            CH[Chat / RAG]
        end
        subgraph Shared["Shared Kernel"]
            COM[Common: errors, config, security]
            LLM[LLM Client Abstraction]
            METRICS[Metrics & Tracing]
        end

        A --> COM
        U --> COM
        D --> COM
        I --> COM
        C --> COM
        CH --> COM
        I --> LLM
        CH --> LLM
        I --> METRICS
        CH --> METRICS
    end

    style Bounded fill:#e8f5e9,stroke:#388e3c
    style Shared fill:#fff9c4,stroke:#fbc02d
```

**Why monolith first?** See [ADR-001](adr/001-monolith-over-microservices.md). Short version: a single dev with no production load doesn't need microservices, and a clean monolith *can* be split later if real pressure demands it.

---

## 2. Component Catalog

```mermaid
graph LR
    subgraph Web["Web Layer"]
        AC[AuthController]
        DC[DocumentController]
        CC[ChatController]
        EF[ExceptionHandler]
        RL[RateLimitFilter]
        JF[JwtAuthFilter]
    end

    subgraph Service["Service Layer"]
        AS[AuthService]
        DS[DocumentService]
        IS[IngestionService]
        CS[ChatService]
        VS[VectorSearchService]
        ES[EmbeddingService]
    end

    subgraph Domain["Domain Layer"]
        PB[PromptBuilder]
        TC[TextChunker]
        PE[PdfTextExtractor]
        TT[TokenCounter]
    end

    subgraph Data["Data Layer"]
        UR[UserRepository]
        DR[DocumentRepository]
        CR[ChunkRepository]
        CVR[ConversationRepository]
        MR[MessageRepository]
    end

    AC --> AS
    DC --> DS
    CC --> CS
    AS --> UR
    DS --> DR
    DS --> IS
    IS --> PE
    IS --> TC
    IS --> ES
    IS --> CR
    CS --> CVR
    CS --> MR
    CS --> VS
    CS --> PB
    VS --> CR
    TC --> TT
    PB --> TT
```

### Key components

| Component | Responsibility |
|---|---|
| `JwtAuthFilter` | Validates Bearer tokens; sets `SecurityContext`. |
| `RateLimitFilter` | Per-user token bucket via Bucket4j; 429 on exhaustion. |
| `DocumentService` | Orchestrates upload, persists metadata, submits async ingestion. |
| `IngestionService` | Pipeline: extract → chunk → embed → persist. Runs on virtual threads. |
| `PdfTextExtractor` | Wraps Apache Tika + PDFBox; preserves page numbers. |
| `TextChunker` | Token-aware sliding-window chunker; respects paragraph boundaries. |
| `EmbeddingService` | Batched embedding calls via Spring AI. |
| `VectorSearchService` | Native pgvector cosine-similarity search with user-scoped filtering. |
| `ChatService` | RAG orchestration: retrieve → augment → generate → cite. |
| `PromptBuilder` | Builds system/context/history/question messages; centralized for iteration. |

---

## 3. Runtime View — Ingestion Pipeline

```mermaid
stateDiagram-v2
    [*] --> PENDING : Upload accepted
    PENDING --> PROCESSING : Worker picks up
    PROCESSING --> EXTRACTING : Extract text
    EXTRACTING --> CHUNKING : Text ready
    CHUNKING --> EMBEDDING : Chunks ready
    EMBEDDING --> READY : All vectors stored
    EXTRACTING --> FAILED : Parse error
    EMBEDDING --> FAILED : LLM/DB error
    FAILED --> PROCESSING : Retry (manual or scheduled)
    READY --> [*]

    note right of EMBEDDING
        Batched: 32 chunks per
        OpenAI Embeddings call
    end note

    note right of FAILED
        error_message column
        captures cause
    end note
```

### Backpressure & resilience

- **Concurrency limit**: a semaphore caps simultaneous ingestions per user (default 2) to prevent one user from monopolizing the worker pool.
- **Retry policy**: Spring Retry with exponential backoff on transient LLM errors (5xx, 429). Non-retryable on 4xx other than 429.
- **Idempotency**: documents have a `content_hash` (SHA-256 of bytes). Re-uploading the same file returns the existing document instead of duplicating.

---

## 4. Runtime View — RAG Query Pipeline

```mermaid
flowchart TB
    START([User question]) --> EMBED[Embed question]
    EMBED --> CACHE{Semantic<br/>cache hit?}
    CACHE -- yes --> RETURN([Return cached])
    CACHE -- no --> SEARCH[Vector search<br/>top-K=5]
    SEARCH --> RERANK{Re-rank<br/>enabled?}
    RERANK -- yes --> CROSS[Cross-encoder rerank]
    RERANK -- no --> ASSEMBLE
    CROSS --> ASSEMBLE[Assemble prompt:<br/>system + history + context + Q]
    ASSEMBLE --> TOKENS{Within<br/>token budget?}
    TOKENS -- no --> TRIM[Trim history /<br/>drop low-score chunks]
    TRIM --> ASSEMBLE
    TOKENS -- yes --> CALL[LLM call - streaming]
    CALL --> EMIT[Emit SSE tokens]
    EMIT --> SAVE[Persist message<br/>+ cited chunk ids]
    SAVE --> CACHEW[Write to semantic cache]
    CACHEW --> END([Done])

    style START fill:#e1f5ff
    style END fill:#e8f5e9
    style RETURN fill:#e8f5e9
    style CALL fill:#fff3e0
```

---

## 5. Deployment Topology

### Local (dev)

```mermaid
graph LR
    DEV[💻 Developer] --> DC[docker-compose]
    DC --> APP[documentor:latest]
    DC --> PG[(postgres:16<br/>+pgvector)]
    DC --> PROM[prometheus]
    DC --> GRAF[grafana]
    APP --> PG
    APP -.metrics.-> PROM
    GRAF --> PROM
```

### Production (single-node, recommended for portfolio demo)

```mermaid
graph TB
    USER[🌍 Users] --> CF[CloudFlare<br/>TLS termination + WAF]
    CF --> ECS[AWS ECS Fargate<br/>1 task, 1 vCPU, 2GB]
    ECS --> RDS[(AWS RDS Postgres 16<br/>db.t4g.micro + pgvector)]
    ECS --> S3[(S3<br/>Document blobs)]
    ECS --> OAI[OpenAI API]
    ECS --> CW[CloudWatch Logs + Metrics]

    style ECS fill:#fff3e0,stroke:#e65100
    style RDS fill:#e1f5ff,stroke:#0277bd
    style S3 fill:#f3e5f5,stroke:#6a1b9a
```

> 💡 **Cost note**: This stack runs on AWS Free Tier for the first 12 months (RDS db.t4g.micro, Fargate spot, S3 within free tier). For zero-cost demo deployment, use **Fly.io** or **Railway** instead.

### Production (scale-out path)

```mermaid
graph TB
    USER[🌍 Users] --> ALB[Application Load Balancer]
    ALB --> ECS1[API Task 1]
    ALB --> ECS2[API Task 2]
    ALB --> ECS3[API Task N]

    subgraph Workers["Async Workers (separate task definition)"]
        W1[Ingestion Worker 1]
        W2[Ingestion Worker 2]
    end

    ECS1 & ECS2 & ECS3 --> SQS[(SQS<br/>Ingestion Queue)]
    SQS --> W1 & W2

    ECS1 & ECS2 & ECS3 --> RDS[(RDS Postgres<br/>Multi-AZ + read replica)]
    W1 & W2 --> RDS
    ECS1 & ECS2 & ECS3 --> REDIS[(ElastiCache Redis<br/>rate limits + cache)]

    style Workers fill:#fff3e0,stroke:#e65100
```

The path from monolith to this layout: **(1)** Extract async ingestion into its own task definition behind SQS, **(2)** Move rate limits and semantic cache to Redis, **(3)** Add read replica for vector search, **(4)** Horizontally scale API tasks.

---

## 6. Cross-cutting Concerns

### Security

- **Authentication**: Stateless JWT (HS256), 24-hour expiry, refresh endpoint planned.
- **Authorization**: All document/chunk/conversation queries are filtered by `user_id` at the repository layer — *never* trust the path parameter alone.
- **Secrets**: Loaded from environment variables; never logged. `.env` is gitignored.
- **Input validation**: Bean Validation (`@Valid`); file uploads capped at 50 MB; MIME-type sniffed via Tika (not trusted from headers).
- **Prompt injection**: Document content is wrapped in clearly delimited fences in the prompt; the system message instructs the model to treat user-supplied content as data, not instructions. Output is never executed.

### Concurrency model

```mermaid
graph LR
    REQ[HTTP Request] --> TOMCAT[Tomcat thread pool<br/>200 platform threads]
    TOMCAT --> CTRL[Controller]
    CTRL --> SVC[Service]
    SVC -- sync --> DB[(Postgres)]
    SVC -- async --> VT[Virtual Thread Executor]
    VT --> LLM[LLM API call]
    VT --> DB

    style VT fill:#e8f5e9,stroke:#2e7d32
```

Long-running ingestion runs on Java 21 **virtual threads** — millions can exist cheaply, and blocking on LLM/DB I/O doesn't pin a platform thread. This is the modern alternative to a reactive stack and keeps the code synchronous/readable.

### Observability

All LLM calls record:

- `llm.latency` (timer, by provider, model, operation)
- `llm.tokens.input` / `llm.tokens.output` (counter)
- `llm.cost.usd` (counter, computed per-model)
- `llm.errors` (counter, by error class)

Plus standard Spring Boot Actuator metrics: HTTP latency, JVM, HikariCP pool, etc.

---

## 7. What's Explicitly Out of Scope (v1)

To keep scope honest:

- ❌ Multi-tenancy at the org level (only per-user isolation)
- ❌ Real-time collaborative chat
- ❌ Document version history
- ❌ Fine-tuning or custom embedding models
- ❌ Mobile push notifications

These are listed because **mid-level engineers know what NOT to build** — and interviewers notice.

---

## 8. Quality Attributes

| Attribute | Target | How it's achieved |
|---|---|---|
| Availability | 99% (demo) | Health checks, graceful shutdown, DB connection retry |
| Query latency | p95 < 4s end-to-end | HNSW index, batched embeddings, streaming first token |
| Ingestion throughput | 10 docs/min sustained | Virtual threads, batched embedding calls |
| Cost | < $0.01 per query | text-embedding-3-small + gpt-4o-mini, semantic cache |
| Recoverability | RPO 24h, RTO 1h | Nightly RDS snapshots, infra as code |

---

See also:
- [Data Model](data-model.md) for schema and indexing
- [API Design](api-design.md) for contracts and errors
- [ADRs](adr/) for the *why* behind every decision
