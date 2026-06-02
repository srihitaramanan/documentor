# 📚 DocuMentor

> **Production-grade RAG API for document question-answering.** Upload PDFs, ask questions in natural language, get answers with citations grounded in your documents.

<p align="center">
  <img src="docs/assets/architecture-hero.svg" alt="DocuMentor Architecture" width="100%"/>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#-api">API</a> •
  <a href="#-design-decisions">Design Decisions</a> •
  <a href="#-roadmap">Roadmap</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql" />
  <img src="https://img.shields.io/badge/pgvector-0.7-336791" />
  <img src="https://img.shields.io/badge/Spring%20AI-1.0-success" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" />
</p>

---

## 🎯 What it does

DocuMentor lets users upload documents (PDF, DOCX, TXT) and ask questions about them in natural language. Answers are **grounded in the source material** — every response includes citations pointing back to the exact passages used.

Under the hood, it's a textbook implementation of **Retrieval-Augmented Generation (RAG)**:

```mermaid
flowchart LR
    A[📄 Upload PDF] --> B[Extract & Chunk]
    B --> C[Generate Embeddings]
    C --> D[(pgvector)]
    E[❓ Ask Question] --> F[Embed Question]
    F --> G[Vector Search]
    D --> G
    G --> H[Build Prompt]
    H --> I[LLM]
    I --> J[💬 Answer + Citations]

    style A fill:#e1f5ff,stroke:#0288d1
    style E fill:#fff3e0,stroke:#f57c00
    style D fill:#f3e5f5,stroke:#7b1fa2
    style I fill:#e8f5e9,stroke:#388e3c
    style J fill:#e8f5e9,stroke:#388e3c
```

---

## ✨ Features

- 🔐 **JWT authentication** with per-user document isolation
- 📄 **Multi-format ingestion** — PDF, DOCX, TXT via Apache Tika
- 🧠 **Token-aware chunking** with configurable overlap
- 🔍 **Semantic search** via pgvector HNSW indexes
- 💬 **Streaming responses** over Server-Sent Events
- 📑 **Citation extraction** — every claim links to source chunks
- 🔄 **Multi-provider LLM** support (OpenAI, Anthropic, Ollama)
- ⚡ **Async ingestion** powered by Java 21 virtual threads
- 📊 **Observability** — Prometheus metrics for tokens, latency, costs
- 🛡 **Rate limiting** per user via Bucket4j
- 🧪 **Tested** with Testcontainers (integration) + JUnit 5 (unit)

---

## 🚀 Quick Start

```bash
# Clone
git clone https://github.com/YOURNAME/documentor.git
cd documentor

# Set your LLM API key
cp .env.example .env
# Edit .env and add OPENAI_API_KEY

# Run everything
docker-compose up

# Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

That's it. Postgres, pgvector, and the API are all running.

---

## 🏗 Architecture

### High-level system view

```mermaid
graph TB
    subgraph Client["🖥️ Clients"]
        SW[Swagger UI]
        CURL[curl / Postman]
        WEB[Web App]
    end

    subgraph API["☕ Spring Boot API"]
        direction TB
        SEC[JWT Auth Filter]
        CTRL[Controllers]
        SVC[Services]
        ASYNC[Virtual Thread Executor]

        SEC --> CTRL
        CTRL --> SVC
        SVC --> ASYNC
    end

    subgraph Storage["💾 Storage"]
        PG[(PostgreSQL 16<br/>+ pgvector)]
        FS[Local FS / S3<br/>Document Blobs]
    end

    subgraph External["🌐 External LLM Providers"]
        OAI[OpenAI API]
        ANT[Anthropic API]
        OLL[Ollama Local]
    end

    subgraph Obs["📊 Observability"]
        PROM[Prometheus]
        GRAF[Grafana]
    end

    Client --> SEC
    SVC --> PG
    SVC --> FS
    ASYNC --> OAI
    ASYNC --> ANT
    ASYNC --> OLL
    API --> PROM
    PROM --> GRAF

    style API fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    style Storage fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    style External fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    style Obs fill:#e1f5ff,stroke:#0288d1,stroke-width:2px
```

📐 **For deeper architecture details**, see [docs/architecture.md](docs/architecture.md).

### Module structure

```mermaid
graph LR
    AUTH[auth] --> USER[user]
    DOC[document] --> USER
    DOC --> ING[ingestion]
    ING --> CHUNK[chunk]
    ING --> LLM[llm-client]
    CHAT[chat] --> CHUNK
    CHAT --> LLM
    CHAT --> USER

    AUTH --> COMMON[common]
    DOC --> COMMON
    CHAT --> COMMON
    ING --> COMMON

    style COMMON fill:#fff9c4,stroke:#fbc02d
    style LLM fill:#ffe0b2,stroke:#f57c00
