package burdem.nacsol.radius.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Shared request DTO for the authorize and authenticate pipeline stages.
 * Fields map 1:1 to the xlat-expanded JSON body sent by rlm_rest.
 */
@Getter
@Setter
public class RadiusAccessRequest {
    private String username;
    private String password;
    private String nasIp;
    private String nasId;
    private String deviceMac;
    private String ssid;
    private String serviceType;
    private String sessionId;
}
