package burdem.nacsol.radius.exception;

public class UserDisabledException extends RuntimeException {
    public UserDisabledException(String username) {
        super("User account disabled: " + username);
    }
}
