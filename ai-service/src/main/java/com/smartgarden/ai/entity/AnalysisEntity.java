package com.smartgarden.ai.entity;

import com.smartgarden.ai.domain.AnalysisSource;
import com.smartgarden.ai.domain.HealthStatus;
import com.smartgarden.ai.domain.NextAction;
import com.smartgarden.ai.domain.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis")
public class AnalysisEntity {

	@Id
	private UUID id;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AnalysisSource source;

	@Column(name = "trigger_type", nullable = false, length = 16)
	private String trigger;

	@Column(name = "recommended_min", nullable = false)
	private int recommendedMin;

	@Column(name = "recommended_max", nullable = false)
	private int recommendedMax;

	@Column(name = "pause_for_rain", nullable = false)
	private boolean pauseForRain;

	@Column(name = "hours_until_next_irrigation")
	private Double hoursUntilNextIrrigation;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", nullable = false, length = 16)
	private RiskLevel riskLevel;

	@Column(nullable = false, length = 1000)
	private String summary;

	@Column(nullable = false, length = 4000)
	private String recommendations;

	@Column(nullable = false)
	private boolean applied;

	@Column(name = "fallback_reason", length = 500)
	private String fallbackReason;

	@Column(name = "input_snapshot", nullable = false, length = 4000)
	private String inputSnapshot;

	@Column(length = 200)
	private String headline;

	@Enumerated(EnumType.STRING)
	@Column(name = "health_status", length = 16)
	private HealthStatus healthStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "next_action", length = 24)
	private NextAction nextAction;

	@Column(name = "recommendation_details", length = 4000)
	private String recommendationDetails;

	@Column(length = 4000)
	private String projection;

	@Column(name = "fallback_detail", length = 500)
	private String fallbackDetail;

	@Enumerated(EnumType.STRING)
	@Column(name = "moisture_level", length = 16)
	private com.smartgarden.ai.domain.MoistureLevel moistureLevel;

	protected AnalysisEntity() {
	}

	public AnalysisEntity(UUID id, Instant createdAt, AnalysisSource source, String trigger, int recommendedMin,
			int recommendedMax, boolean pauseForRain, Double hoursUntilNextIrrigation, RiskLevel riskLevel,
			String summary, String recommendations, String fallbackReason, String inputSnapshot) {
		this.id = id;
		this.createdAt = createdAt;
		this.source = source;
		this.trigger = trigger;
		this.recommendedMin = recommendedMin;
		this.recommendedMax = recommendedMax;
		this.pauseForRain = pauseForRain;
		this.hoursUntilNextIrrigation = hoursUntilNextIrrigation;
		this.riskLevel = riskLevel;
		this.summary = summary;
		this.recommendations = recommendations;
		this.fallbackReason = fallbackReason;
		this.inputSnapshot = inputSnapshot;
	}

	public void setInsights(String headline, HealthStatus healthStatus, NextAction nextAction,
			String recommendationDetails, String projection, String fallbackDetail) {
		this.headline = headline;
		this.healthStatus = healthStatus;
		this.nextAction = nextAction;
		this.recommendationDetails = recommendationDetails;
		this.projection = projection;
		this.fallbackDetail = fallbackDetail;
	}

	public void setMoistureLevel(com.smartgarden.ai.domain.MoistureLevel moistureLevel) {
		this.moistureLevel = moistureLevel;
	}

	public com.smartgarden.ai.domain.MoistureLevel getMoistureLevel() {
		return moistureLevel;
	}

	public void markApplied() {
		this.applied = true;
	}

	public UUID getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public AnalysisSource getSource() {
		return source;
	}

	public String getTrigger() {
		return trigger;
	}

	public int getRecommendedMin() {
		return recommendedMin;
	}

	public int getRecommendedMax() {
		return recommendedMax;
	}

	public boolean isPauseForRain() {
		return pauseForRain;
	}

	public Double getHoursUntilNextIrrigation() {
		return hoursUntilNextIrrigation;
	}

	public RiskLevel getRiskLevel() {
		return riskLevel;
	}

	public String getSummary() {
		return summary;
	}

	public String getRecommendations() {
		return recommendations;
	}

	public boolean isApplied() {
		return applied;
	}

	public String getFallbackReason() {
		return fallbackReason;
	}

	public String getInputSnapshot() {
		return inputSnapshot;
	}

	public String getHeadline() {
		return headline;
	}

	public HealthStatus getHealthStatus() {
		return healthStatus;
	}

	public NextAction getNextAction() {
		return nextAction;
	}

	public String getRecommendationDetails() {
		return recommendationDetails;
	}

	public String getProjection() {
		return projection;
	}

	public String getFallbackDetail() {
		return fallbackDetail;
	}

}
