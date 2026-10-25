package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class Identifiers {

    @XmlElement(name = "header_from")
    private String headerFrom;

    @XmlElement(name = "envelope_from")
    private String envelopeFrom;

    @XmlElement(name = "envelope_to")
    private String envelopeTo;
}
