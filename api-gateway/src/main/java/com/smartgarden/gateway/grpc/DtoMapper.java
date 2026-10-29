package com.smartgarden.gateway.grpc;

import com.google.protobuf.Timestamp;
import com.smartgarden.contracts.ai.v1.Analysis;
import com.smartgarden.contracts.ai.v1.AnalysisInput;
import com.smartgarden.contracts.ai.v1.Confidence;
import com.smartgarden.contracts.ai.v1.HealthStatus;
import com.smartgarden.contracts.ai.v1.InputSnapshot;
import com.smartgarden.contracts.ai.v1.NextAction;
import com.smartgarden.contracts.ai.v1.Projection;
import com.smartgarden.contracts.ai.v1.RecommendationDetail;
import com.smartgarden.contracts.ai.v1.Stats;
import com.smartgarden.contracts.ai.v1.Weather;
import com.smartgarden.contracts.common.v1.IrrigationMode;
import com.smartgarden.contracts.irrigation.v1.CropProfile;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.telemetry.v1.CurrentStatus;
import com.smartgarden.contracts.telemetry.v1.DeviceStatusEvent;
import com.smartgarden.contracts.telemetry.v1.GetHistoryResponse;
import com.smartgarden.contracts.telemetry.v1.IrrigationEvent;
import com.smartgarden.contracts.telemetry.v1.TelemetryReading;
import com.smartgarden.gateway.dto.AnalysisDto;
import com.smartgarden.gateway.dto.AnalysisInputDto;
import com.smartgarden.gateway.dto.ConfigDto;
import com.smartgarden.gateway.dto.CropProfileDto;
import com.smartgarden.gateway.dto.CurrentWeatherDto;
import com.smartgarden.gateway.dto.DailyMoistureDto;
import com.smartgarden.gateway.dto.InputSnapshotDto;
import com.smartgarden.gateway.dto.ProjectionDto;
import com.smartgarden.gateway.dto.ProjectionPointDto;
import com.smartgarden.gateway.dto.RecommendationDetailDto;
import com.smartgarden.gateway.dto.StatsDto;
import com.smartgarden.gateway.dto.SuggestedActionDto;
import com.smartgarden.gateway.dto.ThresholdPointDto;
import com.smartgarden.gateway.dto.DeviceStatusDto;
import com.smartgarden.gateway.dto.HistoryPointDto;
import com.smartgarden.gateway.dto.HistoryResponse;
import com.smartgarden.gateway.dto.HourlyForecastDto;
import com.smartgarden.gateway.dto.IrrigationEventDto;
import com.smartgarden.gateway.dto.IrrigationModeDto;
import com.smartgarden.gateway.dto.StatusResponse;
import com.smartgarden.gateway.dto.TelemetryReadingDto;
import com.smartgarden.gateway.dto.WeatherDto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protobuf → REST/WebSocket DTOs. Enum prefixes are stripped ("IRRIGATION_MODE_AUTO" → "AUTO").
 */
public final class DtoMapper {

	private DtoMapper() {
	}

	static Instant instant(Timestamp ts) {
		return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
	}

	static String mode(IrrigationMode mode) {
		return mode.name().replace("IRRIGATION_MODE_", "");
	}

	public static IrrigationMode mode(IrrigationModeDto mode) {
		return switch (mode) {
			case AUTO -> IrrigationMode.IRRIGATION_MODE_AUTO;
			case MANUAL -> IrrigationMode.IRRIGATION_MODE_MANUAL;
			case AI -> IrrigationMode.IRRIGATION_MODE_AI;
		};
	}

	public static TelemetryReadingDto reading(TelemetryReading r) {
		return new TelemetryReadingDto(r.getDeviceId(), r.getRaw(), r.getMoisture(), r.getPumpOn(), r.getSensorOk(),
				mode(r.getMode()), r.getMinMoisture(), r.getMaxMoisture(), r.getRainPause(), r.getUptimeMs(),
				instant(r.getReceivedAt()));
	}

	public static StatusResponse status(CurrentStatus s) {
		return new StatusResponse(s.getDeviceOnline(), s.getDeviceStatus(),
				s.hasLastSeen() ? instant(s.getLastSeen()) : null, s.getHasReading() ? reading(s.getReading()) : null);
	}

