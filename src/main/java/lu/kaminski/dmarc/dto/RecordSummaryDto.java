package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;

public record RecordSummaryDto(
        Long id,
        String sourceIp,
        String hostname,
        String envelopeTo,
        String domain,
        String orgName,
        LocalDateTime reportBeginDate,
        LocalDateTime reportEndDate,
        String dkimResult,
        String spfResult,
        String disposition
) {}
