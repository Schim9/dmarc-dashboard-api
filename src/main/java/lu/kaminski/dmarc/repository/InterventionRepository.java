package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.Intervention;
import lu.kaminski.dmarc.entity.InterventionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByDmarcRecords_Id(Long recordId);

    List<Intervention> findAllByOrderByCreatedAtDesc();

    List<Intervention> findByStatusOrderByCreatedAtDesc(InterventionStatus status);

    @Query("SELECT DISTINCT i FROM Intervention i JOIN i.dmarcRecords r WHERE r.report.domain = :domain ORDER BY i.createdAt DESC")
    List<Intervention> findByDomain(@Param("domain") String domain);

    @Query("SELECT DISTINCT i FROM Intervention i JOIN i.dmarcRecords r WHERE r.report.domain = :domain AND i.status = :status ORDER BY i.createdAt DESC")
    List<Intervention> findByDomainAndStatus(@Param("domain") String domain, @Param("status") InterventionStatus status);

    @Query("SELECT r.report.domain, COUNT(DISTINCT i) FROM Intervention i JOIN i.dmarcRecords r WHERE i.status = :status GROUP BY r.report.domain")
    List<Object[]> countByDomain(@Param("status") InterventionStatus status);

    @Query("SELECT COUNT(DISTINCT i) FROM Intervention i JOIN i.dmarcRecords r WHERE r.report.domain = :domain AND i.status = :status")
    long countByDomainAndStatus(@Param("domain") String domain, @Param("status") InterventionStatus status);

    @Query("SELECT DISTINCT r.report.id FROM Intervention i JOIN i.dmarcRecords r WHERE r.report.id IN :reportIds AND i.status = 'IN_PROGRESS'")
    List<Long> findReportIdsWithActiveInterventions(@Param("reportIds") List<Long> reportIds);
}
