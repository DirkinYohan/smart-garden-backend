package com.smartgarden.irrigation.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.smartgarden.contracts.common.v1.IrrigationMode;
import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.contracts.irrigation.v1.ApplyAiRecommendationRequest;
import com.smartgarden.contracts.irrigation.v1.ApplyAiRecommendationResponse;
import com.smartgarden.contracts.irrigation.v1.CropProfile;
import com.smartgarden.contracts.irrigation.v1.GetConfigRequest;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc.IrrigationServiceBlockingStub;
import com.smartgarden.contracts.irrigation.v1.ListCropProfilesRequest;
import com.smartgarden.contracts.irrigation.v1.ManualIrrigationRequest;
import com.smartgarden.contracts.irrigation.v1.ManualIrrigationResponse;
import com.smartgarden.contracts.irrigation.v1.StreamConfigChangesRequest;
import com.smartgarden.contracts.irrigation.v1.UpdateConfigRequest;
import com.smartgarden.irrigation.client.TelemetryClient;
import com.smartgarden.irrigation.domain.ConfigView;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Runs the real gRPC service in-process (Spring gRPC test transport) against H2 + Flyway. telemetry-service is
 * mocked.
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ActiveProfiles("test")
class IrrigationGrpcIntegrationTest {

	@Autowired
	private GrpcChannelFactory channels;

	@MockitoBean
	private TelemetryClient telemetry;

	private IrrigationServiceBlockingStub stub;

	private ManagedChannel channel;

	@BeforeEach
	void setUp() {
		if (channel == null) {
			channel = channels.createChannel("irrigation");
		}
		stub = IrrigationServiceGrpc.newBlockingStub(channel);
		// Reset to the seeded defaults: AUTO + "Genérico" (30-70).
		long generic = crop("Genérico").getId();
		stub.updateConfig(UpdateConfigRequest.newBuilder()
			.setMode(IrrigationMode.IRRIGATION_MODE_AUTO)
			.setCropProfileId(generic)
			.setRainPause(false)
			.build());
	}

	@Test
	void seededConfigAndCropProfilesAreAvailable() {
		IrrigationConfig config = stub.getConfig(GetConfigRequest.getDefaultInstance());

		assertThat(config.getMode()).isEqualTo(IrrigationMode.IRRIGATION_MODE_AUTO);
		assertThat(config.getCrop().getName()).isEqualTo("Genérico");
		assertThat(config.getMinMoisture()).isEqualTo(30);
		assertThat(config.getMaxMoisture()).isEqualTo(70);
		assertThat(stub.listCropProfiles(ListCropProfilesRequest.getDefaultInstance()).getProfilesList())
			.extracting(CropProfile::getName)
			.contains("Tomate", "Lechuga", "Cilantro", "Pimentón", "Fresa", "Genérico");
	}

	@Test
	void changingCropInAutoAppliesProfileThresholdsAndPushesConfig() {
		IrrigationConfig config = stub
			.updateConfig(UpdateConfigRequest.newBuilder().setCropProfileId(crop("Lechuga").getId()).build());

		assertThat(config.getMinMoisture()).isEqualTo(50);
		assertThat(config.getMaxMoisture()).isEqualTo(80);
		verify(telemetry, atLeastOnce()).sendConfig(any(ConfigView.class));
	}

