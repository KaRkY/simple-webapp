package simple.simple_webapp.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);
    static final String ADMIN_EMAIL = "admin@example.com";
    static final String ADMIN_PASSWORD = "admin";

    private final UserDao userDao;
    private final UserManagement userManagement;

    DefaultAdminInitializer(UserDao userDao, UserManagement userManagement) {
        this.userDao = userDao;
        this.userManagement = userManagement;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userDao.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        try {
            userManagement.registerAndActivate(ADMIN_EMAIL, ADMIN_PASSWORD);
        } catch (DuplicateEmailException ignored) {
            return;
        }

        var user = userDao.findByEmailIncludeDeleted(ADMIN_EMAIL);
        if (user == null) {
            return;
        }

        userManagement.setRole(user.id(), UserRole.ADMIN);

        log.warn("""
                
                ===========================================
                Default admin user created.
                  Email    : {}
                  Password : {}
                Change this password immediately!
                ===========================================""",
                ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
