package simple.simple_webapp.email;

public record EmailTemplate(Long id, String name, String subject, EmailTemplateType type, String template) {
}
