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
    private final ActivationEmailService activationEmailService;

    public UserManagement(DSLContext dsl, PasswordEncoder passwordEncoder, ActivationEmailService activationEmailService) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.activationEmailService = activationEmailService;
    }

    @Transactional
    public void register(String email, String password) {
        var id = UUID.randomUUID();
        var token = UUID.randomUUID().toString();
        try {
            dsl.insertInto(USERS)
                    .set(USERS.ID, id)
                    .set(USERS.EMAIL, email)
                    .set(USERS.PASSWORD, passwordEncoder.encode(password))
                    .set(USERS.ENABLED, false)
                    .set(USERS.ACTIVATION_TOKEN, token)
                    .set(USERS.ACTIVATION_TOKEN_EXPIRES_AT, OffsetDateTime.now().plusHours(24))
                    .execute();
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_email_unique")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, id)
                .set(USER_ROLES.ROLE, UserRole.USER.name())
                .execute();
        activationEmailService.sendActivationEmail(email, token);
    }

    @Transactional
    public void registerAndActivate(String email, String password) {
        var id = UUID.randomUUID();
        try {
            dsl.insertInto(USERS)
                    .set(USERS.ID, id)
                    .set(USERS.EMAIL, email)
                    .set(USERS.PASSWORD, passwordEncoder.encode(password))
                    .set(USERS.ENABLED, true)
                    .execute();
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_email_unique")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, id)
                .set(USER_ROLES.ROLE, UserRole.USER.name())
                .execute();
    }

    @Transactional
    public void activateUser(String token) {
        var user = dsl.selectFrom(USERS)
                .where(USERS.ACTIVATION_TOKEN.eq(token))
                .and(USERS.DELETED_AT.isNull())
                .fetchOne();
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        if (user.getActivationTokenExpiresAt() == null
                || user.getActivationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        dsl.update(USERS)
                .set(USERS.ENABLED, true)
                .setNull(USERS.ACTIVATION_TOKEN)
                .setNull(USERS.ACTIVATION_TOKEN_EXPIRES_AT)
                .where(USERS.ID.eq(user.getId()))
                .execute();
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .and(USERS.DELETED_AT.isNull())
                .fetchOne();

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + email);
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
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getAccountNonExpired()),
                Boolean.TRUE.equals(user.getCredentialsNonExpired()),
                accountNonLocked,
                authorities
        );
    }

    @Transactional
    public void recordFailedAttempt(String email) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, USERS.FAILED_LOGIN_ATTEMPTS.plus(1))
                .where(USERS.EMAIL.eq(email))
                .execute();

        var attempts = dsl.select(USERS.FAILED_LOGIN_ATTEMPTS)
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOneInto(Integer.class);

        if (attempts != null && attempts >= 3) {
            dsl.update(USERS)
                    .set(USERS.ACCOUNT_NON_LOCKED, false)
                    .set(USERS.LOCKED_AT, OffsetDateTime.now())
                    .where(USERS.EMAIL.eq(email))
                    .execute();
        }
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.EMAIL.eq(email))
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
                user.getEmail(),
                roles,
                Boolean.TRUE.equals(user.getAccountNonLocked()),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getDeletedAt() != null,
                !Boolean.TRUE.equals(user.getEnabled()) && user.getActivationToken() != null
        );
    }

    public List<UserSummary> findAll(boolean includeDeleted) {
        var condition = includeDeleted
                ? org.jooq.impl.DSL.noCondition()
                : USERS.DELETED_AT.isNull();

        var grouped = dsl.select(USERS.ID, USERS.EMAIL, USERS.ACCOUNT_NON_LOCKED, USERS.ENABLED, USERS.DELETED_AT, USERS.ACTIVATION_TOKEN, USER_ROLES.ROLE)
                .from(USERS)
                .leftJoin(USER_ROLES).on(USER_ROLES.USER_ID.eq(USERS.ID))
                .where(condition)
                .orderBy(USERS.EMAIL)
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
                            first.get(USERS.EMAIL),
                            roles,
                            Boolean.TRUE.equals(first.get(USERS.ACCOUNT_NON_LOCKED)),
                            Boolean.TRUE.equals(first.get(USERS.ENABLED)),
                            first.get(USERS.DELETED_AT) != null,
                            !Boolean.TRUE.equals(first.get(USERS.ENABLED)) && first.get(USERS.ACTIVATION_TOKEN) != null
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
    public void changePassword(String email, String currentPassword, String newPassword) {
        var user = dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOne();
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + email);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Wrong current password");
        }
        dsl.update(USERS)
                .set(USERS.PASSWORD, passwordEncoder.encode(newPassword))
                .where(USERS.EMAIL.eq(email))
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
    public void deleteUser(UUID id, String currentEmail) {
        var user = dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOne();
        if (user != null && user.getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException("Cannot delete own account");
        }
        dsl.update(USERS)
                .set(USERS.DELETED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(id))
                .execute();
    }
}
