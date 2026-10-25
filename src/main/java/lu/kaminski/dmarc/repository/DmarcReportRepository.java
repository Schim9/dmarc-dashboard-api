package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.DmarcReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DmarcReportRepository extends JpaRepository<DmarcReport, Long> {

    boolean existsByReportId(String reportId);

    Page<DmarcReport> findAll(Pageable pageable);

    Page<DmarcReport> findByDomain(String domain, Pageable pageable);

    List<DmarcReport> findByDomainOrderByEndDateDesc(String domain);

    java.util.Optional<DmarcReport> findFirstByDomainOrderByEndDateDesc(String domain);

    List<DmarcReport> findByDomainAndEndDateBetween(String domain, java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Query("SELECT DISTINCT r.domain FROM DmarcReport r ORDER BY r.domain")
    List<String> findAllDomains();

    @Query("SELECT r.domain, COUNT(r), SUM(r.totalMessages), SUM(r.passCount), SUM(r.failCount), MAX(r.policy), MAX(r.endDate) " +
           "FROM DmarcReport r GROUP BY r.domain ORDER BY r.domain")
    List<Object[]> findDomainAggregates();

    @Query("SELECT r.domain, COUNT(r), SUM(r.totalMessages), SUM(r.passCount), SUM(r.failCount), MAX(r.policy), MAX(r.endDate) " +
           "FROM DmarcReport r WHERE r.domain = :domain GROUP BY r.domain")
    List<Object[]> findDomainAggregateForDomain(String domain);

    @Query("SELECT COUNT(r) FROM DmarcReport r WHERE r.receivedAt >= :since")
    long countReportsSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT r.domain, FUNCTION('DATE', r.beginDate), SUM(r.totalMessages), SUM(r.passCount) " +
           "FROM DmarcReport r " +
           "WHERE r.beginDate >= :since " +
           "GROUP BY r.domain, FUNCTION('DATE', r.beginDate) " +
           "ORDER BY r.domain, FUNCTION('DATE', r.beginDate)")
    List<Object[]> findDailyCompliance(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT r.domain, FUNCTION('DATE', r.receivedAt), COUNT(r) " +
           "FROM DmarcReport r " +
           "WHERE r.receivedAt >= :since " +
           "GROUP BY r.domain, FUNCTION('DATE', r.receivedAt) " +
           "ORDER BY r.domain, FUNCTION('DATE', r.receivedAt)")
    List<Object[]> findDailyReportCount(@Param("since") java.time.LocalDateTime since);
}
