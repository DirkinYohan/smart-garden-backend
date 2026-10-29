package com.smartgarden.irrigation.domain;

import com.smartgarden.contracts.common.v1.IrrigationMode;

public enum IrrigationModeType {
	AUTO, MANUAL, AI;

	public IrrigationMode toProto() {
		return switch (this) {
			case AUTO -> IrrigationMode.IRRIGATION_MODE_AUTO;
			case MANUAL -> IrrigationMode.IRRIGATION_MODE_MANUAL;
			case AI -> IrrigationMode.IRRIGATION_MODE_AI;
		};
	}

	public static IrrigationModeType fromProto(IrrigationMode mode) {
		return switch (mode) {
			case IRRIGATION_MODE_AUTO -> AUTO;
			case IRRIGATION_MODE_MANUAL -> MANUAL;
			case IRRIGATION_MODE_AI -> AI;
			default -> throw new IllegalArgumentException("Modo inválido: use AUTO, MANUAL o AI");
		};
	}
}
