package lu.kaminski.dmarc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dns_check")
@Getter
@Setter
public class DnsCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255, unique = true)
    private String domain;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    // --- DMARC ---
    @Column(name = "dmarc_record", length = 1000)
    private String dmarcRecord;

    @Column(name = "dmarc_exists")
    private boolean dmarcExists;

    @Column(name = "dmarc_policy", length = 20)
    private String dmarcPolicy;

    @Column(name = "dmarc_rua", length = 500)
    private String dmarcRua;

    @Column(name = "dmarc_pct")
    private Integer dmarcPct;

    @Column(name = "dmarc_valid")
    private boolean dmarcValid;

    // --- SPF ---
    @Column(name = "spf_record", length = 1000)
    private String spfRecord;

    @Column(name = "spf_exists")
    private boolean spfExists;

    @Column(name = "spf_policy", length = 10)
    private String spfPolicy;

    @Column(name = "spf_lookup_count")
    private int spfLookupCount;

    @Column(name = "spf_valid")
    private boolean spfValid;

    // --- MX ---
    @Column(name = "mx_exists")
    private boolean mxExists;

    // --- JSON (sélecteurs DKIM et problèmes détectés) ---
    @Column(name = "dkim_checks_json", columnDefinition = "TEXT")
    private String dkimChecksJson;

    @Column(name = "issues_json", columnDefinition = "TEXT")
    private String issuesJson;
}