```

---

## 🔄 Core Flows

### Document Ingestion (async)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant C as Controller
    participant DS as DocumentService
    participant DB as PostgreSQL
    participant FS as File Storage
    participant W as Virtual Thread Worker
    participant T as Tika/PDFBox
    participant CH as Chunker
    participant E as Embedding Service
    participant LLM as OpenAI Embeddings

    U->>C: POST /api/documents (multipart)
    C->>DS: ingest(file)
    DS->>FS: store raw file
    DS->>DB: INSERT document (status=PENDING)
    DS-->>C: { id, status: PENDING }
    C-->>U: 202 Accepted

    Note over DS,W: Returns immediately - worker continues in background

    DS->>W: submit ingestion task
    W->>DB: UPDATE status=PROCESSING
    W->>T: extract text
    T-->>W: raw text + metadata
    W->>CH: chunk(text)
    CH-->>W: List<Chunk>

    loop For each batch (32 chunks)
        W->>E: embed(batch)
        E->>LLM: POST /v1/embeddings
        LLM-->>E: vectors[1536]
        E-->>W: vectors
        W->>DB: INSERT chunks + embeddings
    end

    W->>DB: UPDATE status=READY
```

### Question Answering (with streaming)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant C as ChatController
    participant CS as ChatService
    participant VS as VectorSearchService
    participant DB as PostgreSQL
    participant PB as PromptBuilder
    participant LLM as LLM Provider

    U->>C: POST /conversations/{id}/ask/stream
    C->>CS: ask(convId, question)

    CS->>DB: load conversation history
    CS->>LLM: embed(question)
    LLM-->>CS: question_vector

    CS->>VS: searchSimilar(vector, userId, topK=5)
    VS->>DB: SELECT chunks ORDER BY embedding <=> ?
    DB-->>VS: top-K chunks with scores
    VS-->>CS: relevant chunks

    CS->>PB: build(systemPrompt, history, chunks, question)
    PB-->>CS: full prompt

    CS->>LLM: chat.stream(prompt)
    activate LLM
    loop For each token
        LLM-->>CS: token
        CS-->>C: SSE: data: {token}
        C-->>U: token
    end
    deactivate LLM

    CS->>DB: INSERT message (with cited chunk ids)
    CS-->>C: complete
    C-->>U: SSE: event: done
```

### Authentication

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant AC as AuthController
    participant AS as AuthService
    participant DB as PostgreSQL
    participant JWT as JwtService

    rect rgb(232, 245, 233)
        Note over U,JWT: Registration
        U->>AC: POST /api/auth/register
        AC->>AS: register(email, password)
        AS->>AS: bcrypt password
        AS->>DB: INSERT user
        AS->>JWT: generate(userId)
        JWT-->>AS: token
        AS-->>U: 201 + JWT
    end

    rect rgb(227, 242, 253)
        Note over U,JWT: Subsequent Requests
        U->>AC: GET /api/documents<br/>Authorization: Bearer ...
        AC->>JWT: validate(token)
        JWT-->>AC: userId
        AC->>DB: query documents WHERE user_id = ?
        DB-->>AC: documents
        AC-->>U: 200 + documents
    end
```

---

## 💾 Data Model

```mermaid
erDiagram
    USERS ||--o{ DOCUMENTS : owns
    USERS ||--o{ CONVERSATIONS : has
    DOCUMENTS ||--o{ CHUNKS : "split into"
    DOCUMENTS ||--o{ CONVERSATIONS : "scoped to"
    CONVERSATIONS ||--o{ MESSAGES : contains
    MESSAGES }o--o{ CHUNKS : cites

    USERS {
        uuid id PK
        string email UK
        string password_hash
        timestamptz created_at
    }
    DOCUMENTS {
        uuid id PK
        uuid user_id FK
        string filename
        bigint file_size_bytes
        string mime_type
        string status "PENDING|PROCESSING|READY|FAILED"
        int page_count
        timestamptz created_at
        timestamptz processed_at
    }
    CHUNKS {
        uuid id PK
        uuid document_id FK
        int chunk_index
        text content
        int page_number
        int token_count
        vector embedding "1536-dim"
    }
    CONVERSATIONS {
        uuid id PK
        uuid user_id FK
        uuid document_id FK
        string title
        timestamptz created_at
    }
    MESSAGES {
        uuid id PK
        uuid conversation_id FK
        string role "USER|ASSISTANT"
        text content
        uuid_array cited_chunk_ids
        int tokens_used
        timestamptz created_at
    }
```

