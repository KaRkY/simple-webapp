---
title: Refactor user module DAO and API
created: 2026-03-19T14:25:19Z
context: userdao-userrecord-refactor
description: >
  UserRecord gets roles (jOOQ multiset); every UserDao method returns UserRecord with roles.
  Remove UserSummary from DAO. Merge UserDao interface into concrete class.
  Introduce CreateUser command record. Drop redundant existsByEmail.
---

assumptions:
- UserRecord stays in `user.internal`.
- UserSummary stays in `user` package — used only at service API and web layer.
- Domain events carry UserSummary — no change.
- Every UserRecord always has roles populated (no split methods).
- findRolesByUserId removed — roles always come with the user via multiset.
- existsByEmail removed — callers already catch DuplicateEmailException.
- UserDao interface has one implementation; merging into concrete class removes dead abstraction.
- rolesField() private helper in UserDao (merged class) avoids duplicating the multiset subquery.

phases:

- phase 1: Add roles to UserRecord
  file: src/main/java/simple/simple_webapp/user/internal/UserRecord.java
  changes:
    - add: List<String> roles  (last field)

- phase 2: Rewrite UserDao interface
  file: src/main/java/simple/simple_webapp/user/internal/UserDao.java
  changes:
    - remove: import UserSummary
    - remove: findRolesByUserId(UUID)
    - remove: existsByEmail(String)
    - rename+retype: findAllWithRoles(boolean) → findAll(boolean) returning List<UserRecord>

- phase 3: Rewrite UserDaoImpl
  file: src/main/java/simple/simple_webapp/user/internal/UserDaoImpl.java
  changes:
    - remove: import UserSummary
    - remove: findRolesByUserId method
    - remove: existsByEmail method
    - add private helper:
        private Field<List<String>> rolesField() {
            return DSL.multiset(
                DSL.selectDistinct(USER_ROLES.ROLE)
                   .from(USER_ROLES)
                   .where(USER_ROLES.USER_ID.eq(USERS.ID))
            ).as("roles").convertFrom(r -> r.getValues(USER_ROLES.ROLE));
            // convertFrom is correct here: field-level T→U conversion on the multiset Field<Result>
        }
    - replace toUserRecord(UsersRecord) with toUserRecord(Record r) used as fetch(this::toUserRecord):
        // convertFrom is NOT used for full-row mapping — it is field-level only.
        // fetchInto(UserRecord.class) won't work: roles comes from a computed multiset field, not a column.
        // fetch(this::toUserRecord) is the correct idiomatic pattern.
        new UserRecord(r.get(USERS.ID), ..., r.get(rolesField()))
    - rewrite findById, findByEmail, findByEmailIncludeDeleted, findByActivationToken:
        dsl.select(USERS.asterisk(), rolesField()).from(USERS).where(...).fetchOne(this::toUserRecord)
    - rename+rewrite findAllWithRoles → findAll:
        dsl.select(USERS.asterisk(), rolesField()).from(USERS).where(condition)
           .orderBy(USERS.EMAIL).fetch(this::toUserRecord)

- phase 4: Update UserManagementImpl
  file: src/main/java/simple/simple_webapp/user/internal/UserManagementImpl.java
  changes:
    - loadUserByUsername: replace findRolesByUserId call with user.roles()
    - findById: remove findRolesByUserId call; build UserSummary from user.roles()
    - findAll: call userDao.findAll(), map UserRecord → UserSummary via user.roles()
    - register: publish new UserRegisteredEvent(id, token) — no findById call needed
    - registerAndActivate: publish new UserRegisteredEvent(id, null) and new UserActivatedEvent(id)
        remove: UserSummary userSummary = findById(id) (no longer needed before publishing)
    - activateUser: publish new UserActivatedEvent(user.id()) — user is already the UserRecord from DAO

- phase 5: Slim down domain events
  changes:
    - UserRegisteredEvent.java:
        change: UserSummary user → UUID userId
        remove: import UserSummary; add: import UUID
    - UserActivatedEvent.java:
        change: UserSummary user → UUID userId
        remove: import UserSummary; add: import UUID
  note: no listener reads event fields (ModulesEventListener is a stub);
        tests only check event type — no test changes required

- phase 7: Simplify initializers
  changes:
    - DefaultUserInitializer.java:
        remove existsByEmail guard; remove UserDao field + constructor param
    - DefaultAdminInitializer.java:
        remove existsByEmail guard only; keep UserDao (still uses findByEmailIncludeDeleted)

- phase 8: Merge UserDao interface → concrete class
  changes:
    - delete: src/main/java/simple/simple_webapp/user/internal/UserDao.java (interface)
    - rename: UserDaoImpl.java → UserDao.java
    - change declaration: class UserDaoImpl implements UserDao → class UserDao
    - no other files change — injection points already reference the UserDao type name

- phase 9: Introduce CreateUser command record
  new file: src/main/java/simple/simple_webapp/user/CreateUser.java
    public record CreateUser(String email, String password) {}
  changes:
    - UserManagement.java:
        register(String, String) → register(CreateUser)
        registerAndActivate(String, String) → registerAndActivate(CreateUser)
    - UserManagementImpl.java: read command.email() / command.password()
    - UserController.java: pass new CreateUser(email, password) to register()
    - DefaultUserInitializer.java + DefaultAdminInitializer.java: wrap args in new CreateUser(...)

- phase 10: Fix tests
  changes:
    - UserManagementTests.java:
        all calls: register(email, "password") → register(new CreateUser(email, "password"))
        all calls: registerAndActivate(email, "password") → registerAndActivate(new CreateUser(email, "password"))
        add import: simple.simple_webapp.user.CreateUser
    - UserControllerTests.java:
        line 74: .when(userManagement).register("alice@example.com", "secret")
              → .when(userManagement).register(new CreateUser("alice@example.com", "secret"))
        add import: simple.simple_webapp.user.CreateUser
    - AdminControllerTests.java: no changes (UserSummary construction and UserManagement mocks unaffected)
    - DefaultUserInitializerTests.java: no changes (idempotency behaviour preserved via DuplicateEmailException catch)
    - DefaultAdminInitializerTests.java: no changes

- phase 11: Verify & test
  commands:
    - ./mvnw test -Dtest=UserManagementTests,AdminControllerTests,UserControllerTests
    - ./mvnw test

open_questions: []

status: ready
