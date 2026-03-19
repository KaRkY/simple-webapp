package simple.simple_webapp.user.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import simple.simple_webapp.user.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserManagementImpl implements UserDetailsService, UserManagement {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    UserManagementImpl(UserDao userDao, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Transactional
    @Override
    public void register(CreateUser command) throws DuplicateEmailException {
        var email = command.email();
        var password = command.password();
        var id = UUID.randomUUID();
        var token = UUID.randomUUID().toString();
        try {
            String encodedPassword = passwordEncoder.encode(password);
            OffsetDateTime activationTokenExpiresAt = OffsetDateTime.now().plusHours(24);
            assert encodedPassword != null;
            userDao.insert(new UserDao.InsertUser(id, email, encodedPassword, false, token, activationTokenExpiresAt));
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_email_unique")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
        userDao.insertRole(id, UserRole.USER.name());
        events.publishEvent(new UserRegisteredEvent(id, token));
    }

    @Transactional
    @Override
    public void registerAndActivate(CreateUser command) throws DuplicateEmailException {
        var email = command.email();
        var password = command.password();
        var id = UUID.randomUUID();
        try {
            String encodedPassword = passwordEncoder.encode(password);
            assert encodedPassword != null;
            userDao.insert(new UserDao.InsertUser(id, email, encodedPassword, true, null, null));
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("users_email_unique")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
        userDao.insertRole(id, UserRole.USER.name());
        events.publishEvent(new UserRegisteredEvent(id, null));
        events.publishEvent(new UserActivatedEvent(id));
    }

    @Transactional
    @Override
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
        events.publishEvent(new UserActivatedEvent(user.id()));
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

        var authorities = user.roles().stream()
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
    @Override
    public void recordFailedAttempt(String email) {
        userDao.incrementFailedAttempts(email);
        var attempts = userDao.countFailedAttempts(email);
        if (attempts != null && attempts >= 3) {
            userDao.lockByEmail(email);
        }
    }

    @Transactional
    @Override
    public void recordSuccessfulLogin(String email) {
        userDao.resetFailedAttempts(email);
    }

    @Override
    public UserSummary findById(UUID id) {
        var user = userDao.findById(id);
        if (user == null) {
            throw new NoSuchElementException("User not found: " + id);
        }
        return toUserSummary(user);
    }

    @Override
    public List<UserSummary> findAll(boolean includeDeleted) {
        return userDao.findAll(includeDeleted).stream()
                .map(this::toUserSummary)
                .toList();
    }

    @Transactional
    @Override
    public void lockUser(UUID id) {
        userDao.lock(id);
    }

    @Transactional
    @Override
    public void unlockUser(UUID id) {
        userDao.unlock(id);
    }

    @Transactional
    @Override
    public void setRole(UUID id, UserRole role) {
        userDao.setRoles(id, role.name());
    }

    @Transactional
    @Override
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
    @Override
    public String resetPassword(UUID id) {
        var password = UUID.randomUUID().toString();
        userDao.updatePassword(id, passwordEncoder.encode(password));
        return password;
    }

    @Transactional
    @Override
    public void deleteUser(UUID id, String currentEmail) {
        var user = userDao.findById(id);
        if (user != null && user.email().equals(currentEmail)) {
            throw new IllegalArgumentException("Cannot delete own account");
        }
        userDao.softDelete(id);
    }

    private UserSummary toUserSummary(UserRecord user) {
        return new UserSummary(
                user.id(),
                user.email(),
                user.roles(),
                user.accountNonLocked(),
                user.enabled(),
                user.deletedAt() != null,
                !user.enabled() && user.activationToken() != null
        );
    }
}



