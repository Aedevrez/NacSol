package burdem.nacsol.radius.repository;

import burdem.nacsol.radius.entity.AccountingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AccountingSessionRepository extends JpaRepository<AccountingSession, Long> {

    Optional<AccountingSession> findBySessionId(String sessionId);

    @Modifying
    @Query("UPDATE AccountingSession s SET s.active = false, s.endedAt = CURRENT_TIMESTAMP, s.lastUpdatedAt = CURRENT_TIMESTAMP WHERE s.nasIp = :nasIp AND s.active = true")
    void closeAllActiveSessionsForNas(String nasIp);
}
