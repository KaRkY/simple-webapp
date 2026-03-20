package simple.simple_webapp.email.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
class EmailMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(EmailMonitorJob.class);

    private final EmailDao emailDao;
    private final Duration staleTolerance;

    EmailMonitorJob(EmailDao emailDao,
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
