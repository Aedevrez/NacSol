package burdem.nacsol.radius;

import burdem.nacsol.radius.exception.InvalidCredentialsException;
import burdem.nacsol.radius.exception.UserDisabledException;
import burdem.nacsol.radius.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps service exceptions to HTTP status codes that rlm_rest understands.
 * 404 → notfound (reject), 403 → userlock (reject), 401 → reject (bad creds).
 */
@Slf4j
@RestControllerAdvice(assignableTypes = RadiusController.class)
public class RadiusExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(UserNotFoundException ex) {
        log.warn("RADIUS reject: {}", ex.getMessage());
        return ResponseEntity.status(404).build();
    }

    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<Void> handleDisabled(UserDisabledException ex) {
        log.warn("RADIUS reject: {}", ex.getMessage());
        return ResponseEntity.status(403).build();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Void> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("RADIUS reject: {}", ex.getMessage());
        return ResponseEntity.status(401).build();
    }
}
