package com.smartgarden.telemetry.grpc;

import com.smartgarden.contracts.telemetry.v1.CurrentStatus;
import com.smartgarden.contracts.telemetry.v1.DeviceStatusEvent;
import com.smartgarden.contracts.telemetry.v1.GetCurrentStatusRequest;
import com.smartgarden.contracts.telemetry.v1.GetDailyStatsRequest;
import com.smartgarden.contracts.telemetry.v1.GetDailyStatsResponse;
import com.smartgarden.contracts.telemetry.v1.GetHistoryRequest;
import com.smartgarden.contracts.telemetry.v1.GetHistoryResponse;
import com.smartgarden.contracts.telemetry.v1.GetIrrigationEventsRequest;
import com.smartgarden.contracts.telemetry.v1.GetIrrigationEventsResponse;
import com.smartgarden.contracts.telemetry.v1.GetRecentSamplesRequest;
import com.smartgarden.contracts.telemetry.v1.GetRecentSamplesResponse;
import com.smartgarden.contracts.telemetry.v1.SendDeviceCommandRequest;
import com.smartgarden.contracts.telemetry.v1.SendDeviceCommandResponse;
import com.smartgarden.contracts.telemetry.v1.StreamDeviceStatusRequest;
import com.smartgarden.contracts.telemetry.v1.StreamTelemetryRequest;
import com.smartgarden.contracts.telemetry.v1.TelemetryReading;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc;
import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.service.DeviceCommandService;
import com.smartgarden.telemetry.service.DeviceCommandService.SentCommand;
import com.smartgarden.telemetry.service.DeviceStatusTracker;
import com.smartgarden.telemetry.service.HistoryRangeSpec;
import com.smartgarden.telemetry.service.IrrigationEventService;
import com.smartgarden.telemetry.service.TelemetryIngestionService;
import com.smartgarden.telemetry.service.TelemetryQueryService;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelemetryGrpcService extends TelemetryServiceGrpc.TelemetryServiceImplBase {

	private static final Logger log = LoggerFactory.getLogger(TelemetryGrpcService.class);

	private static final int DEFAULT_EVENTS_LIMIT = 20;

	private static final int MAX_EVENTS_LIMIT = 200;

	private final TelemetryIngestionService ingestion;

	private final TelemetryQueryService queries;

	private final IrrigationEventService irrigationEvents;

	private final DeviceStatusTracker statusTracker;

	private final DeviceCommandService commands;

	public TelemetryGrpcService(TelemetryIngestionService ingestion, TelemetryQueryService queries,
			IrrigationEventService irrigationEvents, DeviceStatusTracker statusTracker,
			DeviceCommandService commands) {
		this.ingestion = ingestion;
		this.queries = queries;
		this.irrigationEvents = irrigationEvents;
		this.statusTracker = statusTracker;
		this.commands = commands;
	}

	@Override
	public void getCurrentStatus(GetCurrentStatusRequest request, StreamObserver<CurrentStatus> responseObserver) {
		CurrentStatus.Builder status = CurrentStatus.newBuilder()
			.setDeviceOnline(statusTracker.isOnline())
			.setDeviceStatus(statusTracker.lastStatus());
		ingestion.latest().ifPresent(reading -> status.setHasReading(true)
			.setReading(ProtoMapper.reading(reading))
			.setLastSeen(ProtoMapper.timestamp(reading.receivedAt())));
		ingestion.latestValid().ifPresent(valid -> status.setLastValidReading(ProtoMapper.reading(valid)));
		responseObserver.onNext(status.build());
		responseObserver.onCompleted();
	}

	@Override
	public void getHistory(GetHistoryRequest request, StreamObserver<GetHistoryResponse> responseObserver) {
		HistoryRangeSpec range = HistoryRangeSpec.from(request.getRange());
		GetHistoryResponse.Builder response = GetHistoryResponse.newBuilder()
			.setRange(request.getRange())
			.setIntervalMinutes((int) range.interval().toMinutes());
		queries.history(range).forEach(bucket -> response.addPoints(ProtoMapper.historyPoint(bucket)));
		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void getIrrigationEvents(GetIrrigationEventsRequest request,
			StreamObserver<GetIrrigationEventsResponse> responseObserver) {
		int limit = request.getLimit() <= 0 ? DEFAULT_EVENTS_LIMIT : Math.min(request.getLimit(), MAX_EVENTS_LIMIT);
		GetIrrigationEventsResponse.Builder response = GetIrrigationEventsResponse.newBuilder();
		irrigationEvents.latest(limit).forEach(e -> response.addEvents(ProtoMapper.irrigationEvent(e)));
		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void sendDeviceCommand(SendDeviceCommandRequest request,
			StreamObserver<SendDeviceCommandResponse> responseObserver) {
		SentCommand sent = switch (request.getCommandCase()) {
			case PUMP -> commands.sendPump(request.getPump().getAction(), request.getPump().getDurationSec(),
					request.getRequestId());
			case CONFIG -> {
				var c = request.getConfig();
				yield commands.sendConfig(DeviceMode.fromProto(c.getMode()), c.getMinMoisture(), c.getMaxMoisture(),
						c.getRainPause(), request.getRequestId());
			}
			case COMMAND_NOT_SET -> throw new IllegalArgumentException("command is required (pump or config)");
		};
		responseObserver.onNext(SendDeviceCommandResponse.newBuilder()
			.setPublished(true)
			.setTopic(sent.topic())
			.setPayload(sent.payload())
			.setRetained(sent.retained())
			.build());
		responseObserver.onCompleted();
	}

	@Override
	public void getRecentSamples(GetRecentSamplesRequest request,
			StreamObserver<GetRecentSamplesResponse> responseObserver) {
		GetRecentSamplesResponse.Builder response = GetRecentSamplesResponse.newBuilder();
		queries.recentSamples(request.getHours() <= 0 ? 12 : request.getHours())
			.forEach(s -> response.addSamples(ProtoMapper.sample(s)));
		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void getDailyStats(GetDailyStatsRequest request, StreamObserver<GetDailyStatsResponse> responseObserver) {
		GetDailyStatsResponse.Builder response = GetDailyStatsResponse.newBuilder();
		queries.dailyStats(request.getDays() <= 0 ? 7 : request.getDays())
			.forEach(d -> response.addDays(ProtoMapper.dailyStat(d)));
		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void streamTelemetry(StreamTelemetryRequest request, StreamObserver<TelemetryReading> responseObserver) {
		log.debug("New StreamTelemetry subscriber");
		ServerStreams.bind(responseObserver, ingestion.subscribers(), ProtoMapper::reading);
	}

	@Override
	public void streamDeviceStatus(StreamDeviceStatusRequest request,
			StreamObserver<DeviceStatusEvent> responseObserver) {
		log.debug("New StreamDeviceStatus subscriber");
		ServerStreams.bind(responseObserver, statusTracker.subscribers(), ProtoMapper::deviceStatus);
	}

}
