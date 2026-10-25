package lu.kaminski.dmarc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.dto.DkimSelectorCheckDto;
import lu.kaminski.dmarc.dto.DnsCheckResultDto;
import lu.kaminski.dmarc.dto.DnsIssueDto;
import lu.kaminski.dmarc.entity.DnsCheck;
import lu.kaminski.dmarc.repository.DmarcRecordRepository;
import lu.kaminski.dmarc.repository.DnsCheckRepository;
import lu.kaminski.dmarc.repository.KnownDomainRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DnsCheckService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );

    private final DnsCheckRepository checkRepository;
    private final DmarcRecordRepository recordRepository;
    private final KnownDomainRepository domainRepository;
    private final ObjectMapper objectMapper;

    public DnsCheckService(DnsCheckRepository checkRepository,
                           DmarcRecordRepository recordRepository,
                           KnownDomainRepository domainRepository,
                           ObjectMapper objectMapper) {
        this.checkRepository = checkRepository;
        this.recordRepository = recordRepository;
        this.domainRepository = domainRepository;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // API publique
    // =========================================================================

    @Transactional
    public DnsCheckResultDto checkDomain(String domain) {
        validateDomain(domain);
        log.info("Vérification DNS pour {}", domain);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // --- DMARC ---
        String dmarcRaw       = findDmarcRecord(domain);
        boolean dmarcExists   = dmarcRaw != null;
        String dmarcPolicy    = dmarcExists ? parseTag(dmarcRaw, "p")   : null;
        String dmarcRua       = dmarcExists ? parseTag(dmarcRaw, "rua") : null;
        String dmarcPctStr    = dmarcExists ? parseTag(dmarcRaw, "pct") : null;
        Integer dmarcPct      = dmarcPctStr != null ? parseIntSafe(dmarcPctStr) : (dmarcExists ? 100 : null);
        boolean dmarcValid    = dmarcExists && dmarcPolicy != null;

        // --- SPF ---
        String spfRaw         = findSpfRecord(domain);
        boolean spfExists     = spfRaw != null;
        String spfPolicy      = spfExists ? extractAllQualifier(spfRaw) : null;
        int spfLookups        = spfExists ? countSpfLookups(spfRaw) : 0;
        boolean spfValid      = spfExists && spfPolicy != null && !spfPolicy.equals("+all");

        // --- DKIM ---
        List<String> selectors = recordRepository.findDistinctDkimSelectorsByDomain(domain);
        List<DkimSelectorCheckDto> dkimChecks = selectors.stream()
                .map(sel -> new DkimSelectorCheckDto(sel, dkimSelectorExists(sel, domain)))
                .collect(Collectors.toList());

        // --- MX ---
        boolean mxExists = hasMxRecords(domain);

        // --- Issues ---
        List<DnsIssueDto> issues = buildIssues(
                domain, dmarcExists, dmarcPolicy, dmarcRua, dmarcPct,
                spfExists, spfRaw, spfPolicy, spfLookups,
                dkimChecks, mxExists
        );

        // --- Persistance ---
        DnsCheck entity = checkRepository.findByDomain(domain).orElse(new DnsCheck());
        entity.setDomain(domain);
        entity.setCheckedAt(now);
        entity.setDmarcRecord(dmarcRaw);
        entity.setDmarcExists(dmarcExists);
        entity.setDmarcPolicy(dmarcPolicy);
        entity.setDmarcRua(dmarcRua);
        entity.setDmarcPct(dmarcPct);
        entity.setDmarcValid(dmarcValid);
        entity.setSpfRecord(spfRaw);
        entity.setSpfExists(spfExists);
        entity.setSpfPolicy(spfPolicy);
        entity.setSpfLookupCount(spfLookups);
        entity.setSpfValid(spfValid);
        entity.setMxExists(mxExists);
        entity.setDkimChecksJson(toJson(dkimChecks));
        entity.setIssuesJson(toJson(issues));
        checkRepository.save(entity);

        return toDto(entity, dkimChecks, issues);
    }

    @Transactional(readOnly = true)
    public Optional<DnsCheckResultDto> getLastResult(String domain) {
        return checkRepository.findByDomain(domain).map(e -> {
            List<DkimSelectorCheckDto> dkim  = fromJson(e.getDkimChecksJson(), new TypeReference<>() {});
            List<DnsIssueDto>          issues = fromJson(e.getIssuesJson(),      new TypeReference<>() {});
            return toDto(e, dkim, issues);
        });
    }

    @Async
    @Transactional
    public void checkAll() {
        List<String> domains = domainRepository.findAllDomainNames();
        log.info("Vérification DNS pour {} domaine(s)", domains.size());
        domains.forEach(domain -> {
            try {
                checkDomain(domain);
            } catch (IllegalArgumentException e) {
                log.warn("Domaine ignoré lors du check DNS (invalide) : {}", e.getMessage());
            }
        });
    }

    // =========================================================================
    // DNS lookups
    // =========================================================================

    private String findDmarcRecord(String domain) {
        return getTxtRecords("_dmarc." + domain).stream()
                .filter(r -> r.startsWith("v=DMARC1"))
                .findFirst().orElse(null);
    }

    private String findSpfRecord(String domain) {
        return getTxtRecords(domain).stream()
                .filter(r -> r.startsWith("v=spf1"))
                .findFirst().orElse(null);
    }

    private boolean dkimSelectorExists(String selector, String domain) {
        List<String> records = getTxtRecords(selector + "._domainkey." + domain);
        return records.stream().anyMatch(r -> r.contains("p="));
    }

    private boolean hasMxRecords(String domain) {
        try {
            InitialDirContext ctx = createDnsCtx();
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            ctx.close();
            Attribute mx = attrs.get("MX");
            return mx != null && mx.size() > 0;
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException e) {
            log.debug("Erreur MX pour {} : {}", domain, e.getMessage());
            return false;
        }
    }

    private List<String> getTxtRecords(String hostname) {
        try {
            InitialDirContext ctx = createDnsCtx();
            Attributes attrs = ctx.getAttributes(hostname, new String[]{"TXT"});
            ctx.close();
            Attribute txtAttr = attrs.get("TXT");
            if (txtAttr == null) return List.of();
            List<String> results = new ArrayList<>();
            NamingEnumeration<?> values = txtAttr.getAll();
            while (values.hasMoreElements()) {
                String val = values.next().toString().trim();
                if (val.startsWith("\"") && val.endsWith("\""))
                    val = val.substring(1, val.length() - 1);
                results.add(val);
            }
            return results;
        } catch (NameNotFoundException e) {
            return List.of();
        } catch (NamingException e) {
            log.debug("Erreur DNS TXT pour {} : {}", hostname, e.getMessage());
            return List.of();
        }
    }

    private InitialDirContext createDnsCtx() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", "5000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        return new InitialDirContext(env);
    }

    // =========================================================================
    // Parsers SPF / DMARC
    // =========================================================================

    /** Extrait la valeur d'un tag DMARC/SPF du type tag=valeur */
    private String parseTag(String record, String tag) {
        if (record == null) return null;
        for (String part : record.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase().startsWith(tag + "=")) {
                return trimmed.substring(tag.length() + 1).trim();
            }
        }
        return null;
    }

    /** Retourne le qualificateur final du SPF : -all, ~all, +all, ?all */
    private String extractAllQualifier(String spfRecord) {
        if (spfRecord == null) return null;
        String[] terms = spfRecord.split("\\s+");
        for (int i = terms.length - 1; i >= 0; i--) {
            String t = terms[i].toLowerCase();
            if (t.equals("-all") || t.equals("~all") || t.equals("+all") || t.equals("?all") || t.equals("all"))
                return t;
        }
        return null;
    }

    /**
     * Compte les mécanismes SPF nécessitant une résolution DNS.
     * Ne suit pas les include: récursivement (estimation).
     */
    private int countSpfLookups(String spfRecord) {
        if (spfRecord == null) return 0;
        int count = 0;
        for (String term : spfRecord.toLowerCase().split("\\s+")) {
            if (term.matches("[+\\-~?]?a(:.+)?")
                    || term.matches("[+\\-~?]?mx(:.+)?")
                    || term.startsWith("include:")
                    || term.startsWith("exists:")
                    || term.equals("ptr") || term.startsWith("ptr:")) {
                count++;
            }
        }
        return count;
    }

    private Integer parseIntSafe(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    // =========================================================================
    // Validation
    // =========================================================================

    private void validateDomain(String domain) {
        if (domain == null || domain.length() > 253 || !DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Nom de domaine invalide : " + domain);
        }
    }

    // =========================================================================
    // Génération des issues
    // =========================================================================

    private List<DnsIssueDto> buildIssues(
            String domain,
            boolean dmarcExists, String dmarcPolicy, String dmarcRua, Integer dmarcPct,
            boolean spfExists, String spfRecord, String spfPolicy, int spfLookups,
            List<DkimSelectorCheckDto> dkimChecks, boolean mxExists) {

        List<DnsIssueDto> issues = new ArrayList<>();

        // --- MX ---
        if (!mxExists) {
            issues.add(new DnsIssueDto("warning", "MX",
                    "Aucun enregistrement MX trouvé pour " + domain,
                    "Vérifiez que vos enregistrements MX sont correctement configurés."));
        }

        // --- DMARC ---
        if (!dmarcExists) {
            issues.add(new DnsIssueDto("critical", "DMARC",
                    "Aucun enregistrement DMARC trouvé (_dmarc." + domain + ")",
                    "Ajoutez un enregistrement TXT : _dmarc." + domain + " → \"v=DMARC1; p=none; rua=mailto:dmarc@" + domain + "\""));
        } else {
            if (dmarcPolicy == null) {
                issues.add(new DnsIssueDto("critical", "DMARC",
                        "L'enregistrement DMARC ne contient pas de politique (p=)",
                        "Ajoutez p=none, p=quarantine ou p=reject."));
            } else if (dmarcPolicy.equals("none")) {
                issues.add(new DnsIssueDto("warning", "DMARC",
                        "Politique DMARC en mode surveillance (p=none) — aucune protection active",
                        "Une fois la conformité stable > 95%, passez à p=quarantine puis p=reject."));
            }
            if (dmarcRua == null || dmarcRua.isBlank()) {
                issues.add(new DnsIssueDto("warning", "DMARC",
                        "Aucune adresse de rapport (rua=) définie",
                        "Ajoutez rua=mailto:dmarc@" + domain + " pour recevoir les rapports agrégés."));
            }
            if (dmarcPct != null && dmarcPct < 100) {
                issues.add(new DnsIssueDto("info", "DMARC",
                        "La politique DMARC ne s'applique qu'à " + dmarcPct + "% des messages (pct=" + dmarcPct + ")",
                        "Augmentez progressivement jusqu'à pct=100 une fois la conformité validée."));
            }
        }

        // --- SPF ---
        if (!spfExists) {
            issues.add(new DnsIssueDto("critical", "SPF",
                    "Aucun enregistrement SPF trouvé pour " + domain,
                    "Ajoutez un enregistrement TXT : v=spf1 include:mx.votrehebergeur.com ~all"));
        } else {
            if ("+all".equals(spfPolicy)) {
                issues.add(new DnsIssueDto("critical", "SPF",
                        "Politique SPF +all : n'importe quelle IP peut envoyer des emails en votre nom !",
                        "Remplacez +all par -all (strict) ou ~all (softfail)."));
            } else if ("?all".equals(spfPolicy)) {
                issues.add(new DnsIssueDto("warning", "SPF",
                        "Politique SPF ?all (neutre) : faible protection contre l'usurpation",
                        "Utilisez ~all ou -all pour une meilleure protection."));
            } else if (spfPolicy == null) {
                issues.add(new DnsIssueDto("warning", "SPF",
                        "L'enregistrement SPF ne contient pas de politique finale (all)",
                        "Terminez votre enregistrement SPF par ~all ou -all."));
            }
            if (spfLookups > 10) {
                issues.add(new DnsIssueDto("critical", "SPF",
                        "L'enregistrement SPF dépasse 10 résolutions DNS (" + spfLookups + " détectées)",
                        "Fusionnez les includes ou utilisez un outil de flattening SPF."));
            } else if (spfLookups > 7) {
                issues.add(new DnsIssueDto("warning", "SPF",
                        "L'enregistrement SPF approche la limite des 10 résolutions DNS (" + spfLookups + "/10)",
                        "Pensez à optimiser vos includes pour éviter le dépassement."));
            }
        }

        // --- DKIM ---
        if (dkimChecks.isEmpty()) {
            issues.add(new DnsIssueDto("info", "DKIM",
                    "Aucun sélecteur DKIM observé dans les rapports DMARC",
                    "Importez des rapports DMARC pour que les sélecteurs soient détectés automatiquement."));
        } else {
            dkimChecks.stream()
                    .filter(c -> !c.exists())
                    .forEach(c -> issues.add(new DnsIssueDto("critical", "DKIM",
                            "Sélecteur DKIM \"" + c.selector() + "\" introuvable en DNS (" + c.selector() + "._domainkey." + domain + ")",
                            "Vérifiez que l'enregistrement DKIM est bien publié chez votre hébergeur DNS.")));
        }

        return issues;
    }

    // =========================================================================
    // Mapping DTO
    // =========================================================================

    private DnsCheckResultDto toDto(DnsCheck e,
                                     List<DkimSelectorCheckDto> dkimChecks,
                                     List<DnsIssueDto> issues) {
        return new DnsCheckResultDto(
                e.getDomain(), e.getCheckedAt(),
                e.getDmarcRecord(), e.isDmarcExists(), e.getDmarcPolicy(), e.getDmarcRua(), e.getDmarcPct(), e.isDmarcValid(),
                e.getSpfRecord(), e.isSpfExists(), e.getSpfPolicy(), e.getSpfLookupCount(), e.isSpfValid(),
                e.isMxExists(),
                dkimChecks, issues
        );
    }

    private <T> String toJson(T obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }

    private <T> List<T> fromJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { return List.of(); }
    }
}
