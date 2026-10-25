package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.BlacklistResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlacklistResultRepository extends JpaRepository<BlacklistResult, Long> {

    List<BlacklistResult> findByTargetIn(List<String> targets);

    @Query("SELECT r FROM BlacklistResult r WHERE r.listed = true")
    List<BlacklistResult> findAllListed();
}
