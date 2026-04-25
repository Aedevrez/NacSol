package burdem.nacsol.radius.service;

import burdem.nacsol.radius.dto.RadiusAccessRequest;
import burdem.nacsol.radius.entity.NetworkUser;
import burdem.nacsol.radius.exception.InvalidCredentialsException;
import burdem.nacsol.radius.exception.UserDisabledException;
import burdem.nacsol.radius.exception.UserNotFoundException;
import burdem.nacsol.radius.repository.NetworkUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticateService {

    private final NetworkUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticateService(NetworkUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Validates credentials. PAP password arrives in plaintext over HTTPS from FreeRADIUS.
     * Spring is the only component that ever inspects or compares passwords.
     */
    public void authenticate(RadiusAccessRequest request) {
        NetworkUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException(request.getUsername()));

        if (!user.isEnabled()) {
            throw new UserDisabledException(request.getUsername());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException(request.getUsername());
        }
    }


}
