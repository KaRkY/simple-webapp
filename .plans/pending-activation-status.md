# Plan: Pending Activation Status in User List

## Problem

The admin user list shows three statuses: Active, Locked, Deleted.
Accounts that registered but haven't clicked their activation email link
are currently `enabled=false` with a non-null `activation_token`, but
they show as "Active" in the UI — indistinguishable from real active users.

## Approach

No DB schema changes needed — the data is already there:
- `enabled = false` + `activation_token IS NOT NULL` → pending activation
- `enabled = true` + `activation_token IS NULL` → active

Add a derived `pendingActivation` field to `UserSummary`, populate it in
`UserManagement`, and display a distinct badge in the admin UI.

## Todos

1. `UserSummary` — add `boolean pendingActivation()` to the record
2. `UserManagement` — populate `pendingActivation` in both `findAll()` and `findById()` mappers
3. `user-row.html` — add "Pending Activation" badge (yellow/warning), and guard "Active" badge to only show when not pending

## Status Logic

| enabled | activation_token | accountNonLocked | deleted_at | Display          |
|---------|-----------------|------------------|------------|------------------|
| false   | non-null        | any              | null       | Pending Activation |
| true    | null            | true             | null       | Active           |
| any     | any             | false            | null       | Locked           |
| any     | any             | any              | non-null   | Deleted          |

## Files Changed

- `src/main/java/simple/simple_webapp/user/UserSummary.java`
- `src/main/java/simple/simple_webapp/user/UserManagement.java`
- `src/main/resources/templates/admin/user-row.html`
