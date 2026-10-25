package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReportDto(
        Long id,
        String reportId,
        String orgName,
        String orgEmail,
        String domain,
        LocalDateTime beginDate,
        LocalDateTime endDate,
        String policy,
        String adkim,
        String aspf,
        Integer pct,
        LocalDateTime receivedAt,
        long totalMessages,
        long passCount,
        long failCount,
        long dkimFailCount,
        long spfFailCount,
        double complianceRate,
        List<RecordDto> records
) {}
