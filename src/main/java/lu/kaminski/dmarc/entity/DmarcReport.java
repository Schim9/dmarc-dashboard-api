package lu.kaminski.dmarc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dmarc_report", indexes = {
        @Index(name = "idx_report_domain", columnList = "domain"),
        @Index(name = "idx_report_received_at", columnList = "received_at")
})
@Getter
@Setter
public class DmarcReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant unique du rapport, fourni par l'expéditeur (ex: Google, Microsoft...) */
    @Column(name = "report_id", unique = true, nullable = false, length = 255)
    private String reportId;

    /** Organisation qui a généré le rapport (ex: "Google Inc.") */
    @Column(name = "org_name", length = 255)
    private String orgName;

    @Column(name = "org_email", length = 255)
    private String orgEmail;

    /** Domaine protégé par DMARC */
    @Column(nullable = false, length = 255)
    private String domain;

    /** Début de la période couverte par le rapport */
    @Column(name = "begin_date")
    private LocalDateTime beginDate;

    /** Fin de la période couverte */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /** Politique DMARC publiée : none, quarantine, reject */
    @Column(length = 20)
    private String policy;

    /** Alignement DKIM : r (relaxed) ou s (strict) */
    @Column(length = 1)
    private String adkim;

    /** Alignement SPF : r (relaxed) ou s (strict) */
    @Column(length = 1)
    private String aspf;

    /** Pourcentage de messages soumis à la politique */
    private Integer pct;

    /** Date/heure d'import dans notre système */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** Nombre total de messages couverts par ce rapport (dénormalisé) */
    @Column(name = "total_messages")
    private long totalMessages;

    /** Messages ayant passé DMARC (dkim=pass OU spf=pass) */
    @Column(name = "pass_count")
    private long passCount;

    /** Messages ayant échoué DMARC */
    @Column(name = "fail_count")
    private long failCount;

    /** Messages avec DKIM fail */
    @Column(name = "dkim_fail_count")
    private long dkimFailCount;

    /** Messages avec SPF fail */
    @Column(name = "spf_fail_count")
    private long spfFailCount;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DmarcRecord> records = new ArrayList<>();
}
