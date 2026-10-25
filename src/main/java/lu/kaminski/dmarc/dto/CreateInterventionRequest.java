package lu.kaminski.dmarc.dto;

import jakarta.validation.constraints.NotNull;

public record CreateInterventionRequest(
        @NotNull Long recordId,
        String initialNote
) {}
