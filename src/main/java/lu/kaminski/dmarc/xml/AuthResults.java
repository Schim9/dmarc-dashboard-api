package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class AuthResults {

    @XmlElement(name = "dkim")
    private List<DkimAuthResult> dkim;

    @XmlElement(name = "spf")
    private List<SpfAuthResult> spf;
}
