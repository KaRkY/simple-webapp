package simple.simple_webapp.user;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static simple.simple_webapp.user.Tables.USER_ROLES;
import static simple.simple_webapp.user.Tables.USERS;

@Component
class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);
    static final String ADMIN_EMAIL = "admin@example.com";
    static final String ADMIN_PASSWORD = "admin";

    private final DSLContext dsl;
    private final UserManagement userManagement;

    DefaultAdminInitializer(DSLContext dsl, UserManagement userManagement) {
        this.dsl = dsl;
        this.userManagement = userManagement;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean adminExists = dsl.fetchExists(
                dsl.selectOne().from(USERS)
                        .where(USERS.EMAIL.eq(ADMIN_EMAIL)));
        if (adminExists) {
            return;
        }

        try {
            userManagement.registerAndActivate(ADMIN_EMAIL, ADMIN_PASSWORD);
        } catch (DuplicateEmailException ignored) {
            return;
        }

        var user = dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(ADMIN_EMAIL))
                .fetchOne();

        if (user == null) {
            return;
        }

        userManagement.setRole(user.getId(), UserRole.ADMIN);

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
