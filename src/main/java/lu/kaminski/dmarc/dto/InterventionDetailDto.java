package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterventionDetailDto(
        Long id,
        LocalDateTime createdAt,
        String status,
        List<RecordSummaryDto> records,
        List<JournalEntryDto> journalEntries
) {}
