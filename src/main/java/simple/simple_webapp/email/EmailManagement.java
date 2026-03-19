package simple.simple_webapp.email;

import java.util.Map;

public interface EmailManagement {

    EmailTemplate loadEmailTemplate(String templateType);

    void addEmailTemplate(EmailTemplate template);
}
