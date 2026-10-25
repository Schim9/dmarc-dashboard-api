package lu.kaminski.dmarc.service;

import lu.kaminski.dmarc.dto.*;
import lu.kaminski.dmarc.entity.*;
import lu.kaminski.dmarc.repository.DmarcRecordRepository;
import lu.kaminski.dmarc.repository.InterventionJournalRepository;
import lu.kaminski.dmarc.repository.InterventionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final InterventionJournalRepository journalRepository;
    private final DmarcRecordRepository recordRepository;

    public InterventionService(InterventionRepository interventionRepository,
                               InterventionJournalRepository journalRepository,
                               DmarcRecordRepository recordRepository) {
        this.interventionRepository = interventionRepository;
        this.journalRepository = journalRepository;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public InterventionDetailDto create(CreateInterventionRequest request) {
        DmarcRecord record = recordRepository.findById(request.recordId())
                .orElseThrow(() -> new NoSuchElementException("DmarcRecord introuvable : " + request.recordId()));

        Intervention intervention = new Intervention();
        intervention.getDmarcRecords().add(record);
        intervention.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        intervention.setStatus(InterventionStatus.IN_PROGRESS);
        intervention = interventionRepository.save(intervention);

        if (request.initialNote() != null && !request.initialNote().isBlank()) {
            InterventionJournal entry = new InterventionJournal();
            entry.setIntervention(intervention);
            entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            entry.setContent(request.initialNote().trim());
            journalRepository.save(entry);
            intervention.getJournalEntries().add(entry);
        }

        return toDetailDto(intervention);
    }

    @Transactional
    public InterventionDetailDto linkRecord(Long interventionId, Long recordId) {
        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        DmarcRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new NoSuchElementException("DmarcRecord introuvable : " + recordId));

        boolean alreadyLinked = intervention.getDmarcRecords().stream()
                .anyMatch(r -> r.getId().equals(recordId));
        if (!alreadyLinked) {
            intervention.getDmarcRecords().add(record);
            interventionRepository.save(intervention);
        }
        return toDetailDto(intervention);
    }

    @Transactional
    public InterventionDetailDto unlinkRecord(Long interventionId, Long recordId) {
        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));

        intervention.getDmarcRecords().removeIf(r -> r.getId().equals(recordId));
        if (intervention.getDmarcRecords().isEmpty()) {
            throw new IllegalStateException("Une intervention doit avoir au moins un enregistrement lié");
        }
        interventionRepository.save(intervention);
        return toDetailDto(intervention);
    }

    public InterventionDetailDto getById(Long id) {
        Intervention intervention = interventionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        return toDetailDto(intervention);
    }

    public List<InterventionDto> getByRecordId(Long recordId) {
        return interventionRepository.findByDmarcRecords_Id(recordId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<InterventionDto> list(String domain, String status) {
        List<Intervention> interventions;

        if (domain != null && status != null) {
            interventions = interventionRepository.findByDomainAndStatus(domain, InterventionStatus.valueOf(status));
        } else if (domain != null) {
            interventions = interventionRepository.findByDomain(domain);
        } else if (status != null) {
            interventions = interventionRepository.findByStatusOrderByCreatedAtDesc(InterventionStatus.valueOf(status));
        } else {
            interventions = interventionRepository.findAllByOrderByCreatedAtDesc();
        }

        return interventions.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public InterventionDetailDto updateStatus(Long id, UpdateStatusRequest request) {
        Intervention intervention = interventionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        intervention.setStatus(InterventionStatus.valueOf(request.status()));
        return toDetailDto(interventionRepository.save(intervention));
    }

    @Transactional
    public InterventionDetailDto addJournalEntry(Long id, AddJournalEntryRequest request) {
        Intervention intervention = interventionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));

        InterventionJournal entry = new InterventionJournal();
        entry.setIntervention(intervention);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setContent(request.content().trim());
        journalRepository.save(entry);
        intervention.getJournalEntries().add(entry);

        return toDetailDto(intervention);
    }

    @Transactional
    public InterventionDetailDto updateJournalEntry(Long interventionId, Long entryId, String content) {
        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        InterventionJournal entry = journalRepository.findById(entryId)
                .orElseThrow(() -> new NoSuchElementException("Entrée de journal introuvable : " + entryId));
        entry.setContent(content.trim());
        journalRepository.save(entry);
        return toDetailDto(intervention);
    }

    @Transactional
    public InterventionDetailDto deleteJournalEntry(Long interventionId, Long entryId) {
        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        journalRepository.deleteById(entryId);
        intervention.getJournalEntries().removeIf(e -> e.getId().equals(entryId));
        return toDetailDto(intervention);
    }

    public long countActiveByDomain(String domain) {
        return interventionRepository.countByDomainAndStatus(domain, InterventionStatus.IN_PROGRESS);
    }

    public Map<String, Long> countActiveByAllDomains() {
        return interventionRepository.countByDomain(InterventionStatus.IN_PROGRESS).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    public Set<Long> findReportIdsWithActiveInterventions(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) return Set.of();
        return new HashSet<>(interventionRepository.findReportIdsWithActiveInterventions(reportIds));
    }

    private RecordSummaryDto toRecordSummary(DmarcRecord r) {
        DmarcReport rep = r.getReport();
        return new RecordSummaryDto(
                r.getId(),
                r.getSourceIp(),
                r.getHostname(),
                r.getEnvelopeTo(),
                rep.getDomain(),
                rep.getOrgName(),
                rep.getBeginDate(),
                rep.getEndDate(),
                r.getDkimResult(),
                r.getSpfResult(),
                r.getDisposition()
        );
    }

    private InterventionDto toDto(Intervention i) {
        List<RecordSummaryDto> records = i.getDmarcRecords().stream()
                .map(this::toRecordSummary)
                .collect(Collectors.toList());
        return new InterventionDto(
                i.getId(),
                i.getCreatedAt(),
                i.getStatus().name(),
                i.getJournalEntries().size(),
                records
        );
    }

    private InterventionDetailDto toDetailDto(Intervention i) {
        List<RecordSummaryDto> records = i.getDmarcRecords().stream()
                .map(this::toRecordSummary)
                .collect(Collectors.toList());
        List<JournalEntryDto> entries = i.getJournalEntries().stream()
                .map(e -> new JournalEntryDto(e.getId(), e.getCreatedAt(), e.getContent()))
                .collect(Collectors.toList());
        return new InterventionDetailDto(
                i.getId(),
                i.getCreatedAt(),
                i.getStatus().name(),
                records,
                entries
        );
    }
}
