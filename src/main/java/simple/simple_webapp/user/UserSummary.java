package simple.simple_webapp.user;

import java.util.List;

public record UserSummary(
        Long id,
        String email,
        List<String> roles,
        boolean accountNonLocked,
        boolean enabled,
        boolean deleted,
        boolean pendingActivation) {
}
