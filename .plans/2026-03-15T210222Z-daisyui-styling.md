---
title: DaisyUI Styling
created: 2026-03-15T21:02:22Z
context: daisyui-styling
description: Apply DaisyUI 5 + Tailwind 4 styling to all pages
---

assumptions:
- DaisyUI 5.x + Tailwind CSS 4.x already configured in frontend/
- CSS built to target/classes/static/css/style.css, linked in root.html
- input.css uses @import "tailwindcss" + @plugin "daisyui" (Tailwind 4 syntax)
- tailwind.config.js content array scans templates; adding @source to input.css ensures Tailwind 4 picks it up
- Theme: light (default); no toggle needed
- No Java or Spring config changes — HTML class additions only

phases:

  - phase: 1 — CSS build config
    files:
      - frontend/input.css
      - src/main/resources/templates/layouts/root.html
    changes:
      - input.css: add `@source "../src/main/resources/templates/**/*.html";`
      - root.html: add `data-theme="light"` to <html> element

  - phase: 2 — Style layouts
    files:
      - src/main/resources/templates/layouts/user.html
      - src/main/resources/templates/layouts/admin.html
    changes:
      - body: class="bg-base-200 min-h-screen"
      - Replace bare <nav> with DaisyUI navbar:
          <div class="navbar bg-base-100 shadow-md">
            <div class="navbar-start"><a class="btn btn-ghost text-lg">...</a></div>
            <div class="navbar-end">..links.. <button class="btn btn-ghost">Sign out</button></div>
          </div>
      - Wrap <main th:replace> in <div class="container mx-auto p-6">

  - phase: 3 — Style public pages
    files:
      - src/main/resources/templates/login.html
      - src/main/resources/templates/register.html
      - src/main/resources/templates/index.html
    changes:
      - login.html + register.html:
          body: class="min-h-screen flex items-center justify-center bg-base-200"
          wrap form in: <div class="card w-full max-w-sm bg-base-100 shadow-xl"><div class="card-body">
          h1: class="card-title text-2xl justify-center"
          alerts: class="alert alert-error" / "alert alert-success"
          label: class="label" > <span class="label-text">
          inputs: class="input input-bordered w-full"
          form: class="flex flex-col gap-4"
          submit: class="btn btn-primary w-full"
          footer link: class="link link-primary"
      - index.html:
          body: class="min-h-screen bg-base-200"
          unauthenticated div: class="min-h-screen flex items-center justify-center"
            → same card + form style as login
          authenticated div: class="container mx-auto p-6"
            h1: class="text-3xl font-bold mb-6"
            nav links: <ul class="menu bg-base-100 rounded-box w-56 shadow">
            sign-out: class="btn btn-outline mt-4"

  - phase: 4 — Style change-password.html
    files:
      - src/main/resources/templates/account/change-password.html
    changes:
      - main: class="flex justify-center pt-10"
      - wrap in: <div class="card w-full max-w-md bg-base-100 shadow-xl"><div class="card-body">
      - h1: class="card-title text-2xl"
      - alerts: class="alert alert-success" / "alert alert-error"
      - label/input pattern same as login
      - form: class="flex flex-col gap-4"
      - submit: class="btn btn-primary w-full"

  - phase: 5 — Style admin pages
    files:
      - src/main/resources/templates/admin/users.html
      - src/main/resources/templates/admin/user-row.html
    changes:
      - users.html main: class="space-y-6"
          heading row: class="flex items-center justify-between"
          h1: class="text-2xl font-bold"
          logged-in p: class="text-sm text-base-content/60"
          table wrapper: <div class="overflow-x-auto">
          table: class="table table-zebra bg-base-100 shadow rounded-box"
      - user-row.html:
          active span: class="badge badge-success"
          locked span: class="badge badge-error"
          actions td: class="flex flex-wrap gap-1 items-center"
          lock form button: class="btn btn-warning btn-xs"
          unlock form button: class="btn btn-success btn-xs"
          role select: class="select select-xs select-bordered"
          set-role button: class="btn btn-secondary btn-xs"
          delete button: class="btn btn-error btn-xs"
          reset-password button: class="btn btn-info btn-xs"
          temp-password span: class="badge badge-warning badge-outline font-mono"

  - phase: 6 — Verify
    commands:
      - .\mvnw test

open_questions: []

status: ready
