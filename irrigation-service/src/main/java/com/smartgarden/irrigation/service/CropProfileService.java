package com.smartgarden.irrigation.service;

import com.smartgarden.irrigation.domain.CropProfileView;
import com.smartgarden.irrigation.domain.ThresholdValidator;
import com.smartgarden.irrigation.entity.CropProfileEntity;
import com.smartgarden.irrigation.repository.CropProfileRepository;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CropProfileService {

	private final CropProfileRepository repository;

	private final IrrigationConfigService configService;

	private final Clock clock;

	private final TransactionTemplate tx;

	public CropProfileService(CropProfileRepository repository, IrrigationConfigService configService, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.configService = configService;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	public List<CropProfileView> list() {
		return repository.findAllByOrderByNameAsc().stream().map(CropProfileEntity::toView).toList();
	}

	/** Creates the profile when {@code id == 0}, otherwise updates it. */
	public CropProfileView upsert(CropProfileView input) {
		CropProfileView saved = tx.execute(status -> save(input));
		if (input.id() != 0) {
			configService.onCropProfileUpdated(saved.id());
		}
		return saved;
	}

	private CropProfileView save(CropProfileView input) {
		String name = input.name() == null ? "" : input.name().trim();
		if (name.isEmpty() || name.length() > 64) {
			throw new IllegalArgumentException("El nombre del cultivo es obligatorio (máximo 64 caracteres)");
		}
		ThresholdValidator.validate(input.minMoisture(), input.maxMoisture());
		String description = input.description() == null ? null : input.description().trim();
		if (description != null && description.length() > 255) {
			throw new IllegalArgumentException("La descripción no puede superar 255 caracteres");
		}
		repository.findByNameIgnoreCase(name).ifPresent(existing -> {
			if (existing.getId() != input.id()) {
				throw new IllegalArgumentException("Ya existe un cultivo llamado " + name);
			}
		});
		CropProfileEntity entity;
		if (input.id() == 0) {
			entity = new CropProfileEntity(name, input.minMoisture(), input.maxMoisture(), description, clock.instant());
		}
		else {
			entity = repository.findById(input.id())
				.orElseThrow(() -> new NoSuchElementException("No existe el perfil de cultivo " + input.id()));
			entity.update(name, input.minMoisture(), input.maxMoisture(), description, clock.instant());
		}
		return repository.saveAndFlush(entity).toView();
	}

}
