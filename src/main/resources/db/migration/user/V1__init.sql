CREATE SCHEMA IF NOT EXISTS "user";

CREATE SEQUENCE "user".users_id_seq AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE "user".users
(
    id                          BIGINT  NOT NULL DEFAULT nextval('"user".users_id_seq'),
    email                       TEXT    NOT NULL,
    password                    TEXT    NOT NULL,
    enabled                     BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired         BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked          BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired     BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts       INT     NOT NULL DEFAULT 0,
    locked_at                   TIMESTAMP WITH TIME ZONE NULL,
    deleted_at                  TIMESTAMP WITH TIME ZONE NULL,
    activation_token            TEXT NULL,
    activation_token_expires_at TIMESTAMP WITH TIME ZONE NULL,
    PRIMARY KEY (id),
    CONSTRAINT users_email_unique UNIQUE (email)
);

ALTER SEQUENCE "user".users_id_seq OWNED BY "user".users.id;

CREATE TABLE "user".user_roles
(
    user_id BIGINT NOT NULL,
    role    TEXT NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT user_roles_user_id_fk FOREIGN KEY (user_id) REFERENCES "user".users (id) ON DELETE CASCADE
);
