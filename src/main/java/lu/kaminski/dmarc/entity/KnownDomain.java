package lu.kaminski.dmarc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "known_domain")
@Getter
@Setter
public class KnownDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String domain;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
}
