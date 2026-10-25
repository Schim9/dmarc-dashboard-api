package lu.kaminski.dmarc.dto;

/**
 * Alerte DMARC avec niveau de sévérité et action concrète.
 *
 * @param severity   "critical", "warning" ou "info"
 * @param title      Titre court de l'alerte
 * @param detail     Explication détaillée du problème
 * @param action     Action concrète à réaliser
 * @param dnsExample Exemple d'enregistrement DNS à modifier (optionnel)
 */
public record AlertDto(
        String severity,
        String title,
        String detail,
        String action,
        String dnsExample
) {}
