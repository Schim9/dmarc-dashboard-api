package lu.kaminski.dmarc.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.service.DnsCheckService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dmarc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DnsCheckScheduler {

    private final DnsCheckService dnsCheckService;

    /** Vérification quotidienne à 3h du matin */
    @Scheduled(cron = "${dmarc.scheduler.dns-check}")
    public void checkDns() {
        log.info("Vérification DNS planifiée déclenchée");
        dnsCheckService.checkAll();
    }
}
