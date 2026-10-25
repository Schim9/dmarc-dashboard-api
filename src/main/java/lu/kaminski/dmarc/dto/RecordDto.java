package lu.kaminski.dmarc.dto;

public record RecordDto(
        Long id,
        String sourceIp,
        int count,
        String disposition,
        String dkimResult,
        String spfResult,
        String headerFrom,
        String envelopeFrom,
        String envelopeTo,
        String dkimDomain,
        String dkimSelector,
        String spfDomain
) {}
