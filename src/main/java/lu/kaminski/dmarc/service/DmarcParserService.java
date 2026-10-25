package lu.kaminski.dmarc.service;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.xml.Feedback;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.Optional;

@Service
@Slf4j
public class DmarcParserService {

    private final JAXBContext jaxbContext;

    public DmarcParserService() throws JAXBException {
        this.jaxbContext = JAXBContext.newInstance(Feedback.class);
    }

    /**
     * Parse un rapport DMARC XML en objet Feedback.
     *
     * @param xml contenu XML du rapport
     * @return Optional<Feedback> vide si le parsing échoue
     */
    public Optional<Feedback> parse(String xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Feedback feedback = (Feedback) unmarshaller.unmarshal(new StringReader(xml));
            return Optional.of(feedback);
        } catch (JAXBException e) {
            log.error("Échec du parsing XML DMARC : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
