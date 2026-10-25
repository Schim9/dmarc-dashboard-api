package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class PolicyEvaluated {

    /** Action appliquée : none, quarantine, reject */
    private String disposition;

    /** Résultat alignement DKIM : pass ou fail */
    private String dkim;

    /** Résultat alignement SPF : pass ou fail */
    private String spf;
}
