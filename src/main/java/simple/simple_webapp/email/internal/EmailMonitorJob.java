package simple.simple_webapp.email.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
class EmailMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(EmailMonitorJob.class);

    private final EmailDao emailDao;
    private final EmailRetryService retryService;
    private final Duration staleTolerance;

    EmailMonitorJob(EmailDao emailDao, EmailRetryService retryService, EmailMonitorProperties properties) {
        this.emailDao = emailDao;
        this.retryService = retryService;
        this.staleTolerance = properties.staleTolerance();
    }

    @Scheduled(cron = "${email-monitor.monitor-cron:0 * * * * *}")
    void run() {
        var cutoff = OffsetDateTime.now().minus(staleTolerance);
        var stale = emailDao.claimStaleProcessing(cutoff);
        if (!stale.isEmpty()) {
            log.warn("Found {} stale processing email(s), applying retry logic", stale.size());
            stale.forEach(retryService::handleFailure);
        }
    }
}
