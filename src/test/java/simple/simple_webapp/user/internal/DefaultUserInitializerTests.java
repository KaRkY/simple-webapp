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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class DefaultUserInitializerTests {

    @Autowired
    UserManagementImpl userManagement;
    @Autowired
    DefaultUserInitializer initializer;
    @Autowired DSLContext dsl;

    @Test
    void userExistsAfterStartup() {
        var details = userManagement.loadUserByUsername("test@example.com");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER");
    }

    @Test
    void secondRunIsIdempotent() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        var userCount = dsl.fetchCount(
                dsl.selectOne().from(USERS)
                        .where(USERS.EMAIL.eq("test@example.com"))
        );
        assertThat(userCount).isEqualTo(1);
    }
}
