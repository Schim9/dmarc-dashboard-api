package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class Row {

    @XmlElement(name = "source_ip")
    private String sourceIp;

    private int count;

    @XmlElement(name = "policy_evaluated")
    private PolicyEvaluated policyEvaluated;
}
