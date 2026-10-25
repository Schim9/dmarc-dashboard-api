package lu.kaminski.dmarc.util;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilitaire d'extraction XML depuis des archives DMARC (.zip, .gz, .xml).
 * Protège contre les zip bombs via une limite de décompression.
 */
@Component
public class DmarcArchiveExtractor {

    public static final int MAX_COMPRESSED_BYTES   = 5  * 1024 * 1024; // 5 Mo
    public static final int MAX_DECOMPRESSED_BYTES = 20 * 1024 * 1024; // 20 Mo

    /**
     * Extrait le contenu XML depuis un tableau d'octets (zip, gz ou xml brut).
     *
     * @param bytes    Contenu du fichier en mémoire
     * @param fileName Nom du fichier (utilisé pour détecter l'extension)
     * @return Le XML extrait, ou {@code null} si le format n'est pas reconnu
     * @throws IOException si le contenu décompressé dépasse {@link #MAX_DECOMPRESSED_BYTES}
     */
    public String extractXml(byte[] bytes, String fileName) throws IOException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip")) {
            return extractFromZip(bytes);
        } else if (lower.endsWith(".gz")) {
            return extractFromGzip(bytes);
        } else if (lower.endsWith(".xml")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    private String extractFromZip(byte[] bytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".xml")) {
                    return new String(readBounded(zis), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private String extractFromGzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(readBounded(gis), StandardCharsets.UTF_8);
        }
    }

    /**
     * Lit au maximum {@link #MAX_DECOMPRESSED_BYTES} depuis le flux.
     * Lève une {@link IOException} si la limite est dépassée (protection zip bomb).
     */
    private byte[] readBounded(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_DECOMPRESSED_BYTES + 1);
        if (data.length > MAX_DECOMPRESSED_BYTES) {
            throw new IOException("Contenu décompressé trop volumineux (>" + (MAX_DECOMPRESSED_BYTES / 1024 / 1024) + " Mo)");
        }
        return data;
    }
}
