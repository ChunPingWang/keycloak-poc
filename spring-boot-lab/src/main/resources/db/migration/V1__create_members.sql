CREATE TABLE members (
    id           UUID PRIMARY KEY,
    identity_id  VARCHAR(64)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    name         VARCHAR(50)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    tier         VARCHAR(20)  NOT NULL,
    enrolled_at  TIMESTAMPTZ  NOT NULL
);
