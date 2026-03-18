package simple.simple_webapp.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DefaultUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserInitializer.class);
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD = "test";

    private final UserDao userDao;
    private final UserManagement userManagement;

    DefaultUserInitializer(UserDao userDao, UserManagement userManagement) {
        this.userDao = userDao;
        this.userManagement = userManagement;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userDao.existsByEmail(USER_EMAIL)) {
            return;
        }

        try {
            userManagement.registerAndActivate(USER_EMAIL, USER_PASSWORD);
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
