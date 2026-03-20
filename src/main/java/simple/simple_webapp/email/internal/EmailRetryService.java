package simple.simple_webapp.email.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import simple.simple_webapp.email.QueuedEmail;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
class EmailRetryService {

    private static final Logger log = LoggerFactory.getLogger(EmailRetryService.class);

    private final EmailDao emailDao;
    private final int maxAttempts;
    private final Duration initialBackoffDelay;

    EmailRetryService(EmailDao emailDao, EmailMonitorProperties properties) {
        this.emailDao = emailDao;
        this.maxAttempts = properties.maxAttempts();
        this.initialBackoffDelay = properties.initialBackoffDelay();
    }

    void handleFailure(QueuedEmail email) {
        int newCount = email.attemptCount() + 1;
        if (newCount >= maxAttempts) {
            log.warn("Email {} exhausted {} attempt(s), moving to dead-letter", email.id(), maxAttempts);
            emailDao.moveToDeadLetter(email, newCount);
        } else {
            long delaySecs = initialBackoffDelay.toSeconds() * (1L << email.attemptCount());
            var nextRetry = OffsetDateTime.now().plusSeconds(delaySecs);
            log.warn("Email {} failed (attempt {}/{}), next retry at {}", email.id(), newCount, maxAttempts, nextRetry);
            emailDao.failEmail(email.id(), newCount, nextRetry);
        }
    }
}
