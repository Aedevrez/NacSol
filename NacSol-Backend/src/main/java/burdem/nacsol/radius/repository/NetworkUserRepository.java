package burdem.nacsol.radius.repository;

import burdem.nacsol.radius.entity.NetworkUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NetworkUserRepository extends JpaRepository<NetworkUser, Long> {
    Optional<NetworkUser> findByUsername(String username);
}
