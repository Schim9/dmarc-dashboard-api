package lu.kaminski.dmarc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dmarc_record", indexes = {
        @Index(name = "idx_record_source_ip", columnList = "source_ip")
})
@Getter
@Setter
public class DmarcRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private DmarcReport report;

    /** IP source des emails */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    /** Nombre de messages depuis cette IP dans la période */
    private Integer count;

    /** Action DMARC appliquée : none, quarantine, reject */
    @Column(length = 20)
    private String disposition;

    /** Résultat alignement DKIM DMARC : pass ou fail */
    @Column(name = "dkim_result", length = 10)
    private String dkimResult;

    /** Résultat alignement SPF DMARC : pass ou fail */
    @Column(name = "spf_result", length = 10)
    private String spfResult;

    /** Domaine dans le header From: */
    @Column(name = "header_from", length = 255)
    private String headerFrom;

    /** Domaine dans le SMTP envelope From: */
    @Column(name = "envelope_from", length = 255)
    private String envelopeFrom;

    /** Domaine destinataire (envelope To:) */
    @Column(name = "envelope_to", length = 255)
    private String envelopeTo;

    /** Domaine DKIM utilisé (premier résultat) */
    @Column(name = "dkim_domain", length = 255)
    private String dkimDomain;

    /** Sélecteur DKIM utilisé */
    @Column(name = "dkim_selector", length = 100)
    private String dkimSelector;

    /** Domaine SPF utilisé */
    @Column(name = "spf_domain", length = 255)
    private String spfDomain;

    /** Organisation associée à l'IP source (résolu via ip-api.com à l'import) */
    @Column(name = "hostname", length = 255)
    private String hostname;
}
