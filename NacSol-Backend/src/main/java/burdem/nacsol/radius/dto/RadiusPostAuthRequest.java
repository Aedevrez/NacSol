package burdem.nacsol.radius.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for the post-auth pipeline stage.
 * Includes authResult to distinguish accept vs reject events for audit logging.
 */
@Getter
@Setter
public class RadiusPostAuthRequest {
    private String username;
    private String nasIp;
    private String nasId;
    private String deviceMac;
    private String ssid;
    private String serviceType;
    private String sessionId;
    private String authResult;
}
