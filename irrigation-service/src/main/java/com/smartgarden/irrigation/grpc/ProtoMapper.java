package com.smartgarden.irrigation.grpc;

import com.google.protobuf.Timestamp;
import com.smartgarden.contracts.irrigation.v1.CropProfile;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.irrigation.v1.UpdateConfigRequest;
import com.smartgarden.irrigation.domain.ConfigUpdate;
import com.smartgarden.irrigation.domain.ConfigView;
import com.smartgarden.irrigation.domain.CropProfileView;
import com.smartgarden.irrigation.domain.IrrigationModeType;
import java.time.Instant;

final class ProtoMapper {

	private ProtoMapper() {
	}

	static Timestamp timestamp(Instant instant) {
		return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
	}

	static IrrigationConfig config(ConfigView v) {
		IrrigationConfig.Builder b = IrrigationConfig.newBuilder()
			.setMode(v.mode().toProto())
			.setMinMoisture(v.minMoisture())
			.setMaxMoisture(v.maxMoisture())
			.setRainPause(v.rainPause())
			.setAiEnabled(v.aiEnabled())
			.setUpdatedAt(timestamp(v.updatedAt()))
			.setUpdatedBy(v.updatedBy());
		if (v.crop() != null) {
			b.setCrop(crop(v.crop()));
		}
		if (v.lastAnalysisId() != null) {
			b.setLastAnalysisId(v.lastAnalysisId());
		}
		return b.build();
	}

	static CropProfile crop(CropProfileView c) {
		return CropProfile.newBuilder()
			.setId(c.id())
			.setName(c.name())
			.setMinMoisture(c.minMoisture())
			.setMaxMoisture(c.maxMoisture())
			.setDescription(c.description())
			.build();
	}

	static CropProfileView crop(CropProfile c) {
		return new CropProfileView(c.getId(), c.getName(), c.getMinMoisture(), c.getMaxMoisture(), c.getDescription());
	}

	static ConfigUpdate update(UpdateConfigRequest r) {
		return new ConfigUpdate(r.hasMode() ? IrrigationModeType.fromProto(r.getMode()) : null,
				r.hasCropProfileId() ? r.getCropProfileId() : null, r.hasMinMoisture() ? r.getMinMoisture() : null,
				r.hasMaxMoisture() ? r.getMaxMoisture() : null, r.hasRainPause() ? r.getRainPause() : null,
				r.hasAiEnabled() ? r.getAiEnabled() : null);
	}

}
