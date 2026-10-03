# Needs-to-Solutions Platform (API)

Residents report problems by voice, text or photo. The AI groups similar reports into one case, finds a
playbook that already worked in another community, and proposes who should act. A moderator approves,
doers (city offices, NGOs, volunteers) work through a checklist, and residents confirm it helped. Each
solved case improves the playbook, so the next community starts from a better recipe.

The backend (`backend/`) is Quarkus 3.40 with PostgreSQL and pgvector, plus an AI layer that runs
without any API key (offline mode) or with any OpenAI-compatible model (LLM mode). The browser and
mobile app (`app/`, Ionic React with Capacitor) is in progress. Source code, database names and API
keys are English; everything people read comes back in their language.

## Run it

**Docker (easiest).** Needs Docker only.

```bash
docker compose up --build          # API on http://localhost:8080, demo data loaded
./scripts/demo-flow.sh             # Maria's case end to end (needs curl and jq)
```

Open http://localhost:8080/q/swagger-ui to call any endpoint by hand. Every start reloads the demo
data; `DEMO_RESET=false docker compose up` keeps your changes.

**Dev mode.** Needs JDK 21 and Docker (Quarkus starts PostgreSQL with pgvector for you).

```bash
cd backend
./mvnw quarkus:dev                 # live reload; Dev UI at http://localhost:8080/q/dev-ui
```

**Plain JVM.** Needs JDK 21 and a PostgreSQL 15+ with the `vector` extension available.

```bash
cd backend && ./mvnw package
DB_URL=jdbc:postgresql://localhost:5432/needs DB_USER=postgres DB_PASSWORD=postgres \
  java -jar target/quarkus-app/quarkus-run.jar
```

Flyway creates the schema and loads the demo data on first start. `/q/health/started` turns UP once
the startup AI work (vectors for the seed data, suggestions for waiting cases) is done.

## The demo

Sign in by sending the header `X-Demo-User: <login>` (or `?as=<login>` where headers are impossible,
such as `EventSource` and `<img>`). Add `?lang=pl|en|uk` to any call to switch the reader's language.

| Login | Who | Language |
| --- | --- | --- |
| `maria` | Resident of Riverside; her report is the demo | pl |
| `ewa` | Riverside moderator | en |
| `kasia` | Doer for Good Neighbours Foundation (NGO) | pl |
| `jan` | Doer for Riverside Social Welfare Office | pl |
| `anna`, `tomasz`, `ola` | Residents who volunteer nearby | pl, pl, uk |
| `zofia` | Old Town moderator (Stair Buddies started there) | pl |
| `piotr` | Admin of all four communities | en |
| `neighbour-01` … `neighbour-17` | Residents behind the seeded reports | pl / uk |

Four communities show that one platform fits many shapes: `riverside` (city district, pl/en/uk, the
demo), `old-town` (where the Stair Buddies playbook was proven), `north-campus` (a university) and
`oak-street-coop` (a housing co-op with its own status names and a members-only map).

`scripts/demo-flow.sh` walks through the whole loop:

1. Maria reports in Polish that her neighbour cannot leave a block without a lift (`POST /api/reports`).
2. The AI reads it and finds case R-0412 with 17 similar reports 48 m away; Maria joins it.
3. Ewa's queue already holds the AI's proposal: the Stair Buddies playbook from Old Town, plus an NGO,
   the welfare office and three volunteers who live nearby. She approves.
4. Kasia accepts, ticks her checklist steps and posts what worked, marking the case resolved.
5. Maria answers "Did this help?" with yes. The case closes and the AI drafts Stair Buddies v2 with the
   new step Kasia's team discovered.
6. Ewa publishes v2. Meanwhile every phone and browser received live events.

`PAUSE=1 ./scripts/demo-flow.sh` waits for Enter between steps, for presenting.

## API

All paths start with `/api`; keys are English camelCase, enums travel as codes plus a translated label,
and people's text arrives as `{text, lang, machineTranslated, original}`. Calls that start AI work
return `202 Accepted`, and the result arrives on the event stream. Errors are `{status, error}`.

| Endpoint | Who | Does |
| --- | --- | --- |
| `GET /communities/{slug}` | anyone | Categories, status labels and languages for the UI |
| `GET /map?community=&bbox=&layers=` | anyone (members for private maps) | Public cases with rounded locations, plus initiatives |
| `POST /reports` | signed in | Multipart (`text`, `audio`, `photo`…) or JSON; returns 202 |
| `GET /reports/{id}` | author, moderators | What the AI understood and the closest open case |
| `POST /reports/{id}/submit` | author | `{"caseId": …}` joins that case; no id opens a new one |
| `GET /cases/{id}` | whoever can see the case | Timeline, who is helping, checklist, playbook used |
| `POST /cases/{id}/outcome` | linked residents | `HELPED` confirms the case; `NOT_HELPED` reopens it |
| `GET /stream` | signed in | Server-sent events for everything the caller may see |
| `GET /moderation/queue?community=` | moderators | Waiting cases by urgency then support, with AI status |
| `GET /moderation/cases/{id}` | moderators | Every report, the timeline and the pending suggestion |
| `POST /moderation/suggestions/{id}/approve` | moderators | Optional `playbookVersionId` and `actorIds` override the AI |
| `GET /doer/assignments?status=` | doers | New and active assignments for the caller's organisation |
| `POST /assignments/{id}/accept` (`/decline`, `/redirect`) | the assigned doer | Decline and redirect take `{"note": …}` |
| `PATCH /tasks/{id}` | accepted doers, moderators | `{"done": true}` ticks a checklist step |
| `POST /cases/{id}/updates` | accepted doers, moderators | Text and photos for residents; `resolve=true` resolves |
| `GET /playbooks/{id or slug}` | anyone | Current version, waiting draft, results, history |
| `POST /playbooks/{id}/versions/{n}/publish` | moderators | Makes the draft current; the old version is archived |
| `GET` / `PUT /admin/communities/{slug}/config` | admins | Export or import a community's whole configuration |

