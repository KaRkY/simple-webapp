---
title: EmailSenderJob — batched SELECT FOR UPDATE SKIP LOCKED + monitor job
created: 2026-03-20T01:32:12Z
context: email-sender-job-batch-select-for-update
description: |
  Add status/processing_since columns directly to V1 migration. Refactor EmailSenderJob
  to claim emails atomically in batches (SELECT FOR UPDATE SKIP LOCKED + UPDATE status).
  Send outside the claim tx. Archive on success; release back to pending on failure.
  Add EmailMonitorJob that resets stale 'processing' rows back to 'pending' (crash recovery).
---

assumptions:
- PostgreSQL (SKIP LOCKED supported).
- jOOQ for all queries; sources are regenerated via generate-sources.
- Schema update goes into the existing V1 migration (not yet applied in prod).
- Stale tolerance default: PT5M (5 min). Covers crash recovery.
- On explicit send failure: immediately release back to 'pending'.
  Monitor job handles crash/hung-instance recovery only.

phases:

- phase: 1 — Update V1 schema migration
  file: src/main/resources/db/migration/email/V1__init.sql
  changes:
    - Add two columns to the emails table definition:
        status           TEXT         NOT NULL DEFAULT 'pending'
                         CHECK (status IN ('pending', 'processing')),
        processing_since TIMESTAMPTZ  NULL
    - Add index after the table definition:
        CREATE INDEX emails_pending_idx ON "email".emails (created_at, id)
            WHERE status = 'pending';
  note: |
    V1 is edited in-place (not yet applied to prod). Run `./mvnw flyway:repair`
    in any dev environment that already applied the old V1 checksum.

- phase: 2 — Regenerate jOOQ sources
  command: ./mvnw generate-sources -pl . -am
  note: Adds EMAILS.STATUS and EMAILS.PROCESSING_SINCE to the generated jOOQ record.

- phase: 3 — EmailDao: replace findPendingEmails, add claim/release methods
  file: src/main/java/simple/simple_webapp/email/internal/EmailDao.java
  changes:
    - Remove `findPendingEmails()`.
    - Add `claimBatch(int batchSize)` — must be called within a @Transactional context:
        Step A: SELECT full row (JOIN email_templates) WHERE status='pending'
                ORDER BY created_at, id LIMIT :batchSize FOR UPDATE SKIP LOCKED.
        Step B: UPDATE emails SET status='processing', processing_since=NOW()
                WHERE id IN (ids from Step A).
        Returns List<QueuedEmail>.
      jooq snippet: |
        var rows = dsl.select(EMAILS.ID, EMAILS.FROM, EMAILS.TO, EMAILS.SUBJECT,
                               EMAILS.CONTENT, EMAILS.EMAIL_TEMPLATE_ID,
                               EMAIL_TEMPLATES.TYPE, EMAILS.CREATED_AT)
                      .from(EMAILS)
                      .join(EMAIL_TEMPLATES).on(EMAILS.EMAIL_TEMPLATE_ID.eq(EMAIL_TEMPLATES.ID))
                      .where(EMAILS.STATUS.eq("pending"))
                      .orderBy(EMAILS.CREATED_AT.asc(), EMAILS.ID.asc())
                      .limit(batchSize)
                      .forUpdate().skipLocked()
                      .fetch(this::toQueuedEmail);
        var ids = rows.stream().map(QueuedEmail::id).toList();
        if (!ids.isEmpty()) {
            dsl.update(EMAILS)
               .set(EMAILS.STATUS, "processing")
               .set(EMAILS.PROCESSING_SINCE, OffsetDateTime.now())
               .where(EMAILS.ID.in(ids))
               .execute();
        }
        return rows;

    - Add `releaseEmail(UUID id)`:
        UPDATE emails SET status='pending', processing_since=NULL WHERE id=:id.

    - Add `releaseStaleEmails(OffsetDateTime olderThan)` → int (rows updated):
        UPDATE emails SET status='pending', processing_since=NULL
        WHERE status='processing' AND processing_since < :olderThan.

- phase: 4 — Refactor EmailSenderJob
  file: src/main/java/simple/simple_webapp/email/internal/EmailSenderJob.java
  changes:
    - Inject: `@Value("${email-monitor.batch-size:10}") int batchSize`.
    - Annotate `run()` with `@Transactional`:
        Wraps the claim (SELECT FOR UPDATE SKIP LOCKED + UPDATE status) in one short tx.
        TX commits before any SMTP call — no long-held DB connections.
    - Replace `emailDao.findPendingEmails()` → `emailDao.claimBatch(batchSize)`.
    - On send success: `emailDao.archiveEmail(email, OffsetDateTime.now())` (unchanged, runs outside claim tx).
    - On send failure (catch): call `emailDao.releaseEmail(email.id())`, then log.
  note: archiveEmail (INSERT archive + DELETE) runs in its own implicit tx per call.

- phase: 5 — Add EmailMonitorJob
  file: src/main/java/simple/simple_webapp/email/internal/EmailMonitorJob.java
  content skeleton: |
    @Component
    class EmailMonitorJob {
        private static final Logger log = LoggerFactory.getLogger(EmailMonitorJob.class);
        private final EmailDao emailDao;
        private final Duration staleTolerance;

        EmailMonitorJob(
                EmailDao emailDao,
                @Value("${email-monitor.stale-tolerance:PT5M}") Duration staleTolerance) {
            this.emailDao = emailDao;
            this.staleTolerance = staleTolerance;
        }

        @Scheduled(cron = "${email-monitor.monitor-cron:0 * * * * *}")
        void run() {
            var cutoff = OffsetDateTime.now().minus(staleTolerance);
            int released = emailDao.releaseStaleEmails(cutoff);
            if (released > 0) {
                log.warn("Released {} stale processing email(s) back to pending", released);
            }
        }
    }
  config properties:
    - email-monitor.batch-size (int, default 10)
    - email-monitor.stale-tolerance (ISO-8601 duration, default PT5M)
    - email-monitor.monitor-cron (cron, default every minute: 0 * * * * *)
    - email-monitor.cron (existing sender job cron, unchanged default)

- phase: 6 — Tests
  file: src/test/java/simple/simple_webapp/email/internal/EmailManagementTests.java
  changes:
    - Autowire EmailMonitorJob.
    - Add `senderJobRespectsConfiguredBatchSize`:
        - Insert batchSize+1 emails.
        - emailSenderJob.run().
        - Assert batchSize in archive, 1 still pending with status='pending'.
    - Add `monitorJobReleasesStaleProcessingEmails`:
        - Insert email, manually UPDATE status='processing',
          processing_since=OffsetDateTime.now().minusHours(1).
        - emailMonitorJob.run().
        - Assert status='pending', processing_since=NULL.
    - Existing `senderJobLeavesEmailPendingWhenSendFails`:
        - Verify email stays in emails table with status='pending' (no archive row).

open_questions: []

status: ready
