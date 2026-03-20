---
title: Email retry tracking with exponential backoff and dead-letter
created: 2026-03-20T17:17:47Z
summary: Track send attempts on queued emails; apply exponential backoff between retries; move exhausted emails to a dead-letter table.
description: >
  EmailSenderJob currently releases failed emails straight back to 'pending' with no
  attempt limit or delay. This plan adds attempt_count and next_retry_at to the emails
  table, computes exponential backoff on each failure, and moves emails that exceed
  maxAttempts into a new emails_dead_letter table.
---

assumptions:
- App is not in production, so schema changes go directly into V1__init.sql (no new migration).
- Backoff formula: nextRetryAt = now + initialBackoffDelay × 2^(attemptCount before increment)
  Example with initialBackoffDelay=PT1M: fail 1→1min, fail 2→2min, fail 3→4min, …
- EmailMonitorJob ALSO increments attempt_count and applies backoff/dead-letter for stale emails.
  Rationale: if SMTP hangs longer than staleTolerance repeatedly, the email would loop forever
  without this. A stale-processing release is treated the same as a confirmed send failure.
  Risk acknowledged: if the JVM crashed after sending but before archiving, the email may be
  sent twice eventually — this is inherent at-least-once delivery risk in the current architecture.
- Dead-letter table lives in the same "email" schema.
- jOOQ sources are generated at build time (target/); no committed generated files to edit.
- Default maxAttempts = 5, default initialBackoffDelay = PT1M.
- Backoff/dead-letter decision logic is extracted to a shared EmailRetryService to avoid
  duplication between EmailSenderJob and EmailMonitorJob.

phases:

- phase_id: 1
  description: "DB migration — add retry columns to emails; create emails_dead_letter (edit V1)"
  tasks:
    - file_changes:
        path: src/main/resources/db/migration/email/V1__init.sql
        action: edit
        stub: false
        details: |
          In the emails table definition, add two columns:
            attempt_count  INT                      NOT NULL DEFAULT 0,
            next_retry_at  TIMESTAMP WITH TIME ZONE NULL,

          Update emails_pending_idx to also filter on next_retry_at:
            CREATE INDEX emails_pending_idx ON "email".emails (created_at, id)
              WHERE status = 'pending'
                AND (next_retry_at IS NULL OR next_retry_at <= NOW());

          Add new table after emails_archive:
            CREATE TABLE "email".emails_dead_letter (
              id                UUID                     NOT NULL,
              "from"            TEXT                     NOT NULL,
              "to"              TEXT                     NOT NULL,
              subject           TEXT                     NOT NULL,
              content           TEXT                     NOT NULL,
              email_template_id UUID                     NOT NULL,
              created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
              attempt_count     INT                      NOT NULL,
              failed_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
              PRIMARY KEY (id),
              CONSTRAINT emails_dead_letter_template_fk
                FOREIGN KEY (email_template_id)
                REFERENCES "email".email_templates (id) ON DELETE CASCADE
            );
    - commands:
        - "./mvnw generate-sources  (regenerates jOOQ sources with new columns)"
    - rollback: "N/A — not in production."

- phase_id: 2
  description: "Config — add maxAttempts and initialBackoffDelay to EmailMonitorProperties"
  tasks:
    - file_changes:
        path: src/main/java/simple/simple_webapp/email/internal/EmailMonitorProperties.java
        action: edit
        stub: false
        details: |
          Add two new record components:
            @DefaultValue("5")    int maxAttempts
            @DefaultValue("PT1M") Duration initialBackoffDelay

- phase_id: 3
  description: "Domain model — add attemptCount to QueuedEmail"
  tasks:
    - file_changes:
        path: src/main/java/simple/simple_webapp/email/QueuedEmail.java
        action: edit
        stub: false
        details: |
          Add component: int attemptCount
          Construction sites to update: only EmailDao.toQueuedEmail().

- phase_id: 4
  description: "DAO — update claimBatch, add claimStaleProcessing, failEmail, moveToDeadLetter"
  tasks:
    - file_changes:
        path: src/main/java/simple/simple_webapp/email/internal/EmailDao.java
        action: edit
        stub: false
        details: |
          claimBatch():
            - Add EMAILS.ATTEMPT_COUNT to SELECT list.
            - Add WHERE filter:
                .and(EMAILS.NEXT_RETRY_AT.isNull()
                     .or(EMAILS.NEXT_RETRY_AT.lessOrEqual(OffsetDateTime.now())))
            - Pass attemptCount to toQueuedEmail().

          Add claimStaleProcessing(OffsetDateTime olderThan) → List<QueuedEmail>:
            - SELECT + FOR UPDATE SKIP LOCKED on emails WHERE status='processing'
              AND processing_since < olderThan.
            - Includes EMAILS.ATTEMPT_COUNT.
            - Does NOT change status (caller decides fate).
            - Returns list of QueuedEmail for caller to process individually.

          Add failEmail(UUID id, int newAttemptCount, OffsetDateTime nextRetryAt):
            dsl.update(EMAILS)
               .set(EMAILS.STATUS, "pending")
               .set(EMAILS.ATTEMPT_COUNT, newAttemptCount)
               .set(EMAILS.NEXT_RETRY_AT, nextRetryAt)
               .setNull(EMAILS.PROCESSING_SINCE)
               .where(EMAILS.ID.eq(id))
               .execute();

          Add moveToDeadLetter(QueuedEmail email, int finalAttemptCount):
            dsl.insertInto(EMAILS_DEAD_LETTER) ...
            dsl.deleteFrom(EMAILS).where(EMAILS.ID.eq(email.id())).execute();

          toQueuedEmail(): read EMAILS.ATTEMPT_COUNT.

          Remove releaseEmail() — no longer used.
          Remove releaseStaleEmails() — replaced by claimStaleProcessing + EmailRetryService.

