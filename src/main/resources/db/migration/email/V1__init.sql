CREATE SCHEMA IF NOT EXISTS "email";

CREATE TABLE "email".email_templates
(
    id       UUID NOT NULL,
    template TEXT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE "email".emails
(
    id                UUID    NOT NULL,
    "from"            TEXT    NOT NULL,
    "to"              TEXT    NOT NULL,
    subject           BOOLEAN NOT NULL DEFAULT TRUE,
    content           BOOLEAN NOT NULL DEFAULT TRUE,
    email_template_id UUID    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT emails_email_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);

CREATE TABLE "email".emails_archive
(
    id                UUID    NOT NULL,
    "from"            TEXT    NOT NULL,
    "to"              TEXT    NOT NULL,
    subject           BOOLEAN NOT NULL DEFAULT TRUE,
    content           BOOLEAN NOT NULL DEFAULT TRUE,
    sent_at           TIMESTAMP WITH TIME ZONE NULL,
    email_template_id UUID    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT emails_email_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);
