package simple.simple_webapp.email.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import simple.simple_webapp.email.EmailManagement;
import simple.simple_webapp.email.EmailTemplate;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EmailManagementImpl implements EmailManagement {

    private final EmailDao emailDao;
    private final TemplateEngine htmlEmailTemplateEngine;
    private final TemplateEngine textEmailTemplateEngine;
    private final String fromAddress;

    EmailManagementImpl(
            EmailDao emailDao,
            TemplateEngine htmlEmailTemplateEngine,
            TemplateEngine textEmailTemplateEngine,
            @Value("${app.mail.from}") String fromAddress) {
        this.emailDao = emailDao;
        this.htmlEmailTemplateEngine = htmlEmailTemplateEngine;
        this.textEmailTemplateEngine = textEmailTemplateEngine;
        this.fromAddress = fromAddress;
    }

    @Override
    @Cacheable(value = "email.templates", key = "#name")
    @Transactional(readOnly = true)
    public EmailTemplate loadEmailTemplate(String name) {
        var template = emailDao.findTemplateByName(name);
        if (template == null) {
            throw new NoSuchElementException("Email template not found: " + name);
        }
        return template;
    }

    @Override
    @Transactional
    public void queueEmail(String to, String templateName, Map<String, Object> variables) {
        var template = loadEmailTemplate(templateName);
        var context = new Context();
        context.setVariables(variables);

        var renderedSubject = textEmailTemplateEngine.process(template.subject(), context);
        var renderedContent = switch (template.type()) {
            case TEXT -> textEmailTemplateEngine.process(template.template(), context);
            case HTML -> htmlEmailTemplateEngine.process(template.template(), context);
        };

        emailDao.saveEmail(new EmailDao.InsertEmail(
                UUID.randomUUID(),
                fromAddress,
                to,
                renderedSubject,
                renderedContent,
                template.id()
        ));
    }
}
