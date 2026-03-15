---
title: Soft Delete Users
created: 2026-03-15T21:16:32Z
context: soft-delete-users
description: Replace hard delete with soft delete (deleted_at timestamp)
---

assumptions:
- deleted_at IS NULL = active user; NOT NULL = soft-deleted
- Soft-deleted users cannot log in (loadUserByUsername filters them)
- Soft-deleted users are hidden from the admin user list (findAll filters them)
- Self-delete protection stays: cannot soft-delete own account
- @DeleteMapping kept for API compat, also soft-deletes
- jOOQ classes regenerate automatically on next Maven build (testcontainers-jooq-codegen-maven-plugin)

phases:

  - phase: 1 — DB migration
    files:
      - src/main/resources/db/migration/user/V2__soft_delete.sql
    changes:
      - ALTER TABLE "user".users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE NULL;

  - phase: 2 — Update UserManagement
    file: src/main/java/simple/simple_webapp/user/UserManagement.java
    changes:
      - deleteUser: replace dsl.deleteFrom(USERS) with:
          dsl.update(USERS)
             .set(USERS.DELETED_AT, OffsetDateTime.now())
             .where(USERS.ID.eq(id))
             .execute();
      - findAll: add .where(USERS.DELETED_AT.isNull()) to the query
      - loadUserByUsername: add .and(USERS.DELETED_AT.isNull()) to the where clause
        (deleted users → UsernameNotFoundException, same as not found)

  - phase: 3 — Update tests
    file: src/test/java/simple/simple_webapp/user/UserManagementTests.java
    changes:
      - Add test: deleteUser sets deleted_at, does NOT remove the row
      - Add test: findAll excludes soft-deleted users
      - Add test: loadUserByUsername throws UsernameNotFoundException for soft-deleted user

  - phase: 4 — Run tests
    commands:
      - .\mvnw test

open_questions: []

status: ready
