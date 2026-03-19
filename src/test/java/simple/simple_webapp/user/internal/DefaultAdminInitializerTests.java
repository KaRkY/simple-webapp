package simple.simple_webapp.user.internal;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static simple.simple_webapp.user.Tables.USERS;
import static simple.simple_webapp.user.internal.DefaultAdminInitializer.ADMIN_EMAIL;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class DefaultAdminInitializerTests {

    @Autowired
    UserManagementImpl userManagement;
    @Autowired
    DefaultAdminInitializer initializer;
    @Autowired DSLContext dsl;

    @Test
    void adminUserExistsAfterStartup() {
        var details = userManagement.loadUserByUsername("admin@example.com");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN");
    }

    @Test
    void secondRunIsIdempotent() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        var adminCount = dsl.fetchCount(
                        dsl.selectOne().from(USERS)
                                .where(USERS.EMAIL.eq(ADMIN_EMAIL))
        );
        assertThat(adminCount).isEqualTo(1);
    }
}
