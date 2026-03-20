package simple.simple_webapp.email;

import java.time.OffsetDateTime;

public record QueuedEmail(
        Long id,
        String from,
        String to,
        String subject,
        String content,
        Long emailTemplateId,
        EmailTemplateType templateType,
        OffsetDateTime createdAt,
        int attemptCount) {
}
