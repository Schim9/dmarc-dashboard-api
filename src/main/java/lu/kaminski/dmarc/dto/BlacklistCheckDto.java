package lu.kaminski.dmarc.dto;

public record BlacklistCheckDto(
        String dnsbl,
        String label,
        boolean listed
) {}
