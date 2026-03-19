package simple.simple_webapp.user;

import simple.simple_webapp.user.internal.UserRole;

import java.util.List;
import java.util.UUID;

public interface UserManagement {
    void register(CreateUser command) throws DuplicateEmailException;

    void registerAndActivate(CreateUser command) throws DuplicateEmailException;

    void activateUser(String token);

    void recordFailedAttempt(String email);

    void recordSuccessfulLogin(String email);

    UserSummary findById(UUID id);

    List<UserSummary> findAll(boolean includeDeleted);

    void lockUser(UUID id);

    void unlockUser(UUID id);

    void setRole(UUID id, UserRole role);

    void changePassword(String email, String currentPassword, String newPassword);

    String resetPassword(UUID id);

    void deleteUser(UUID id, String currentEmail);
}
