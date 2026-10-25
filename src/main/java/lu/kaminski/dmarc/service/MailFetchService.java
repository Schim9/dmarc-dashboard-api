package lu.kaminski.dmarc.service;

import jakarta.mail.*;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.util.DmarcArchiveExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
@ConditionalOnProperty(name = "dmarc.source.mode", havingValue = "mail")
@Slf4j
public class MailFetchService implements ReportSourceService {

    @Autowired
    private NotifService notifService;

    @Autowired
    private DmarcArchiveExtractor extractor;

    @Value("${dmarc.mail.host}")
    private String host;

    @Value("${dmarc.mail.port}")
    private int port;

    @Value("${dmarc.mail.username}")
    private String username;

    @Value("${dmarc.mail.password}")
    private String password;

    @Value("${dmarc.mail.ssl:true}")
    private boolean ssl;

    @Value("${dmarc.mail.folder:INBOX}")
    private String folderName;

    @Value("${dmarc.mail.processed-folder:Processed}")
    private String processedFolderName;

    /**
     * Se connecte au serveur IMAP, récupère les messages non lus contenant
     * des rapports DMARC (pièces jointes .zip ou .gz), et retourne les XML extraits.
     * Les messages traités sont marqués comme lus.
     */
    @Override
    public List<FetchedReport> fetchDmarcXmlReports() {
        List<FetchedReport> xmlReports = new ArrayList<>();

        Properties props = buildMailProperties();
        Session session = Session.getInstance(props);

        String protocol = ssl ? "imaps" : "imap";

        try {
            Store store = session.getStore(protocol);
            store.connect(host, port, username, password);
            log.info("Connecté au serveur IMAP : {}", host);

            try (store) {
                Folder processedFolder = getOrCreateFolder(store, processedFolderName);

                try (Folder folder = store.getFolder(folderName)) {
                    folder.open(Folder.READ_WRITE);

                    Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                    log.info("{} message(s) non lu(s) trouvé(s) dans {}", messages.length, folderName);

                    for (Message message : messages) {
                        try {
                            LocalDateTime receivedAt = toLocalDateTime(message.getReceivedDate());
                            List<String> xmls = extractXmlFromMessage(message);
                            if (!xmls.isEmpty()) {
                                xmls.forEach(xml -> xmlReports.add(new FetchedReport(xml, receivedAt)));
                                folder.copyMessages(new Message[]{message}, processedFolder);
                                message.setFlag(Flags.Flag.DELETED, true);
                                log.debug("Message traité et déplacé vers '{}' : {}", processedFolderName, message.getSubject());
                            } else {
                                log.warn("Mail non-DMARC ignoré : {}", message.getSubject());
                                notifService.sendPushBulletNotif(
                                        "Un mail sans rapport DMARC a été reçu dans la boîte de collecte : \"" + message.getSubject() + "\"",
                                        "DMARC ⚠️"
                                );
                            }
                        } catch (Exception e) {
                            log.error("Erreur traitement message '{}' : {}", message.getSubject(), e.getMessage());
                        }
                    }
                    folder.expunge();
                }
            }
        } catch (Exception e) {
            log.error("Erreur connexion IMAP : {}", e.getMessage());
            notifService.sendPushBulletNotif(
                    "Impossible de se connecter à la boîte mail IMAP : " + e.getMessage(),
                    "DMARC ❌"
            );
        }

        log.info("{} rapport(s) XML DMARC récupéré(s)", xmlReports.size());
        return xmlReports;
    }

    private Folder getOrCreateFolder(Store store, String name) throws MessagingException {
        Folder folder = store.getFolder(name);
        if (!folder.exists()) {
            folder.create(Folder.HOLDS_MESSAGES);
            log.info("Dossier IMAP '{}' créé automatiquement", name);
        }
        return folder;
    }

    private List<String> extractXmlFromMessage(Message message) throws Exception {
        List<String> results = new ArrayList<>();
        Object content = message.getContent();
        log.debug("Message '{}' - content type: {}", message.getSubject(), message.getContentType());

        if (content instanceof Multipart multipart) {
            extractXmlFromMultipart(multipart, results);
        } else {
            // Cas Google : le ZIP est directement le corps du message (pas de multipart)
            String fileName = message.getFileName();
            if (fileName == null) {
                // Extraire le nom depuis le content type (name=...)
                String ct = message.getContentType();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("name=[\"']?([^\"';\\s]+)[\"']?").matcher(ct);
                if (m.find()) fileName = m.group(1);
            }
            if (fileName != null) {
                fileName = MimeUtility.decodeText(fileName).toLowerCase();
                log.debug("Message corps direct, fileName={}", fileName);
                String xml = extractXmlFromAttachment(message.getInputStream(), fileName);
                if (xml != null) results.add(xml);
            } else {
                log.debug("Message non-multipart ignoré, content type: {}", message.getContentType());
            }
        }
        return results;
    }

    private void extractXmlFromMultipart(Multipart multipart, List<String> results) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();
            String fileName = part.getFileName();

            if (fileName != null) {
                fileName = MimeUtility.decodeText(fileName).toLowerCase();
            }

            log.debug("  Part[{}] disposition={} contentType={} fileName={}", i, disposition, part.getContentType(), fileName);

            // Récursion si la partie est elle-même un multipart imbriqué
            if (part.getContent() instanceof Multipart nested) {
                extractXmlFromMultipart(nested, results);
                continue;
            }

            boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                    || (fileName != null && (disposition == null || Part.INLINE.equalsIgnoreCase(disposition)));

            if (isAttachment && fileName != null) {
                String xml = extractXmlFromAttachment(part.getInputStream(), fileName);
                if (xml != null) {
                    results.add(xml);
                }
            }
        }
    }

    private String extractXmlFromAttachment(InputStream inputStream, String fileName) throws Exception {
        byte[] bytes = inputStream.readAllBytes();

        if (bytes.length > DmarcArchiveExtractor.MAX_COMPRESSED_BYTES) {
            log.warn("Pièce jointe '{}' trop volumineuse ({} octets compressés), ignorée", fileName, bytes.length);
            notifService.sendPushBulletNotif(
                    "Rapport DMARC rejeté : pièce jointe trop volumineuse (" + (bytes.length / 1024) + " Ko compressés) — fichier : " + fileName,
                    "DMARC ⚠️ Fichier volumineux"
            );
            return null;
        }

        try {
            String xml = extractor.extractXml(bytes, fileName);
            if (xml == null) {
                log.warn("Type de pièce jointe non reconnu : {}", fileName);
            }
            return xml;
        } catch (IOException e) {
            log.warn("Extraction rejetée pour '{}' : {}", fileName, e.getMessage());
            notifService.sendPushBulletNotif(
                    "Rapport DMARC rejeté : contenu décompressé trop volumineux (>" + (DmarcArchiveExtractor.MAX_DECOMPRESSED_BYTES / 1024 / 1024) + " Mo) — fichier : " + fileName,
                    "DMARC ⚠️ Fichier volumineux"
            );
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return LocalDateTime.now(ZoneOffset.UTC);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneOffset.UTC);
    }

    private Properties buildMailProperties() {
        Properties props = new Properties();
        String protocol = ssl ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", host);
        props.put("mail." + protocol + ".port", String.valueOf(port));
        if (ssl) {
            props.put("mail.imaps.ssl.enable", "true");
        }
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "10000");
        return props;
    }
}
