package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class DkimAuthResult {
    private String domain;
    private String selector;
    private String result;
}
