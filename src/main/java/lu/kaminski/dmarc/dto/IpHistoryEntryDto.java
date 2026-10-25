package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;

public record IpHistoryEntryDto(
        String reportId,
        String domain,
        String orgName,
        LocalDateTime beginDate,
        LocalDateTime endDate,
        int count,
        String dkimResult,
        String spfResult,
        String disposition,
        String headerFrom
) {}
