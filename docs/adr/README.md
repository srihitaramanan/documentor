# Architecture Decision Records

This directory captures every significant architectural decision in DocuMentor, why it was made, and what was considered as alternatives.

Format follows Michael Nygard's [original ADR template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions): **Status**, **Context**, **Decision**, **Consequences**.

| # | Title | Status |
|---|---|---|
| [001](001-monolith-over-microservices.md) | Monolith over microservices | ✅ Accepted |
| [002](002-pgvector-over-pinecone.md) | pgvector over Pinecone | ✅ Accepted |
| [003](003-spring-ai-abstraction.md) | Spring AI as LLM client | ✅ Accepted |
| [004](004-virtual-threads-async.md) | Virtual threads over reactive | ✅ Accepted |
| [005](005-token-aware-chunking.md) | Token-aware chunking | ✅ Accepted |
| [006](006-jwt-stateless-auth.md) | Stateless JWT auth | ✅ Accepted |

## Why ADRs?

> "Architecture decisions are the things you can't easily change later." — anonymous

Future-me (and interviewers reading this repo) shouldn't have to guess why pgvector and not Pinecone. ADRs make decisions auditable and arguments inheritable.
