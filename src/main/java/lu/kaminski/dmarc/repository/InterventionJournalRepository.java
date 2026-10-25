package lu.kaminski.dmarc.repository;

import lu.kaminski.dmarc.entity.InterventionJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterventionJournalRepository extends JpaRepository<InterventionJournal, Long> {
}
