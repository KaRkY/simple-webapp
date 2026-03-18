package simple.simple_webapp.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserManagement implements UserDetailsService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final ActivationEmailService activationEmailService;

    public UserManagement(UserDao userDao, PasswordEncoder passwordEncoder, ActivationEmailService activationEmailService) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.activationEmailService = activationEmailService;
    }

    @Transactional
    public void register(String email, String password) {
        var id = UUID.randomUUID();
        var token = UUID.randomUUID().toString();
        try {
            userDao.insert(id, email, passwordEncoder.encode(password), false, token, OffsetDateTime.now().plusHours(24));
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_email_unique")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
        userDao.insertRole(id, UserRole.USER.name());
        activationEmailService.sendActivationEmail(email, token);
    }

    @Transactional
    public void registerAndActivate(String email, String password) {
        var id = UUID.randomUUID();
        try {
            userDao.insert(id, email, passwordEncoder.encode(password), true, null, null);
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_email_unique")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
        userDao.insertRole(id, UserRole.USER.name());
    }

    @Transactional
    public void activateUser(String token) {
        var user = userDao.findByActivationToken(token);
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        if (user.activationTokenExpiresAt() == null
                || user.activationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        userDao.activate(user.id());
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = userDao.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + email);
        }

        var accountNonLocked = user.accountNonLocked();
        if (!accountNonLocked && user.lockedAt() != null
                && user.lockedAt().plusMinutes(5).isBefore(OffsetDateTime.now())) {
            userDao.autoUnlock(user.id());
            accountNonLocked = true;
        }

        var authorities = userDao.findRolesByUserId(user.id()).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.email(),
                user.password(),
                user.enabled(),
                user.accountNonExpired(),
                user.credentialsNonExpired(),
                accountNonLocked,
                authorities
        );
    }

    @Transactional
    public void recordFailedAttempt(String email) {
        userDao.incrementFailedAttempts(email);
        var attempts = userDao.countFailedAttempts(email);
        if (attempts != null && attempts >= 3) {
            userDao.lockByEmail(email);
        }
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        userDao.resetFailedAttempts(email);
    }

    public UserSummary findById(UUID id) {
        var user = userDao.findById(id);
        if (user == null) {
            throw new NoSuchElementException("User not found: " + id);
        }
        var roles = userDao.findRolesByUserId(id);
        return new UserSummary(
                user.id(),
                user.email(),
                roles,
                user.accountNonLocked(),
                user.enabled(),
                user.deletedAt() != null,
                !user.enabled() && user.activationToken() != null
        );
    }

    public List<UserSummary> findAll(boolean includeDeleted) {
        return userDao.findAllWithRoles(includeDeleted);
    }

    @Transactional
    public void lockUser(UUID id) {
        userDao.lock(id);
    }

    @Transactional
    public void unlockUser(UUID id) {
        userDao.unlock(id);
    }

    @Transactional
    public void setRole(UUID id, UserRole role) {
        userDao.setRoles(id, role.name());
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        var user = userDao.findByEmailIncludeDeleted(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + email);
        }
        if (!passwordEncoder.matches(currentPassword, user.password())) {
            throw new BadCredentialsException("Wrong current password");
        }
        userDao.updatePassword(user.id(), passwordEncoder.encode(newPassword));
    }

    @Transactional
    public String resetPassword(UUID id) {
        var password = UUID.randomUUID().toString();
        userDao.updatePassword(id, passwordEncoder.encode(password));
        return password;
    }

    @Transactional
    public void deleteUser(UUID id, String currentEmail) {
        var user = userDao.findById(id);
        if (user != null && user.email().equals(currentEmail)) {
            throw new IllegalArgumentException("Cannot delete own account");
        }
        userDao.softDelete(id);
    }
}