Helpers outside the 18 demo endpoints: `GET /api/communities`, `GET /api/me`, `GET /api/media/{id}`,
`/q/health`, `/q/swagger-ui` and `/q/openapi`.

Live event types: `REPORT_PROCESSED`, `SAFETY_ALERT`, `REPORT_ADDED`, `STATUS_CHANGED`,
`MATCH_SUGGESTED`, `MATCH_APPROVED`, `ASSIGNMENT_ACCEPTED`, `ASSIGNMENT_DECLINED`, `TASK_UPDATED`,
`UPDATE_POSTED`, `OUTCOME_RECORDED`, `PLAYBOOK_DRAFTED`, `PLAYBOOK_PUBLISHED`, `CONFIG_CHANGED`, plus
`CONNECTED` and a `HEARTBEAT` every 20 s. Each carries ids and codes; the UI writes the sentence.

## AI modes

| | `AI_MODE=offline` (default) | `AI_MODE=llm` |
| --- | --- | --- |
| Needs | nothing | any OpenAI-compatible API: OpenAI, Ollama, vLLM, LM Studio… |
| Reading a report | keyword rules per category, urgency and injury words | the chat model returns codes, title and summary in every community language |
| Similar cases and playbooks | hashed word, stem and trigram vectors; matches words, not meaning | real embeddings; matches meaning across languages |
| Choosing playbook and doers | similarity, category, distance and service radius | vector search narrows, the chat model picks and explains |
| Translation | none (readers see the original) | people's text is translated and flagged as machine-translated |
| Playbook drafts | copies the steps and adds the doers' latest learning | rewrites the steps from everything the doers reported |

Vector search always narrows the candidates first, so the model only ever chooses among real ids. If a
chat call fails, that step falls back to the offline engine and the demo keeps going. Switching the
embedding model is safe: the app notices at startup and recomputes every stored vector.

```bash
# OpenAI
AI_MODE=llm OPENAI_API_KEY=sk-... docker compose up
# Ollama on this machine (pull a chat model and a multilingual embedding model first)
AI_MODE=llm OPENAI_BASE_URL=http://host.docker.internal:11434/v1/ OPENAI_API_KEY=ollama \
  CHAT_MODEL=qwen2.5:7b EMBEDDING_MODEL=bge-m3 docker compose up
```

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local `needs` database | PostgreSQL with pgvector (production profile) |
| `DEMO_RESET` | `false` (`true` in Docker Compose) | Wipe the database at startup and reload the demo data |
| `AI_MODE` | `offline` | `offline` or `llm` |
| `OPENAI_API_KEY`, `OPENAI_BASE_URL` | none, OpenAI | Any OpenAI-compatible endpoint |
| `CHAT_MODEL`, `EMBEDDING_MODEL` | `gpt-4o-mini`, `text-embedding-3-small` | Model names at that endpoint |
| `MEDIA_DIR` | `data/media` | Where photos and voice notes are stored |
| `PORT` | `8080` | HTTP port (cloud platforms set it) |

Matching thresholds live in `backend/src/main/resources/application.properties` (`app.matching.*`). Each community's categories,
status names, roles and rules (merge radius, map rounding, auto-close days, public map) live in its
configuration JSON, editable through the admin endpoint.

## Deploy to the cloud

The app is one container plus PostgreSQL with pgvector, which most managed offerings provide (for
example Neon, Supabase, AWS RDS, Google Cloud SQL and Azure Database for PostgreSQL). Build with the
`Dockerfile`, set `DB_URL`, `DB_USER` and `DB_PASSWORD`, and point the platform's health check at
`/q/health/ready` (or `/q/health/started` for a startup probe). Photos go to local disk in `MEDIA_DIR`;
mount a volume there, or swap `support/MediaStorage` for S3-compatible storage.

## Code map

```
backend/src/main/java/app/needs/
  model/     entities (Panache), enums, JSON value types such as LocalizedText and CommunityConfig
  ai/        Ai facade, OfflineAi, LlmAi and the langchain4j AI services in ai/llm
  service/   intake, matching, cases, assignments, playbooks, vectors, translations, background jobs
  api/       REST resources, view models (CaseViews), access rules (Access)
  support/   demo login, reader's language, live events, media storage, after-commit hooks
backend/src/main/resources/db/migration/   V1 schema, V2 demo data
scripts/demo-flow.sh                       the scripted demo
```

`cd backend && ./mvnw test` runs the unit tests (offline AI rules, similarity, translated-text flags, status labels,
map rounding); the demo script is the end-to-end test.

## Known limits

- Demo login only: replace `support/CurrentUser` with `quarkus-oidc` before real residents use it.
- The live event hub is in memory, so run one instance (or move it to Redis or Postgres `LISTEN/NOTIFY`).
- Voice notes are stored, not transcribed: the phone sends its own transcript as `text` (for example
  from the Web Speech API).
- The 10 MVP endpoints from the design are not built yet: "me too" support, help offers, my cases,
  rejecting a suggestion, editing and merging cases, playbook search, editing and discarding drafts,
  and the doer directory.
