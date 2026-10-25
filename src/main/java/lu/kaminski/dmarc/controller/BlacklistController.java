package lu.kaminski.dmarc.controller;

import lu.kaminski.dmarc.dto.BlacklistStatusDto;
import lu.kaminski.dmarc.service.BlacklistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/blacklist")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    /** Statut blacklist pour un domaine et ses IPs */
    @GetMapping("/status")
    public BlacklistStatusDto getStatus(@RequestParam String domain) {
        return blacklistService.getStatusForDomain(domain);
    }

    /** Résumé par domaine : true = au moins un problème détecté */
    @GetMapping("/summary")
    public Map<String, Boolean> getSummary() {
        return blacklistService.getSummaryByDomain();
    }

    /** Déclenche une vérification manuelle (async non bloquant) */
    @PostMapping("/check")
    public ResponseEntity<Void> triggerCheck() {
        blacklistService.checkAll();
        return ResponseEntity.accepted().build();
    }
}
