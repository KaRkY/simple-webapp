package simple.simple_webapp.user;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptListener {

    private final UserManagement userManagement;

    LoginAttemptListener(UserManagement userManagement) {
        this.userManagement = userManagement;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        var username = (String) event.getAuthentication().getPrincipal();
        assert username != null;
        userManagement.recordFailedAttempt(username);
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        var username = event.getAuthentication().getName();
        userManagement.recordSuccessfulLogin(username);
    }
}