	public static HistoryResponse history(String range, GetHistoryResponse h) {
		return new HistoryResponse(range, h.getIntervalMinutes(),
				h.getPointsList()
					.stream()
					.map(p -> new HistoryPointDto(instant(p.getBucketStart()), p.getAvgMoisture(), p.getMinMoisture(),
							p.getMaxMoisture(), p.getSamples(), p.getPumpActive()))
					.toList());
	}

	public static IrrigationEventDto event(IrrigationEvent e) {
		return new IrrigationEventDto(e.getId(), e.getDeviceId(), instant(e.getStartedAt()),
				e.hasEndedAt() ? instant(e.getEndedAt()) : null, e.getInProgress() ? null : e.getDurationSec(),
				e.getStartMoisture(), e.getInProgress() ? null : e.getEndMoisture(), mode(e.getMode()),
				e.getInProgress());
	}

	public static CropProfileDto crop(CropProfile c) {
		return new CropProfileDto(c.getId(), c.getName(), c.getMinMoisture(), c.getMaxMoisture(), c.getDescription());
	}

	public static ConfigDto config(IrrigationConfig c) {
		return new ConfigDto(mode(c.getMode()), c.hasCrop() ? crop(c.getCrop()) : null, c.getMinMoisture(),
				c.getMaxMoisture(), c.getRainPause(), c.getAiEnabled(),
				c.hasUpdatedAt() ? instant(c.getUpdatedAt()) : null, c.getUpdatedBy(),
				c.getLastAnalysisId().isEmpty() ? null : c.getLastAnalysisId());
	}

	public static AnalysisDto analysis(Analysis a) {
		AnalysisInput in = a.getInput();
		return new AnalysisDto(a.getId(), instant(a.getCreatedAt()), a.getSource().name().replace("ANALYSIS_SOURCE_", ""),
				a.getTrigger(), a.getRecommendedMin(), a.getRecommendedMax(), a.getPauseForRain(),
				a.hasHoursUntilNextIrrigation() ? a.getHoursUntilNextIrrigation() : null,
				a.getRiskLevel().name().replace("RISK_LEVEL_", ""), a.getSummary(), a.getRecommendationsList(),
				a.getApplied(), a.getFallbackReason().isEmpty() ? null : a.getFallbackReason(),
				new AnalysisInputDto(in.getCropName(), in.getMode(), in.getCurrentMin(), in.getCurrentMax(),
						in.getProfileMin(), in.getProfileMax(), in.getHasReading(), in.getCurrentMoisture(),
						in.getAvgMoisture24H(), in.getMinMoisture24H(), in.getMaxMoisture24H(),
						in.getWeatherAvailable(), in.getRainProbabilityMax12H(), in.getPrecipitationSum12H(),
						in.getTemperature(), in.getAirHumidity(), in.getCropId()),
				a.getHeadline().isEmpty() ? null : a.getHeadline(),
				a.getHealthStatus() == HealthStatus.HEALTH_STATUS_UNSPECIFIED ? null
						: a.getHealthStatus().name().replace("HEALTH_STATUS_", ""),
				a.getNextAction() == NextAction.NEXT_ACTION_UNSPECIFIED ? null
						: a.getNextAction().name().replace("NEXT_ACTION_", ""),
				a.getRecommendationDetailsList().stream().map(DtoMapper::detail).toList(),
				a.hasInputSnapshot() ? snapshot(a.getInputSnapshot()) : null,
				a.hasProjection() ? projection(a.getProjection()) : null,
				a.getFallbackDetail().isEmpty() ? null : a.getFallbackDetail(),
				a.getMoistureLevel() == com.smartgarden.contracts.ai.v1.MoistureLevel.MOISTURE_LEVEL_UNSPECIFIED ? null
						: a.getMoistureLevel().name().replace("MOISTURE_LEVEL_", ""));
	}

	static RecommendationDetailDto detail(RecommendationDetail d) {
		return new RecommendationDetailDto(d.getTitle(), d.getDetail(),
				d.getCategory().name().replace("RECOMMENDATION_CATEGORY_", ""),
				d.getPriority().name().replace("PRIORITY_", ""),
				d.hasAction() ? new SuggestedActionDto(d.getAction().getType().name().replace("ACTION_TYPE_", ""),
						d.getAction().getLabel()) : null);
	}

