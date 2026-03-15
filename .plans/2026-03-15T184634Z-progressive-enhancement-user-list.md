---
title: Progressive enhancement for admin user list
created: 2026-03-15T18:46:34Z
context: progressive-enhancement-user-list
description: Refactor admin user list so all functionality works without JS. Server-render rows on page load. All actions use native HTML forms; HTMX enhances them when JS is available.
---

assumptions:
- htmx-spring-boot-thymeleaf 5.0.0 is already in pom.xml — no new dependency needed.
- @HxRequest (io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest) marks a handler that
  only matches requests with HX-Request: true header. Two methods with the same path:
  one @HxRequest (fragment), one plain (redirect). No HttpServletRequest or manual header checks.
- CSRF tokens added to every action form inside user-row.html.

phases:
- phase 1 — AdminController changes
    file: src/main/java/simple/simple_webapp/user/AdminController.java

    Add import: jakarta.servlet.http.HttpServletRequest

    Add private helper:
      private boolean isHtmxRequest(HttpServletRequest request) {
          return "true".equals(request.getHeader("HX-Request"));
      }

    GET /admin/users — add model param, populate users:
      @GetMapping("/users")
      String users(Model model) {
          model.addAttribute("users", userManagement.findAll());
          return "admin/users";
      }

    lockUser, unlockUser, setRole, resetPassword — add HttpServletRequest param,
    check isHtmxRequest; if false return "redirect:/admin/users"; else return fragment as before.
    Change return type from String to Object.

    New endpoint (POST delete fallback):
      @PostMapping("/users/{id}/delete")
      Object deleteUserFallback(@PathVariable UUID id,
                                @AuthenticationPrincipal UserDetails currentUser,
                                HttpServletRequest request) {
          try {
              userManagement.deleteUser(id, currentUser.getUsername());
          } catch (IllegalArgumentException e) {
              if (isHtmxRequest(request)) return ResponseEntity.status(403).body(e.getMessage());
              return "redirect:/admin/users";
          }
          if (isHtmxRequest(request)) return ResponseEntity.ok("");
          return "redirect:/admin/users";
      }

- phase 2 — users.html: server-render tbody
    file: src/main/resources/templates/admin/users.html

    Replace:
      <tbody id="user-table-body"
             hx-get="/admin/users/list"
             hx-trigger="load"
             hx-swap="innerHTML">
      </tbody>
    With:
      <tbody id="user-table-body">
          <th:block th:insert="~{admin/user-row :: userRows}"></th:block>
      </tbody>

- phase 3 — user-row.html: replace hx-only buttons with native forms + HTMX enhancement
    file: src/main/resources/templates/admin/user-row.html

    Lock button → form:
      <form method="post" th:action="@{/admin/users/{id}/lock(id=${user.id()})}"
            hx-target="closest tr" hx-swap="outerHTML">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <button type="submit">Lock</button>
      </form>

    Unlock button → form:
      <form method="post" th:action="@{/admin/users/{id}/unlock(id=${user.id()})}"
            hx-target="closest tr" hx-swap="outerHTML">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <button type="submit">Unlock</button>
      </form>

    Role form — change th:hx-post to native th:action, add CSRF, keep hx-target/hx-swap:
      <form method="post" th:action="@{/admin/users/{id}/role(id=${user.id()})}"
            hx-target="closest tr" hx-swap="outerHTML">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <select name="role">...</select>
        <button type="submit">Set role</button>
      </form>

    Delete button → form (POST to /delete endpoint):
      <form method="post" th:action="@{/admin/users/{id}/delete(id=${user.id()})}"
            hx-target="closest tr" hx-swap="outerHTML"
            hx-confirm="Delete this user?">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <button type="submit">Delete</button>
      </form>

    Reset password button → form:
      <form method="post" th:action="@{/admin/users/{id}/reset-password(id=${user.id()})}"
            hx-target="closest tr" hx-swap="outerHTML"
            hx-confirm="Reset this user's password?">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <button type="submit">Reset password</button>
      </form>

- phase 4 — Tests
    file: src/test/java/simple/simple_webapp/user/AdminControllerTests.java

    Update existing GET /admin/users test: verify model contains "users".
    For each action endpoint (lock/unlock/role/reset-password):
      - With HX-Request header → assert returns 200 with HTML fragment (existing behavior)
      - Without HX-Request header → assert redirects to /admin/users
    For POST /admin/users/{id}/delete:
      - With HX-Request header → 200, empty body
      - Without HX-Request header → redirect to /admin/users
      - Self-delete attempt → redirect (or 403 for HTMX)

- phase 5 — Verify
    command: ./mvnw test
    expected: BUILD SUCCESS, all tests pass

open_questions: []

status: ready
