CREATE SCHEMA IF NOT EXISTS "email";

CREATE SEQUENCE "email".email_templates_id_seq AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE "email".emails_id_seq          AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE "email".email_templates
(
    id       BIGINT NOT NULL DEFAULT nextval('"email".email_templates_id_seq'),
    name     TEXT NOT NULL,
    subject  TEXT NOT NULL,
    type     TEXT NOT NULL CHECK (type IN ('text', 'html')),
    template TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT email_templates_name_unique UNIQUE (name)
);

ALTER SEQUENCE "email".email_templates_id_seq OWNED BY "email".email_templates.id;

CREATE TABLE "email".emails
(
    id                BIGINT                   NOT NULL DEFAULT nextval('"email".emails_id_seq'),
    "from"            TEXT                     NOT NULL,
    "to"              TEXT                     NOT NULL,
    subject           TEXT                     NOT NULL,
    content           TEXT                     NOT NULL,
    email_template_id BIGINT                   NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status            TEXT                     NOT NULL DEFAULT 'pending'
        CONSTRAINT emails_status_check CHECK (status IN ('pending', 'processing')),
    processing_since  TIMESTAMP WITH TIME ZONE NULL,
    attempt_count     INT                      NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMP WITH TIME ZONE NULL,
    PRIMARY KEY (id),
    CONSTRAINT emails_email_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);

ALTER SEQUENCE "email".emails_id_seq OWNED BY "email".emails.id;

CREATE INDEX emails_pending_idx ON "email".emails (created_at, id)
    WHERE status = 'pending';

CREATE TABLE "email".emails_archive
(
    id                BIGINT                   NOT NULL,
    "from"            TEXT                     NOT NULL,
    "to"              TEXT                     NOT NULL,
    subject           TEXT                     NOT NULL,
    content           TEXT                     NOT NULL,
    sent_at           TIMESTAMP WITH TIME ZONE NULL,
    email_template_id BIGINT                   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT emails_archive_email_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);

CREATE TABLE "email".emails_dead_letter
(
    id                BIGINT                   NOT NULL,
    "from"            TEXT                     NOT NULL,
    "to"              TEXT                     NOT NULL,
    subject           TEXT                     NOT NULL,
    content           TEXT                     NOT NULL,
    email_template_id BIGINT                   NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count     INT                      NOT NULL,
    failed_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT emails_dead_letter_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);

INSERT INTO "email".email_templates (name, subject, type, template)
VALUES ('user-registered',
        'Activate your account',
        'html',
        '<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<p>Hello,</p>
<p>Thank you for registering. Please activate your account by clicking the link below:</p>
<p><a th:href="${activationUrl}">Activate account</a></p>
<p>If you did not register, please ignore this email.</p>
</body>
</html>');
