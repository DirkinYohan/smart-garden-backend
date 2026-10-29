package com.smartgarden.gateway.grpc;

import com.smartgarden.contracts.ai.v1.AiServiceGrpc.AiServiceBlockingStub;
import com.smartgarden.contracts.ai.v1.GetLatestAnalysisRequest;
import com.smartgarden.contracts.ai.v1.GetStatsRequest;
import com.smartgarden.contracts.ai.v1.GetWeatherRequest;
import com.smartgarden.contracts.ai.v1.ListAnalysesRequest;
import com.smartgarden.contracts.ai.v1.RequestAnalysisRequest;
import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.contracts.irrigation.v1.CropProfile;
import com.smartgarden.contracts.irrigation.v1.GetConfigRequest;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc.IrrigationServiceBlockingStub;
import com.smartgarden.contracts.irrigation.v1.ListCropProfilesRequest;
import com.smartgarden.contracts.irrigation.v1.ManualIrrigationRequest;
import com.smartgarden.contracts.irrigation.v1.UpsertCropProfileRequest;
import com.smartgarden.contracts.telemetry.v1.GetCurrentStatusRequest;
import com.smartgarden.contracts.telemetry.v1.GetHistoryRequest;
import com.smartgarden.contracts.telemetry.v1.GetIrrigationEventsRequest;
import com.smartgarden.contracts.telemetry.v1.HistoryRange;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc.TelemetryServiceBlockingStub;
import com.smartgarden.gateway.config.GrpcGatewayProperties;
import com.smartgarden.gateway.dto.AnalysisDto;
import com.smartgarden.gateway.dto.ConfigDto;
import com.smartgarden.gateway.dto.CropProfileDto;
import com.smartgarden.gateway.dto.CropProfileRequest;
import com.smartgarden.gateway.dto.HistoryResponse;
import com.smartgarden.gateway.dto.IrrigationEventDto;
import com.smartgarden.gateway.dto.PumpResponse;
import com.smartgarden.gateway.dto.StatsDto;
import com.smartgarden.gateway.dto.StatusResponse;
import com.smartgarden.gateway.dto.UpdateConfigRequest;
import com.smartgarden.gateway.dto.WeatherDto;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Facade over the three blocking stubs used by the REST controllers. Every call has a deadline;
 * {@link io.grpc.StatusRuntimeException}s are translated to HTTP by the exception handler.
 */
@Component
public class GardenGrpcClient {

	private final TelemetryServiceBlockingStub telemetry;

	private final IrrigationServiceBlockingStub irrigation;

	private final AiServiceBlockingStub ai;

	private final GrpcGatewayProperties props;

	public GardenGrpcClient(TelemetryServiceBlockingStub telemetry, IrrigationServiceBlockingStub irrigation,
			AiServiceBlockingStub ai, GrpcGatewayProperties props) {
		this.telemetry = telemetry;
		this.irrigation = irrigation;
		this.ai = ai;
		this.props = props;
	}

	// --- telemetry-service

	public StatusResponse status() {
		return DtoMapper.status(telemetry().getCurrentStatus(GetCurrentStatusRequest.getDefaultInstance()));
	}

	public HistoryResponse history(String range) {
		HistoryRange protoRange = switch (range) {
			case "24h" -> HistoryRange.HISTORY_RANGE_24H;
			case "7d" -> HistoryRange.HISTORY_RANGE_7D;
			case "30d" -> HistoryRange.HISTORY_RANGE_30D;
			default -> throw new IllegalArgumentException("range debe ser 24h, 7d o 30d");
		};
		return DtoMapper.history(range,
				telemetry().getHistory(GetHistoryRequest.newBuilder().setRange(protoRange).build()));
	}

	public List<IrrigationEventDto> irrigationEvents(int limit) {
		return telemetry().getIrrigationEvents(GetIrrigationEventsRequest.newBuilder().setLimit(limit).build())
			.getEventsList()
			.stream()
			.map(DtoMapper::event)
			.toList();
	}

	// --- irrigation-service

	public ConfigDto config() {
		return DtoMapper.config(irrigation().getConfig(GetConfigRequest.getDefaultInstance()));
	}

