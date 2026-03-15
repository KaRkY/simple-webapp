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
    private static final String ADMIN_USERNAME = "admin";

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
                dsl.selectOne().from(USER_ROLES)
                        .where(USER_ROLES.ROLE.eq(UserRole.ADMIN.name()))
        );
        if (adminExists) {
            return;
        }

        var password = UUID.randomUUID().toString();

        try {
            userManagement.register(ADMIN_USERNAME, password);
        } catch (DuplicateUsernameException ignored) {
            // "admin" username exists but has no ADMIN role — skip initialization
            return;
        }

        var user = dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(ADMIN_USERNAME))
                .fetchOne();

        if (user == null) {
            // register() was mocked or failed — skip
            return;
        }

        userManagement.setRole(user.getId(), UserRole.ADMIN);

        log.warn("""
                
                ===========================================
                Default admin user created.
                  Username : {}
                  Password : {}
                Change this password immediately!
                ===========================================""",
                ADMIN_USERNAME, password);
    }
}
