package lu.kaminski.dmarc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Résout le nom d'organisation associé à une adresse IP via ip-api.com (gratuit, sans clé API).
 * Les résultats sont mis en cache en mémoire pour éviter les appels redondants.
 */
@Service
@Slf4j
public class IpResolverService {

    private final RestClient restClient;
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 500;
                }
            }
    );

    public IpResolverService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl("http://ip-api.com")
                .requestFactory(factory)
                .build();
    }

    /**
     * Retourne le nom d'organisation pour une IP (ex: "OVH SAS", "Google LLC").
     * Retourne null si la résolution échoue.
     */
    public String resolve(String ip) {
        if (ip == null || ip.isBlank()) return null;
        return cache.computeIfAbsent(ip, this::doResolve);
    }

    private String doResolve(String ip) {
        try {
            Map<String, String> response = restClient.get()
                    .uri("/json/{ip}?fields=org,isp", ip)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response != null) {
                String org = response.get("org");
                if (org != null && !org.isBlank()) {
                    // Supprime le préfixe ASN : "AS16509 Amazon.com, Inc." → "Amazon.com, Inc."
                    return org.replaceAll("^AS\\d+\\s+", "");
                }
                String isp = response.get("isp");
                if (isp != null && !isp.isBlank()) return isp;
            }
        } catch (Exception e) {
            log.warn("Résolution IP {} échouée : {}", ip, e.getMessage());
        }
        return null;
    }
}