- phase_id: 5
  description: "Extract shared retry logic + wire into both jobs"
  tasks:
    - file_changes:
        path: src/main/java/simple/simple_webapp/email/internal/EmailRetryService.java
        action: add
        stub: false
        details: |
          @Component package-private class.
          Constructor: EmailDao, int maxAttempts, Duration initialBackoffDelay (from properties).

          void handleFailure(QueuedEmail email):
            int newCount = email.attemptCount() + 1;
            if (newCount >= maxAttempts) {
                log.warn("Email {} exhausted {} attempts, moving to dead-letter", email.id(), maxAttempts);
                emailDao.moveToDeadLetter(email, newCount);
            } else {
                long delaySecs = initialBackoffDelay.toSeconds() * (1L << email.attemptCount()); // 2^n
                var nextRetry = OffsetDateTime.now().plusSeconds(delaySecs);
                log.warn("Email {} failed (attempt {}/{}), retry at {}", email.id(), newCount, maxAttempts, nextRetry);
                emailDao.failEmail(email.id(), newCount, nextRetry);
            }

    - file_changes:
        path: src/main/java/simple/simple_webapp/email/internal/EmailSenderJob.java
        action: edit
        stub: false
        details: |
          Constructor: replace EmailMonitorProperties with direct EmailRetryService injection
          (remove maxAttempts/initialBackoffDelay from this class).

          Catch block: replace releaseEmail call with emailRetryService.handleFailure(email).

    - file_changes:
        path: src/main/java/simple/simple_webapp/email/internal/EmailMonitorJob.java
        action: edit
        stub: false
        details: |
          Constructor: inject EmailRetryService in addition to EmailDao.

          run():
            List<QueuedEmail> stale = emailDao.claimStaleProcessing(cutoff);
            if (!stale.isEmpty()) {
                log.warn("Found {} stale processing email(s), applying retry logic", stale.size());
                for (var email : stale) {
                    emailRetryService.handleFailure(email);
                }
            }
          Remove releaseStaleEmails call.

- phase_id: 6
  description: "Tests — update existing and add new cases for both jobs"
  tasks:
    - file_changes:
        path: src/test/java/simple/simple_webapp/email/internal/EmailManagementTests.java
        action: edit
        stub: false
        details: |
          Update senderJobLeavesEmailPendingWhenSendFails():
            - Assert attempt_count = 1.
            - Assert next_retry_at is NOT NULL and is in the future.

          Add senderJobIncrementsAttemptCountAndBacksOff():
            - Queue email, set attempt_count=2 in DB directly.
            - mailSender throws MailSendException.
            - Run emailSenderJob.run().
            - Assert attempt_count=3, next_retry_at ≈ now + 4 min (2^2 × 1min).

          Add senderJobMovesEmailToDeadLetterAfterMaxAttempts():
            - Queue email, set attempt_count=4 in DB.
            - mailSender throws MailSendException.
            - Run emailSenderJob.run().
            - Assert EMAILS count = 0, EMAILS_DEAD_LETTER count = 1 with attempt_count=5.

          Update monitorJobReleasesStaleProcessingEmails():
            - After emailMonitorJob.run(): assert attempt_count=1, next_retry_at is set.
            (was asserting status='pending' with null processing_since — keep those too)

          Add monitorJobMovesStaleEmailToDeadLetterAfterMaxAttempts():
            - Queue email, set status='processing', processing_since=1 hour ago, attempt_count=4.
            - Run emailMonitorJob.run().
            - Assert EMAILS count = 0, EMAILS_DEAD_LETTER count = 1.

          Add EMAILS_DEAD_LETTER import and @AfterEach cleanup for dead-letter table.

          Note: Tests use default config values (maxAttempts=5, initialBackoffDelay=PT1M).

open_questions: []

status: draft

change_log:
- timestamp: 2026-03-20T17:17:47Z
  changes: "Initial draft"
- timestamp: 2026-03-20T17:30:00Z
  changes: "Removed V2 migration — edit V1 directly (app not in production)"
- timestamp: 2026-03-20T17:26:14Z
  changes: >
    Fixed stale-processing gap: EmailMonitorJob now also applies backoff/dead-letter via
    EmailRetryService. Added claimStaleProcessing() to DAO. Extracted shared handleFailure()
    logic into new EmailRetryService component. Removed releaseEmail() and releaseStaleEmails()
    from DAO. Added monitor-job-specific tests.
