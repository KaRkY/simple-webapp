package simple.simple_webapp.email;

import java.util.Map;

public interface EmailManagement {

    EmailTemplate loadEmailTemplate(String name);

    void queueEmail(String to, String templateName, Map<String, Object> variables);
}
