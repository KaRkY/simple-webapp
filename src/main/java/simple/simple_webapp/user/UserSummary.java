package simple.simple_webapp.user;

import java.util.List;
import java.util.UUID;

public record UserSummary(UUID id, String username, List<String> roles, boolean accountNonLocked, boolean enabled, boolean deleted) {}
