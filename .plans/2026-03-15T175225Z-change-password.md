---
title: Change password functionality
created: 2026-03-15T17:52:25Z
context: change-password
description: User self-service change password + admin reset with auto-generated password.
---

assumptions:
- User change-password: requires current password verification before updating.
- New password is not confirmed (no repeat field) — keeps form simple.
- Admin reset: auto-generates UUID password, displays it in the HTMX row response (one-time).
- Both operations reuse existing PasswordEncoder + jOOQ USERS table.
- credentialsNonExpired stays true after change (no forced re-login required).
- Authenticated user resolved from Spring Security principal (@AuthenticationPrincipal).
- SecurityConfiguration already permits /login, /register; must permit /account/**.

phases:
  - phase: 1 — Service layer
    files:
      - src/main/java/simple/simple_webapp/user/UserManagement.java
        add method:
          @Transactional
          public void changePassword(String username, String currentPassword, String newPassword)
            - load raw password hash via dsl.selectFrom(USERS).where(USERNAME.eq(username))
            - if user == null → throw UsernameNotFoundException
            - if !passwordEncoder.matches(currentPassword, user.getPassword())
                → throw new BadCredentialsException("Wrong current password")
            - dsl.update(USERS).set(PASSWORD, encode(newPassword)).where(USERNAME.eq(username))

        add method:
          @Transactional
          public String resetPassword(UUID id)
            - generate password = UUID.randomUUID().toString()
            - dsl.update(USERS).set(PASSWORD, encode(password)).where(ID.eq(id))
            - return password   ← caller logs / returns to admin

  - phase: 2 — User self-service web layer
    files:
      - src/main/java/simple/simple_webapp/user/UserController.java
        add:
          @GetMapping("/account/change-password")
          String changePasswordForm() → return "account/change-password"

          @PostMapping("/account/change-password")
          String changePassword(@RequestParam String currentPassword,
                                @RequestParam String newPassword,
                                @AuthenticationPrincipal UserDetails user,
                                Model model)
            - call userManagement.changePassword(user.getUsername(), currentPassword, newPassword)
            - on success → redirect:/account/change-password?success
            - on BadCredentialsException → model.addAttribute("error", "Current password is incorrect")
                                           return "account/change-password"

      - src/main/resources/templates/account/change-password.html  (NEW)
        Standard layout. Form: current password, new password. Shows error/success messages.

      - src/main/java/simple/simple_webapp/SecurityConfiguration.java
        update requestMatchers to add /account/** → authenticated (already covered by
        anyRequest().authenticated() but ensure /account/** is not accidentally excluded).

  - phase: 3 — Admin reset web layer
    files:
      - src/main/java/simple/simple_webapp/user/AdminController.java
        add:
          @PostMapping("/users/{id}/reset-password")
          String resetPassword(@PathVariable UUID id, Model model)
            - String tempPassword = userManagement.resetPassword(id)
            - model.addAttribute("user", userManagement.findById(id))
            - model.addAttribute("tempPassword", tempPassword)
            - return "admin/user-row :: userRow"

      - src/main/resources/templates/admin/user-row.html
        update userRow fragment:
          - add a "Reset password" button → hx-post="/admin/users/{id}/reset-password"
          - if tempPassword model attr present: show it inline in the row (th:if="${tempPassword}")
            with a note "One-time password — copy now"

  - phase: 4 — Tests
    files:
      - src/test/java/simple/simple_webapp/user/UserManagementTests.java
        add:
          changePasswordHappyPath()
          changePasswordWrongCurrentPasswordThrows()
          resetPasswordReturnsNewEncodedPassword()

      - src/test/java/simple/simple_webapp/user/UserControllerTests.java
        add:
          changePasswordFormReturns200()
          changePasswordSuccessRedirects()
          changePasswordWrongCurrentReturnsError()

      - src/test/java/simple/simple_webapp/user/AdminControllerTests.java
        add:
          resetPasswordReturnsPartialRow()

open_questions: []

status: ready
