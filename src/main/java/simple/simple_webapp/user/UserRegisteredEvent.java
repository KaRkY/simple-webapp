package simple.simple_webapp.user;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, @Nullable String activationToken) {
}
