package com.smartgarden.ai.domain;

/** What the system will / should do next, computed deterministically (HealthEvaluator). */
public enum NextAction {
	IRRIGATE_NOW, WAIT, PAUSED_FOR_RAIN, CHECK_SENSOR
}
