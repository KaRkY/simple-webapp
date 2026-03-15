---
title: Default Test User
created: 2026-03-15T21:31:33Z
context: default-test-user
description: Add DefaultUserInitializer that seeds a default "user" account on startup
---

assumptions:
- Mirrors DefaultAdminInitializer pattern exactly
- Username: "test", password: "test" (fixed, not random)
- Guard condition: skip if username "test" already exists in DB (checks USERS table)
- ROLE_USER assigned automatically by register() — no setRole() needed
- Runs in main (not dev-only profile), same as DefaultAdminInitializer

phases:

  - phase: 1 — Create DefaultUserInitializer
    file: src/main/java/simple/simple_webapp/user/DefaultUserInitializer.java
    changes:
      - @Component class implementing ApplicationRunner
      - USER_USERNAME = "test", USER_PASSWORD = "test"
      - Guard: dsl.fetchExists(USERS.where(USERS.USERNAME.eq(USER_USERNAME))) → return if true
      - Call userManagement.register(USER_USERNAME, USER_PASSWORD) wrapped in try/catch DuplicateUsernameException
      - log.info(...) with username + password

  - phase: 2 — Create DefaultUserInitializerTests
    file: src/test/java/simple/simple_webapp/user/DefaultUserInitializerTests.java
    changes:
      - @SpringBootTest + @Testcontainers + @Import(TestcontainersConfiguration)
      - Test: userExistsAfterStartup — loadUserByUsername("test") has ROLE_USER
      - Test: secondRunIsIdempotent — run twice, count USER-role entries for username "test" = 1

  - phase: 3 — Run tests
    commands:
      - .\mvnw test

open_questions: []

status: ready
