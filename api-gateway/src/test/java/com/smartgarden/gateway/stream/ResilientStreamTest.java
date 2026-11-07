package com.smartgarden.gateway.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResilientStreamTest {

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	private final List<Context.CancellableContext> contexts = new CopyOnWriteArrayList<>();

	private final List<StreamObserver<String>> observers = new CopyOnWriteArrayList<>();

	private final List<String> relayed = new CopyOnWriteArrayList<>();

	@AfterEach
	void tearDown() {
		scheduler.shutdownNow();
	}

	private ResilientStream<String> stream(Duration rotateAfter) {
		return new ResilientStream<>("test", (ctx, obs) -> {
			contexts.add(ctx);
			observers.add(obs);
		}, relayed::add, scheduler, Duration.ofMillis(50), Duration.ofSeconds(1), rotateAfter);
	}

	@Test
	void renewsTheStreamBeforeTheProxyTimeoutWithoutAGap() {
		ResilientStream<String> s = stream(Duration.ofMillis(200));
		s.start();
		observers.get(0).onNext("a");

		await().atMost(Duration.ofSeconds(2)).until(() -> observers.size() == 2);
		// From the rotation on only the new call relays; the old one is cancelled after the overlap.
		observers.get(0).onNext("late-from-old");
		observers.get(1).onNext("b");
		assertThat(relayed).containsExactly("a", "b");

		await().atMost(ResilientStream.ROTATION_OVERLAP.plusSeconds(2)).until(() -> contexts.get(0).isCancelled());
		// The old call ending (as the cancellation reports) does not trigger a reconnect.
		observers.get(0).onError(Status.CANCELLED.asRuntimeException());
		assertThat(contexts.get(1).isCancelled()).isFalse();
		s.stop();
	}

	@Test
	void reconnectsWhenTheStreamBreaks() {
		ResilientStream<String> s = stream(Duration.ZERO);
		s.start();
		observers.get(0).onError(Status.UNAVAILABLE.withDescription("stream timeout").asRuntimeException());

		await().atMost(Duration.ofSeconds(2)).until(() -> observers.size() == 2);
		observers.get(1).onNext("x");
		assertThat(relayed).containsExactly("x");
		s.stop();
		assertThat(contexts.get(1).isCancelled()).isTrue();
	}

}
