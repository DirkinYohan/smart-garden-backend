package com.smartgarden.ai.domain;

public enum ActionType {

	APPLY_THRESHOLDS("Aplicar umbrales recomendados"),
	ENABLE_AI_MODE("Activar el modo IA"),
	DISABLE_RAIN_PAUSE("Desactivar la pausa por lluvia"),
	ENABLE_RAIN_PAUSE("Activar la pausa por lluvia"),
	CHECK_SENSOR("Revisar el sensor");

	private final String label;

	ActionType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public SuggestedAction toAction() {
		return new SuggestedAction(this, label);
	}

}
