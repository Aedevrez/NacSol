package burdem.nacsol.radius.service;

import burdem.nacsol.radius.dto.RadiusPostAuthRequest;
import burdem.nacsol.radius.entity.AuthEvent;
import burdem.nacsol.radius.repository.AuthEventRepository;
import org.springframework.stereotype.Service;

@Service
public class PostAuthService {

    private final AuthEventRepository authEventRepository;

    public PostAuthService(AuthEventRepository authEventRepository) {
        this.authEventRepository = authEventRepository;
    }

    /**
     * Logs every authentication outcome (accept and reject) for audit purposes.
     * Called by FreeRADIUS post-auth for both Access-Accept and Access-Reject.
     */
    public void logAuthEvent(RadiusPostAuthRequest request) {
        AuthEvent event = new AuthEvent();
        event.setUsername(request.getUsername());
        event.setNasIp(request.getNasIp());
        event.setDeviceMac(request.getDeviceMac());
        event.setSsid(request.getSsid());
        event.setAuthResult(request.getAuthResult());
        authEventRepository.save(event);
    }
}
