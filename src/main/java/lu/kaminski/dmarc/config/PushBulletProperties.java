package lu.kaminski.dmarc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pushbullet", ignoreUnknownFields = false)
@Getter
@Setter
public class PushBulletProperties {

    private String apiKey;

}
