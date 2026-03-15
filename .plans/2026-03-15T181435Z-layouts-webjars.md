---
title: Thymeleaf layouts + WebJar scripts
created: 2026-03-15T18:14:35Z
context: layouts-webjars
description: Add 2 Thymeleaf fragment-based layouts (user, admin). Switch HTMX + Hyperscript to webjars.
---

assumptions:
- Layout pattern: th:fragment="layout(title, content)" in layout file.
- Content pages use: th:replace="~{layouts/user :: layout(~{::title}, ~{::main})}"
- Layout replaces ${title} and ${content} with th:replace="${title}" / th:replace="${content}".
- No extra dependencies needed (no layout dialect, no webjars-locator — Spring Boot handles both).
- WebJar paths (Spring Boot auto-resolves version):
    htmx:        th:src="@{/webjars/htmx.org/dist/htmx.min.js}"
    hyperscript: th:src="@{/webjars/hyperscript.org/dist/_hyperscript.min.js}"
- HTMX CSRF listener JS lives only in layouts.
- Layout 1 (user)  → nav: Home | Change Password | Sign out
- Layout 2 (admin) → nav: Home | Admin Panel | Sign out
- user-row.html is a fragment — no layout applied.
- Root pages (index, login, register) — no layout.

phases:
  - phase: 1 — Layout templates
    files:
      - src/main/resources/templates/layouts/user.html  (NEW)
        <!DOCTYPE html>
        <html th:fragment="layout(title, content)" xmlns:th="..." xmlns:sec="...">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" .../>
          <title th:replace="${title}">App</title>
          <script th:src="@{/webjars/htmx.org/dist/htmx.min.js}"></script>
          <script th:src="@{/webjars/hyperscript.org/dist/_hyperscript.min.js}"></script>
          <script th:inline="javascript">
            document.addEventListener('htmx:configRequest', (evt) => {
              evt.detail.headers['X-CSRF-TOKEN'] = /*[[${_csrf.token}]]*/ '';
            });
          </script>
        </head>
        <body>
          <nav>
            <a href="/">Home</a> |
            <a href="/account/change-password">Change password</a> |
            <form method="post" action="/logout" style="display:inline">
              <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
              <button type="submit">Sign out</button>
            </form>
          </nav>
          <main th:replace="${content}"></main>
        </body>
        </html>

      - src/main/resources/templates/layouts/admin.html  (NEW)
        Same structure, nav: Home | Admin Panel | Sign out

  - phase: 2 — Update content templates
    files:
      - src/main/resources/templates/account/change-password.html
          change <html> to: <html th:replace="~{layouts/user :: layout(~{::title}, ~{::main})}">
          keep <title> and wrap body content in <main>

      - src/main/resources/templates/admin/users.html
          change <html> to: <html th:replace="~{layouts/admin :: layout(~{::title}, ~{::main})}">
          keep <title>, wrap table in <main>
          remove: inline CDN script + CSRF JS (now in layout)
          remove: <a> Home link (now in layout nav)

  - phase: 3 — Verify
    - mvnw test → all 34 tests must pass

open_questions: []

status: ready
