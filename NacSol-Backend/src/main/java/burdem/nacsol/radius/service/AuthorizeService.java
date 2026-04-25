package burdem.nacsol.radius.service;

import burdem.nacsol.radius.dto.RadiusAccessRequest;
import burdem.nacsol.radius.dto.RadiusResponse;
import burdem.nacsol.radius.entity.NetworkUser;
import burdem.nacsol.radius.exception.UserDisabledException;
import burdem.nacsol.radius.exception.UserNotFoundException;
import burdem.nacsol.radius.repository.NetworkUserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthorizeService {

    private final NetworkUserRepository userRepository;

    public AuthorizeService(NetworkUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Looks up the user and returns policy attributes for rlm_rest.
     * Spring is the single source of truth — all authorization decisions happen here.
     */
    public Map<String, Object> authorize(RadiusAccessRequest request) {
        NetworkUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException(request.getUsername()));

        if (!user.isEnabled()) {
            throw new UserDisabledException(request.getUsername());
        }

        RadiusResponse response = RadiusResponse.create()
                .reply("Session-Timeout", user.getSessionTimeout());

        // VLAN assignment — standard 802.1X tunnel attributes
        if (user.getVlanId() != null) {
            response.reply("Tunnel-Type", 13)
                    .reply("Tunnel-Medium-Type", 6)
                    .reply("Tunnel-Private-Group-Id", String.valueOf(user.getVlanId()));
        }

        // VSAs — company-specific policy attributes
        response.reply("MyCompany-AccessTier", user.getAccessTier());

        if (user.getRole() != null) {
            response.reply("MyCompany-Role", user.getRole());
        }
        if (user.getDepartment() != null) {
            response.reply("MyCompany-Department", user.getDepartment());
        }
        if (user.getBandwidthLimit() != null) {
            response.reply("MyCompany-BandwidthLimit", user.getBandwidthLimit());
        }
        if (user.getCostCenter() != null) {
            response.reply("MyCompany-CostCenter", user.getCostCenter());
        }

        return response.build();
    }
}
