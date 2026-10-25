package lu.kaminski.dmarc.controller;

import lu.kaminski.dmarc.dto.DnsCheckResultDto;
import lu.kaminski.dmarc.service.DnsCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/dns-check")
public class DnsCheckController {

    private final DnsCheckService dnsCheckService;

    public DnsCheckController(DnsCheckService dnsCheckService) {
        this.dnsCheckService = dnsCheckService;
    }

    /** Dernier résultat en cache pour un domaine */
    @GetMapping("/{domain}")
    public ResponseEntity<DnsCheckResultDto> getLastResult(@PathVariable String domain) {
        Optional<DnsCheckResultDto> result = dnsCheckService.getLastResult(domain);
        return result.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    /** Déclenche un check DNS frais pour un domaine */
    @PostMapping("/{domain}")
    public DnsCheckResultDto checkDomain(@PathVariable String domain) {
        return dnsCheckService.checkDomain(domain);
    }

    /** Déclenche un check DNS pour tous les domaines (async) */
    @PostMapping("/check-all")
    public ResponseEntity<Void> checkAll() {
        dnsCheckService.checkAll();
        return ResponseEntity.accepted().build();
    }
}
