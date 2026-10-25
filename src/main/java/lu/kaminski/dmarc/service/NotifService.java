package lu.kaminski.dmarc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.config.PushBulletProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifService {

    private final PushBulletProperties pushBulletProperties;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    public void sendPushBulletNotif(String message, String title) {
        log.debug("Send a notification to PushBullet: [{}]", message);

        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("type", "note", "title", title, "body", message)
            );
            restClient.post()
                    .uri("https://api.pushbullet.com/v2/pushes")
                    .header("Access-Token", pushBulletProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la notification PushBullet : {}", e.getMessage());
        }
    }
}
