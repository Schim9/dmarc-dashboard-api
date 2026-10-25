package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterventionDto(
        Long id,
        LocalDateTime createdAt,
        String status,
        int journalCount,
        List<RecordSummaryDto> records
) {}
