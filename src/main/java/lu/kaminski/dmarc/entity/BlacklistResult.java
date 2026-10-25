package lu.kaminski.dmarc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklist_result", indexes = {
        @Index(name = "idx_bl_target", columnList = "target"),
        @Index(name = "idx_bl_listed",  columnList = "listed")
})
@Getter
@Setter
public class BlacklistResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IP ou domaine vérifié */
    @Column(nullable = false, length = 255)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private BlacklistTargetType targetType;

    /** Identifiant de la liste DNS (ex : zen.spamhaus.org) */
    @Column(nullable = false, length = 100)
    private String dnsbl;

    /** Libellé lisible (ex : Spamhaus ZEN) */
    @Column(name = "dnsbl_label", nullable = false, length = 100)
    private String dnsblLabel;

    /** true si le target est présent sur cette liste */
    @Column(nullable = false)
    private boolean listed;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
