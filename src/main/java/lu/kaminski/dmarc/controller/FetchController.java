package lu.kaminski.dmarc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.scheduler.DmarcScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fetch")
@RequiredArgsConstructor
@Slf4j
public class FetchController {

    private final DmarcScheduler dmarcScheduler;

    /**
     * Déclenche manuellement une collecte des rapports DMARC depuis la boîte mail.
     */
    @PostMapping
    public ResponseEntity<DmarcScheduler.FetchResult> triggerFetch() {
        DmarcScheduler.FetchResult result = dmarcScheduler.runManually();
        return ResponseEntity.ok(result);
    }
}
