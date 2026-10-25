package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.DmarcRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DmarcRecordRepository extends JpaRepository<DmarcRecord, Long> {

    @Query("SELECT r FROM DmarcRecord r JOIN FETCH r.report rp WHERE r.sourceIp = :ip ORDER BY rp.beginDate DESC")
    List<DmarcRecord> findBySourceIpOrderByReportBeginDateDesc(@Param("ip") String ip);

    @Query("SELECT r.envelopeTo, r.disposition, SUM(r.count), MAX(r.report.beginDate), MAX(r.report.id), COUNT(DISTINCT r.report.id), MIN(r.report.beginDate) " +
           "FROM DmarcRecord r WHERE r.report.domain = :domain " +
           "AND r.envelopeTo IS NOT NULL AND r.disposition <> 'none' " +
           "GROUP BY r.envelopeTo, r.disposition ORDER BY SUM(r.count) DESC")
    List<Object[]> findImpactedRecipients(@Param("domain") String domain);

    @Query("SELECT DISTINCT r.dkimSelector FROM DmarcRecord r WHERE r.report.domain = :domain AND r.dkimSelector IS NOT NULL")
    List<String> findDistinctDkimSelectorsByDomain(@Param("domain") String domain);

    @Query("SELECT DISTINCT r.sourceIp FROM DmarcRecord r WHERE r.sourceIp IS NOT NULL")
    List<String> findAllDistinctSourceIps();

    @Query("SELECT DISTINCT r.sourceIp FROM DmarcRecord r WHERE r.report.domain = :domain AND r.sourceIp IS NOT NULL")
    List<String> findDistinctSourceIpsByDomain(@Param("domain") String domain);

    @Query("SELECT r.sourceIp, SUM(r.count) as total, MAX(r.hostname) as hostname, " +
           "SUM(CASE WHEN r.dkimResult = 'pass' THEN r.count ELSE 0 END) as dkimPass, " +
           "SUM(CASE WHEN r.dkimResult <> 'pass' THEN r.count ELSE 0 END) as dkimFail, " +
           "SUM(CASE WHEN r.spfResult = 'pass' THEN r.count ELSE 0 END) as spfPass, " +
           "SUM(CASE WHEN r.spfResult <> 'pass' THEN r.count ELSE 0 END) as spfFail " +
           "FROM DmarcRecord r WHERE r.report.domain = :domain " +
           "GROUP BY r.sourceIp ORDER BY total DESC")
    List<Object[]> findTopSourcesByDomain(@Param("domain") String domain, Pageable pageable);
}
