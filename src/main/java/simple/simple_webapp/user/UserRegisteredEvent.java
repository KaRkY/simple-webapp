package simple.simple_webapp.user;

import org.jspecify.annotations.Nullable;

public record UserRegisteredEvent(Long userId, @Nullable String activationToken) {
}
