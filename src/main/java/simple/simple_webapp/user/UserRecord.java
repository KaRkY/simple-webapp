package simple.simple_webapp.user;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

record UserRecord(
        UUID id,
        String email,
        String password,
        boolean enabled,
        boolean accountNonExpired,
        boolean accountNonLocked,
        boolean credentialsNonExpired,
        int failedLoginAttempts,
        @Nullable OffsetDateTime lockedAt,
        @Nullable OffsetDateTime deletedAt,
        @Nullable String activationToken,
        @Nullable OffsetDateTime activationTokenExpiresAt
) {}
