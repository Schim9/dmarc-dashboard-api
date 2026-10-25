package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.DnsCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DnsCheckRepository extends JpaRepository<DnsCheck, Long> {
    Optional<DnsCheck> findByDomain(String domain);
}
