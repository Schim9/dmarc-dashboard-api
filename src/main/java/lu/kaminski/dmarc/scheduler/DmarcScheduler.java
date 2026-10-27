package lu.kaminski.dmarc.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.entity.DmarcReport;
import lu.kaminski.dmarc.service.DmarcParserService;
import lu.kaminski.dmarc.service.NotifService;
import lu.kaminski.dmarc.service.ReportService;
import lu.kaminski.dmarc.service.ReportSourceService;
import lu.kaminski.dmarc.service.ReportSourceService.FetchedReport;
import lu.kaminski.dmarc.xml.Feedback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "dmarc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DmarcScheduler {

    private final ReportSourceService reportSourceService;
    private final DmarcParserService parserService;
    private final ReportService reportService;
    private final NotifService notifService;

    @Scheduled(cron = "${dmarc.scheduler.check-mailbox}")
    public void fetchAndProcessReports() {
        log.info("=== Début de la collecte DMARC ===");

        List<FetchedReport> xmlReports = reportSourceService.fetchDmarcXmlReports();

        AtomicInteger imported = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        List<DmarcReport> importedReports = new ArrayList<>();

        for (FetchedReport fetched : xmlReports) {
            parserService.parse(fetched.xml()).ifPresentOrElse(
                    feedback -> reportService.saveFromFeedback(feedback, fetched.receivedAt()).ifPresentOrElse(
                            r -> { imported.incrementAndGet(); importedReports.add(r); },
                            skipped::incrementAndGet
                    ),
                    errors::incrementAndGet
            );
        }

        log.info("=== Collecte DMARC terminée : {} importé(s), {} ignoré(s), {} erreur(s) ===",
                imported.get(), skipped.get(), errors.get());

        notifySummary(importedReports);
    }

    /**
     * Méthode publique pour déclencher une collecte manuellement via le contrôleur.
     */
    public FetchResult runManually() {
        log.info("Collecte DMARC déclenchée manuellement");

        List<FetchedReport> xmlReports = reportSourceService.fetchDmarcXmlReports();

        int imported = 0, skipped = 0, errors = 0;

        List<DmarcReport> importedReports = new ArrayList<>();

        for (FetchedReport fetched : xmlReports) {
            try {
                Feedback feedback = parserService.parse(fetched.xml()).orElse(null);
                if (feedback == null) { errors++; continue; }
                var saved = reportService.saveFromFeedback(feedback, fetched.receivedAt());
                if (saved.isPresent()) { imported++; importedReports.add(saved.get()); }
                else skipped++;
            } catch (Exception e) {
                log.error("Erreur traitement rapport : {}", e.getMessage());
                errors++;
            }
        }

        notifySummary(importedReports);
        return new FetchResult(xmlReports.size(), imported, skipped, errors);
    }

    @Scheduled(cron = "${dmarc.scheduler.weekly-report}")
    public void sendWeeklySummary() {
        long count = reportService.countReportsThisWeek();
        notifService.sendPushBulletNotif(
                count + " rapport(s) DMARC traités cette semaine. Consultez le dashboard pour le détail.",
                "DMARC ✅"
        );
        log.info("Résumé hebdomadaire envoyé : {} rapport(s) cette semaine", count);
    }

    private void notifySummary(List<DmarcReport> reports) {
        if (reports.isEmpty()) return;

        // Agréger par domaine : totalMessages et passCount
        Map<String, long[]> byDomain = new LinkedHashMap<>();
        for (DmarcReport r : reports) {
            byDomain.computeIfAbsent(r.getDomain(), k -> new long[2]);
            byDomain.get(r.getDomain())[0] += r.getTotalMessages();
            byDomain.get(r.getDomain())[1] += r.getPassCount();
        }

        List<String> anomalies = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : byDomain.entrySet()) {
            long total = entry.getValue()[0];
            long pass  = entry.getValue()[1];
            if (total > 0 && pass < total) {
                double rate = (double) pass / total * 100;
                anomalies.add(String.format("%s : %.1f%%", entry.getKey(), rate));
            }
        }

        if (!anomalies.isEmpty()) {
            notifService.sendPushBulletNotif(
                    "Anomalie(s) détectée(s) :\n" + String.join("\n", anomalies),
                    "DMARC ⚠️"
            );
        }
    }

    public record FetchResult(int fetched, int imported, int skipped, int errors) {}
}
