package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.KnownDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnownDomainRepository extends JpaRepository<KnownDomain, Long> {

    boolean existsByDomain(String domain);

    void deleteByDomain(String domain);

    @Query("SELECT k.domain FROM KnownDomain k")
    List<String> findAllDomainNames();
}