	static InputSnapshotDto snapshot(InputSnapshot s) {
		return new InputSnapshotDto(s.hasCurrentMoisture() ? s.getCurrentMoisture() : null,
				s.hasAvgMoisture24H() ? s.getAvgMoisture24H() : null,
				s.hasMinMoisture24H() ? s.getMinMoisture24H() : null,
				s.hasMaxMoisture24H() ? s.getMaxMoisture24H() : null,
				s.hasDryingRatePerHour() ? s.getDryingRatePerHour() : null, s.getIrrigations24H(),
				s.getPumpMinutes24H(), s.hasRainProbability12H() ? s.getRainProbability12H() : null,
				s.hasPrecipitation12H() ? s.getPrecipitation12H() : null,
				s.hasTemperature() ? s.getTemperature() : null, s.hasAirHumidity() ? s.getAirHumidity() : null,
				s.getCropName(), s.getCropMin(), s.getCropMax(), s.getCurrentMin(), s.getCurrentMax(), s.getMode(),
				s.getSensorOk(), s.getRainPause(), s.hasReadingAgeMinutes() ? s.getReadingAgeMinutes() : null,
				s.getCropId() == 0 ? null : s.getCropId(),
				s.getMoistureLevel() == com.smartgarden.contracts.ai.v1.MoistureLevel.MOISTURE_LEVEL_UNSPECIFIED ? null
						: s.getMoistureLevel().name().replace("MOISTURE_LEVEL_", ""));
	}

	static ProjectionDto projection(Projection p) {
		return new ProjectionDto(
				p.getPointsList()
					.stream()
					.map(pt -> new ProjectionPointDto(instant(pt.getTimestamp()), pt.getMoisture()))
					.toList(),
				p.hasDryingRatePerHour() ? p.getDryingRatePerHour() : null,
				p.hasEstimatedNextIrrigationAt() ? instant(p.getEstimatedNextIrrigationAt()) : null,
				p.getConfidence() == Confidence.CONFIDENCE_UNSPECIFIED ? null
						: p.getConfidence().name().replace("CONFIDENCE_", ""));
	}

	public static StatsDto stats(Stats s) {
		Map<String, Integer> risk = new LinkedHashMap<>();
		risk.put("LOW", s.getRiskLow());
		risk.put("MEDIUM", s.getRiskMedium());
		risk.put("HIGH", s.getRiskHigh());
		Map<String, Integer> health = new LinkedHashMap<>();
		health.put("GOOD", s.getHealthGood());
		health.put("ATTENTION", s.getHealthAttention());
		health.put("CRITICAL", s.getHealthCritical());
		return new StatsDto(s.getDays(), s.getTotalAnalyses(), s.getGeminiCount(), s.getRulesCount(), risk, health,
				s.getRainPauses(),
				s.getThresholdHistoryList()
					.stream()
					.map(t -> new ThresholdPointDto(instant(t.getTimestamp()), t.getMin(), t.getMax(),
							t.getSource().name().replace("ANALYSIS_SOURCE_", ""),
							t.getRiskLevel().name().replace("RISK_LEVEL_", "")))
					.toList(),
				s.getDailyMoistureList()
					.stream()
					.map(d -> new DailyMoistureDto(d.getDate(), d.hasAvg() ? d.getAvg() : null,
							d.hasMin() ? d.getMin() : null, d.hasMax() ? d.getMax() : null, d.getIrrigations(),
							d.getPumpMinutes()))
					.toList());
	}

	public static WeatherDto weather(Weather w) {
		var c = w.getCurrent();
		return new WeatherDto(instant(w.getFetchedAt()), w.getLatitude(), w.getLongitude(),
				new CurrentWeatherDto(instant(c.getTime()), c.getTemperature(), c.getRelativeHumidity(),
						c.getPrecipitation(), c.getWeatherCode()),
				w.getHourlyList()
					.stream()
					.map(h -> new HourlyForecastDto(instant(h.getTime()), h.getTemperature(), h.getRelativeHumidity(),
							h.getPrecipitationProbability(), h.getPrecipitation()))
					.toList(),
				w.getMaxRainProbability12H(), w.getTotalPrecipitation12H());
	}

	public static DeviceStatusDto deviceStatus(DeviceStatusEvent e) {
		return new DeviceStatusDto(e.getDeviceId(), e.getOnline(), e.getReason(), instant(e.getChangedAt()));
	}

}
