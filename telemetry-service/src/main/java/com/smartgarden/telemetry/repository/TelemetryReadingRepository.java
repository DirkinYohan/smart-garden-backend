package com.smartgarden.telemetry.repository;

import com.smartgarden.telemetry.domain.MoistureSample;
import com.smartgarden.telemetry.entity.TelemetryReadingEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReadingEntity, Long> {

	Optional<TelemetryReadingEntity> findTopByOrderByReceivedAtDesc();

	Optional<TelemetryReadingEntity> findTopBySensorOkTrueOrderByReceivedAtDesc();

	/** Lightweight samples for history aggregation; readings with a faulty sensor are excluded. */
	@Query("""
			select new com.smartgarden.telemetry.domain.MoistureSample(r.receivedAt, r.moisture, r.pumpOn)
			from TelemetryReadingEntity r
			where r.receivedAt >= :since and r.sensorOk = true
			order by r.receivedAt asc
			""")
	List<MoistureSample> findSamplesSince(@Param("since") Instant since);

}
