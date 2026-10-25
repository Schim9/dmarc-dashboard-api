package lu.kaminski.dmarc.dto;

import jakarta.validation.constraints.NotBlank;

public record AddJournalEntryRequest(@NotBlank String content) {}
