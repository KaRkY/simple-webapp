package simple.simple_webapp.user.internal;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserRecord(
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
        @Nullable OffsetDateTime activationTokenExpiresAt,
        List<String> roles
) {}
