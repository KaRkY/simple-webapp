package simple.simple_webapp.email.internal;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import simple.simple_webapp.email.EmailManagement;
import simple.simple_webapp.email.EmailTemplate;

@Service
public class EmailManagementImpl implements EmailManagement {

    @Override
    public EmailTemplate loadEmailTemplate(String templateType) {
        return null;
    }

    @Override
    public void addEmailTemplate(EmailTemplate template) {

    }
}
