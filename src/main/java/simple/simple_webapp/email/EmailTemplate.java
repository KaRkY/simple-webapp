package simple.simple_webapp.email;

import java.util.UUID;

public record EmailTemplate(UUID id, String name, String subject, EmailTemplateType type, String template) {
}
