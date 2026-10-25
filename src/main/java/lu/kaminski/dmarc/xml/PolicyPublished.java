package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class PolicyPublished {

    /** Domaine protégé */
    private String domain;

    /** Alignement DKIM : r (relaxed) ou s (strict) */
    private String adkim;

    /** Alignement SPF : r (relaxed) ou s (strict) */
    private String aspf;

    /** Politique principale : none, quarantine, reject */
    private String p;

    /** Politique sous-domaines : none, quarantine, reject */
    private String sp;

    /** Pourcentage des messages soumis à la politique (0-100) */
    private Integer pct;
}
