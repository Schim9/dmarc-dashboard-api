package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BlacklistTargetResultDto(
        String target,
        String targetType,
        boolean clean,
        LocalDateTime checkedAt,
        List<BlacklistCheckDto> checks
) {}
