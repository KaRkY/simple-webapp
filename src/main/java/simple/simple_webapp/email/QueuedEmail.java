package simple.simple_webapp.email;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QueuedEmail(
        UUID id,
        String from,
        String to,
        String subject,
        String content,
        UUID emailTemplateId,
        EmailTemplateType templateType,
        OffsetDateTime createdAt) {
}
