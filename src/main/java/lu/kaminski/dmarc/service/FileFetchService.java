package lu.kaminski.dmarc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.util.DmarcArchiveExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit les rapports DMARC depuis un dossier local.
 * Formats acceptés : .xml, .zip, .gz
 * Après traitement, les fichiers sont déplacés dans un sous-dossier "processed/".
 */
@Service
@ConditionalOnProperty(name = "dmarc.source.mode", havingValue = "folder", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class FileFetchService implements ReportSourceService {

    @Value("${dmarc.source.folder.path}")
    private String folderPath;

    private final NotifService notifService;
    private final DmarcArchiveExtractor extractor;

    @Override
    public List<FetchedReport> fetchDmarcXmlReports() {
        List<FetchedReport> xmlReports = new ArrayList<>();

        Path inputDir = Path.of(folderPath);

        if (!Files.exists(inputDir)) {
            log.warn("Dossier DMARC introuvable : {}", folderPath);
            return xmlReports;
        }

        Path processedDir = inputDir.resolve("processed");
        try {
            Files.createDirectories(processedDir);
        } catch (IOException e) {
            log.error("Impossible de créer le dossier 'processed' : {}", e.getMessage());
            return xmlReports;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir,
                entry -> Files.isRegularFile(entry) && isSupported(entry.getFileName().toString()))) {

            for (Path file : stream) {
                try {
                    String xml = extractXml(file);
                    if (xml != null) {
                        LocalDateTime receivedAt = LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(file).toInstant(), ZoneOffset.UTC);
                        xmlReports.add(new FetchedReport(xml, receivedAt));
                        Files.move(file, processedDir.resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        log.info("Fichier traité : {}", file.getFileName());
                    }
                } catch (Exception e) {
                    log.error("Erreur traitement fichier {} : {}", file.getFileName(), e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Erreur lecture dossier {} : {}", folderPath, e.getMessage());
        }

        log.info("{} rapport(s) XML extrait(s) depuis {}", xmlReports.size(), folderPath);
        return xmlReports;
    }

    private boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xml") || lower.endsWith(".zip") || lower.endsWith(".gz");
    }

    private String extractXml(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        byte[] bytes = Files.readAllBytes(file);

        if (bytes.length > DmarcArchiveExtractor.MAX_COMPRESSED_BYTES) {
            log.warn("Fichier '{}' trop volumineux ({} octets compressés), ignoré", fileName, bytes.length);
            notifService.sendPushBulletNotif(
                    "Rapport DMARC rejeté : fichier trop volumineux (" + (bytes.length / 1024) + " Ko compressés) — fichier : " + fileName,
                    "DMARC ⚠️ Fichier volumineux"
            );
            return null;
        }

        try {
            return extractor.extractXml(bytes, fileName);
        } catch (IOException e) {
            log.warn("Extraction rejetée pour '{}' : {}", fileName, e.getMessage());
            notifService.sendPushBulletNotif(
                    "Rapport DMARC rejeté : contenu décompressé trop volumineux (>" + (DmarcArchiveExtractor.MAX_DECOMPRESSED_BYTES / 1024 / 1024) + " Mo) — fichier : " + fileName,
                    "DMARC ⚠️ Fichier volumineux"
            );
            return null;
        }
    }
}
