---
title: Show Deleted Users Toggle
created: 2026-03-15T21:23:50Z
context: show-deleted-users
description: Add toggle to show/hide soft-deleted users in admin user list
---

assumptions:
- Deleted users shown with "Deleted" badge; action buttons hidden for them
- Toggle is a link: ?showDeleted=true / ?showDeleted=false (full page reload, no HTMX needed)
- findAll(boolean includeDeleted): false = exclude deleted (current), true = include all
- UserSummary gains boolean deleted field
- No restore action in scope

phases:

  - phase: 1 — Update UserSummary
    file: src/main/java/simple/simple_webapp/user/UserSummary.java
    changes:
      - Add boolean deleted as last field:
          public record UserSummary(UUID id, String username, List<String> roles,
                                    boolean accountNonLocked, boolean enabled, boolean deleted) {}

  - phase: 2 — Update UserManagement
    file: src/main/java/simple/simple_webapp/user/UserManagement.java
    changes:
      - findAll(): replace with findAll(boolean includeDeleted)
          - Add USERS.DELETED_AT to select
          - When !includeDeleted: .where(USERS.DELETED_AT.isNull())
          - Map deleted_at != null to UserSummary.deleted
      - findById(): add USERS.DELETED_AT to query; pass deleted_at != null to UserSummary

  - phase: 3 — Update AdminController
    file: src/main/java/simple/simple_webapp/user/AdminController.java
    changes:
      - GET /admin/users: add @RequestParam(defaultValue = "false") boolean showDeleted
          - Call userManagement.findAll(showDeleted)
          - Add showDeleted to model: model.addAttribute("showDeleted", showDeleted)
      - All other findAll() calls: update to findAll(false)

  - phase: 4 — Update users.html
    file: src/main/resources/templates/admin/users.html
    changes:
      - Add toggle link below heading:
          <a th:href="@{/admin/users(showDeleted=${!showDeleted})}"
             class="btn btn-sm btn-outline"
             th:text="${showDeleted} ? 'Hide deleted users' : 'Show deleted users'">
          </a>

  - phase: 5 — Update user-row.html
    file: src/main/resources/templates/admin/user-row.html
    changes:
      - Status td: add th:if deleted → show badge badge-ghost "Deleted"
      - Actions td: wrap all action forms in th:unless="${user.deleted()}"
          (deleted users have no action buttons)
      - <tr>: add class="opacity-50" when user.deleted() via th:classappend

  - phase: 6 — Update tests
    files:
      - src/test/java/simple/simple_webapp/user/UserManagementTests.java
      - src/test/java/simple/simple_webapp/user/AdminControllerTests.java
    changes:
      - UserManagementTests: update all findAll() → findAll(false); update soft-delete tests to use findAll(true)
      - AdminControllerTests: add test for GET /admin/users?showDeleted=true returns 200

  - phase: 7 — Run tests
    commands:
      - .\mvnw test

open_questions: []

status: ready
