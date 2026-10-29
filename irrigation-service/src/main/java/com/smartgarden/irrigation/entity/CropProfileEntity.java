package com.smartgarden.irrigation.entity;

import com.smartgarden.irrigation.domain.CropProfileView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "crop_profile")
public class CropProfileEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String name;

	@Column(name = "min_moisture", nullable = false)
	private int minMoisture;

	@Column(name = "max_moisture", nullable = false)
	private int maxMoisture;

	@Column(length = 255)
	private String description;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CropProfileEntity() {
	}

	public CropProfileEntity(String name, int minMoisture, int maxMoisture, String description, Instant updatedAt) {
		update(name, minMoisture, maxMoisture, description, updatedAt);
	}

	public void update(String name, int minMoisture, int maxMoisture, String description, Instant updatedAt) {
		this.name = name;
		this.minMoisture = minMoisture;
		this.maxMoisture = maxMoisture;
		this.description = description;
		this.updatedAt = updatedAt;
	}

	public CropProfileView toView() {
		return new CropProfileView(id, name, minMoisture, maxMoisture, description == null ? "" : description);
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getMinMoisture() {
		return minMoisture;
	}

	public int getMaxMoisture() {
		return maxMoisture;
	}

}
