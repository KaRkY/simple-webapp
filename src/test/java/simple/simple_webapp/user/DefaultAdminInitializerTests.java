package simple.simple_webapp.user;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static simple.simple_webapp.user.Tables.USER_ROLES;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class DefaultAdminInitializerTests {

    @Autowired UserManagement userManagement;
    @Autowired DefaultAdminInitializer initializer;
    @Autowired DSLContext dsl;

    @Test
    void adminUserExistsAfterStartup() {
        var details = userManagement.loadUserByUsername("admin");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN");
    }

    @Test
    void secondRunIsIdempotent() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        var adminCount = dsl.fetchCount(
                dsl.selectOne().from(USER_ROLES)
                        .where(USER_ROLES.ROLE.eq(UserRole.ADMIN.name()))
        );
        assertThat(adminCount).isEqualTo(1);
    }
}
