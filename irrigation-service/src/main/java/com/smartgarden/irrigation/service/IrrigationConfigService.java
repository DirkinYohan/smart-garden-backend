package com.smartgarden.irrigation.service;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.irrigation.client.TelemetryClient;
import com.smartgarden.irrigation.config.IrrigationProperties;
import com.smartgarden.irrigation.domain.ConfigUpdate;
import com.smartgarden.irrigation.domain.ConfigView;
import com.smartgarden.common.thresholds.CropThresholdClamp;
import com.smartgarden.irrigation.domain.IrrigationModeType;
import com.smartgarden.irrigation.domain.ThresholdValidator;
import com.smartgarden.irrigation.entity.CropProfileEntity;
import com.smartgarden.irrigation.entity.IrrigationConfigEntity;
import com.smartgarden.irrigation.repository.CropProfileRepository;
import com.smartgarden.irrigation.repository.IrrigationConfigRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IrrigationConfigService {

	private static final Logger log = LoggerFactory.getLogger(IrrigationConfigService.class);

	public static final int MAX_MANUAL_DURATION_SEC = 120;

	private final IrrigationConfigRepository configRepository;

	private final CropProfileRepository cropRepository;

	private final DeviceConfigSync deviceSync;

	private final TelemetryClient telemetry;

	private final IrrigationProperties props;

	private final Clock clock;

	private final TransactionTemplate tx;

	private final Subscribers<ConfigView> subscribers = new Subscribers<>();

	public IrrigationConfigService(IrrigationConfigRepository configRepository, CropProfileRepository cropRepository,
			DeviceConfigSync deviceSync, TelemetryClient telemetry, IrrigationProperties props, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.configRepository = configRepository;
		this.cropRepository = cropRepository;
		this.deviceSync = deviceSync;
		this.telemetry = telemetry;
		this.props = props;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void publishOnStartup() {
		if (props.publishConfigOnStartup()) {
			deviceSync.publish(current());
		}
	}

	public ConfigView current() {
		return tx.execute(status -> load().toView());
	}

	public ConfigView update(ConfigUpdate update) {
		ConfigView view = tx.execute(status -> {
			IrrigationConfigEntity cfg = load();
			if (update.mode() != null) {
				cfg.setMode(update.mode());
				if (update.mode() == IrrigationModeType.AI) {
					if (Boolean.FALSE.equals(update.aiEnabled())) {
						throw new IllegalArgumentException("El modo AI requiere aiEnabled = true");
					}
					cfg.setAiEnabled(true);
				}
			}
			if (update.cropProfileId() != null) {
				CropProfileEntity crop = findCrop(update.cropProfileId());
				cfg.setCrop(crop);

				if (update.minMoisture() == null && update.maxMoisture() == null) {
					cfg.setMinMoisture(crop.getMinMoisture());
					cfg.setMaxMoisture(crop.getMaxMoisture());
				}
			}
			if (update.minMoisture() != null) {
				cfg.setMinMoisture(update.minMoisture());
			}
			if (update.maxMoisture() != null) {
				cfg.setMaxMoisture(update.maxMoisture());
			}
			if (update.rainPause() != null) {
				cfg.setRainPause(update.rainPause());
			}
			if (update.aiEnabled() != null && update.mode() != IrrigationModeType.AI) {
				if (!update.aiEnabled() && cfg.getMode() == IrrigationModeType.AI) {
					throw new IllegalArgumentException("No se puede desactivar la IA mientras el modo sea AI");
				}
				cfg.setAiEnabled(update.aiEnabled());
			}
			ThresholdValidator.validate(cfg.getMinMoisture(), cfg.getMaxMoisture());
			cfg.touch(clock.instant(), "USER");
			return cfg.toView();
		});
		log.info("Config updated by user: {}", view);
		afterChange(view);
		return view;
	}

	public ManualResult manualIrrigation(PumpAction action, int durationSec) {
		return manualIrrigation(action, durationSec, null);
	}

	public ManualResult manualIrrigation(PumpAction action, int durationSec, String requestId) {
		ConfigView cfg = current();
		if (cfg.mode() != IrrigationModeType.MANUAL) {
			throw new IllegalStateException("El riego manual solo está permitido en modo MANUAL");
		}
		String message = switch (action) {
			case PUMP_ACTION_ON -> {
				if (durationSec < 1 || durationSec > MAX_MANUAL_DURATION_SEC) {
					throw new IllegalArgumentException(
							"La duración debe estar entre 1 y " + MAX_MANUAL_DURATION_SEC + " segundos");
				}
				yield "Bomba encendida por " + durationSec + " s";
			}
			case PUMP_ACTION_OFF -> "Bomba apagada";
			default -> throw new IllegalArgumentException("Acción inválida: use ON u OFF");
		};
		String key = (requestId == null || requestId.isBlank()) ? java.util.UUID.randomUUID().toString() : requestId;
		telemetry.sendPump(action, durationSec, key);
		log.info("Manual irrigation command: {} {}s", action, durationSec);
		return new ManualResult(true, message);
	}

	public AiApplyResult applyAiRecommendation(int min, int max, boolean rainPause, String analysisId) {
		AiApplyResult result = tx.execute(status -> {
			IrrigationConfigEntity cfg = load();
			if (cfg.getMode() != IrrigationModeType.AI || !cfg.isAiEnabled()) {
				return new AiApplyResult(false, "La configuración no está en modo AI; recomendación no aplicada",
						cfg.toView());
			}

			int profileMin = cfg.getCrop() == null ? 0 : cfg.getCrop().getMinMoisture();
			int profileMax = cfg.getCrop() == null ? 0 : cfg.getCrop().getMaxMoisture();
			int[] clamped = CropThresholdClamp.clamp(min, max, profileMin, profileMax);
			if (clamped[0] != min || clamped[1] != max) {
				log.info("AI thresholds {}-{} clamped to the crop profile {}-{}: {}-{}", min, max, profileMin,
						profileMax, clamped[0], clamped[1]);
			}
			ThresholdValidator.validate(clamped[0], clamped[1]);
			cfg.setMinMoisture(clamped[0]);
			cfg.setMaxMoisture(clamped[1]);
			cfg.setRainPause(rainPause);
			cfg.setLastAnalysisId(analysisId);
			cfg.touch(clock.instant(), "AI");
			return new AiApplyResult(true, "Recomendación de la IA aplicada", cfg.toView());
		});
		if (result.applied()) {
			log.info("AI recommendation {} applied: min={} max={} rainPause={}", analysisId, min, max, rainPause);
			afterChange(result.config());
		}
		return result;
	}

	public void onCropProfileUpdated(long cropId) {
		ConfigView view = tx.execute(status -> {
			IrrigationConfigEntity cfg = load();
			if (cfg.getCrop() == null || cfg.getCrop().getId() != cropId || cfg.getMode() != IrrigationModeType.AUTO) {
				return null;
			}
			cfg.setMinMoisture(cfg.getCrop().getMinMoisture());
			cfg.setMaxMoisture(cfg.getCrop().getMaxMoisture());
			cfg.touch(clock.instant(), "SYSTEM");
			return cfg.toView();
		});
		if (view != null) {
			afterChange(view);
		}
	}

	public Subscribers<ConfigView> subscribers() {
		return subscribers;
	}

	private void afterChange(ConfigView view) {
		deviceSync.publish(view);
		subscribers.publish(view);
	}

	private IrrigationConfigEntity load() {
		return configRepository.findById(IrrigationConfigEntity.SINGLETON_ID)
			.orElseThrow(() -> new IllegalStateException("Irrigation config row is missing (Flyway V2)"));
	}

	private CropProfileEntity findCrop(long id) {
		return cropRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("No existe el perfil de cultivo " + id));
	}

	public record ManualResult(boolean accepted, String message) {
	}

	public record AiApplyResult(boolean applied, String message, ConfigView config) {
	}

}
