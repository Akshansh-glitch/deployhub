CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE applications (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    name        VARCHAR(30)  NOT NULL UNIQUE,
    repo_url    VARCHAR(300) NOT NULL,
    branch      VARCHAR(100) NOT NULL,
    app_path    VARCHAR(200) NOT NULL DEFAULT '',
    runtime     VARCHAR(20)  NOT NULL,
    port        INT          NOT NULL,
    health_path VARCHAR(200),
    replicas    INT          NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE deployments (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT       NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    image            VARCHAR(300),
    status           VARCHAR(20)  NOT NULL,
    trigger          VARCHAR(20)  NOT NULL DEFAULT 'BUILD',
    workflow_run_id  BIGINT,
    workflow_run_url VARCHAR(300),
    message          VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);

CREATE INDEX idx_deployments_app ON deployments(application_id, id DESC);
CREATE INDEX idx_deployments_status ON deployments(status);
