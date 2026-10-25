package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DomainStatsDto(
        String domain,
        long totalReports,
        long totalMessages,
        long passCount,
        long failCount,
        double complianceRate,
        double lastReportComplianceRate,
        String currentPolicy,
        LocalDateTime lastReportDate,
        List<SourceIpDto> topSources,
        List<ImpactedRecipientDto> impactedRecipients,
        List<AlertDto> alerts,
        SpfAnalysisDto spfAnalysis,
        boolean declared
) {}
