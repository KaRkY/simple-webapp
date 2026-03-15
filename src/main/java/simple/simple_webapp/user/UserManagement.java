package simple.simple_webapp.user;

import org.jooq.DSLContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static simple.simple_webapp.user.Tables.USER_ROLES;
import static simple.simple_webapp.user.Tables.USERS;

@Service
public class UserManagement implements UserDetailsService {

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    public UserManagement(DSLContext dsl, PasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String username, String password) {
        var id = UUID.randomUUID();
        try {
            dsl.insertInto(USERS)
                    .set(USERS.ID, id)
                    .set(USERS.USERNAME, username)
                    .set(USERS.PASSWORD, passwordEncoder.encode(password))
                    .execute();
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_username_unique")) {
                throw new DuplicateUsernameException(username);
            }
            throw e;
        }
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, id)
                .set(USER_ROLES.ROLE, UserRole.USER.name())
                .execute();
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne();

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        var accountNonLocked = Boolean.TRUE.equals(user.getAccountNonLocked());
        var lockedAt = user.getLockedAt();

        if (!accountNonLocked && lockedAt != null && lockedAt.plusMinutes(5).isBefore(OffsetDateTime.now())) {
            dsl.update(USERS)
                    .set(USERS.ACCOUNT_NON_LOCKED, true)
                    .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                    .set(USERS.LOCKED_AT, (OffsetDateTime) null)
                    .where(USERS.ID.eq(user.getId()))
                    .execute();
            accountNonLocked = true;
        }

        var authorities = dsl.select(USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(user.getId()))
                .fetchInto(String.class)
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getAccountNonExpired()),
                Boolean.TRUE.equals(user.getCredentialsNonExpired()),
                accountNonLocked,
                authorities
        );
    }

    @Transactional
    public void recordFailedAttempt(String username) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, USERS.FAILED_LOGIN_ATTEMPTS.plus(1))
                .where(USERS.USERNAME.eq(username))
                .execute();

        var attempts = dsl.select(USERS.FAILED_LOGIN_ATTEMPTS)
                .from(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOneInto(Integer.class);

        if (attempts != null && attempts >= 3) {
            dsl.update(USERS)
                    .set(USERS.ACCOUNT_NON_LOCKED, false)
                    .set(USERS.LOCKED_AT, OffsetDateTime.now())
                    .where(USERS.USERNAME.eq(username))
                    .execute();
        }
    }

    @Transactional
    public void recordSuccessfulLogin(String username) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.USERNAME.eq(username))
                .execute();
    }

    public UserSummary findById(UUID id) {
        var user = dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOne();
        if (user == null) {
            throw new java.util.NoSuchElementException("User not found: " + id);
        }
        var roles = dsl.select(USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(id))
                .fetchInto(String.class);
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                roles,
                Boolean.TRUE.equals(user.getAccountNonLocked()),
                Boolean.TRUE.equals(user.getEnabled())
        );
    }

    public List<UserSummary> findAll() {
        var grouped = dsl.select(USERS.ID, USERS.USERNAME, USERS.ACCOUNT_NON_LOCKED, USERS.ENABLED, USER_ROLES.ROLE)
                .from(USERS)
                .leftJoin(USER_ROLES).on(USER_ROLES.USER_ID.eq(USERS.ID))
                .orderBy(USERS.USERNAME)
                .fetchGroups(USERS.ID);

        return grouped.values().stream()
                .map(rows -> {
                    var first = rows.getFirst();
                    var roles = rows.stream()
                            .map(r -> r.get(USER_ROLES.ROLE))
                            .filter(Objects::nonNull)
                            .toList();
                    return new UserSummary(
                            first.get(USERS.ID),
                            first.get(USERS.USERNAME),
                            roles,
                            Boolean.TRUE.equals(first.get(USERS.ACCOUNT_NON_LOCKED)),
                            Boolean.TRUE.equals(first.get(USERS.ENABLED))
                    );
                })
                .toList();
    }

    @Transactional
    public void lockUser(UUID id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(id))
                .execute();
    }

    @Transactional
    public void unlockUser(UUID id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, true)
                .set(USERS.LOCKED_AT, (OffsetDateTime) null)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.ID.eq(id))
                .execute();
    }

    @Transactional
    public void setRole(UUID id, UserRole role) {
        dsl.deleteFrom(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(id))
                .execute();
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, id)
                .set(USER_ROLES.ROLE, role.name())
                .execute();
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        var user = dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne();
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Wrong current password");
        }
        dsl.update(USERS)
                .set(USERS.PASSWORD, passwordEncoder.encode(newPassword))
                .where(USERS.USERNAME.eq(username))
                .execute();
    }

    @Transactional
    public String resetPassword(UUID id) {
        var password = UUID.randomUUID().toString();
        dsl.update(USERS)
                .set(USERS.PASSWORD, passwordEncoder.encode(password))
                .where(USERS.ID.eq(id))
                .execute();
        return password;
    }

    @Transactional
    public void deleteUser(UUID id, String currentUsername) {
        var user = dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOne();
        if (user != null && user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("Cannot delete own account");
        }
        dsl.deleteFrom(USERS)
                .where(USERS.ID.eq(id))
                .execute();
    }
}
