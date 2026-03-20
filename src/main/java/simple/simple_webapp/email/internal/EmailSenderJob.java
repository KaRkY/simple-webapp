package simple.simple_webapp.email.internal;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import simple.simple_webapp.email.QueuedEmail;

import java.time.OffsetDateTime;

@Component
public class EmailSenderJob {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderJob.class);

    private final EmailDao emailDao;
    private final JavaMailSender mailSender;
    private final int batchSize;

    EmailSenderJob(EmailDao emailDao, JavaMailSender mailSender, EmailMonitorProperties properties) {
        this.emailDao = emailDao;
        this.mailSender = mailSender;
        this.batchSize = properties.batchSize();
    }

    @Scheduled(cron = "${email-monitor.cron:0/2 * * * * *}")
    @Transactional
    void run() {
        for (var email : emailDao.claimBatch(batchSize)) {
            try {
                switch (email.templateType()) {
                    case TEXT -> send(email);
                    case HTML -> sendHtml(email);
                }
                emailDao.archiveEmail(email, OffsetDateTime.now());
            } catch (MailException | MessagingException e) {
                log.error("Failed to send email {}", email.id(), e);
                emailDao.releaseEmail(email.id());
            }
        }
    }

    private void send(QueuedEmail email) throws MessagingException {
        var message = new SimpleMailMessage();
        message.setFrom(email.from());
        message.setTo(email.to());
        message.setSubject(email.subject());
        message.setText(email.content());
        mailSender.send(message);
    }

    private void sendHtml(QueuedEmail email) throws MessagingException {
        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(email.from());
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        helper.setText(email.content(), true);
        mailSender.send(message);
    }
}
