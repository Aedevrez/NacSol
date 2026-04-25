package burdem.nacsol.radius.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for the accounting pipeline stage.
 * statusType values: 1=Start, 2=Stop, 3=Interim-Update, 7=Accounting-On, 8=Accounting-Off.
 */
@Getter
@Setter
public class RadiusAccountingRequest {
    private String sessionId;
    private String nasSessionId;
    private int statusType;
    private String username;
    private String nasIp;
    private String framedIp;
    private String deviceMac;
    private String ssid;
    private long sessionTime;
    private long bytesIn;
    private long bytesOut;
    private String terminateCause;
}
