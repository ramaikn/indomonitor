CREATE TABLE ingestion_runs (
 id uuid PRIMARY KEY,
 adapter text NOT NULL,
 artifact text NOT NULL,
 started_at timestamptz NOT NULL DEFAULT now(),
 finished_at timestamptz,
 status text NOT NULL CHECK (status IN ('running','success','partial','failed','skipped')),
 fetched integer NOT NULL DEFAULT 0,
 accepted integer NOT NULL DEFAULT 0,
 inserted integer NOT NULL DEFAULT 0,
 updated integer NOT NULL DEFAULT 0,
 duplicates integer NOT NULL DEFAULT 0,
 rejected integer NOT NULL DEFAULT 0,
 out_of_scope integer NOT NULL DEFAULT 0,
 error text,
 parameters jsonb NOT NULL DEFAULT '{}'
);
CREATE TABLE articles (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 url text NOT NULL UNIQUE,
 title text,
 domain text NOT NULL,
 language text,
 scope text NOT NULL CHECK (scope IN ('indonesian_outlet','mentions_indonesia')),
 first_seen_at timestamptz NOT NULL,
 last_seen_at timestamptz NOT NULL,
 collected_at timestamptz NOT NULL DEFAULT now(),
 tone double precision CHECK (tone BETWEEN -100 AND 100),
 themes jsonb NOT NULL DEFAULT '[]',
 locations jsonb NOT NULL DEFAULT '[]',
 content_hash text NOT NULL,
 last_run_id uuid NOT NULL REFERENCES ingestion_runs(id)
);
CREATE INDEX articles_seen_idx ON articles(first_seen_at DESC,id DESC);
CREATE INDEX articles_domain_idx ON articles(domain);
CREATE INDEX articles_themes_idx ON articles USING gin(themes);
CREATE TABLE article_observations (
 article_id bigint NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
 run_id uuid NOT NULL REFERENCES ingestion_runs(id),
 PRIMARY KEY(article_id,run_id)
);
CREATE TABLE checkpoints (
 adapter text NOT NULL,
 artifact text NOT NULL,
 run_id uuid NOT NULL REFERENCES ingestion_runs(id),
 committed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(adapter,artifact)
);
CREATE TABLE quarantines (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 run_id uuid NOT NULL REFERENCES ingestion_runs(id),
 row_number integer NOT NULL,
 reason text NOT NULL
);
CREATE TABLE watchlists (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 name varchar(100) NOT NULL,
 keyword varchar(100) NOT NULL,
 threshold integer NOT NULL CHECK (threshold BETWEEN 1 AND 10000),
 created_at timestamptz NOT NULL DEFAULT now()
);
