# GitHub AI Code Assistant

Sign in with GitHub, index a repository, and ask questions about it in plain
English. Answers are generated with a free/local LLM, grounded in your
actual source code via retrieval-augmented generation (RAG), and streamed
back token-by-token with the specific files/lines they came from.

This is a portfolio/interview project: **the priority is that every piece is
simple enough to explain confidently**, not that it scales to production.

---

## Features

- GitHub OAuth2 login (Spring Security)
- Repository dashboard with per-repo indexing status
- Background indexing pipeline: fetch → filter → chunk → embed → store
- Semantic code search via PostgreSQL + pgvector (cosine similarity)
- RAG-based Q&A with source file/line citations
- Real token-level streaming over Server-Sent Events (SSE)
- Per-user authorization enforced on every repository-scoped endpoint
- Pluggable LLM/embedding provider (Ollama by default, Hugging Face as an
  alternative) behind two small interfaces

## Architecture

```
                 NEXT.JS (React, TS, Tailwind)
                          │
                    REST / SSE (cookies)
                          │
                          ▼
                   SPRING BOOT (single app)
                          │
        ┌─────────────────┼───────────────────┐
        │                 │                    │
        ▼                 ▼                    ▼
     GitHub           Indexing              Chat / RAG
   (OAuth2 + API)    (@Async job)         (retrieval + LLM)
        │                 │                    │
        │                 ▼                    │
        │             Chunking                 │
        │                 │                    │
        │                 ▼                    │
        │            Embeddings                │
        │                 │                    │
        │                 ▼                    │
        └───────────► PostgreSQL ◄──────────────┘
                         + pgvector
```

### RAG pipeline

```
Question → Question embedding → pgvector similarity search (top 5)
        → Prompt (context + question) → LLM (streamed) → SSE → Frontend
```

### Indexing pipeline

```
GitHub repo → fetch file tree → filter (extension/path) → read contents
            → line-based chunking (100 lines, 20 overlap)
            → generate embeddings → store chunks in pgvector
            → status = COMPLETED (or FAILED with an error message)
```

## Tech stack

**Backend:** Java 21, Spring Boot 3, Spring Security + OAuth2 Client, Spring
Data JPA/Hibernate, PostgreSQL + pgvector, Flyway, Maven, WebFlux's
`WebClient`/`Flux` (for calling GitHub/Ollama and streaming SSE — the rest
of the app is a conventional Spring MVC app, not a full reactive stack).

**Frontend:** Next.js (App Router), React, TypeScript, Tailwind CSS,
TanStack React Query, `react-markdown` + `react-syntax-highlighter`.

## Project structure

```
backend/
  src/main/java/com/example/codeassistant/
    config/       WebClient + async thread pool config
    security/     GitHub OAuth2 login, session → local User resolution
    user/         User entity + /api/auth/me
    github/       GitHubClient (raw HTTP), GitHubService (domain layer)
    repository/   RepositoryEntity, ownership checks, dashboard endpoints
    indexing/     FileFilter, CodeChunker, IndexingService/Worker
    embedding/    EmbeddingService interface + Ollama/HuggingFace impls
    vector/       CodeChunk entity + native pgvector similarity query
    rag/          PromptBuilder + RagService
    chat/         LLMService interface + impls, ChatService (SSE), entities
    common/       Global exception handling, shared error types
  src/main/resources/db/migration/   Flyway migrations
  src/test/                          Unit tests
frontend/
  app/login, app/dashboard, app/chat/[repoId]
  lib/            API client, SSE stream consumer, React Query hooks
  components/     Shared UI (top bar, status badge)
docker-compose.yml
.env.example
```

## Database schema

Four tables only — see `backend/src/main/resources/db/migration/V1__init_schema.sql`.

- **users** — githubId, username, email, avatarUrl
- **repositories** — one row per repo a user has indexed or is indexing;
  `indexing_status` is `NOT_INDEXED | INDEXING | COMPLETED | FAILED`
- **code_chunks** — repositoryId, filePath, content, startLine, endLine,
  `embedding vector(768)` (pgvector column, cosine-similarity indexed)
- **chat_messages** — userId, repositoryId, role (`USER`/`ASSISTANT`),
  content, sources

## GitHub OAuth flow

```
User clicks "Continue with GitHub"
  → redirected to GitHub for authorization
  → GitHub redirects back to Spring Security's callback
  → GitHubOAuth2UserService loads the profile and upserts a local User row
  → session cookie is set; browser is redirected to the Next.js dashboard
```

The GitHub access token is held by Spring's `OAuth2AuthorizedClientService`
and is **never** sent to the frontend. It's resolved server-side whenever we
need to call the GitHub API (listing repos, reading files), and captured
explicitly before handing work to a background thread, since Spring's
security context doesn't propagate to `@Async` worker threads automatically.

## Indexing flow