	@Test
	void invalidThresholdsAreRejected() {
		assertThatThrownBy(() -> stub
			.updateConfig(UpdateConfigRequest.newBuilder().setMinMoisture(60).setMaxMoisture(65).build()))
			.isInstanceOf(StatusRuntimeException.class)
			.extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
			.isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void manualIrrigationRequiresManualMode() {
		ManualIrrigationRequest on = ManualIrrigationRequest.newBuilder()
			.setAction(PumpAction.PUMP_ACTION_ON)
			.setDurationSec(30)
			.build();

		assertThatThrownBy(() -> stub.manualIrrigation(on)).isInstanceOf(StatusRuntimeException.class)
			.extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
			.isEqualTo(Status.Code.FAILED_PRECONDITION);

		stub.updateConfig(UpdateConfigRequest.newBuilder().setMode(IrrigationMode.IRRIGATION_MODE_MANUAL).build());
		ManualIrrigationResponse response = stub.manualIrrigation(on);

		assertThat(response.getAccepted()).isTrue();
		verify(telemetry).sendPump(eq(PumpAction.PUMP_ACTION_ON), eq(30), anyString());
	}

	@Test
	void aiRecommendationOnlyAppliedInAiMode() {
		ApplyAiRecommendationRequest recommendation = ApplyAiRecommendationRequest.newBuilder()
			.setMinMoisture(35)
			.setMaxMoisture(65)
			.setRainPause(true)
			.setAnalysisId("analysis-1")
			.build();

		assertThat(stub.applyAiRecommendation(recommendation).getApplied()).isFalse();

		stub.updateConfig(UpdateConfigRequest.newBuilder().setMode(IrrigationMode.IRRIGATION_MODE_AI).build());
		ApplyAiRecommendationResponse applied = stub.applyAiRecommendation(recommendation);

		assertThat(applied.getApplied()).isTrue();
		assertThat(applied.getConfig().getMinMoisture()).isEqualTo(35);
		assertThat(applied.getConfig().getRainPause()).isTrue();
		assertThat(applied.getConfig().getUpdatedBy()).isEqualTo("AI");
		assertThat(applied.getConfig().getLastAnalysisId()).isEqualTo("analysis-1");
	}

	@Test
	void configChangesAreStreamed() throws Exception {
		CompletableFuture<IrrigationConfig> received = new CompletableFuture<>();
		IrrigationServiceGrpc.newStub(channel)
			.streamConfigChanges(StreamConfigChangesRequest.getDefaultInstance(), new StreamObserver<>() {
				@Override
				public void onNext(IrrigationConfig value) {
					received.complete(value);
				}

				@Override
				public void onError(Throwable t) {
					received.completeExceptionally(t);
				}

				@Override
				public void onCompleted() {
				}
			});
		Thread.sleep(200); // let the subscription register

		stub.updateConfig(UpdateConfigRequest.newBuilder().setRainPause(true).build());

		assertThat(received.get(5, TimeUnit.SECONDS).getRainPause()).isTrue();
	}

	@Test
	void changingCropInManualAppliesProfileThresholds() {
		stub.updateConfig(UpdateConfigRequest.newBuilder().setMode(IrrigationMode.IRRIGATION_MODE_MANUAL).build());

		IrrigationConfig config = stub
			.updateConfig(UpdateConfigRequest.newBuilder().setCropProfileId(crop("Fresa").getId()).build());

		assertThat(config.getCrop().getName()).isEqualTo("Fresa");
		assertThat(config.getMinMoisture()).isEqualTo(50);
		assertThat(config.getMaxMoisture()).isEqualTo(75);
	}

	@Test
	void changingCropInAiAppliesProfileThresholds() {
		stub.updateConfig(UpdateConfigRequest.newBuilder().setMode(IrrigationMode.IRRIGATION_MODE_AI).build());

		IrrigationConfig tomato = stub
			.updateConfig(UpdateConfigRequest.newBuilder().setCropProfileId(crop("Tomate").getId()).build());
		assertThat(tomato.getMinMoisture()).isEqualTo(40);
		assertThat(tomato.getMaxMoisture()).isEqualTo(70);

		IrrigationConfig lemon = stub
			.updateConfig(UpdateConfigRequest.newBuilder().setCropProfileId(crop("Cilantro").getId()).build());
		assertThat(lemon.getMinMoisture()).isEqualTo(45);
		assertThat(lemon.getMaxMoisture()).isEqualTo(75);
	}

	@Test
	void manualThresholdsAreRespectedUntilTheCropChanges() {
		stub.updateConfig(UpdateConfigRequest.newBuilder()
			.setMode(IrrigationMode.IRRIGATION_MODE_MANUAL)
			.setMinMoisture(55)
			.setMaxMoisture(65)
			.build());

		// Otro cambio (pausa por lluvia) no toca los umbrales elegidos a mano
		IrrigationConfig kept = stub.updateConfig(UpdateConfigRequest.newBuilder().setRainPause(true).build());
		assertThat(kept.getMinMoisture()).isEqualTo(55);
		assertThat(kept.getMaxMoisture()).isEqualTo(65);

		// Cambiar de cultivo sí aplica el rango del perfil nuevo
		IrrigationConfig changed = stub
			.updateConfig(UpdateConfigRequest.newBuilder().setCropProfileId(crop("Fresa").getId()).build());
		assertThat(changed.getMinMoisture()).isEqualTo(50);
		assertThat(changed.getMaxMoisture()).isEqualTo(75);
	}

	@Test
	void aiRecommendationIsClampedToTheCropProfile() {
		stub.updateConfig(UpdateConfigRequest.newBuilder()
			.setMode(IrrigationMode.IRRIGATION_MODE_AI)
			.setCropProfileId(crop("Tomate").getId())
			.build());

		ApplyAiRecommendationResponse applied = stub.applyAiRecommendation(ApplyAiRecommendationRequest.newBuilder()
			.setMinMoisture(20)
			.setMaxMoisture(45)
			.setAnalysisId("analysis-clamp")
			.build());

		assertThat(applied.getApplied()).isTrue();
		// Tomate 40-70: la IA solo puede moverse dentro de 30-80
		assertThat(applied.getConfig().getMinMoisture()).isEqualTo(30);
		assertThat(applied.getConfig().getMaxMoisture()).isEqualTo(45);
	}

	private CropProfile crop(String name) {
		return stub.listCropProfiles(ListCropProfilesRequest.getDefaultInstance())
			.getProfilesList()
			.stream()
			.filter(c -> c.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

}
