package simple.simple_webapp.email.internal.preparer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import simple.simple_webapp.email.EmailManagement;
import simple.simple_webapp.user.UserManagement;
import simple.simple_webapp.user.UserRegisteredEvent;

import java.util.Map;

@Component
public class UserRegisteredEmailPreparer {

    private final EmailManagement emailManagement;
    private final UserManagement userManagement;
    private final String baseUrl;

    UserRegisteredEmailPreparer(
            EmailManagement emailManagement,
            UserManagement userManagement,
            @Value("${app.base-url}") String baseUrl) {
        this.emailManagement = emailManagement;
        this.userManagement = userManagement;
        this.baseUrl = baseUrl;
    }

    @ApplicationModuleListener
    void on(UserRegisteredEvent event) {
        var activationToken = event.activationToken();
        if (activationToken == null) {
            return;
        }

        var user = userManagement.findById(event.userId());
        emailManagement.queueEmail(
                user.email(),
                "user-registered",
                Map.of(
                        "email", user.email(),
                        "activationToken", activationToken,
                        "activationUrl", activationUrl(activationToken)
                )
        );
    }

    private String activationUrl(String activationToken) {
        return stripTrailingSlash(baseUrl) + "/activate?token=" + activationToken;
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
