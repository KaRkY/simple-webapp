package simple.simple_webapp.email.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "email-monitor")
record EmailMonitorProperties(
        @DefaultValue("0/2 * * * * *") String cron,
        @DefaultValue("10") int batchSize,
        @DefaultValue("PT5M") Duration staleTolerance,
        @DefaultValue("0 * * * * *") String monitorCron,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("PT1M") Duration initialBackoffDelay) {
}
