package simple.simple_webapp.user;

import simple.simple_webapp.user.internal.UserRole;

import java.util.List;

public interface UserManagement {
    void register(CreateUser command) throws DuplicateEmailException;

    void registerAndActivate(CreateUser command) throws DuplicateEmailException;

    void activateUser(String token);

    void recordFailedAttempt(String email);

    void recordSuccessfulLogin(String email);

    UserSummary findById(Long id);

    List<UserSummary> findAll(boolean includeDeleted);

    void lockUser(Long id);

    void unlockUser(Long id);

    void setRole(Long id, UserRole role);

    void changePassword(String email, String currentPassword, String newPassword);

    String resetPassword(Long id);

    void deleteUser(Long id, String currentEmail);
}
