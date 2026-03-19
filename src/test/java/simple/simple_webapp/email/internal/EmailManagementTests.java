package simple.simple_webapp.email.internal;

import jakarta.mail.Session;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;
import simple.simple_webapp.email.EmailTemplateType;
import simple.simple_webapp.user.UserManagement;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static simple.simple_webapp.email.Tables.EMAILS;
import static simple.simple_webapp.email.Tables.EMAILS_ARCHIVE;
import static simple.simple_webapp.email.Tables.EMAIL_TEMPLATES;

@ApplicationModuleTest(module = "email")
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, MailTestConfig.class})
class EmailManagementTests {

    @Autowired
    EmailManagementImpl emailManagement;
    @Autowired
    EmailSenderJob emailSenderJob;
    @Autowired
    DSLContext dsl;
    @Autowired
    JavaMailSender mailSender;
    @MockitoBean
    UserManagement userManagement;

    @BeforeEach
    void before() {
        reset(mailSender);
    }

    @AfterEach
    void after() {
        dsl.deleteFrom(EMAILS).execute();
    }

    @Test
    void loadEmailTemplateReturnsSeededTemplate() {
        var template = emailManagement.loadEmailTemplate("user-registered");

        assertThat(template.name()).isEqualTo("user-registered");
        assertThat(template.subject()).isEqualTo("Activate your account");
        assertThat(template.type()).isEqualTo(EmailTemplateType.HTML);
        assertThat(template.template()).contains("Activate account");
    }

    @Test
    void queueEmailRendersTemplateAndStoresPendingEmail() {
        var templateName = insertTemplate(
                EmailTemplateType.TEXT,
                "Hello [[${email}]]",
                "Activate at [[${activationUrl}]]"
        );
        var recipient = uniqueEmail();

        emailManagement.queueEmail(
                recipient,
                templateName,
                Map.of(
                        "email", recipient,
                        "activationUrl", "http://localhost:8080/activate?token=abc"
                )
        );

        var record = dsl.selectFrom(EMAILS)
                .where(EMAILS.TO.eq(recipient))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getFrom()).isEqualTo("noreply@example.com");
        assertThat(record.getTo()).isEqualTo(recipient);
        assertThat(record.getSubject()).isEqualTo("Hello " + recipient);
        assertThat(record.getContent()).isEqualTo("Activate at http://localhost:8080/activate?token=abc");
    }

    @Test
    void senderJobSendsTextEmailAndArchivesIt() {
        var templateName = insertTemplate(
                EmailTemplateType.TEXT,
                "Hello [[${email}]]",
                "Plain body for [[${email}]]"
        );
        var recipient = uniqueEmail();
        emailManagement.queueEmail(recipient, templateName, Map.of("email", recipient));

        emailSenderJob.run();

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly(recipient);
        assertThat(dsl.fetchCount(EMAILS, EMAILS.TO.eq(recipient))).isZero();
        assertThat(dsl.fetchCount(EMAILS_ARCHIVE, EMAILS_ARCHIVE.TO.eq(recipient))).isEqualTo(1);
    }

    @Test
    void senderJobSendsHtmlEmailAndArchivesIt() throws Exception {
        var templateName = insertTemplate(
                EmailTemplateType.HTML,
                "HTML subject",
                "<p>Click [[${activationUrl}]]</p>"
        );
        var recipient = uniqueEmail();
        emailManagement.queueEmail(recipient, templateName, Map.of("activationUrl", "http://localhost:8080/activate?token=abc"));

        var mimeMessage = new jakarta.mail.internet.MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailSenderJob.run();

        verify(mailSender).send(mimeMessage);
        assertThat(dsl.fetchCount(EMAILS, EMAILS.TO.eq(recipient))).isZero();
        assertThat(dsl.fetchCount(EMAILS_ARCHIVE, EMAILS_ARCHIVE.TO.eq(recipient))).isEqualTo(1);
    }

    @Test
    void senderJobLeavesEmailPendingWhenSendFails() throws Exception {
        var templateName = insertTemplate(
                EmailTemplateType.HTML,
                "HTML subject",
                "<p>Click [[${activationUrl}]]</p>"
        );
        var recipient = uniqueEmail();
        emailManagement.queueEmail(recipient, templateName, Map.of("activationUrl", "http://localhost:8080/activate?token=abc"));

        var mimeMessage = new jakarta.mail.internet.MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("boom")).when(mailSender).send(eq(mimeMessage));

        emailSenderJob.run();

        assertThat(dsl.fetchCount(EMAILS, EMAILS.TO.eq(recipient))).isEqualTo(1);
        assertThat(dsl.fetchCount(EMAILS_ARCHIVE, EMAILS_ARCHIVE.TO.eq(recipient))).isZero();
    }

    private String insertTemplate(EmailTemplateType type, String subject, String template) {
        var id = UUID.randomUUID();
        var name = "template-" + id;
        dsl.insertInto(EMAIL_TEMPLATES)
                .set(EMAIL_TEMPLATES.ID, id)
                .set(EMAIL_TEMPLATES.NAME, name)
                .set(EMAIL_TEMPLATES.SUBJECT, subject)
                .set(EMAIL_TEMPLATES.TYPE, type.toDbValue())
                .set(EMAIL_TEMPLATES.TEMPLATE, template)
                .execute();
        return name;
    }

    private static String uniqueEmail() {
        return "email-" + UUID.randomUUID() + "@example.com";
    }
}
