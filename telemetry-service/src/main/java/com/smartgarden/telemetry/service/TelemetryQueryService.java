package com.smartgarden.telemetry.service;

import com.smartgarden.telemetry.config.TelemetryProperties;
import com.smartgarden.telemetry.domain.MoistureSample;
import com.smartgarden.telemetry.repository.IrrigationEventRepository;
import com.smartgarden.telemetry.repository.TelemetryReadingRepository;
import com.smartgarden.telemetry.service.DailyStatsCalculator.DailyStat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import com.smartgarden.telemetry.service.HistoryAggregator.HistoryBucket;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryQueryService {

	private final TelemetryReadingRepository repository;

	private final IrrigationEventRepository events;

	private final TelemetryProperties props;

	private final Clock clock;

	public TelemetryQueryService(TelemetryReadingRepository repository, IrrigationEventRepository events,
			TelemetryProperties props, Clock clock) {
		this.repository = repository;
		this.events = events;
		this.props = props;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<MoistureSample> recentSamples(int hours) {
		int h = Math.clamp(hours, 1, 24);
		return repository.findSamplesSince(clock.instant().minus(Duration.ofHours(h)));
	}

	@Transactional(readOnly = true)
	public List<DailyStat> dailyStats(int days) {
		int d = Math.clamp(days, 1, 31);
		Instant now = clock.instant();
		Instant since = LocalDate.ofInstant(now, props.zone()).minusDays(d - 1L).atStartOfDay(props.zone()).toInstant();
		return DailyStatsCalculator.calculate(repository.findSamplesSince(since),
				events.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(since), d, props.zone(), now);
	}

	@Transactional(readOnly = true)
	public List<HistoryBucket> history(HistoryRangeSpec range) {
		var samples = repository.findSamplesSince(clock.instant().minus(range.window()));
		return HistoryAggregator.aggregate(samples, range.interval());
	}

}
