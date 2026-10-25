package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;

/**
 * Correspond à un élément <record> dans le XML DMARC.
 * (Nommé ReportRecord car "record" est un mot-clé Java 16+)
 */
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class ReportRecord {

    private Row row;

    private Identifiers identifiers;

    @XmlElement(name = "auth_results")
    private AuthResults authResults;
}
