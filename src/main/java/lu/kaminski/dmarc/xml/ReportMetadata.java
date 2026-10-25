package lu.kaminski.dmarc.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
public class ReportMetadata {

    @XmlElement(name = "org_name")
    private String orgName;

    @XmlElement(name = "email")
    private String email;

    @XmlElement(name = "extra_contact_info")
    private String extraContactInfo;

    @XmlElement(name = "report_id")
    private String reportId;

    @XmlElement(name = "date_range")
    private DateRange dateRange;
}
