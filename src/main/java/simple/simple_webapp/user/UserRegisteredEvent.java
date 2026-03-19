package simple.simple_webapp.user;


import java.util.UUID;

public record UserRegisteredEvent(UUID userId) {
}
