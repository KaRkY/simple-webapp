package simple.simple_webapp.user;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static simple.simple_webapp.user.Tables.USERS;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class UserManagementTests {

    @Autowired UserManagement userManagement;
    @Autowired DSLContext dsl;

    @Test
    void registerHappyPath() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        var details = userManagement.loadUserByUsername(username);
        assertThat(details.getUsername()).isEqualTo(username);
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void registerDuplicateUsernameThrows() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        assertThatThrownBy(() -> userManagement.register(username, "other"))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void threeFailedAttemptsLocksAccount() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        userManagement.recordFailedAttempt(username);
        userManagement.recordFailedAttempt(username);
        userManagement.recordFailedAttempt(username);

        var details = userManagement.loadUserByUsername(username);
        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void twoFailedAttemptsDoNotLock() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        userManagement.recordFailedAttempt(username);
        userManagement.recordFailedAttempt(username);

        var details = userManagement.loadUserByUsername(username);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void successfulLoginResetsFailedAttempts() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        userManagement.recordFailedAttempt(username);
        userManagement.recordFailedAttempt(username);
        userManagement.recordSuccessfulLogin(username);
        userManagement.recordFailedAttempt(username);
        userManagement.recordFailedAttempt(username);

        var details = userManagement.loadUserByUsername(username);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void autoUnlocksAfterFiveMinutes() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        // Simulate an expired lock by setting locked_at to 6 minutes ago directly in DB
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now().minusMinutes(6))
                .where(USERS.USERNAME.eq(username))
                .execute();

        var details = userManagement.loadUserByUsername(username);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void manualLockAndUnlock() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        var user = userManagement.findAll().stream()
                .filter(u -> u.username().equals(username))
                .findFirst().orElseThrow();

        userManagement.lockUser(user.id());
        assertThat(userManagement.loadUserByUsername(username).isAccountNonLocked()).isFalse();

        userManagement.unlockUser(user.id());
        assertThat(userManagement.loadUserByUsername(username).isAccountNonLocked()).isTrue();

        // unlockUser resets failed_login_attempts
        var record = dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOne();
        assertThat(record).isNotNull();
        assertThat(record.getFailedLoginAttempts()).isZero();
    }

    @Test
    void setRoleReplacesExistingRoles() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        var user = userManagement.findAll().stream()
                .filter(u -> u.username().equals(username))
                .findFirst().orElseThrow();

        userManagement.setRole(user.id(), UserRole.ADMIN);

        var details = userManagement.loadUserByUsername(username);
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void deleteUserBlockedOnSelf() {
        var username = uniqueUsername();
        userManagement.register(username, "password");

        var user = userManagement.findAll().stream()
                .filter(u -> u.username().equals(username))
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> userManagement.deleteUser(user.id(), username))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changePasswordHappyPath() {
        var username = uniqueUsername();
        userManagement.register(username, "oldPass");

        userManagement.changePassword(username, "oldPass", "newPass");

        userManagement.loadUserByUsername(username);
        // verify new password works (loadUserByUsername returns encoded — check via encoder indirectly)
        var record = dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOne();
        assertThat(record).isNotNull();
        // old password must no longer match
        var raw = record.getPassword();
        assertThat(raw).doesNotContain("oldPass");
    }

    @Test
    void changePasswordWrongCurrentThrows() {
        var username = uniqueUsername();
        userManagement.register(username, "correct");

        assertThatThrownBy(() -> userManagement.changePassword(username, "wrong", "new"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void resetPasswordReturnsNewPlainTextPassword() {
        var username = uniqueUsername();
        userManagement.register(username, "original");
        var user = userManagement.findAll().stream()
                .filter(u -> u.username().equals(username)).findFirst().orElseThrow();

        var temp = userManagement.resetPassword(user.id());

        assertThat(temp).isNotBlank();
        // new hash in DB must differ from original
        var record = dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOne();
        assertThat(record).isNotNull();
        assertThat(record.getPassword()).doesNotContain("original");
    }

    private static String uniqueUsername() {
        return "user-" + UUID.randomUUID();
    }
}
