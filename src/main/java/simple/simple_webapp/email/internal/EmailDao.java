package simple.simple_webapp.email.internal;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import simple.simple_webapp.email.EmailTemplate;
import simple.simple_webapp.email.EmailTemplateType;
import simple.simple_webapp.email.QueuedEmail;

import java.time.OffsetDateTime;
import java.util.List;

import static simple.simple_webapp.email.Tables.EMAILS;
import static simple.simple_webapp.email.Tables.EMAILS_ARCHIVE;
import static simple.simple_webapp.email.Tables.EMAILS_DEAD_LETTER;
import static simple.simple_webapp.email.Tables.EMAIL_TEMPLATES;

@Repository
public class EmailDao {

    private final DSLContext dsl;

    EmailDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Nullable EmailTemplate findTemplateByName(String name) {
        return dsl.select(EMAIL_TEMPLATES.asterisk())
                .from(EMAIL_TEMPLATES)
                .where(EMAIL_TEMPLATES.NAME.eq(name))
                .fetchOne(this::toEmailTemplate);
    }

    void saveEmail(InsertEmail email) {
        dsl.insertInto(EMAILS)
                .set(EMAILS.FROM, email.from())
                .set(EMAILS.TO, email.to())
                .set(EMAILS.SUBJECT, email.subject())
                .set(EMAILS.CONTENT, email.content())
                .set(EMAILS.EMAIL_TEMPLATE_ID, email.templateId())
                .execute();
    }

    List<QueuedEmail> claimBatch(int batchSize) {
        var now = OffsetDateTime.now();
        var rows = dsl.select(
                        EMAILS.ID,
                        EMAILS.FROM,
                        EMAILS.TO,
                        EMAILS.SUBJECT,
                        EMAILS.CONTENT,
                        EMAILS.EMAIL_TEMPLATE_ID,
                        EMAIL_TEMPLATES.TYPE,
                        EMAILS.CREATED_AT,
                        EMAILS.ATTEMPT_COUNT
                )
                .from(EMAILS)
                .join(EMAIL_TEMPLATES).on(EMAILS.EMAIL_TEMPLATE_ID.eq(EMAIL_TEMPLATES.ID))
                .where(EMAILS.STATUS.eq("pending"))
                .and(EMAILS.NEXT_RETRY_AT.isNull().or(EMAILS.NEXT_RETRY_AT.lessOrEqual(now)))
                .orderBy(EMAILS.CREATED_AT.asc(), EMAILS.ID.asc())
                .limit(batchSize)
                .forUpdate()
                .skipLocked()
                .fetch(this::toQueuedEmail);

        if (!rows.isEmpty()) {
            var ids = rows.stream().map(QueuedEmail::id).toList();
            dsl.update(EMAILS)
                    .set(EMAILS.STATUS, "processing")
                    .set(EMAILS.PROCESSING_SINCE, OffsetDateTime.now())
                    .where(EMAILS.ID.in(ids))
                    .execute();
        }
        return rows;
    }

    List<QueuedEmail> claimStaleProcessing(OffsetDateTime olderThan) {
        return dsl.select(
                        EMAILS.ID,
                        EMAILS.FROM,
                        EMAILS.TO,
                        EMAILS.SUBJECT,
                        EMAILS.CONTENT,
                        EMAILS.EMAIL_TEMPLATE_ID,
                        EMAIL_TEMPLATES.TYPE,
                        EMAILS.CREATED_AT,
                        EMAILS.ATTEMPT_COUNT
                )
                .from(EMAILS)
                .join(EMAIL_TEMPLATES).on(EMAILS.EMAIL_TEMPLATE_ID.eq(EMAIL_TEMPLATES.ID))
                .where(EMAILS.STATUS.eq("processing"))
                .and(EMAILS.PROCESSING_SINCE.lessThan(olderThan))
                .forUpdate()
                .skipLocked()
                .fetch(this::toQueuedEmail);
    }

    void failEmail(Long id, int newAttemptCount, OffsetDateTime nextRetryAt) {
        dsl.update(EMAILS)
                .set(EMAILS.STATUS, "pending")
                .set(EMAILS.ATTEMPT_COUNT, newAttemptCount)
                .set(EMAILS.NEXT_RETRY_AT, nextRetryAt)
                .setNull(EMAILS.PROCESSING_SINCE)
                .where(EMAILS.ID.eq(id))
                .execute();
    }

    void moveToDeadLetter(QueuedEmail email, int finalAttemptCount) {
        dsl.insertInto(EMAILS_DEAD_LETTER)
                .set(EMAILS_DEAD_LETTER.ID, email.id())
                .set(EMAILS_DEAD_LETTER.FROM, email.from())
                .set(EMAILS_DEAD_LETTER.TO, email.to())
                .set(EMAILS_DEAD_LETTER.SUBJECT, email.subject())
                .set(EMAILS_DEAD_LETTER.CONTENT, email.content())
                .set(EMAILS_DEAD_LETTER.EMAIL_TEMPLATE_ID, email.emailTemplateId())
                .set(EMAILS_DEAD_LETTER.CREATED_AT, email.createdAt())
                .set(EMAILS_DEAD_LETTER.ATTEMPT_COUNT, finalAttemptCount)
                .execute();

        dsl.deleteFrom(EMAILS)
                .where(EMAILS.ID.eq(email.id()))
                .execute();
    }

    void archiveEmail(QueuedEmail email, OffsetDateTime sentAt) {
        dsl.insertInto(EMAILS_ARCHIVE)
                .set(EMAILS_ARCHIVE.ID, email.id())
                .set(EMAILS_ARCHIVE.FROM, email.from())
                .set(EMAILS_ARCHIVE.TO, email.to())
                .set(EMAILS_ARCHIVE.SUBJECT, email.subject())
                .set(EMAILS_ARCHIVE.CONTENT, email.content())
                .set(EMAILS_ARCHIVE.SENT_AT, sentAt)
                .set(EMAILS_ARCHIVE.EMAIL_TEMPLATE_ID, email.emailTemplateId())
                .execute();

        dsl.deleteFrom(EMAILS)
                .where(EMAILS.ID.eq(email.id()))
                .execute();
    }

    private EmailTemplate toEmailTemplate(Record r) {
        return new EmailTemplate(
                r.get(EMAIL_TEMPLATES.ID),
                r.get(EMAIL_TEMPLATES.NAME),
                r.get(EMAIL_TEMPLATES.SUBJECT),
                EmailTemplateType.fromString(r.get(EMAIL_TEMPLATES.TYPE)),
                r.get(EMAIL_TEMPLATES.TEMPLATE)
        );
    }

    private QueuedEmail toQueuedEmail(Record r) {
        return new QueuedEmail(
                r.get(EMAILS.ID),
                r.get(EMAILS.FROM),
                r.get(EMAILS.TO),
                r.get(EMAILS.SUBJECT),
                r.get(EMAILS.CONTENT),
                r.get(EMAILS.EMAIL_TEMPLATE_ID),
                EmailTemplateType.fromString(r.get(EMAIL_TEMPLATES.TYPE)),
                r.get(EMAILS.CREATED_AT),
                r.get(EMAILS.ATTEMPT_COUNT)
        );
    }

    record InsertEmail(
            String from,
            String to,
            String subject,
            String content,
            Long templateId) {
    }
}
