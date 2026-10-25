package lu.kaminski.dmarc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.dto.AlertDto;
import lu.kaminski.dmarc.dto.ImpactedRecipientDto;
import lu.kaminski.dmarc.dto.DomainStatsDto;
import lu.kaminski.dmarc.dto.IpHistoryEntryDto;
import lu.kaminski.dmarc.dto.RecordDto;
import lu.kaminski.dmarc.dto.ReportDto;
import lu.kaminski.dmarc.dto.SourceIpDto;
import lu.kaminski.dmarc.dto.SpfAnalysisDto;
import lu.kaminski.dmarc.dto.TrendDto;
import lu.kaminski.dmarc.dto.VolumeTrendDto;
import lu.kaminski.dmarc.entity.DmarcRecord;
import lu.kaminski.dmarc.entity.DmarcReport;
import lu.kaminski.dmarc.entity.KnownDomain;
import lu.kaminski.dmarc.repository.DmarcRecordRepository;
import lu.kaminski.dmarc.repository.DmarcReportRepository;
import lu.kaminski.dmarc.repository.KnownDomainRepository;
import lu.kaminski.dmarc.xml.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final DmarcReportRepository reportRepository;
    private final DmarcRecordRepository recordRepository;
    private final KnownDomainRepository knownDomainRepository;
    private final IpResolverService ipResolverService;
    private final SpfAnalyzerService spfAnalyzerService;

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    /**
     * Sauvegarde un rapport DMARC parsé. Ignore les doublons (même reportId).
     */
    @Transactional
    public Optional<DmarcReport> saveFromFeedback(Feedback feedback, LocalDateTime receivedAt) {
        if (feedback.getReportMetadata() == null || feedback.getPolicyPublished() == null) {
            log.warn("Rapport DMARC incomplet, ignoré");
            return Optional.empty();
        }

        String reportId = feedback.getReportMetadata().getReportId();

        if (reportRepository.existsByReportId(reportId)) {
            log.info("Rapport {} déjà importé, ignoré", reportId);
            return Optional.empty();
        }

        DmarcReport report = mapToReport(feedback, receivedAt);
        List<DmarcRecord> records = mapToRecords(feedback.getRecords(), report);

        // Calcul des statistiques dénormalisées
        long totalMessages = records.stream().mapToLong(r -> r.getCount() != null ? r.getCount() : 0).sum();
        long passCount = records.stream()
                .filter(r -> "pass".equalsIgnoreCase(r.getDkimResult()) || "pass".equalsIgnoreCase(r.getSpfResult()))
                .mapToLong(r -> r.getCount() != null ? r.getCount() : 0)
                .sum();
        long dkimFailCount = records.stream()
                .filter(r -> !"pass".equalsIgnoreCase(r.getDkimResult()))
                .mapToLong(r -> r.getCount() != null ? r.getCount() : 0)
                .sum();
        long spfFailCount = records.stream()
                .filter(r -> !"pass".equalsIgnoreCase(r.getSpfResult()))
                .mapToLong(r -> r.getCount() != null ? r.getCount() : 0)
                .sum();

        report.setTotalMessages(totalMessages);
        report.setPassCount(passCount);
        report.setFailCount(totalMessages - passCount);
        report.setDkimFailCount(dkimFailCount);
        report.setSpfFailCount(spfFailCount);

        reportRepository.save(report);
        recordRepository.saveAll(records);

        log.info("Rapport importé : {} | org={} | domaine={} | {} message(s)",
                reportId, report.getOrgName(), report.getDomain(), totalMessages);

        return Optional.of(report);
    }

    // -------------------------------------------------------------------------
    // Requêtes pour les contrôleurs
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ReportDto> getReports(String domain, Pageable pageable) {
        Page<DmarcReport> page = (domain != null && !domain.isBlank())
                ? reportRepository.findByDomain(domain, pageable)
                : reportRepository.findAll(pageable);

        return page.map(r -> toDto(r, false));
    }

    @Transactional(readOnly = true)
    public Optional<ReportDto> getReport(Long id) {
        return reportRepository.findById(id).map(r -> toDto(r, true));
    }

    @Transactional(readOnly = true)
    public List<DomainStatsDto> getAllDomainStats() {
        List<Object[]> aggregates = reportRepository.findDomainAggregates();
        List<DomainStatsDto> result = new ArrayList<>(aggregates.stream().map(this::todomainStats).toList());

        // Ajouter les domaines déclarés manuellement sans rapport
        Set<String> domainsWithReports = result.stream().map(DomainStatsDto::domain).collect(Collectors.toSet());
        knownDomainRepository.findAll().stream()
                .filter(kd -> !domainsWithReports.contains(kd.getDomain()))
                .map(kd -> buildDeclaredDomainStats(kd.getDomain()))
                .forEach(result::add);

        return result;
    }

    @Transactional(readOnly = true)
    public Optional<DomainStatsDto> getDomainStats(String domain) {
        List<Object[]> aggregates = reportRepository.findDomainAggregateForDomain(domain);
        if (!aggregates.isEmpty()) {
            return Optional.of(todomainStats(aggregates.get(0)));
        }
        if (knownDomainRepository.existsByDomain(domain)) {
            return Optional.of(buildDeclaredDomainStats(domain));
        }
        return Optional.empty();
    }

    @Transactional
    public void declareDomain(String domain) {
        if (!knownDomainRepository.existsByDomain(domain)) {
            KnownDomain kd = new KnownDomain();
            kd.setDomain(domain);
            kd.setAddedAt(LocalDateTime.now(ZoneOffset.UTC));
            knownDomainRepository.save(kd);
            log.info("Domaine déclaré manuellement : {}", domain);
        }
    }

    @Transactional
    public void undeclareDomain(String domain) {
        knownDomainRepository.deleteByDomain(domain);
        log.info("Domaine retiré de la liste déclarée : {}", domain);
    }

    @Transactional(readOnly = true)
    public List<String> listAllDomains() {
        Set<String> domains = new LinkedHashSet<>(reportRepository.findAllDomains());
        domains.addAll(knownDomainRepository.findAllDomainNames());
        return List.copyOf(domains);
    }

    @Transactional(readOnly = true)
    public List<IpHistoryEntryDto> getIpHistory(String ip) {
        return recordRepository.findBySourceIpOrderByReportBeginDateDesc(ip).stream()
                .map(r -> new IpHistoryEntryDto(
                        r.getReport().getReportId(),
                        r.getReport().getDomain(),
                        r.getReport().getOrgName(),
                        r.getReport().getBeginDate(),
                        r.getReport().getEndDate(),
                        r.getCount() != null ? r.getCount() : 0,
                        r.getDkimResult(),
                        r.getSpfResult(),
                        r.getDisposition(),
                        r.getHeaderFrom()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countReportsThisWeek() {
        return reportRepository.countReportsSince(LocalDateTime.now(ZoneOffset.UTC).minusDays(7));
    }

    @Transactional(readOnly = true)
    public TrendDto getTrend(int days) {
        DateWindow dw = buildDateWindow(days);
        List<LocalDate> dates = dw.dates();
        List<String> labels = dw.labels();

        List<Object[]> raw = reportRepository.findDailyCompliance(dates.get(0).atStartOfDay());

        // Regroupement par domaine → (date → taux de conformité)
        Map<String, Map<LocalDate, Double>> byDomain = new LinkedHashMap<>();
        for (Object[] row : raw) {
            String domain = (String) row[0];
            LocalDate date = toLocalDate(row[1]);
            long total = ((Number) row[2]).longValue();
            long pass  = ((Number) row[3]).longValue();
            double rate = total > 0 ? Math.round((double) pass / total * 10000.0) / 100.0 : 0.0;
            byDomain.computeIfAbsent(domain, k -> new LinkedHashMap<>()).put(date, rate);
        }

        List<TrendDto.DatasetDto> datasets = byDomain.entrySet().stream()
                .map(e -> new TrendDto.DatasetDto(
                        e.getKey(),
                        dates.stream().map(d -> e.getValue().getOrDefault(d, null)).toList()
                ))
                .toList();

        return new TrendDto(labels, datasets);
    }

    @Transactional(readOnly = true)
    public VolumeTrendDto getVolumeTrend(int days) {
        DateWindow dw = buildDateWindow(days);
        List<LocalDate> dates = dw.dates();
        List<String> labels = dw.labels();

        List<Object[]> raw = reportRepository.findDailyCompliance(dates.get(0).atStartOfDay());

        Map<String, Map<LocalDate, long[]>> byDomain = new LinkedHashMap<>();
        for (Object[] row : raw) {
            String domain = (String) row[0];
            LocalDate date = toLocalDate(row[1]);
            long total = ((Number) row[2]).longValue();
            long pass  = ((Number) row[3]).longValue();
            byDomain.computeIfAbsent(domain, k -> new LinkedHashMap<>())
                    .put(date, new long[]{pass, total - pass});
        }

        List<VolumeTrendDto.DatasetDto> datasets = byDomain.entrySet().stream()
                .map(e -> new VolumeTrendDto.DatasetDto(
                        e.getKey(),
                        dates.stream().map(d -> { long[] v = e.getValue().get(d); return v != null ? v[0] : null; }).toList(),
                        dates.stream().map(d -> { long[] v = e.getValue().get(d); return v != null ? v[1] : null; }).toList()
                ))
                .toList();

        return new VolumeTrendDto(labels, datasets);
    }

    @Transactional(readOnly = true)
    public TrendDto getReportCountTrend(int days) {
        DateWindow dw = buildDateWindow(days);
        List<LocalDate> dates = dw.dates();
        List<String> labels = dw.labels();

        List<Object[]> raw = reportRepository.findDailyReportCount(dates.get(0).atStartOfDay());

        Map<String, Map<LocalDate, Double>> byDomain = new LinkedHashMap<>();
        for (Object[] row : raw) {
            String domain = (String) row[0];
            LocalDate date = toLocalDate(row[1]);
            double count = ((Number) row[2]).doubleValue();
            byDomain.computeIfAbsent(domain, k -> new LinkedHashMap<>()).put(date, count);
        }

        List<TrendDto.DatasetDto> datasets = byDomain.entrySet().stream()
                .map(e -> new TrendDto.DatasetDto(
                        e.getKey(),
                        dates.stream().map(d -> e.getValue().getOrDefault(d, null)).toList()
                ))
                .toList();

        return new TrendDto(labels, datasets);
    }

    private record DateWindow(List<LocalDate> dates, List<String> labels) {}

    private DateWindow buildDateWindow(int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate since = today.minusDays(days - 1);
        List<LocalDate> dates = since.datesUntil(today.plusDays(1)).toList();
        List<String> labels = dates.stream()
                .map(d -> d.getDayOfMonth() + " " + d.getMonth()
                        .getDisplayName(TextStyle.FULL, Locale.FRENCH).toLowerCase())
                .toList();
        return new DateWindow(dates, labels);
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj instanceof LocalDate d) return d;
        if (obj instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(obj.toString());
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private DmarcReport mapToReport(Feedback feedback, LocalDateTime receivedAt) {
        ReportMetadata meta = feedback.getReportMetadata();
        PolicyPublished policy = feedback.getPolicyPublished();
        DateRange range = meta.getDateRange();

        DmarcReport report = new DmarcReport();
        report.setReportId(meta.getReportId());
        report.setOrgName(meta.getOrgName());
        report.setOrgEmail(meta.getEmail());
        report.setDomain(policy.getDomain());
        report.setBeginDate(LocalDateTime.ofEpochSecond(range.getBegin(), 0, ZoneOffset.UTC));
        report.setEndDate(LocalDateTime.ofEpochSecond(range.getEnd(), 0, ZoneOffset.UTC));
        report.setPolicy(policy.getP());
        report.setAdkim(policy.getAdkim());
        report.setAspf(policy.getAspf());
        report.setPct(policy.getPct() != null ? policy.getPct() : 100);
        report.setReceivedAt(receivedAt != null ? receivedAt : LocalDateTime.now(ZoneOffset.UTC));
        return report;
    }

    private List<DmarcRecord> mapToRecords(List<ReportRecord> xmlRecords, DmarcReport report) {
        if (xmlRecords == null) return Collections.emptyList();

        return xmlRecords.stream().map(rec -> {
            DmarcRecord entity = new DmarcRecord();
            entity.setReport(report);

            Row row = rec.getRow();
            if (row != null) {
                entity.setSourceIp(row.getSourceIp());
                entity.setHostname(ipResolverService.resolve(row.getSourceIp()));
                entity.setCount(row.getCount());
                if (row.getPolicyEvaluated() != null) {
                    entity.setDisposition(row.getPolicyEvaluated().getDisposition());
                    entity.setDkimResult(row.getPolicyEvaluated().getDkim());
                    entity.setSpfResult(row.getPolicyEvaluated().getSpf());
                }
            }

            if (rec.getIdentifiers() != null) {
                entity.setHeaderFrom(rec.getIdentifiers().getHeaderFrom());
                entity.setEnvelopeFrom(rec.getIdentifiers().getEnvelopeFrom());
                entity.setEnvelopeTo(rec.getIdentifiers().getEnvelopeTo());
            }

            if (rec.getAuthResults() != null) {
                List<DkimAuthResult> dkimList = rec.getAuthResults().getDkim();
                if (dkimList != null && !dkimList.isEmpty()) {
                    entity.setDkimDomain(dkimList.get(0).getDomain());
                    entity.setDkimSelector(dkimList.get(0).getSelector());
                }
                List<SpfAuthResult> spfList = rec.getAuthResults().getSpf();
                if (spfList != null && !spfList.isEmpty()) {
                    entity.setSpfDomain(spfList.get(0).getDomain());
                }
            }

            return entity;
        }).toList();
    }

    private ReportDto toDto(DmarcReport report, boolean includeRecords) {
        double complianceRate = report.getTotalMessages() > 0
                ? (double) report.getPassCount() / report.getTotalMessages() * 100
                : 0.0;

        List<RecordDto> recordDtos = null;
        if (includeRecords) {
            recordDtos = report.getRecords().stream().map(this::toRecordDto).toList();
        }

        return new ReportDto(
                report.getId(), report.getReportId(), report.getOrgName(), report.getOrgEmail(),
                report.getDomain(), report.getBeginDate(), report.getEndDate(),
                report.getPolicy(), report.getAdkim(), report.getAspf(), report.getPct(),
                report.getReceivedAt(), report.getTotalMessages(), report.getPassCount(),
                report.getFailCount(), report.getDkimFailCount(), report.getSpfFailCount(),
                Math.round(complianceRate * 100.0) / 100.0,
                recordDtos
        );
    }

    private RecordDto toRecordDto(DmarcRecord r) {
        return new RecordDto(
                r.getId(), r.getSourceIp(), r.getCount() != null ? r.getCount() : 0,
                r.getDisposition(), r.getDkimResult(), r.getSpfResult(),
                r.getHeaderFrom(), r.getEnvelopeFrom(), r.getEnvelopeTo(),
                r.getDkimDomain(), r.getDkimSelector(), r.getSpfDomain()
        );
    }

    private DomainStatsDto todomainStats(Object[] row) {
        String domain = (String) row[0];
        long totalReports = ((Number) row[1]).longValue();
        long totalMessages = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long passCount = row[3] != null ? ((Number) row[3]).longValue() : 0;
        long failCount = row[4] != null ? ((Number) row[4]).longValue() : 0;
        String policy = (String) row[5];
        LocalDateTime lastReportDate = row[6] instanceof LocalDateTime ldt ? ldt : null;

        double complianceRate = totalMessages > 0
                ? Math.round((double) passCount / totalMessages * 10000.0) / 100.0
                : 0.0;

        // Top 5 IP sources
        List<Object[]> topSourcesRaw = recordRepository.findTopSourcesByDomain(
                domain, PageRequest.of(0, 5));
        List<SourceIpDto> topSources = topSourcesRaw.stream()
                .map(s -> new SourceIpDto(
                        (String) s[0],
                        ((Number) s[1]).longValue(),
                        (String) s[2],
                        s[3] != null ? ((Number) s[3]).longValue() : 0,
                        s[4] != null ? ((Number) s[4]).longValue() : 0,
                        s[5] != null ? ((Number) s[5]).longValue() : 0,
                        s[6] != null ? ((Number) s[6]).longValue() : 0
                ))
                .toList();

        // Conformité agrégée sur tous les rapports dans la fenêtre de 48h autour du dernier rapport reçu
        double lastReportComplianceRate = reportRepository.findFirstByDomainOrderByEndDateDesc(domain)
                .map(latest -> {
                    LocalDateTime windowStart = latest.getEndDate().minusHours(48);
                    LocalDateTime windowEnd   = latest.getEndDate().plusHours(1);
                    List<DmarcReport> window  = reportRepository.findByDomainAndEndDateBetween(domain, windowStart, windowEnd);
                    long totalMsg = window.stream().mapToLong(DmarcReport::getTotalMessages).sum();
                    long passMsg  = window.stream().mapToLong(DmarcReport::getPassCount).sum();
                    return totalMsg > 0 ? Math.round((double) passMsg / totalMsg * 10000.0) / 100.0 : 100.0;
                })
                .orElse(100.0);

        // Destinataires impactés (quarantine ou reject)
        List<ImpactedRecipientDto> impactedRecipients = recordRepository.findImpactedRecipients(domain).stream()
                .map(r -> new ImpactedRecipientDto((String) r[0], (String) r[1], ((Number) r[2]).longValue(), (java.time.LocalDateTime) r[3], ((Number) r[4]).longValue(), ((Number) r[5]).longValue(), (java.time.LocalDateTime) r[6]))
                .toList();

        // Génération des alertes
        List<AlertDto> alerts = buildAlerts(domain, policy, complianceRate, failCount, totalMessages, lastReportComplianceRate);

        // Analyse SPF
        SpfAnalysisDto spfAnalysis = null;
        try {
            spfAnalysis = spfAnalyzerService.analyze(domain);
        } catch (IllegalArgumentException e) {
            log.warn("Analyse SPF ignorée pour domaine invalide : {}", domain);
        }

        return new DomainStatsDto(domain, totalReports, totalMessages, passCount, failCount,
                complianceRate, lastReportComplianceRate, policy, lastReportDate, topSources, impactedRecipients, alerts, spfAnalysis, false);
    }

    private DomainStatsDto buildDeclaredDomainStats(String domain) {
        SpfAnalysisDto spfAnalysis = null;
        try {
            spfAnalysis = spfAnalyzerService.analyze(domain);
        } catch (IllegalArgumentException e) {
            log.warn("Analyse SPF ignorée pour domaine invalide : {}", domain);
        }
        List<AlertDto> alerts = List.of(new AlertDto(
                "info",
                "Aucun rapport DMARC reçu",
                "Ce domaine est déclaré manuellement. Aucun rapport d'agrégat n'a encore été reçu — c'est normal si le domaine n'envoie pas d'emails ou si la configuration est très récente.",
                "Vérifiez que l'adresse rua= dans votre enregistrement DMARC est correcte et accessible. Les fournisseurs de messagerie envoient généralement les rapports quotidiennement.",
                null
        ));
        return new DomainStatsDto(domain, 0, 0, 0, 0, 0.0, 100.0, null, null, List.of(), List.of(), alerts, spfAnalysis, true);
    }

    /**
     * Génère les points d'attention structurés pour un domaine.
     */
    private List<AlertDto> buildAlerts(String domain, String policy, double complianceRate, long failCount, long totalMessages, double lastReportComplianceRate) {
        List<AlertDto> alerts = new ArrayList<>();

        if ("none".equalsIgnoreCase(policy)) {
            alerts.add(new AlertDto(
                    "warning",
                    "Politique DMARC non appliquée",
                    "Votre politique est en mode surveillance (p=none). Les emails non conformes sont livrés normalement — aucune protection active contre l'usurpation de votre domaine.",
                    "Une fois la conformité stable au-dessus de 95%, passez à p=quarantine pour envoyer les emails suspects en spam, puis à p=reject pour les bloquer complètement.",
                    "_dmarc." + domain + " TXT \"v=DMARC1; p=quarantine; pct=25; rua=mailto:dmarc@" + domain + "\""
            ));
        } else if ("quarantine".equalsIgnoreCase(policy)) {
            alerts.add(new AlertDto(
                    "info",
                    "Politique en quarantaine — prochaine étape : rejet",
                    "Votre politique (p=quarantine) envoie les emails non conformes en spam. C'est une bonne étape intermédiaire.",
                    "Une fois la conformité stable au-dessus de 98%, passez à p=reject pour une protection maximale.",
                    "_dmarc." + domain + " TXT \"v=DMARC1; p=reject; rua=mailto:dmarc@" + domain + "\""
            ));
        }

        if (!"none".equalsIgnoreCase(policy) && lastReportComplianceRate < 100.0) {
            alerts.add(new AlertDto(
                    "warning",
                    String.format("Dernier rapport : %.1f%% de conformité", lastReportComplianceRate),
                    "Le rapport le plus récent contient des messages non conformes. Des emails légitimes sont peut-être partis en spam.",
                    "Consultez le détail du domaine pour identifier les IPs sources en échec et corriger la configuration.",
                    null
            ));
        }

        if (totalMessages > 0 && complianceRate < 80) {
            alerts.add(new AlertDto(
                    "critical",
                    String.format("Conformité critique : %.1f%%", complianceRate),
                    String.format("%d message(s) sur %d échouent DMARC. Ce taux élevé d'échecs peut indiquer des sources non autorisées envoyant en votre nom, ou une misconfiguration SPF/DKIM importante.",
                            failCount, totalMessages),
                    "Consultez le détail du domaine pour identifier les IPs sources en échec. Vérifiez votre SPF (maximum 10 lookups DNS) et assurez-vous que DKIM est configuré sur tous vos serveurs d'envoi.",
                    null
            ));
        } else if (totalMessages > 0 && complianceRate < 95) {
            alerts.add(new AlertDto(
                    "warning",
                    String.format("Taux de conformité insuffisant : %.1f%%", complianceRate),
                    String.format("%d message(s) sur %d échouent DMARC. Le seuil recommandé avant de renforcer la politique est de 95%%.",
                            failCount, totalMessages),
                    "Identifiez les sources en échec dans le détail du domaine. Les causes fréquentes : serveurs relais non listés dans le SPF, transferts d'emails (forwarding) qui cassent DKIM, ou outils marketing non configurés.",
                    null
            ));
        }

        return alerts;
    }
}
