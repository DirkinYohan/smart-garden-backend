package com.smartgarden.irrigation.entity;

import com.smartgarden.irrigation.domain.ConfigView;
import com.smartgarden.irrigation.domain.IrrigationModeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "irrigation_config")
public class IrrigationConfigEntity {

	public static final long SINGLETON_ID = 1L;

	@Id
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IrrigationModeType mode;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "crop_profile_id")
	private CropProfileEntity crop;

	@Column(name = "min_moisture", nullable = false)
	private int minMoisture;

	@Column(name = "max_moisture", nullable = false)
	private int maxMoisture;

	@Column(name = "rain_pause", nullable = false)
	private boolean rainPause;

	@Column(name = "ai_enabled", nullable = false)
	private boolean aiEnabled;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by", nullable = false, length = 16)
	private String updatedBy;

	@Column(name = "last_analysis_id", length = 64)
	private String lastAnalysisId;

	protected IrrigationConfigEntity() {
	}

	public ConfigView toView() {
		return new ConfigView(mode, crop == null ? null : crop.toView(), minMoisture, maxMoisture, rainPause, aiEnabled,
				updatedAt, updatedBy, lastAnalysisId);
	}

	public void touch(Instant at, String by) {
		this.updatedAt = at;
		this.updatedBy = by;
	}

	public IrrigationModeType getMode() {
		return mode;
	}

	public void setMode(IrrigationModeType mode) {
		this.mode = mode;
	}

	public CropProfileEntity getCrop() {
		return crop;
	}

	public void setCrop(CropProfileEntity crop) {
		this.crop = crop;
	}

	public int getMinMoisture() {
		return minMoisture;
	}

	public void setMinMoisture(int minMoisture) {
		this.minMoisture = minMoisture;
	}

	public int getMaxMoisture() {
		return maxMoisture;
	}

	public void setMaxMoisture(int maxMoisture) {
		this.maxMoisture = maxMoisture;
	}

	public boolean isRainPause() {
		return rainPause;
	}

	public void setRainPause(boolean rainPause) {
		this.rainPause = rainPause;
	}

	public boolean isAiEnabled() {
		return aiEnabled;
	}

	public void setAiEnabled(boolean aiEnabled) {
		this.aiEnabled = aiEnabled;
	}

	public void setLastAnalysisId(String lastAnalysisId) {
		this.lastAnalysisId = lastAnalysisId;
	}

}
