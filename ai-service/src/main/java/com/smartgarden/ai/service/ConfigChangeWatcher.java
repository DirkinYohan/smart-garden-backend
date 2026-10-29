package com.smartgarden.ai.service;

import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc;
import com.smartgarden.contracts.irrigation.v1.StreamConfigChangesRequest;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code IrrigationService.StreamConfigChanges}. When the crop, the mode or the thresholds change it
 * launches an immediate analysis, with a {@value #DEBOUNCE_SECONDS} s debounce so several changes in a row (sliders,
 * crop + mode) produce a single analysis instead of a burst.
 * <p>
 * The analysis re-reads the crop and the thresholds from irrigation-service right before running (see
 * {@link AnalysisService#requestConfigAnalysis()}), so the new analysis always talks about the new crop. The
 * subscription reconnects with exponential backoff when irrigation-service restarts.
 */
@Component
public class ConfigChangeWatcher {

	private static final Logger log = LoggerFactory.getLogger(ConfigChangeWatcher.class);

	static final long DEBOUNCE_SECONDS = 10;

	private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(5);

	private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

	private final IrrigationServiceGrpc.IrrigationServiceStub irrigation;

	private final AnalysisService analyses;

	private final ScheduledExecutorService scheduler = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("config-watch").daemon().factory());

	private final AtomicBoolean running = new AtomicBoolean();

	private volatile ScheduledFuture<?> pending;

	private volatile Duration backoff = INITIAL_BACKOFF;

	public ConfigChangeWatcher(IrrigationServiceGrpc.IrrigationServiceStub irrigation, AnalysisService analyses) {
		this.irrigation = irrigation;
		this.analyses = analyses;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (running.compareAndSet(false, true)) {
			open();
		}
	}

	@PreDestroy
	public void stop() {
		running.set(false);
		scheduler.shutdownNow();
	}

	private void open() {
		if (!running.get()) {
			return;
		}
		try {
			irrigation.withWaitForReady()
				.streamConfigChanges(StreamConfigChangesRequest.getDefaultInstance(), new StreamObserver<>() {

					@Override
					public void onNext(IrrigationConfig config) {
						onConfig(config);
					}

					@Override
					public void onError(Throwable error) {
						reconnect(error);
					}

					@Override
					public void onCompleted() {
						reconnect(null);
					}
				});
		}
		catch (RuntimeException ex) {
			reconnect(ex);
		}
	}

	private void reconnect(Throwable cause) {
		if (!running.get()) {
			return;
		}
		Duration delay = backoff;
		backoff = Duration.ofMillis(Math.min(MAX_BACKOFF.toMillis(), backoff.toMillis() * 2));
		log.warn("Config stream closed ({}); reconnecting in {} s",
				cause == null ? "completed" : String.valueOf(cause.getMessage()), delay.toSeconds());
		scheduler.schedule(this::open, delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void onConfig(IrrigationConfig config) {
		backoff = INITIAL_BACKOFF;
		String crop = config.hasCrop() ? config.getCrop().getName() : "";
		log.info("Config change received: mode={} crop={} ({}) min={} max={} rainPause={} by={}", config.getMode(),
				crop, config.hasCrop() ? config.getCrop().getId() : 0, config.getMinMoisture(), config.getMaxMoisture(),
				config.getRainPause(), config.getUpdatedBy());
		synchronized (this) {
			if (pending != null) {
				pending.cancel(false);
			}
			pending = scheduler.schedule(this::runAnalysis, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
		}
	}

	/** Runs off the scheduler thread: Gemini may take up to its timeout. */
	private void runAnalysis() {
		Thread.ofVirtual().name("config-analysis").start(() -> {
			try {
				analyses.requestConfigAnalysis();
			}
			catch (RateLimitedException ex) {
				log.info("Immediate analysis skipped: {}", ex.getMessage());
			}
			catch (RuntimeException ex) {
				log.warn("Immediate analysis after a config change failed: {}", ex.getMessage());
			}
		});
	}

}
