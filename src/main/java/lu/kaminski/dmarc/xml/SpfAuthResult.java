package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class SpfAuthResult {
    private String domain;
    private String result;
}
