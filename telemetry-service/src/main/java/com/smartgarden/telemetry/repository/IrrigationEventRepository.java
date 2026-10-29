package com.smartgarden.telemetry.repository;

import com.smartgarden.telemetry.entity.IrrigationEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IrrigationEventRepository extends JpaRepository<IrrigationEventEntity, Long> {

	Optional<IrrigationEventEntity> findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(String deviceId);

	List<IrrigationEventEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

	List<IrrigationEventEntity> findByStartedAtGreaterThanEqualOrderByStartedAtAsc(java.time.Instant since);

}
