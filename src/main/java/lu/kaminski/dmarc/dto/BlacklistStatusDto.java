package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BlacklistStatusDto(
        String domain,
        boolean hasIssues,
        LocalDateTime checkedAt,
        List<BlacklistTargetResultDto> targets
) {}
