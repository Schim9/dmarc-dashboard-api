package lu.kaminski.dmarc.service;

import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.dto.SpfAnalysisDto;
import org.springframework.stereotype.Service;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SpfAnalyzerService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );

    private static final int MAX_LOOKUPS  = 10;
    private static final int WARN_LOOKUPS = 8;
    private static final int MAX_DEPTH    = 2;

    public SpfAnalysisDto analyze(String domain) {
        validateDomain(domain);

        FetchResult fetch = fetchSpfRecord(domain);

        if (fetch.multiple()) {
            return new SpfAnalysisDto(null, null, 0,
                    List.of(new SpfAnalysisDto.SpfIssue("critical",
                            "Plusieurs enregistrements SPF détectés — un seul est autorisé, les autres sont ignorés.")));
        }

        if (fetch.record() == null) {
            return new SpfAnalysisDto(null, null, 0,
                    List.of(new SpfAnalysisDto.SpfIssue("critical",
                            "Aucun enregistrement SPF trouvé — votre domaine peut être usurpé sans contrôle SPF.")));
        }

        String record   = fetch.record();
        String policy   = extractPolicy(record);
        int    lookups  = countLookups(record, 0);

        return new SpfAnalysisDto(record, policy, lookups, buildIssues(policy, lookups));
    }

    // -------------------------------------------------------------------------

    private FetchResult fetchSpfRecord(String domain) {
        try {
            // Valider le domaine avant tout lookup JNDI (valeurs issues des include: SPF)
            if (domain == null || domain.length() > 253 || !DOMAIN_PATTERN.matcher(domain).matches()) {
                log.warn("Domaine SPF invalide ignoré : {}", domain);
                return new FetchResult(null, false);
            }

            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"TXT"});
            Attribute txtAttr = attrs.get("TXT");

            if (txtAttr == null) return new FetchResult(null, false);

            List<String> spfRecords = new ArrayList<>();
            for (int i = 0; i < txtAttr.size(); i++) {
                String val = txtAttr.get(i).toString().replace("\"", "").trim();
                if (val.startsWith("v=spf1")) {
                    spfRecords.add(val);
                }
            }

            if (spfRecords.isEmpty())  return new FetchResult(null, false);
            if (spfRecords.size() > 1) return new FetchResult(null, true);
            return new FetchResult(spfRecords.get(0), false);

        } catch (Exception e) {
            log.warn("Impossible de récupérer l'enregistrement SPF pour {} : {}", domain, e.getMessage());
            return new FetchResult(null, false);
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateDomain(String domain) {
        if (domain == null || domain.length() > 253 || !DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Nom de domaine invalide : " + domain);
        }
    }

    // -------------------------------------------------------------------------

    private String extractPolicy(String record) {
        String[] parts = record.toLowerCase().split("\\s+");
        for (String part : parts) {
            if (part.endsWith("all")) return part;
        }
        return "?all";
    }

    private int countLookups(String record, int depth) {
        if (record == null || depth > MAX_DEPTH) return 0;

        int count = 0;
        for (String part : record.toLowerCase().split("\\s+")) {
            // Supprimer les qualificateurs +, -, ~, ?
            String mech = part.replaceAll("^[+\\-~?]", "");

            if (mech.startsWith("include:")) {
                count++;
                if (depth < MAX_DEPTH) {
                    String included = fetchSpfRecord(mech.substring("include:".length())).record();
                    count += countLookups(included, depth + 1);
                }
            } else if (mech.startsWith("redirect=")) {
                count++;
                if (depth < MAX_DEPTH) {
                    String redirected = fetchSpfRecord(mech.substring("redirect=".length())).record();
                    count += countLookups(redirected, depth + 1);
                }
            } else if (mech.equals("a") || mech.startsWith("a:") || mech.startsWith("a/")) {
                count++;
            } else if (mech.equals("mx") || mech.startsWith("mx:") || mech.startsWith("mx/")) {
                count++;
            } else if (mech.equals("ptr") || mech.startsWith("ptr:")) {
                count++;
            } else if (mech.startsWith("exists:")) {
                count++;
            }
        }
        return count;
    }

    private List<SpfAnalysisDto.SpfIssue> buildIssues(String policy, int lookups) {
        List<SpfAnalysisDto.SpfIssue> issues = new ArrayList<>();

        switch (policy) {
            case "+all", "all" -> issues.add(new SpfAnalysisDto.SpfIssue("critical",
                    "Politique '+all' : n'importe quel serveur peut envoyer en votre nom."));
            case "?all" -> issues.add(new SpfAnalysisDto.SpfIssue("warning",
                    "Politique '?all' (neutre) : aucune action n'est recommandée aux serveurs destinataires."));
            case "~all" -> issues.add(new SpfAnalysisDto.SpfIssue("info",
                    "Politique '~all' (softfail) : les emails non conformes sont acceptés mais marqués suspects."));
        }

        if (lookups > MAX_LOOKUPS) {
            issues.add(new SpfAnalysisDto.SpfIssue("critical",
                    String.format("Limite DNS dépassée : %d lookups sur 10 maximum. Certains serveurs rejettent vos emails.", lookups)));
        } else if (lookups >= WARN_LOOKUPS) {
            issues.add(new SpfAnalysisDto.SpfIssue("warning",
                    String.format("Proche de la limite DNS : %d/10 lookups utilisés.", lookups)));
        }

        return issues;
    }

    private record FetchResult(String record, boolean multiple) {}
}
