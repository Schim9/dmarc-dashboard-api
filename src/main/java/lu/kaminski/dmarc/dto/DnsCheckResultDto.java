package lu.kaminski.dmarc.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DnsCheckResultDto(
        String domain,
        LocalDateTime checkedAt,
        // DMARC
        String dmarcRecord,
        boolean dmarcExists,
        String dmarcPolicy,
        String dmarcRua,
        Integer dmarcPct,
        boolean dmarcValid,
        // SPF
        String spfRecord,
        boolean spfExists,
        String spfPolicy,
        int spfLookupCount,
        boolean spfValid,
        // MX
        boolean mxExists,
        // Détail
        List<DkimSelectorCheckDto> dkimChecks,
        List<DnsIssueDto> issues
) {}
