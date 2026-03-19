package simple.simple_webapp.user;

public class DuplicateEmailException extends Exception {

    public DuplicateEmailException(String email) {
        super("Email already registered: " + email);
    }
}
