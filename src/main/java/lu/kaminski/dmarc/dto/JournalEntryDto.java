package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;

public record JournalEntryDto(
        Long id,
        LocalDateTime createdAt,
        String content
) {}
