package simple.simple_webapp.email.internal;

import org.springframework.stereotype.Controller;
import simple.simple_webapp.email.EmailManagement;

@Controller
public class EmailManagementController {

    private EmailManagement emailManagement;

    EmailManagementController(EmailManagement emailManagement) {
        this.emailManagement = emailManagement;
    }
}
