package lu.kaminski.dmarc.controller;

import lombok.RequiredArgsConstructor;
import lu.kaminski.dmarc.dto.DomainStatsDto;
import lu.kaminski.dmarc.dto.TrendDto;
import lu.kaminski.dmarc.dto.VolumeTrendDto;
import lu.kaminski.dmarc.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final ReportService reportService;

    @GetMapping
    public List<DomainStatsDto> getAllDomainStats() {
        return reportService.getAllDomainStats();
    }

    @GetMapping("/{domain}")
    public ResponseEntity<DomainStatsDto> getDomainStats(@PathVariable String domain) {
        return reportService.getDomainStats(domain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public List<String> listDomains() {
        return reportService.listAllDomains();
    }

    @PostMapping("/declare")
    public ResponseEntity<Void> declareDomain(@RequestBody Map<String, String> body) {
        String domain = body.get("domain");
        if (domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        reportService.declareDomain(domain.trim().toLowerCase());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{domain}/declare")
    public ResponseEntity<Void> undeclareDomain(@PathVariable String domain) {
        reportService.undeclareDomain(domain);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trend")
    public TrendDto getTrend() {
        return reportService.getTrend(14);
    }

    @GetMapping("/volume")
    public VolumeTrendDto getVolumeTrend() {
        return reportService.getVolumeTrend(14);
    }

    @GetMapping("/report-count")
    public TrendDto getReportCountTrend() {
        return reportService.getReportCountTrend(14);
    }
}
