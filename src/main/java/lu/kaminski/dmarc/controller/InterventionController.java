package lu.kaminski.dmarc.controller;

import lu.kaminski.dmarc.dto.*;
import lu.kaminski.dmarc.service.InterventionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class InterventionController {

    private final InterventionService interventionService;

    public InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    /** Liste des interventions, filtrée optionnellement par domaine et/ou statut */
    @GetMapping("/interventions")
    public List<InterventionDto> list(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        return interventionService.list(domain, status);
    }

    /** Détail d'une intervention avec son journal */
    @GetMapping("/interventions/{id}")
    public InterventionDetailDto getById(@PathVariable Long id) {
        return interventionService.getById(id);
    }

    /** Créer une intervention pour un dmarc_record */
    @PostMapping("/interventions")
    public ResponseEntity<InterventionDetailDto> create(@RequestBody @Valid CreateInterventionRequest request) {
        return ResponseEntity.ok(interventionService.create(request));
    }

    /** Lier un dmarc_record à une intervention existante */
    @PostMapping("/interventions/{id}/records/{recordId}")
    public ResponseEntity<InterventionDetailDto> linkRecord(@PathVariable Long id, @PathVariable Long recordId) {
        return ResponseEntity.ok(interventionService.linkRecord(id, recordId));
    }

    /** Délier un dmarc_record d'une intervention */
    @DeleteMapping("/interventions/{id}/records/{recordId}")
    public ResponseEntity<InterventionDetailDto> unlinkRecord(@PathVariable Long id, @PathVariable Long recordId) {
        return ResponseEntity.ok(interventionService.unlinkRecord(id, recordId));
    }

    /** Changer le statut d'une intervention */
    @PatchMapping("/interventions/{id}/status")
    public InterventionDetailDto updateStatus(@PathVariable Long id, @RequestBody @Valid UpdateStatusRequest request) {
        return interventionService.updateStatus(id, request);
    }

    /** Ajouter une entrée de journal */
    @PostMapping("/interventions/{id}/journal")
    public InterventionDetailDto addJournalEntry(@PathVariable Long id, @RequestBody @Valid AddJournalEntryRequest request) {
        return interventionService.addJournalEntry(id, request);
    }

    /** Modifier une entrée de journal */
    @PutMapping("/interventions/{id}/journal/{entryId}")
    public InterventionDetailDto updateJournalEntry(@PathVariable Long id, @PathVariable Long entryId,
                                                    @RequestBody @Valid AddJournalEntryRequest request) {
        return interventionService.updateJournalEntry(id, entryId, request.content());
    }

    /** Supprimer une entrée de journal */
    @DeleteMapping("/interventions/{id}/journal/{entryId}")
    public InterventionDetailDto deleteJournalEntry(@PathVariable Long id, @PathVariable Long entryId) {
        return interventionService.deleteJournalEntry(id, entryId);
    }

    /** Liste des interventions liées à un dmarc_record */
    @GetMapping("/records/{recordId}/interventions")
    public List<InterventionDto> getByRecord(@PathVariable Long recordId) {
        return interventionService.getByRecordId(recordId);
    }

    /** Nombre d'interventions actives pour un domaine (pour les tuiles) */
    @GetMapping("/domains/{domain}/intervention-count")
    public Map<String, Long> getActiveCount(@PathVariable String domain) {
        return Map.of("count", interventionService.countActiveByDomain(domain));
    }

    /** Compteurs d'interventions actives pour tous les domaines (pour le dashboard) */
    @GetMapping("/interventions/counts-by-domain")
    public Map<String, Long> getCountsByDomain() {
        return interventionService.countActiveByAllDomains();
    }

    /** Parmi une liste de reportIds, retourne ceux qui ont au moins une intervention active */
    @PostMapping("/interventions/active-report-ids")
    public Set<Long> getActiveReportIds(@RequestBody List<Long> reportIds) {
        return interventionService.findReportIdsWithActiveInterventions(reportIds);
    }
}
