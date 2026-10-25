package lu.kaminski.dmarc.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface commune pour les sources de rapports DMARC.
 * Deux implémentations disponibles, sélectionnées via dmarc.source.mode :
 * - "mail"  : récupération depuis une boîte IMAP
 * - "folder": lecture depuis un dossier local
 */
public interface ReportSourceService {

    List<FetchedReport> fetchDmarcXmlReports();

    record FetchedReport(String xml, LocalDateTime receivedAt) {}
}
