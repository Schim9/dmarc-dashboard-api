package lu.kaminski.dmarc.dto;

public record DnsIssueDto(
        String severity,    // critical | warning | info
        String type,        // DMARC | SPF | DKIM | MX
        String message,
        String suggestion
) {}
