package simple.simple_webapp.user.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import simple.simple_webapp.user.CreateUser;
import simple.simple_webapp.user.DuplicateEmailException;
import simple.simple_webapp.user.UserManagement;

@Component
class DefaultUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserInitializer.class);
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD = "test";

    private final UserManagement userManagement;

    DefaultUserInitializer(UserManagement userManagement) {
        this.userManagement = userManagement;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            userManagement.registerAndActivate(new CreateUser(USER_EMAIL, USER_PASSWORD));
        } catch (DuplicateEmailException ignored) {
            return;
        }

        log.info("""
                
                ===========================================
                Default test user created.
                  Email    : {}
                  Password : {}
                ===========================================""",
                USER_EMAIL, USER_PASSWORD);
    }
}
