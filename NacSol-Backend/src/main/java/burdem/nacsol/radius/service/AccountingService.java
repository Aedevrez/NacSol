package burdem.nacsol.radius.service;

import burdem.nacsol.radius.dto.RadiusAccountingRequest;
import burdem.nacsol.radius.entity.AccountingSession;
import burdem.nacsol.radius.repository.AccountingSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
public class AccountingService {

    private final AccountingSessionRepository sessionRepository;

    public AccountingService(AccountingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Processes all accounting event types. Every handler is idempotent —
     * NAS devices may retransmit accounting packets.
     */
    @Transactional
    public void processAccounting(RadiusAccountingRequest request) {
        switch (request.getStatusType()) {
            case 1 -> handleStart(request);
            case 2 -> handleStop(request);
            case 3 -> handleInterim(request);
            case 7 -> handleAccountingOn(request);
            case 8 -> handleAccountingOff(request);
            default -> log.warn("Unknown Acct-Status-Type: {}", request.getStatusType());
        }
    }

    private void handleStart(RadiusAccountingRequest request) {
        // Idempotent: if session already exists (retransmit), update it
        AccountingSession session = sessionRepository.findBySessionId(request.getSessionId())
                .orElseGet(AccountingSession::new);

        session.setSessionId(request.getSessionId());
        session.setNasSessionId(request.getNasSessionId());
        session.setUsername(request.getUsername());
        session.setNasIp(request.getNasIp());
        session.setFramedIp(request.getFramedIp());
        session.setDeviceMac(request.getDeviceMac());
        session.setSsid(request.getSsid());
        session.setActive(true);
        sessionRepository.save(session);

        log.info("Session started: {} user={} nas={}", request.getSessionId(), request.getUsername(), request.getNasIp());
    }

    private void handleInterim(RadiusAccountingRequest request) {
        sessionRepository.findBySessionId(request.getSessionId())
                .ifPresent(session -> {
                    session.setSessionTime(request.getSessionTime());
                    session.setBytesIn(request.getBytesIn());
                    session.setBytesOut(request.getBytesOut());
                    if (request.getFramedIp() != null) {
                        session.setFramedIp(request.getFramedIp());
                    }
                    sessionRepository.save(session);
                });
    }

    private void handleStop(RadiusAccountingRequest request) {
        sessionRepository.findBySessionId(request.getSessionId())
                .ifPresent(session -> {
                    session.setSessionTime(request.getSessionTime());
                    session.setBytesIn(request.getBytesIn());
                    session.setBytesOut(request.getBytesOut());
                    session.setTerminateCause(request.getTerminateCause());
                    session.setActive(false);
                    session.setEndedAt(Instant.now());
                    sessionRepository.save(session);

                    log.info("Session stopped: {} user={} duration={}s bytes_in={} bytes_out={}",
                            request.getSessionId(), request.getUsername(),
                            request.getSessionTime(), request.getBytesIn(), request.getBytesOut());
                });
    }

    /** NAS came online — mark any stale sessions from this NAS as ended */
    private void handleAccountingOn(RadiusAccountingRequest request) {
        sessionRepository.closeAllActiveSessionsForNas(request.getNasIp());
        log.info("Accounting-On from NAS {}: closed stale sessions", request.getNasIp());
    }

    /** NAS going offline — close all its active sessions */
    private void handleAccountingOff(RadiusAccountingRequest request) {
        sessionRepository.closeAllActiveSessionsForNas(request.getNasIp());
        log.info("Accounting-Off from NAS {}: closed all sessions", request.getNasIp());
    }
}
