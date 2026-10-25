package lu.kaminski.dmarc.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.service.BlacklistService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dmarc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class BlacklistScheduler {

    private final BlacklistService blacklistService;

    /** Vérification quotidienne à 2h du matin */
    @Scheduled(cron = "${dmarc.blacklist.cron:0 0 2 * * *}")
    public void checkBlacklists() {
        log.info("Vérification blacklists planifiée déclenchée");
        blacklistService.checkAll();
    }
}
