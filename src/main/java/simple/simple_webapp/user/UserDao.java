package simple.simple_webapp.user;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static simple.simple_webapp.user.Tables.USER_ROLES;
import static simple.simple_webapp.user.Tables.USERS;

@Repository
class UserDao {

    private final DSLContext dsl;

    UserDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    void insert(UUID id, String email, String encodedPassword, boolean enabled,
                @Nullable String activationToken, @Nullable OffsetDateTime activationTokenExpiresAt) {
        dsl.insertInto(USERS)
                .set(USERS.ID, id)
                .set(USERS.EMAIL, email)
                .set(USERS.PASSWORD, encodedPassword)
                .set(USERS.ENABLED, enabled)
                .set(USERS.ACTIVATION_TOKEN, activationToken)
                .set(USERS.ACTIVATION_TOKEN_EXPIRES_AT, activationTokenExpiresAt)
                .execute();
    }

    void insertRole(UUID userId, String role) {
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE, role)
                .execute();
    }

    @Nullable
    UserRecord findByActivationToken(String token) {
        return dsl.selectFrom(USERS)
                .where(USERS.ACTIVATION_TOKEN.eq(token))
                .and(USERS.DELETED_AT.isNull())
                .fetchOne(this::toUserRecord);
    }

    void activate(UUID id) {
        dsl.update(USERS)
                .set(USERS.ENABLED, true)
                .setNull(USERS.ACTIVATION_TOKEN)
                .setNull(USERS.ACTIVATION_TOKEN_EXPIRES_AT)
                .where(USERS.ID.eq(id))
                .execute();
    }

    @Nullable
    UserRecord findByEmail(String email) {
        return dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .and(USERS.DELETED_AT.isNull())
                .fetchOne(this::toUserRecord);
    }

    @Nullable
    UserRecord findByEmailIncludeDeleted(String email) {
        return dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOne(this::toUserRecord);
    }

    void autoUnlock(UUID id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, true)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .set(USERS.LOCKED_AT, (OffsetDateTime) null)
                .where(USERS.ID.eq(id))
                .execute();
    }

    List<String> findRolesByUserId(UUID userId) {
        return dsl.select(USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .fetchInto(String.class);
    }

    void incrementFailedAttempts(String email) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, USERS.FAILED_LOGIN_ATTEMPTS.plus(1))
                .where(USERS.EMAIL.eq(email))
                .execute();
    }

    @Nullable
    Integer countFailedAttempts(String email) {
        return dsl.select(USERS.FAILED_LOGIN_ATTEMPTS)
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOneInto(Integer.class);
    }

    void lockByEmail(String email) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now())
                .where(USERS.EMAIL.eq(email))
                .execute();
    }

    void resetFailedAttempts(String email) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.EMAIL.eq(email))
                .execute();
    }

    @Nullable
    UserRecord findById(UUID id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOne(this::toUserRecord);
    }

    List<UserSummary> findAllWithRoles(boolean includeDeleted) {
        var condition = includeDeleted ? DSL.noCondition() : USERS.DELETED_AT.isNull();

        var grouped = dsl.select(USERS.ID, USERS.EMAIL, USERS.ACCOUNT_NON_LOCKED, USERS.ENABLED,
                        USERS.DELETED_AT, USERS.ACTIVATION_TOKEN, USER_ROLES.ROLE)
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

    void lock(UUID id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(id))
                .execute();
    }

    void unlock(UUID id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, true)
                .set(USERS.LOCKED_AT, (OffsetDateTime) null)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.ID.eq(id))
                .execute();
    }

    void setRoles(UUID userId, String role) {
        dsl.deleteFrom(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .execute();
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE, role)
                .execute();
    }

    void updatePassword(UUID id, String encodedPassword) {
        dsl.update(USERS)
                .set(USERS.PASSWORD, encodedPassword)
                .where(USERS.ID.eq(id))
                .execute();
    }

    void softDelete(UUID id) {
        dsl.update(USERS)
                .set(USERS.DELETED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(id))
                .execute();
    }

    boolean existsByEmail(String email) {
        return dsl.fetchExists(
                dsl.selectOne().from(USERS).where(USERS.EMAIL.eq(email)));
    }

    private UserRecord toUserRecord(simple.simple_webapp.user.tables.records.UsersRecord r) {
        return new UserRecord(
                r.getId(),
                r.getEmail(),
                r.getPassword(),
                Boolean.TRUE.equals(r.getEnabled()),
                Boolean.TRUE.equals(r.getAccountNonExpired()),
                Boolean.TRUE.equals(r.getAccountNonLocked()),
                Boolean.TRUE.equals(r.getCredentialsNonExpired()),
                r.getFailedLoginAttempts() != null ? r.getFailedLoginAttempts() : 0,
                r.getLockedAt(),
                r.getDeletedAt(),
                r.getActivationToken(),
                r.getActivationTokenExpiresAt()
        );
    }
}
