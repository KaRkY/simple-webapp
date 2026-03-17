package simple.simple_webapp.user;

class DuplicateEmailException extends RuntimeException {

    DuplicateEmailException(String email) {
        super("Email already registered: " + email);
    }
}
