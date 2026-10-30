package com.smartgarden.irrigation.grpc;

import com.smartgarden.contracts.irrigation.v1.ApplyAiRecommendationRequest;
import com.smartgarden.contracts.irrigation.v1.ApplyAiRecommendationResponse;
import com.smartgarden.contracts.irrigation.v1.CropProfile;
import com.smartgarden.contracts.irrigation.v1.GetConfigRequest;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc;
import com.smartgarden.contracts.irrigation.v1.ListCropProfilesRequest;
import com.smartgarden.contracts.irrigation.v1.ListCropProfilesResponse;
import com.smartgarden.contracts.irrigation.v1.ManualIrrigationRequest;
import com.smartgarden.contracts.irrigation.v1.ManualIrrigationResponse;
import com.smartgarden.contracts.irrigation.v1.StreamConfigChangesRequest;
import com.smartgarden.contracts.irrigation.v1.UpdateConfigRequest;
import com.smartgarden.contracts.irrigation.v1.UpsertCropProfileRequest;
import com.smartgarden.irrigation.service.CropProfileService;
import com.smartgarden.irrigation.service.IrrigationConfigService;
import com.smartgarden.irrigation.service.IrrigationConfigService.AiApplyResult;
import com.smartgarden.irrigation.service.IrrigationConfigService.ManualResult;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class IrrigationGrpcService extends IrrigationServiceGrpc.IrrigationServiceImplBase {

	private final IrrigationConfigService configService;

	private final CropProfileService cropService;

	public IrrigationGrpcService(IrrigationConfigService configService, CropProfileService cropService) {
		this.configService = configService;
		this.cropService = cropService;
	}

	@Override
	public void getConfig(GetConfigRequest request, StreamObserver<IrrigationConfig> responseObserver) {
		responseObserver.onNext(ProtoMapper.config(configService.current()));
		responseObserver.onCompleted();
	}

	@Override
	public void updateConfig(UpdateConfigRequest request, StreamObserver<IrrigationConfig> responseObserver) {
		responseObserver.onNext(ProtoMapper.config(configService.update(ProtoMapper.update(request))));
		responseObserver.onCompleted();
	}

	@Override
	public void listCropProfiles(ListCropProfilesRequest request,
			StreamObserver<ListCropProfilesResponse> responseObserver) {
		ListCropProfilesResponse.Builder response = ListCropProfilesResponse.newBuilder();
		cropService.list().forEach(c -> response.addProfiles(ProtoMapper.crop(c)));
		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void upsertCropProfile(UpsertCropProfileRequest request, StreamObserver<CropProfile> responseObserver) {
		if (!request.hasProfile()) {
			throw new IllegalArgumentException("profile es obligatorio");
		}
		responseObserver.onNext(ProtoMapper.crop(cropService.upsert(ProtoMapper.crop(request.getProfile()))));
		responseObserver.onCompleted();
	}

	@Override
	public void manualIrrigation(ManualIrrigationRequest request,
			StreamObserver<ManualIrrigationResponse> responseObserver) {
		ManualResult result = configService.manualIrrigation(request.getAction(), request.getDurationSec(),
				request.getRequestId());
		responseObserver.onNext(ManualIrrigationResponse.newBuilder()
			.setAccepted(result.accepted())
			.setMessage(result.message())
			.build());
		responseObserver.onCompleted();
	}

	@Override
	public void applyAiRecommendation(ApplyAiRecommendationRequest request,
			StreamObserver<ApplyAiRecommendationResponse> responseObserver) {
		AiApplyResult result = configService.applyAiRecommendation(request.getMinMoisture(),
				request.getMaxMoisture(), request.getRainPause(), request.getAnalysisId());
		responseObserver.onNext(ApplyAiRecommendationResponse.newBuilder()
			.setApplied(result.applied())
			.setMessage(result.message())
			.setConfig(ProtoMapper.config(result.config()))
			.build());
		responseObserver.onCompleted();
	}

	@Override
	public void streamConfigChanges(StreamConfigChangesRequest request,
			StreamObserver<IrrigationConfig> responseObserver) {
		ServerStreams.bind(responseObserver, configService.subscribers(), ProtoMapper::config);
	}

}
