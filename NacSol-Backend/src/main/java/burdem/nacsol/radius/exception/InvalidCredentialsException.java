package burdem.nacsol.radius.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String username) {
        super("Invalid credentials for user: " + username);
    }
}
