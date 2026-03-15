package simple.simple_webapp.user;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static simple.simple_webapp.user.Tables.USERS;

@Component
class DefaultUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserInitializer.class);
    private static final String USER_USERNAME = "test";
    private static final String USER_PASSWORD = "test";

    private final DSLContext dsl;
    private final UserManagement userManagement;

    DefaultUserInitializer(DSLContext dsl, UserManagement userManagement) {
        this.dsl = dsl;
        this.userManagement = userManagement;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean userExists = dsl.fetchExists(
                dsl.selectOne().from(USERS)
                        .where(USERS.USERNAME.eq(USER_USERNAME))
        );
        if (userExists) {
            return;
        }

        try {
            userManagement.register(USER_USERNAME, USER_PASSWORD);
        } catch (DuplicateUsernameException ignored) {
            return;
        }

        log.info("""
                
                ===========================================
                Default test user created.
                  Username : {}
                  Password : {}
                ===========================================""",
                USER_USERNAME, USER_PASSWORD);
    }
}
