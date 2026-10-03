-- Needs-to-Solutions Platform: schema.
-- Every name is English. Translatable text is jsonb {"source": "pl", "values": {"pl": "...", "en": "..."}}.
-- Embeddings are vector(1024); app.needs.model.Embeddings.DIMENSIONS must match.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE community (
    id              uuid PRIMARY KEY,
    slug            text        NOT NULL UNIQUE,
    kind            text        NOT NULL,
    name            jsonb       NOT NULL,
    default_locale  text        NOT NULL,
    locales         text[]      NOT NULL DEFAULT '{}',
    config          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    auth_subject  text        NOT NULL UNIQUE,
    display_name  text        NOT NULL,
    email         text,
    phone         text,
    locale        text        NOT NULL DEFAULT 'en',
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE actor (
    id                uuid PRIMARY KEY,
    community_id      uuid        NOT NULL REFERENCES community (id),
    kind              text        NOT NULL,
    name              text        NOT NULL,
    description       jsonb,
    capabilities      text[]      NOT NULL DEFAULT '{}',
    service_lat       double precision,
    service_lng       double precision,
    service_radius_m  integer,
    user_id           uuid REFERENCES app_user (id),
    contact_email     text,
    embedding         vector(1024),
    active            boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE membership (
    id            uuid PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES app_user (id),
    community_id  uuid        NOT NULL REFERENCES community (id),
    role          text        NOT NULL,
    actor_id      uuid REFERENCES actor (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, community_id, role)
);

CREATE TABLE initiative (
    id              uuid PRIMARY KEY,
    community_id    uuid        NOT NULL REFERENCES community (id),
    actor_id        uuid REFERENCES actor (id),
    title           jsonb       NOT NULL,
    description     jsonb,
    schedule        jsonb,
    category_codes  text[]      NOT NULL DEFAULT '{}',
    lat             double precision,
    lng             double precision,
    active          boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE playbook (
    id                   uuid PRIMARY KEY,
    origin_community_id  uuid REFERENCES community (id),
    slug                 text        NOT NULL UNIQUE,
    title                jsonb       NOT NULL,
    problem              jsonb,
    category_codes       text[]      NOT NULL DEFAULT '{}',
    status               text        NOT NULL,
    shared               boolean     NOT NULL DEFAULT true,
    current_version_id   uuid,
    embedding            vector(1024),
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE playbook_version (
    id               uuid PRIMARY KEY,
    playbook_id      uuid        NOT NULL REFERENCES playbook (id),
    number           integer     NOT NULL,
    steps            jsonb       NOT NULL DEFAULT '[]'::jsonb,
    effort           jsonb,
    change_notes     jsonb,
    source_case_ids  uuid[]      NOT NULL DEFAULT '{}',
    drafted_by       text        NOT NULL,
    status           text        NOT NULL,
    published_by     uuid REFERENCES app_user (id),
    published_at     timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (playbook_id, number)
);

ALTER TABLE playbook
    ADD CONSTRAINT fk_playbook_current_version FOREIGN KEY (current_version_id) REFERENCES playbook_version (id);

CREATE TABLE case_file (
    id                   uuid PRIMARY KEY,
    community_id         uuid        NOT NULL REFERENCES community (id),
    number               integer     NOT NULL,
    kind                 text        NOT NULL,
    title                jsonb       NOT NULL,
    summary              jsonb,
    category_code        text,
    urgency              text        NOT NULL DEFAULT 'MEDIUM',
    status               text        NOT NULL DEFAULT 'NEW',
    visibility           text        NOT NULL DEFAULT 'PUBLIC',
    lat                  double precision,
    lng                  double precision,
    area_label           text,
    report_count         integer     NOT NULL DEFAULT 0,
    supporter_count      integer     NOT NULL DEFAULT 0,
    playbook_version_id  uuid REFERENCES playbook_version (id),
    embedding            vector(1024),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    resolved_at          timestamptz,
    closed_at            timestamptz,
    version              bigint      NOT NULL DEFAULT 0,
    UNIQUE (community_id, number)
);

CREATE TABLE report (
    id             uuid PRIMARY KEY,
    community_id   uuid        NOT NULL REFERENCES community (id),
    author_id      uuid REFERENCES app_user (id),
    case_id        uuid REFERENCES case_file (id),
    kind           text        NOT NULL,
    input_mode     text        NOT NULL DEFAULT 'TEXT',
    status         text        NOT NULL,
    body           jsonb       NOT NULL,
    lat            double precision,
    lng            double precision,
    address_label  text,
    ai_result      jsonb,
    anonymous      boolean     NOT NULL DEFAULT false,
    embedding      vector(1024),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE case_participant (
    id           uuid PRIMARY KEY,
    case_id      uuid        NOT NULL REFERENCES case_file (id),
    user_id      uuid        NOT NULL REFERENCES app_user (id),
    role         text        NOT NULL,
    notify       boolean     NOT NULL DEFAULT true,
    outcome      text,
    outcome_at   timestamptz,
    joined_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (case_id, user_id)
);

CREATE TABLE case_event (
    id              uuid PRIMARY KEY,
    case_id         uuid        NOT NULL REFERENCES case_file (id),
    type            text        NOT NULL,
    author_user_id  uuid REFERENCES app_user (id),
    actor_id        uuid REFERENCES actor (id),
    visibility      text        NOT NULL DEFAULT 'PUBLIC',
    body            jsonb,
    data            jsonb,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE media_asset (
    id             uuid PRIMARY KEY,
    community_id   uuid        NOT NULL REFERENCES community (id),
    report_id      uuid REFERENCES report (id),
    case_event_id  uuid REFERENCES case_event (id),
    kind           text        NOT NULL,
    storage_key    text        NOT NULL,
    mime_type      text        NOT NULL,
    size_bytes     bigint      NOT NULL,
    duration_ms    integer,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CHECK (report_id IS NOT NULL OR case_event_id IS NOT NULL)
);

CREATE TABLE match_suggestion (
    id                   uuid PRIMARY KEY,
    case_id              uuid        NOT NULL REFERENCES case_file (id),
    playbook_version_id  uuid REFERENCES playbook_version (id),
    reason               jsonb,
    status               text        NOT NULL,
    model                text,
    scores               jsonb,
    decided_by           uuid REFERENCES app_user (id),
    decided_at           timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE match_candidate (
    id             uuid PRIMARY KEY,
    suggestion_id  uuid        NOT NULL REFERENCES match_suggestion (id) ON DELETE CASCADE,
    actor_id       uuid        NOT NULL REFERENCES actor (id),
    reason         jsonb,
    score          double precision,
    selected       boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE case_assignment (
    id            uuid PRIMARY KEY,
    case_id       uuid        NOT NULL REFERENCES case_file (id),
    actor_id      uuid        NOT NULL REFERENCES actor (id),
    status        text        NOT NULL,
    assigned_by   uuid REFERENCES app_user (id),
    responded_at  timestamptz,
    note          jsonb,
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (case_id, actor_id)
);

CREATE TABLE case_task (
    id             uuid PRIMARY KEY,
    case_id        uuid        NOT NULL REFERENCES case_file (id),
    assignment_id  uuid REFERENCES case_assignment (id),
    position       integer     NOT NULL,
    title          jsonb       NOT NULL,
    done_at        timestamptz,
    done_by        uuid REFERENCES app_user (id),
    created_at     timestamptz NOT NULL DEFAULT now()
);

-- Not one of the 18 model tables: remembers which embedding model filled the vector columns.
CREATE TABLE app_setting (
    key    text PRIMARY KEY,
    value  text NOT NULL
);

CREATE INDEX idx_membership_user ON membership (user_id);
CREATE INDEX idx_actor_community ON actor (community_id);
CREATE INDEX idx_case_file_queue ON case_file (community_id, status);
CREATE INDEX idx_report_case ON report (case_id);
CREATE INDEX idx_case_event_case ON case_event (case_id, created_at);
CREATE INDEX idx_case_participant_user ON case_participant (user_id);
CREATE INDEX idx_case_assignment_actor ON case_assignment (actor_id, status);
CREATE INDEX idx_match_suggestion_case ON match_suggestion (case_id, status);

-- Cosine-distance indexes for similarity search (pgvector indexes up to 2,000 dimensions).
CREATE INDEX idx_case_file_embedding ON case_file USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_playbook_embedding ON playbook USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_actor_embedding ON actor USING hnsw (embedding vector_cosine_ops);
