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
import java.util.UUID;

import static simple.simple_webapp.email.Tables.EMAILS;
import static simple.simple_webapp.email.Tables.EMAILS_ARCHIVE;
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
                .set(EMAILS.ID, email.id())
                .set(EMAILS.FROM, email.from())
                .set(EMAILS.TO, email.to())
                .set(EMAILS.SUBJECT, email.subject())
                .set(EMAILS.CONTENT, email.content())
                .set(EMAILS.EMAIL_TEMPLATE_ID, email.templateId())
                .execute();
    }

    List<QueuedEmail> findPendingEmails() {
        return dsl.select(
                        EMAILS.ID,
                        EMAILS.FROM,
                        EMAILS.TO,
                        EMAILS.SUBJECT,
                        EMAILS.CONTENT,
                        EMAILS.EMAIL_TEMPLATE_ID,
                        EMAIL_TEMPLATES.TYPE,
                        EMAILS.CREATED_AT
                )
                .from(EMAILS)
                .join(EMAIL_TEMPLATES).on(EMAILS.EMAIL_TEMPLATE_ID.eq(EMAIL_TEMPLATES.ID))
                .orderBy(EMAILS.CREATED_AT.asc(), EMAILS.ID.asc())
                .fetch(this::toQueuedEmail);
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
                r.get(EMAILS.CREATED_AT)
        );
    }

    record InsertEmail(
            UUID id,
            String from,
            String to,
            String subject,
            String content,
            UUID templateId) {
    }
}
