package lu.kaminski.dmarc.dto;

import java.util.List;

public record SpfAnalysisDto(
        String record,
        String policy,
        int dnsLookups,
        List<SpfIssue> issues
) {
    public record SpfIssue(String severity, String message) {}
}
