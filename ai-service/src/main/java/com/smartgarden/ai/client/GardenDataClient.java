package com.smartgarden.ai.client;

import com.smartgarden.ai.config.AiProperties;
import com.smartgarden.contracts.irrigation.v1.ApplyAiRecommendationRequest;
import com.smartgarden.contracts.irrigation.v1.ApplyAiRecommendationResponse;
import com.smartgarden.contracts.irrigation.v1.GetConfigRequest;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc.IrrigationServiceBlockingStub;
import com.smartgarden.contracts.telemetry.v1.CurrentStatus;
import com.smartgarden.contracts.telemetry.v1.GetCurrentStatusRequest;
import com.smartgarden.contracts.telemetry.v1.GetHistoryRequest;
import com.smartgarden.contracts.telemetry.v1.DailyStat;
import com.smartgarden.contracts.telemetry.v1.GetDailyStatsRequest;
import com.smartgarden.contracts.telemetry.v1.GetHistoryResponse;
import com.smartgarden.contracts.telemetry.v1.GetIrrigationEventsRequest;
import com.smartgarden.contracts.telemetry.v1.GetRecentSamplesRequest;
import com.smartgarden.contracts.telemetry.v1.IrrigationEvent;
import com.smartgarden.ai.domain.Sample;
import java.time.Instant;
import java.util.List;
import com.smartgarden.contracts.telemetry.v1.HistoryRange;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc.TelemetryServiceBlockingStub;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * gRPC calls to telemetry-service and irrigation-service.
 * @throws io.grpc.StatusRuntimeException on any failure
 */
@Component
public class GardenDataClient {

	private final TelemetryServiceBlockingStub telemetry;

	private final IrrigationServiceBlockingStub irrigation;

	private final AiProperties props;

	public GardenDataClient(TelemetryServiceBlockingStub telemetry, IrrigationServiceBlockingStub irrigation,
			AiProperties props) {
		this.telemetry = telemetry;
		this.irrigation = irrigation;
		this.props = props;
	}

	public CurrentStatus currentStatus() {
		return telemetry.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.getCurrentStatus(GetCurrentStatusRequest.getDefaultInstance());
	}

	public GetHistoryResponse history24h() {
		return telemetry.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.getHistory(GetHistoryRequest.newBuilder().setRange(HistoryRange.HISTORY_RANGE_24H).build());
	}

	/** Valid readings of the last hours, oldest first. */
	public List<Sample> recentSamples(int hours) {
		return telemetry.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.getRecentSamples(GetRecentSamplesRequest.newBuilder().setHours(hours).build())
			.getSamplesList()
			.stream()
			.map(s -> new Sample(Instant.ofEpochSecond(s.getTime().getSeconds(), s.getTime().getNanos()),
					s.getMoisture(), s.getPumpOn()))
			.toList();
	}

	/** Most recent irrigation events, newest first. */
	public List<IrrigationEvent> irrigationEvents(int limit) {
		return telemetry.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.getIrrigationEvents(GetIrrigationEventsRequest.newBuilder().setLimit(limit).build())
			.getEventsList();
	}

	public List<DailyStat> dailyStats(int days) {
		return telemetry.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.getDailyStats(GetDailyStatsRequest.newBuilder().setDays(days).build())
			.getDaysList();
	}

	public IrrigationConfig irrigationConfig() {
		return irrigation.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.getConfig(GetConfigRequest.getDefaultInstance());
	}

	public ApplyAiRecommendationResponse applyRecommendation(int min, int max, boolean rainPause, String analysisId) {
		return irrigation.withDeadlineAfter(deadlineMs(), TimeUnit.MILLISECONDS)
			.applyAiRecommendation(ApplyAiRecommendationRequest.newBuilder()
				.setMinMoisture(min)
				.setMaxMoisture(max)
				.setRainPause(rainPause)
				.setAnalysisId(analysisId)
				.build());
	}

	private long deadlineMs() {
		return props.grpcDeadline().toMillis();
	}

}
