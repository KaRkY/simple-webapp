package simple.simple_webapp.email.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import simple.simple_webapp.email.EmailManagement;

@Component
public class ModulesEventListener {

    private EmailManagement emailManagement;

    ModulesEventListener(EmailManagement emailManagement) {
        this.emailManagement = emailManagement;
    }

    void listen(Object object) {

    }
}
