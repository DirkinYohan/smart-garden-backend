package com.smartgarden.gateway.stream;

import com.smartgarden.contracts.ai.v1.AiServiceGrpc.AiServiceStub;
import com.smartgarden.contracts.ai.v1.Analysis;
import com.smartgarden.contracts.ai.v1.AnalysisReason;
import com.smartgarden.contracts.ai.v1.RequestAnalysisRequest;
import com.smartgarden.gateway.config.GrpcGatewayProperties;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tells ai-service that the irrigation configuration changed, so it analyses right away. Replaces the
 * {@code StreamConfigChanges} subscription of ai-service when that service scales to zero: the call wakes it only
 * when needed and avoids a dependency cycle. Debounced: several quick changes produce a single call. ai-service
 * still applies its own spacing and skips configurations it already analysed.
 */
@Component
public class ConfigChangeNotifier {

	private static final Logger log = LoggerFactory.getLogger(ConfigChangeNotifier.class);

	/** Gemini can take up to ~25 s, plus the cold start of ai-service and the config-analysis spacing. */
	private static final long CALL_DEADLINE_SECONDS = 180;

	private final AiServiceStub ai;

	private final GrpcGatewayProperties props;

	private final ScheduledExecutorService scheduler = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("config-notify").daemon().factory());

	private ScheduledFuture<?> pending;

	public ConfigChangeNotifier(AiServiceStub ai, GrpcGatewayProperties props) {
		this.ai = ai;
		this.props = props;
	}

	public synchronized void configChanged() {
		if (!props.notifyAiOnConfigChange()) {
			return;
		}
		if (pending != null) {
			pending.cancel(false);
		}
		pending = scheduler.schedule(this::notifyAi, props.configChangeDebounce().toMillis(), TimeUnit.MILLISECONDS);
	}

	private void notifyAi() {
		log.info("Configuration changed; asking ai-service for an analysis");
		RequestAnalysisRequest request = RequestAnalysisRequest.newBuilder()
			.setReason(AnalysisReason.ANALYSIS_REASON_CONFIG_CHANGED)
			.build();
		ai.withWaitForReady()
			.withDeadlineAfter(CALL_DEADLINE_SECONDS, TimeUnit.SECONDS)
			.requestAnalysis(request, new StreamObserver<>() {

				@Override
				public void onNext(Analysis analysis) {
					log.info("Analysis after the config change: {} ({})", analysis.getId(), analysis.getSource());
				}

				@Override
				public void onError(Throwable error) {
					Status status = Status.fromThrowable(error);
					log.warn("Analysis after the config change failed: {} {}", status.getCode(),
							status.getDescription() == null ? "" : status.getDescription());
				}

				@Override
				public void onCompleted() {
				}
			});
	}

	@PreDestroy
	public void stop() {
		scheduler.shutdownNow();
	}

}
