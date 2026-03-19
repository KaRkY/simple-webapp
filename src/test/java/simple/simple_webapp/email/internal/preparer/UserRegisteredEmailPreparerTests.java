package simple.simple_webapp.email.internal.preparer;

import jakarta.mail.internet.MimeMessage;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;
import simple.simple_webapp.email.internal.MailTestConfig;
import simple.simple_webapp.user.UserManagement;
import simple.simple_webapp.user.UserRegisteredEvent;
import simple.simple_webapp.user.UserSummary;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static simple.simple_webapp.email.Tables.EMAILS;

@ApplicationModuleTest(module = "email")
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, MailTestConfig.class})
class UserRegisteredEmailPreparerTests {

    @Autowired
    DSLContext dsl;
    @MockitoBean
    UserManagement userManagement;
    @Autowired
    JavaMailSender mailSender;

    @BeforeEach
    void before() {
        reset(mailSender);
    }

    @AfterEach
    void after() {
        dsl.deleteFrom(EMAILS).execute();
    }

    @Test
    void publishedRegisteredEventQueuesActivationEmail(Scenario scenario) {
        var userId = UUID.randomUUID();
        var recipient = "email-" + UUID.randomUUID() + "@example.com";
        when(userManagement.findById(userId)).thenReturn(new UserSummary(
                userId,
                recipient,
                List.of("USER"),
                true,
                false,
                false,
                true
        ));

        scenario.publish(new UserRegisteredEvent(userId, "token-123"))
                .andWaitForStateChange(() -> dsl.fetchCount(EMAILS, EMAILS.TO.eq(recipient)), count -> count == 1);

        var record = dsl.selectFrom(EMAILS)
                .where(EMAILS.TO.eq(recipient))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getSubject()).isEqualTo("Activate your account");
        assertThat(record.getContent()).contains("token-123");
        assertThat(record.getContent()).contains("/activate?token=token-123");
    }

    @Test
    void publishedRegisteredEventWithNullTokenDoesNothing(Scenario scenario) {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        scenario.publish(new UserRegisteredEvent(UUID.randomUUID(), null))
                .andWaitForStateChange(() -> dsl.fetchCount(EMAILS), count -> count == 0);

        assertThat(dsl.fetchCount(EMAILS)).isZero();
        verifyNoInteractions(userManagement);
    }
}
