package lu.kaminski.dmarc.dto;

public record SourceIpDto(
        String sourceIp,
        long messageCount,
        String hostname,
        long dkimPass,
        long dkimFail,
        long spfPass,
        long spfFail
) {}
