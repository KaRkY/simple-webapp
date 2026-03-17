---
title: Email-as-username + activation email
created: 2026-03-17T19:37:00Z
context: email-username-activation
description: Rename username→email in DB, block login until activation link is clicked, send activation email via SMTP.
---

assumptions:
- App is not in production: V1__init.sql is edited in-place (no V2 migration needed)
- DB column `username` renamed to `email` directly in V1__init.sql
- Activation token: UUID string, stored in DB, 24h expiry
- Unactivated login shows "Please activate your account" (not a generic error)
- Email for dev/test: Mailhog already wired in TestcontainersConfiguration (SMTP host/port injected dynamically)
- Email for prod: no SMTP config yet; application.yaml uses env-var stubs (MAIL_HOST, etc.)
- spring-boot-starter-mail added; dev mail works via Mailhog, prod config is TBD
- Default seeded users (test@example.com, admin@example.com) are pre-activated — no email sent
- Spring Security form login reconfigured to use parameter name `email` instead of `username`

---

phases:

  - name: Phase 1 — Dependency
    files:
      - pom.xml
    changes:
      - Add inside <dependencies>:
          <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
          </dependency>

  - name: Phase 2 — Edit V1 migration in place
    files:
      - src/main/resources/db/migration/user/V1__init.sql (EDIT)
    changes: |
      -- rename column
      username VARCHAR(255) NOT NULL  →  email VARCHAR(255) NOT NULL
      -- rename constraint
      CONSTRAINT users_username_unique UNIQUE (username)  →  CONSTRAINT users_email_unique UNIQUE (email)
      -- add columns
      activation_token               VARCHAR(255)             NULL,
      activation_token_expires_at    TIMESTAMP WITH TIME ZONE NULL,
    note: App is not in prod; editing V1 in-place is safe. jOOQ codegen picks this up on next
          ./mvnw generate-sources and regenerates USERS.EMAIL, USERS.ACTIVATION_TOKEN etc.

  - name: Phase 3 — jOOQ regeneration
    command: ./mvnw generate-sources   # requires Docker
    note: Must run before editing Java code so generated USERS.EMAIL field exists.

  - name: Phase 4 — Rename DuplicateUsernameException → DuplicateEmailException
    files:
      - src/main/java/simple/simple_webapp/user/DuplicateUsernameException.java (DELETE + recreate as DuplicateEmailException.java)
    changes:
      - Class name: DuplicateEmailException
      - Message: "Email already registered: " + email
      - Update all references in UserManagement.java, UserController.java

  - name: Phase 5 — UserSummary record
    files:
      - src/main/java/simple/simple_webapp/user/UserSummary.java
    changes:
      - Rename field: username → email
      - Signature: record UserSummary(UUID id, String email, List<String> roles, boolean accountNonLocked, boolean enabled, boolean deleted)

  - name: Phase 6 — New ActivationEmailService
    files:
      - src/main/java/simple/simple_webapp/user/ActivationEmailService.java (CREATE)
    changes: |
      @Service
      class ActivationEmailService {
        private final JavaMailSender mailSender;
        @Value("${app.base-url}") String baseUrl;
        @Value("${app.mail.from}") String from;

        void sendActivationEmail(String to, String token) {
          // SimpleMailMessage with subject "Activate your account"
          // body contains: baseUrl + "/activate?token=" + token
          // valid for 24 hours
        }
      }

  - name: Phase 7 — UserManagement (major refactor)
    files:
      - src/main/java/simple/simple_webapp/user/UserManagement.java
    changes:
      - Add ActivationEmailService dependency
      - register(String email, String password):
          * Creates user with enabled=FALSE
          * Generates UUID activation token + expires_at = now+24h
          * Stores token in DB (USERS.ACTIVATION_TOKEN, USERS.ACTIVATION_TOKEN_EXPIRES_AT)
          * Calls activationEmailService.sendActivationEmail(email, token)
          * Constraint check: "users_email_unique" (was "users_username_unique")
          * Throws DuplicateEmailException (was DuplicateUsernameException)
      - ADD registerAndActivate(String email, String password):
          * Same as register but enabled=TRUE, no token, no email — used by seeders
      - ADD activateUser(String token):
          * Finds user by USERS.ACTIVATION_TOKEN.eq(token) AND DELETED_AT is null
          * Throws IllegalArgumentException("Invalid or expired token") if not found
          * Checks ACTIVATION_TOKEN_EXPIRES_AT; throws same if expired
          * Sets ENABLED=true, clears ACTIVATION_TOKEN + ACTIVATION_TOKEN_EXPIRES_AT
      - loadUserByUsername(String email):
          * Change USERS.USERNAME → USERS.EMAIL
      - recordFailedAttempt / recordSuccessfulLogin:
          * Change USERS.USERNAME → USERS.EMAIL
      - changePassword(String email, ...):
          * Change USERS.USERNAME → USERS.EMAIL
      - findById:
          * Return UserSummary with email field (user.getEmail())
      - findAll:
          * Change to use USERS.EMAIL
      - deleteUser(UUID id, String currentEmail):
          * Compare by email: user.getEmail().equals(currentEmail)

  - name: Phase 8 — SecurityConfiguration
    files:
      - src/main/java/simple/simple_webapp/SecurityConfiguration.java
    changes:
      - Permit /activate in requestMatchers
      - formLogin: add .usernameParameter("email")
      - Add custom AuthenticationFailureHandler bean:
          * If exception is DisabledException → redirect /login?not-activated
          * Else → redirect /login?error
      - Wire: formLogin.failureHandler(customFailureHandler())

  - name: Phase 9 — UserController
    files:
      - src/main/java/simple/simple_webapp/user/UserController.java
    changes:
      - register(@RequestParam String email, @RequestParam String password, Model model):
          * Calls userManagement.register(email, password)
          * On DuplicateEmailException: model.addAttribute("error", "Email already registered")
          * On success: redirect:/login?check-email
      - ADD @GetMapping("/activate"):
          * @RequestParam String token
          * try { userManagement.activateUser(token); return "redirect:/login?activated"; }
          * catch (IllegalArgumentException) { return "redirect:/login?activation-failed"; }

  - name: Phase 10 — DefaultUserInitializer
    files:
      - src/main/java/simple/simple_webapp/user/DefaultUserInitializer.java
    changes:
      - USER_EMAIL = "test@example.com"  (was USER_USERNAME = "test")
      - USER_PASSWORD = "test"
      - Lookup: USERS.EMAIL.eq(USER_EMAIL)
      - Call: userManagement.registerAndActivate(USER_EMAIL, USER_PASSWORD)
      - Log message updated to show Email label

  - name: Phase 11 — DefaultAdminInitializer
    files:
      - src/main/java/simple/simple_webapp/user/DefaultAdminInitializer.java
    changes:
      - ADMIN_EMAIL = "admin@example.com"  (was ADMIN_USERNAME = "admin")
      - Lookup: USERS.EMAIL.eq(ADMIN_EMAIL)
      - Call: userManagement.registerAndActivate(ADMIN_EMAIL, password)
      - Log message updated

  - name: Phase 12 — Templates
    files:
      - src/main/resources/templates/register.html
      - src/main/resources/templates/login.html
      - src/main/resources/templates/admin/users.html
      - src/main/resources/templates/admin/user-row.html
    changes:
      register.html:
        - Label "Username" → "Email"
        - Input: type="email", name="email" (was type="text", name="username")
        - autocomplete="email"
      login.html:
        - Label "Username" → "Email"
        - Input: type="email", name="email" (was name="username")
        - autocomplete="email"
        - ADD message blocks:
            ?not-activated  → "Please activate your account. Check your inbox."
            ?check-email    → "Registration successful. Check your email to activate your account."
            ?activated      → "Account activated! You can now sign in."
            ?activation-failed → "Activation link is invalid or has expired."
        - Remove ?registered block (replaced by ?check-email)
      admin/users.html + admin/user-row.html:
        - Display user.email instead of user.username

  - name: Phase 13 — application.yaml
    files:
      - src/main/resources/application.yaml
    changes: |
      app:
        base-url: ${APP_BASE_URL:http://localhost:8080}
        mail:
          from: ${MAIL_FROM:noreply@example.com}

  - name: Phase 14 — Tests
    files:
      - src/test/java/simple/simple_webapp/user/UserManagementTests.java
      - src/test/java/simple/simple_webapp/user/UserControllerTests.java
      - src/test/java/simple/simple_webapp/user/DefaultUserInitializerTests.java
      - src/test/java/simple/simple_webapp/user/DefaultAdminInitializerTests.java
      - src/test/java/simple/simple_webapp/user/AdminControllerTests.java
    changes:
      UserManagementTests:
        - uniqueUsername() → uniqueEmail() returning "user-<uuid>@example.com"
        - All register() calls → registerAndActivate() (tests don't cover email flow)
        - DuplicateUsernameException → DuplicateEmailException
        - USERS.USERNAME.eq(...) → USERS.EMAIL.eq(...)
        - UserSummary.username() → UserSummary.email()
        - registerHappyPath: enabled=true after registerAndActivate (passes)
        - ADD activateUserHappyPath: register() → activateUser(token) → loadUserByUsername → isEnabled=true
        - ADD activateUserExpiredTokenThrows: register(), manually set expires_at to past, activateUser throws
        - ADD activateUserInvalidTokenThrows
      UserControllerTests:
        - Add @MockBean JavaMailSender (so register() doesn't attempt real SMTP in controller-only tests)
        - Update field name "email" (was "username") in form params
        - Update expected redirect from ?registered → ?check-email
        - Add test for GET /activate?token=valid → redirects ?activated
        - Add test for GET /activate?token=bad → redirects ?activation-failed
      DefaultUserInitializerTests + DefaultAdminInitializerTests:
        - Update email constants
      AdminControllerTests:
        - Update any username references to email

open_questions: []

status: ready
