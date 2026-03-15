package simple.simple_webapp.user;

class DuplicateUsernameException extends RuntimeException {

    DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
