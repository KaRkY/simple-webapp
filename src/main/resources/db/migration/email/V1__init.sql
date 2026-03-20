CREATE SCHEMA IF NOT EXISTS "email";

CREATE TABLE "email".email_templates
(
    id       UUID NOT NULL,
    name     TEXT NOT NULL,
    subject  TEXT NOT NULL,
    type     TEXT NOT NULL CHECK (type IN ('text', 'html')),
    template TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT email_templates_name_unique UNIQUE (name)
);

CREATE TABLE "email".emails
(
    id                UUID                     NOT NULL,
    "from"            TEXT                     NOT NULL,
    "to"              TEXT                     NOT NULL,
    subject           TEXT                     NOT NULL,
    content           TEXT                     NOT NULL,
    email_template_id UUID                     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status            TEXT                     NOT NULL DEFAULT 'pending'
        CONSTRAINT emails_status_check CHECK (status IN ('pending', 'processing')),
    processing_since  TIMESTAMP WITH TIME ZONE NULL,
    PRIMARY KEY (id),
    CONSTRAINT emails_email_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);

CREATE INDEX emails_pending_idx ON "email".emails (created_at, id)
    WHERE status = 'pending';

CREATE TABLE "email".emails_archive
(
    id                UUID                     NOT NULL,
    "from"            TEXT                     NOT NULL,
    "to"              TEXT                     NOT NULL,
    subject           TEXT                     NOT NULL,
    content           TEXT                     NOT NULL,
    sent_at           TIMESTAMP WITH TIME ZONE NULL,
    email_template_id UUID                     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT emails_archive_email_template_fk FOREIGN KEY (email_template_id) REFERENCES "email".email_templates (id) ON DELETE CASCADE
);

INSERT INTO "email".email_templates (id, name, subject, type, template)
VALUES (gen_random_uuid(),
        'user-registered',
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
