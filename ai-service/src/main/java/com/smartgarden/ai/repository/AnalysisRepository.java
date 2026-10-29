package com.smartgarden.ai.repository;

import com.smartgarden.ai.entity.AnalysisEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {

	Optional<AnalysisEntity> findTopByOrderByCreatedAtDesc();

	List<AnalysisEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	List<AnalysisEntity> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(java.time.Instant since);

}
