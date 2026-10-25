package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;

public record ImpactedRecipientDto(
        String envelopeTo,
        String disposition,
        long count,
        LocalDateTime lastReportDate,
        Long lastReportId,
        long reportCount,
        LocalDateTime firstReportDate
) {}