	public ConfigDto updateConfig(UpdateConfigRequest r) {
		var request = com.smartgarden.contracts.irrigation.v1.UpdateConfigRequest.newBuilder();
		if (r.mode() != null) {
			request.setMode(DtoMapper.mode(r.mode()));
		}
		if (r.cropProfileId() != null) {
			request.setCropProfileId(r.cropProfileId());
		}
		if (r.minMoisture() != null) {
			request.setMinMoisture(r.minMoisture());
		}
		if (r.maxMoisture() != null) {
			request.setMaxMoisture(r.maxMoisture());
		}
		if (r.rainPause() != null) {
			request.setRainPause(r.rainPause());
		}
		if (r.aiEnabled() != null) {
			request.setAiEnabled(r.aiEnabled());
		}
		return DtoMapper.config(irrigation().updateConfig(request.build()));
	}

	public List<CropProfileDto> crops() {
		return irrigation().listCropProfiles(ListCropProfilesRequest.getDefaultInstance())
			.getProfilesList()
			.stream()
			.map(DtoMapper::crop)
			.toList();
	}

	/** {@code id == 0} creates a new profile. */
	public CropProfileDto upsertCrop(long id, CropProfileRequest r) {
		CropProfile profile = CropProfile.newBuilder()
			.setId(id)
			.setName(r.name())
			.setMinMoisture(r.minMoisture())
			.setMaxMoisture(r.maxMoisture())
			.setDescription(r.description() == null ? "" : r.description())
			.build();
		return DtoMapper
			.crop(irrigation().upsertCropProfile(UpsertCropProfileRequest.newBuilder().setProfile(profile).build()));
	}

	public PumpResponse pump(PumpAction action, int durationSec) {
		return pump(action, durationSec, "");
	}

	/**
	 * Comando de bomba con clave de idempotencia: repetir la misma clave (reintento del navegador) no vuelve a
	 * encender la bomba; telemetry-service lo deduplica.
	 */
	public PumpResponse pump(PumpAction action, int durationSec, String idempotencyKey) {
		var response = irrigation().manualIrrigation(ManualIrrigationRequest.newBuilder()
			.setAction(action)
			.setDurationSec(durationSec)
			.setRequestId(idempotencyKey == null ? "" : idempotencyKey)
			.build());
		return new PumpResponse(response.getAccepted(), response.getMessage());
	}

	// --- ai-service

	public AnalysisDto latestAnalysis() {
		return DtoMapper.analysis(ai().getLatestAnalysis(GetLatestAnalysisRequest.getDefaultInstance()));
	}

	public List<AnalysisDto> analyses(int limit) {
		return ai().listAnalyses(ListAnalysesRequest.newBuilder().setLimit(limit).build())
			.getAnalysesList()
			.stream()
			.map(DtoMapper::analysis)
			.toList();
	}

	public AnalysisDto requestAnalysis() {
		// Data collection + one Gemini call (bounded to 25 s by ai-service): allow 4.5x the normal deadline.
		return DtoMapper.analysis(ai.withDeadlineAfter(props.deadline().toMillis() * 9 / 2, TimeUnit.MILLISECONDS)
			.requestAnalysis(RequestAnalysisRequest.getDefaultInstance()));
	}

	public StatsDto stats(int days) {
		return DtoMapper.stats(ai().getStats(GetStatsRequest.newBuilder().setDays(days).build()));
	}

	public WeatherDto weather() {
		return DtoMapper.weather(ai().getWeather(GetWeatherRequest.getDefaultInstance()));
	}

	private TelemetryServiceBlockingStub telemetry() {
		return telemetry.withDeadlineAfter(props.deadline().toMillis(), TimeUnit.MILLISECONDS);
	}

	private IrrigationServiceBlockingStub irrigation() {
		return irrigation.withDeadlineAfter(props.deadline().toMillis(), TimeUnit.MILLISECONDS);
	}

	private AiServiceBlockingStub ai() {
		return ai.withDeadlineAfter(props.deadline().toMillis(), TimeUnit.MILLISECONDS);
	}

}
