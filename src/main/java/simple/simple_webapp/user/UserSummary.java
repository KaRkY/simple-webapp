package simple.simple_webapp.user;

import java.util.List;
import java.util.UUID;

public record UserSummary(UUID id, String email, List<String> roles, boolean accountNonLocked, boolean enabled, boolean deleted, boolean pendingActivation) {}
