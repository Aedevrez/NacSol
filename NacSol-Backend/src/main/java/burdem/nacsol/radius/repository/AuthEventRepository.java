package burdem.nacsol.radius.repository;

import burdem.nacsol.radius.entity.AuthEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {
}
