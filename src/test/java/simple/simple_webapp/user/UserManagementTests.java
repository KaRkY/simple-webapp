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
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var details = userManagement.loadUserByUsername(email);
        assertThat(details.getUsername()).isEqualTo(email);
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void registerDuplicateEmailThrows() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        assertThatThrownBy(() -> userManagement.registerAndActivate(email, "other"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void threeFailedAttemptsLocksAccount() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        userManagement.recordFailedAttempt(email);
        userManagement.recordFailedAttempt(email);
        userManagement.recordFailedAttempt(email);

        var details = userManagement.loadUserByUsername(email);
        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void twoFailedAttemptsDoNotLock() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        userManagement.recordFailedAttempt(email);
        userManagement.recordFailedAttempt(email);

        var details = userManagement.loadUserByUsername(email);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void successfulLoginResetsFailedAttempts() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        userManagement.recordFailedAttempt(email);
        userManagement.recordFailedAttempt(email);
        userManagement.recordSuccessfulLogin(email);
        userManagement.recordFailedAttempt(email);
        userManagement.recordFailedAttempt(email);

        var details = userManagement.loadUserByUsername(email);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void autoUnlocksAfterFiveMinutes() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now().minusMinutes(6))
                .where(USERS.EMAIL.eq(email))
                .execute();

        var details = userManagement.loadUserByUsername(email);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void manualLockAndUnlock() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email))
                .findFirst().orElseThrow();

        userManagement.lockUser(user.id());
        assertThat(userManagement.loadUserByUsername(email).isAccountNonLocked()).isFalse();

        userManagement.unlockUser(user.id());
        assertThat(userManagement.loadUserByUsername(email).isAccountNonLocked()).isTrue();

        var record = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne();
        assertThat(record).isNotNull();
        assertThat(record.getFailedLoginAttempts()).isZero();
    }

    @Test
    void setRoleReplacesExistingRoles() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email))
                .findFirst().orElseThrow();

        userManagement.setRole(user.id(), UserRole.ADMIN);

        var details = userManagement.loadUserByUsername(email);
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void deleteUserSoftDeletesSetsDeletedAt() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email))
                .findFirst().orElseThrow();

        userManagement.deleteUser(user.id(), "other@example.com");

        var record = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne();
        assertThat(record).isNotNull();
        assertThat(record.getDeletedAt()).isNotNull();
    }

    @Test
    void findAllExcludesSoftDeletedUsers() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email))
                .findFirst().orElseThrow();

        userManagement.deleteUser(user.id(), "other@example.com");

        var found = userManagement.findAll(false).stream()
                .anyMatch(u -> u.email().equals(email));
        assertThat(found).isFalse();
    }

    @Test
    void loadUserByEmailThrowsForSoftDeletedUser() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email))
                .findFirst().orElseThrow();

        userManagement.deleteUser(user.id(), "other@example.com");

        assertThatThrownBy(() -> userManagement.loadUserByUsername(email))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    void deleteUserBlockedOnSelf() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "password");

        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email))
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> userManagement.deleteUser(user.id(), email))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changePasswordHappyPath() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "oldPass");

        userManagement.changePassword(email, "oldPass", "newPass");

        var record = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne();
        assertThat(record).isNotNull();
        assertThat(record.getPassword()).doesNotContain("oldPass");
    }

    @Test
    void changePasswordWrongCurrentThrows() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "correct");

        assertThatThrownBy(() -> userManagement.changePassword(email, "wrong", "new"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void resetPasswordReturnsNewPlainTextPassword() {
        var email = uniqueEmail();
        userManagement.registerAndActivate(email, "original");
        var user = userManagement.findAll(false).stream()
                .filter(u -> u.email().equals(email)).findFirst().orElseThrow();

        var temp = userManagement.resetPassword(user.id());

        assertThat(temp).isNotBlank();
        var record = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne();
        assertThat(record).isNotNull();
        assertThat(record.getPassword()).doesNotContain("original");
    }

    @Test
    void activateUserHappyPath() {
        var email = uniqueEmail();
        userManagement.register(email, "password");

        var tokenRecord = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne();
        assertThat(tokenRecord).isNotNull();
        var token = tokenRecord.getActivationToken();
        assertThat(token).isNotNull();

        assertThat(userManagement.loadUserByUsername(email).isEnabled()).isFalse();

        userManagement.activateUser(token);

        assertThat(userManagement.loadUserByUsername(email).isEnabled()).isTrue();
    }

    @Test
    void activateUserExpiredTokenThrows() {
        var email = uniqueEmail();
        userManagement.register(email, "password");

        dsl.update(USERS)
                .set(USERS.ACTIVATION_TOKEN_EXPIRES_AT, OffsetDateTime.now().minusHours(1))
                .where(USERS.EMAIL.eq(email))
                .execute();

        var token = dsl.select(USERS.ACTIVATION_TOKEN).from(USERS)
                .where(USERS.EMAIL.eq(email)).fetchOneInto(String.class);

        assertThatThrownBy(() -> userManagement.activateUser(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activateUserInvalidTokenThrows() {
        assertThatThrownBy(() -> userManagement.activateUser("no-such-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
