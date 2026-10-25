package lu.kaminski.dmarc.controller;

import lombok.RequiredArgsConstructor;
import lu.kaminski.dmarc.dto.IpHistoryEntryDto;
import lu.kaminski.dmarc.dto.ReportDto;
import lu.kaminski.dmarc.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Liste paginée des rapports.
     * Paramètres : domain (optionnel), page (défaut 0), size (défaut 20)
     */
    @GetMapping
    public Page<ReportDto> getReports(
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("endDate").descending());
        return reportService.getReports(domain, pageable);
    }

    /**
     * Détail d'un rapport avec tous ses enregistrements.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportDto> getReport(@PathVariable Long id) {
        return reportService.getReport(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Historique de tous les enregistrements pour une IP source donnée.
     */
    @GetMapping("/ip-history")
    public List<IpHistoryEntryDto> getIpHistory(@RequestParam String ip) {
        return reportService.getIpHistory(ip);
    }
}
