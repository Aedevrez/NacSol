package burdem.nacsol.radius.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "accounting_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AccountingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    private String nasSessionId;

    @Column(nullable = false)
    private String username;

    private String nasIp;

    private String framedIp;

    private String deviceMac;

    private String ssid;

    private long sessionTime;

    private long bytesIn;

    private long bytesOut;

    private String terminateCause;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant lastUpdatedAt;

    private Instant endedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = Instant.now();
        lastUpdatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = Instant.now();
    }
}
