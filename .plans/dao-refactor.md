# Plan: DAO Layer Refactor

## Problem

All jOOQ/DSLContext code lives directly in service classes (`UserManagement`,
`DefaultAdminInitializer`, `DefaultUserInitializer`). This mixes persistence
concerns with business logic and makes the DB layer hard to test or swap.

## Approach

Introduce a single `UserDao` class in the `user` module that owns all jOOQ
queries. Services become pure orchestrators — no `DSLContext` imports outside
the DAO. Order and Inventory modules are unaffected (no jOOQ usage there).

## Scope

Only the `user` module needs changes. 3 classes currently hold DSLContext:
- `UserManagement` — 15+ query types (the bulk of the work)
- `DefaultAdminInitializer` — 2 queries (existence check + select)
- `DefaultUserInitializer` — 1 query (existence check)

## Todos

1. **create-user-dao** — New `UserDao` (@Repository) with all query methods
2. **refactor-user-management** — Swap DSLContext for UserDao (depends on 1)
3. **refactor-initializers** — Swap DSLContext for UserDao (depends on 1)
4. **run-tests** — Verify all 49 tests pass (depends on 2 & 3)

## UserDao Method Inventory

| Method | Called by |
|--------|-----------|
| `insert(id, email, encodedPassword, enabled, token, tokenExpiresAt)` | UserManagement.register |
| `insertRole(userId, role)` | UserManagement.register / registerAndActivate |
| `findByActivationToken(token)` | UserManagement.activateUser |
| `activate(id)` | UserManagement.activateUser |
| `findByEmail(email)` | UserManagement.loadUserByUsername / changePassword |
| `autoUnlock(id)` | UserManagement.loadUserByUsername |
| `findRolesByUserId(userId)` | UserManagement.loadUserByUsername / findById |
| `incrementFailedAttempts(email)` | UserManagement.recordFailedAttempt |
| `countFailedAttempts(email)` | UserManagement.recordFailedAttempt |
| `lockByEmail(email)` | UserManagement.recordFailedAttempt |
| `resetFailedAttempts(email)` | UserManagement.recordSuccessfulLogin |
| `findById(id)` | UserManagement.findById |
| `findAllWithRoles(includeDeleted)` | UserManagement.findAll |
| `lock(id)` | UserManagement.lockUser |
| `unlock(id)` | UserManagement.unlockUser |
| `setRoles(userId, role)` | UserManagement.setRole |
| `updatePassword(id, encodedPassword)` | UserManagement.changePassword / resetPassword |
| `softDelete(id)` | UserManagement.deleteUser |
| `existsByEmail(email)` | DefaultAdminInitializer / DefaultUserInitializer |

## Files Changed

- `src/main/java/simple/simple_webapp/user/UserDao.java` ← new
- `src/main/java/simple/simple_webapp/user/UserManagement.java` ← refactored
- `src/main/java/simple/simple_webapp/user/DefaultAdminInitializer.java` ← refactored
- `src/main/java/simple/simple_webapp/user/DefaultUserInitializer.java` ← refactored
