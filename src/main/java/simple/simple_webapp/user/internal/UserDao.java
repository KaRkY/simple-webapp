package simple.simple_webapp.user.internal;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

import static simple.simple_webapp.user.Tables.USER_ROLES;
import static simple.simple_webapp.user.Tables.USERS;

@Repository
class UserDao {

    private final DSLContext dsl;

    UserDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Long insert(InsertUser insertUser) {
        return dsl.insertInto(USERS)
                .set(USERS.EMAIL, insertUser.email())
                .set(USERS.PASSWORD, insertUser.encodedPassword())
                .set(USERS.ENABLED, insertUser.enabled())
                .set(USERS.ACTIVATION_TOKEN, insertUser.activationToken())
                .set(USERS.ACTIVATION_TOKEN_EXPIRES_AT, insertUser.activationTokenExpiresAt())
                .returningResult(USERS.ID)
                .fetchOne()
                .value1();
    }

    public void insertRole(Long userId, String role) {
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE, role)
                .execute();
    }

    public @Nullable UserRecord findByActivationToken(String token) {
        return dsl.select(USERS.asterisk(), rolesField())
                .from(USERS)
                .where(USERS.ACTIVATION_TOKEN.eq(token))
                .and(USERS.DELETED_AT.isNull())
                .fetchOne(this::toUserRecord);
    }

    public void activate(Long id) {
        dsl.update(USERS)
                .set(USERS.ENABLED, true)
                .setNull(USERS.ACTIVATION_TOKEN)
                .setNull(USERS.ACTIVATION_TOKEN_EXPIRES_AT)
                .where(USERS.ID.eq(id))
                .execute();
    }

    public @Nullable UserRecord findByEmail(String email) {
        return dsl.select(USERS.asterisk(), rolesField())
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .and(USERS.DELETED_AT.isNull())
                .fetchOne(this::toUserRecord);
    }

    public @Nullable UserRecord findByEmailIncludeDeleted(String email) {
        return dsl.select(USERS.asterisk(), rolesField())
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOne(this::toUserRecord);
    }

    public void autoUnlock(Long id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, true)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .set(USERS.LOCKED_AT, (OffsetDateTime) null)
                .where(USERS.ID.eq(id))
                .execute();
    }

    public void incrementFailedAttempts(String email) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, USERS.FAILED_LOGIN_ATTEMPTS.plus(1))
                .where(USERS.EMAIL.eq(email))
                .execute();
    }

    public @Nullable Integer countFailedAttempts(String email) {
        return dsl.select(USERS.FAILED_LOGIN_ATTEMPTS)
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOneInto(Integer.class);
    }

    public void lockByEmail(String email) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now())
                .where(USERS.EMAIL.eq(email))
                .execute();
    }

    public void resetFailedAttempts(String email) {
        dsl.update(USERS)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.EMAIL.eq(email))
                .execute();
    }

    public @Nullable UserRecord findById(Long id) {
        return dsl.select(USERS.asterisk(), rolesField())
                .from(USERS)
                .where(USERS.ID.eq(id))
                .fetchOne(this::toUserRecord);
    }

    public List<UserRecord> findAll(boolean includeDeleted) {
        var condition = includeDeleted ? DSL.noCondition() : USERS.DELETED_AT.isNull();
        return dsl.select(USERS.asterisk(), rolesField())
                .from(USERS)
                .where(condition)
                .orderBy(USERS.EMAIL)
                .fetch(this::toUserRecord);
    }

    public void lock(Long id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, false)
                .set(USERS.LOCKED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(id))
                .execute();
    }

    public void unlock(Long id) {
        dsl.update(USERS)
                .set(USERS.ACCOUNT_NON_LOCKED, true)
                .set(USERS.LOCKED_AT, (OffsetDateTime) null)
                .set(USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .where(USERS.ID.eq(id))
                .execute();
    }

    public void setRoles(Long userId, String role) {
        dsl.deleteFrom(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .execute();
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE, role)
                .execute();
    }

    public void updatePassword(Long id, String encodedPassword) {
        dsl.update(USERS)
                .set(USERS.PASSWORD, encodedPassword)
                .where(USERS.ID.eq(id))
                .execute();
    }

    public void softDelete(Long id) {
        dsl.update(USERS)
                .set(USERS.DELETED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(id))
                .execute();
    }

    record InsertUser(
            String email,
            String encodedPassword,
            boolean enabled,
            @Nullable String activationToken,
            @Nullable OffsetDateTime activationTokenExpiresAt) {
    }

    @SuppressWarnings("unchecked")
    private Field<List<String>> rolesField() {
        return (Field<List<String>>) (Field<?>) DSL.multiset(
                DSL.selectDistinct(USER_ROLES.ROLE)
                        .from(USER_ROLES)
                        .where(USER_ROLES.USER_ID.eq(USERS.ID))
        ).as("roles").convertFrom(r -> r.getValues(USER_ROLES.ROLE));
    }

    private UserRecord toUserRecord(Record r) {
        return new UserRecord(
                r.get(USERS.ID),
                r.get(USERS.EMAIL),
                r.get(USERS.PASSWORD),
                Boolean.TRUE.equals(r.get(USERS.ENABLED)),
                Boolean.TRUE.equals(r.get(USERS.ACCOUNT_NON_EXPIRED)),
                Boolean.TRUE.equals(r.get(USERS.ACCOUNT_NON_LOCKED)),
                Boolean.TRUE.equals(r.get(USERS.CREDENTIALS_NON_EXPIRED)),
                r.get(USERS.FAILED_LOGIN_ATTEMPTS) != null ? r.get(USERS.FAILED_LOGIN_ATTEMPTS) : 0,
                r.get(USERS.LOCKED_AT),
                r.get(USERS.DELETED_AT),
                r.get(USERS.ACTIVATION_TOKEN),
                r.get(USERS.ACTIVATION_TOKEN_EXPIRES_AT),
                r.get("roles", (Class<List<String>>) (Class<?>) List.class)
        );
    }

}