1. `POST /api/repositories/{githubRepositoryId}/index`
2. Backend validates the repo belongs to the caller (or creates the local
   tracking row from the caller's own GitHub repo list)
3. Status flips to `INDEXING` immediately; the request returns
4. A background job (Spring `ThreadPoolTaskExecutor`, no external
   queue/broker) fetches the file tree, filters files, chunks them,
   generates embeddings, and replaces the repository's chunks
5. Status flips to `COMPLETED`, or `FAILED` with a stored error message

Re-indexing just deletes the repo's existing chunks and repeats the pipeline
— no incremental indexing or webhooks in this version.

## Chat flow

1. Frontend `POST`s a question to `/api/repositories/{id}/chat`
2. `RagService` embeds the question, retrieves the top 5 most similar chunks
   via pgvector cosine distance, and builds a prompt instructing the model
   not to invent behavior and to cite file/line ranges
3. The LLM's response streams back as SSE frames (`{"type":"token",...}`)
4. A final `{"type":"done","sources":[...]}` frame carries the retrieved
   source references
5. Both the question and the full answer are persisted to `chat_messages`

A small amount of recent history (last few messages) is included in the
prompt. There's no conversation summarization, long-term memory, or
multi-conversation management — deliberately, per the project's scope.

## SSE streaming

`ChatController#chat` returns a `reactor.core.publisher.Flux<String>` with
`produces = text/event-stream`. With Ollama as the provider, this is a real
token stream all the way from the model — no artificial splitting of an
already-complete answer. (The Hugging Face fallback doesn't support true
token streaming on its free inference API, so it emits the finished answer
split into word-sized SSE events — still genuine incremental delivery over
the wire, just not token-level from the model itself. See
`HuggingFaceLLMService`.)

## Why no Kafka/RabbitMQ/Redis/CrewAI

Indexing is I/O-bound and bounded by a handful of files per repo — a small
in-process `ThreadPoolTaskExecutor` is enough to keep the HTTP request fast
without introducing an external broker to run, monitor, and explain. This
project is a RAG pipeline, not an agent system, so there's no CrewAI/agent
framework — retrieval and generation are explicit, inspectable steps.

## Security

Every repository-scoped endpoint resolves the repository through
`RepositoryService`, which only ever looks up rows scoped to
`(user_id, repository_id)` (or, for not-yet-tracked repos, cross-checks
against the caller's *own* GitHub repo list before creating the row). A
repository that exists but belongs to someone else returns `404`, not `403`,
so we don't confirm its existence to an unauthorized caller. GitHub access
tokens are never logged or sent to the client.

## Setup

### Prerequisites

- Java 21, Maven
- Node 20+
- Docker (for Postgres + pgvector)
- [Ollama](https://ollama.com) running locally (default provider), with:
  ```
  ollama pull llama3.1
  ollama pull nomic-embed-text
  ```
- A GitHub OAuth App: https://github.com/settings/developers
  - Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`

### Environment variables

Copy `.env.example` to `.env` and fill in `GITHUB_CLIENT_ID` /
`GITHUB_CLIENT_SECRET`. See that file for the full list and what each
variable does.

### Running locally (without Docker for the app itself)

```bash
# 1. Start Postgres + pgvector
docker compose up -d postgres

# 2. Start Ollama and pull the models (see Prerequisites)

# 3. Backend
cd backend
export $(grep -v '^#' ../.env | xargs)   # or set env vars another way
mvn spring-boot:run

# 4. Frontend
cd frontend
npm install
npm run dev
```

Visit `http://localhost:3000`.

### Running everything with Docker

```bash
cp .env.example .env   # fill in GitHub credentials
docker compose up --build
```

By default the backend container talks to Ollama on the Docker host via
`OLLAMA_URL=http://host.docker.internal:11434` — adjust if Ollama runs
elsewhere.

### Changing the embedding model

The `code_chunks.embedding` column is created as `vector(768)` in the Flyway
migration (matching `nomic-embed-text`'s output size). If you switch to a
model with a different embedding dimension, update both `EMBEDDING_DIMENSION`
and the `vector(768)` dimension in
`V1__init_schema.sql` before the first run, then re-index your repositories.

## Testing

```bash
cd backend
mvn test
```

Covers: `CodeChunker` (line-splitting math), `FileFilter` (ignore rules),
`PromptBuilder` (prompt shape), repository ownership checks, and chat
message/source serialization.

## Known limitations

- Line-based chunking only — no AST awareness, so a chunk can split a
  function in half
- No incremental indexing or GitHub webhooks — re-indexing always
  reprocesses the whole repo
- Retrieval is pure vector similarity — no hybrid/BM25 search or reranking
- Conversation memory is "last few messages," not summarized or vectorized
- Hugging Face's free inference API doesn't support real token streaming
  (see "SSE streaming" above)
- Single-instance in-process thread pool for indexing — not horizontally
  scalable without moving to an external queue

## Future improvements

- AST-aware chunking (split on function/class boundaries)
- Incremental indexing via GitHub webhooks
- Hybrid search (vector + BM25) and reranking
- Redis caching for hot queries
- Usage tracking and production-grade observability
- CI/CD pipeline
