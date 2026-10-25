package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;

import java.util.List;

/**
 * Classe racine d'un rapport DMARC aggregate (RUA).
 * Correspond à l'élément XML <feedback>.
 */
@XmlRootElement(name = "feedback")
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class Feedback {

    @XmlElement(name = "report_metadata")
    private ReportMetadata reportMetadata;

    @XmlElement(name = "policy_published")
    private PolicyPublished policyPublished;

    @XmlElement(name = "record")
    private List<ReportRecord> records;
}