📐 **More detail and indexing strategy** in [docs/data-model.md](docs/data-model.md).

---

## 🌐 API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create account |
| `POST` | `/api/auth/login` | Get JWT |
| `POST` | `/api/documents` | Upload document (multipart) |
| `GET`  | `/api/documents` | List user's documents |
| `GET`  | `/api/documents/{id}` | Get document metadata + status |
| `DELETE` | `/api/documents/{id}` | Delete document + chunks |
| `POST` | `/api/conversations` | Start a new conversation |
| `GET`  | `/api/conversations` | List conversations |
| `POST` | `/api/conversations/{id}/ask` | Ask question (blocking) |
| `POST` | `/api/conversations/{id}/ask/stream` | Ask question (SSE stream) |
| `GET`  | `/actuator/health` | Liveness/readiness |
| `GET`  | `/actuator/prometheus` | Metrics |

📐 **Full request/response schemas** in [docs/api-design.md](docs/api-design.md).

---

## 🧠 Design Decisions

> Every non-obvious choice is recorded as an [Architecture Decision Record](docs/adr/).

| ADR | Title | Decision |
|---|---|---|
| [001](docs/adr/001-monolith-over-microservices.md) | Architecture style | Monolith over microservices |
| [002](docs/adr/002-pgvector-over-pinecone.md) | Vector store | pgvector over Pinecone/Weaviate |
| [003](docs/adr/003-spring-ai-abstraction.md) | LLM client | Spring AI for provider abstraction |
| [004](docs/adr/004-virtual-threads-async.md) | Concurrency | Virtual threads over reactive stack |
| [005](docs/adr/005-token-aware-chunking.md) | Chunking | Token-aware with paragraph preservation |
| [006](docs/adr/006-jwt-stateless-auth.md) | Auth | Stateless JWT over sessions |

---

## 🧪 Testing Strategy

```mermaid
graph TB
    subgraph Pyramid["Test Pyramid"]
        direction TB
        E2E[End-to-End<br/>~5%<br/>Full HTTP flow with Testcontainers]
        INT[Integration Tests<br/>~25%<br/>Repos, Services with real Postgres]
        UNIT[Unit Tests<br/>~70%<br/>Pure logic: chunkers, builders, mappers]

        E2E --> INT
        INT --> UNIT
    end

    style E2E fill:#ffcdd2,stroke:#c62828
    style INT fill:#fff9c4,stroke:#f9a825
    style UNIT fill:#c8e6c9,stroke:#2e7d32
```

- **Unit tests** — JUnit 5, Mockito for pure logic (token counters, prompt builders).
- **Integration tests** — Testcontainers spins up real Postgres + pgvector per suite.
- **API tests** — MockMvc for controller layer; SSE streams asserted via `WebTestClient`.
- **LLM tests** — Replayed using recorded fixtures (no live calls in CI).

Target coverage: **≥ 70%**, enforced via Jacoco.

---

## 📊 Observability

Every LLM call is instrumented:

```mermaid
flowchart LR
    REQ[HTTP Request] --> APP[Spring Boot]
    APP --> M1[micrometer.timer<br/>llm.latency]
    APP --> M2[micrometer.counter<br/>llm.tokens.input]
    APP --> M3[micrometer.counter<br/>llm.tokens.output]
    APP --> M4[micrometer.counter<br/>llm.cost.usd]
    M1 & M2 & M3 & M4 --> PROM[Prometheus]
    PROM --> GRAF[Grafana Dashboard]

    style PROM fill:#ffe0b2
    style GRAF fill:#ffccbc
```

A pre-built **Grafana dashboard JSON** lives in [`ops/grafana/`](ops/grafana/).

---

## 🗺 Roadmap

- [x] Phase 1 — Core RAG, JWT auth, async ingestion
- [x] Phase 2 — SSE streaming, multi-provider LLM support
- [ ] Phase 3 — Hybrid search (vector + BM25 with RRF)
- [ ] Phase 4 — Cross-encoder re-ranking
- [ ] Phase 5 — Semantic answer caching
- [ ] Phase 6 — Eval harness with golden Q&A dataset
- [ ] Phase 7 — S3 storage backend + multi-tenant isolation tests

---

## 📚 Documentation Index

- [Architecture](docs/architecture.md) — components, deployment, scaling
- [Data Model](docs/data-model.md) — schema, indexes, query patterns
- [API Design](docs/api-design.md) — endpoints, contracts, errors
- [ADRs](docs/adr/) — every significant decision, explained
- [Development Guide](docs/development.md) — local setup, conventions
- [Operations](docs/operations.md) — deployment, monitoring, runbooks

---

## 📜 License

MIT — see [LICENSE](LICENSE).