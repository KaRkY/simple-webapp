package simple.simple_webapp.email.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailSenderJob {

    @Scheduled(cron = "${email-monitor.cron:0/2 * * * * *}")
    void run() {
        //TODO: Send email
    }
}
