package com.smartgarden.gateway.stream;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A gRPC server stream that is always open: it reconnects with backoff when it breaks and, optionally, is renewed
 * before a proxy cuts it.
 * <p>
 * Azure Container Apps ends every request after 240 s, long-lived gRPC streams included. With {@code rotateAfter}
 * set below that limit the stream is renewed "make before break": the new call is opened first, the old one stops
 * relaying at that moment and is cancelled a few seconds later, so no message is lost to a reconnect gap.
 */
public class ResilientStream<T> {

	private static final Logger log = LoggerFactory.getLogger(ResilientStream.class);

	/** A stream that lived this long and then broke was healthy: reconnect right away, without backoff. */
	static final Duration HEALTHY_LIFETIME = Duration.ofSeconds(30);

	/** How long the replaced call stays open after a rotation before it is cancelled. */
	static final Duration ROTATION_OVERLAP = Duration.ofSeconds(3);

	private final String name;

	private final BiConsumer<Context.CancellableContext, StreamObserver<T>> opener;

	private final Consumer<T> onMessage;

	private final ScheduledExecutorService scheduler;

	private final Duration initialBackoff;

	private final Duration maxBackoff;

	private final Duration rotateAfter;

	private volatile Duration nextBackoff;

	private volatile boolean stopped;

	private volatile Context.CancellableContext context;

	private volatile ScheduledFuture<?> rotation;

	public ResilientStream(String name, BiConsumer<Context.CancellableContext, StreamObserver<T>> opener,
			Consumer<T> onMessage, ScheduledExecutorService scheduler, Duration initialBackoff, Duration maxBackoff) {
		this(name, opener, onMessage, scheduler, initialBackoff, maxBackoff, Duration.ZERO);
	}

	public ResilientStream(String name, BiConsumer<Context.CancellableContext, StreamObserver<T>> opener,
			Consumer<T> onMessage, ScheduledExecutorService scheduler, Duration initialBackoff, Duration maxBackoff,
			Duration rotateAfter) {
		this.name = name;
		this.opener = opener;
		this.onMessage = onMessage;
		this.scheduler = scheduler;
		this.initialBackoff = initialBackoff;
		this.maxBackoff = maxBackoff;
		this.rotateAfter = rotateAfter == null ? Duration.ZERO : rotateAfter;
		this.nextBackoff = initialBackoff;
	}

	public void start() {
		open();
	}

	public void stop() {
		stopped = true;
		cancelRotation();
		Context.CancellableContext ctx = context;
		if (ctx != null) {
			ctx.cancel(null);
		}
	}

	private synchronized void open() {
		if (stopped) {
			return;
		}
		Context.CancellableContext ctx = Context.current().withCancellation();
		Instant openedAt = Instant.now();
		context = ctx;
		log.debug("Opening gRPC stream '{}'", name);
		try {
			opener.accept(ctx, new StreamObserver<>() {
				@Override
				public void onNext(T value) {
					if (context != ctx) {
						return; // superseded by a rotation: the new call relays from now on
					}
					nextBackoff = initialBackoff;
					try {
						onMessage.accept(value);
					}
					catch (RuntimeException ex) {
						log.warn("Error relaying '{}' message: {}", name, ex.getMessage());
					}
				}

				@Override
				public void onError(Throwable t) {
					ctx.cancel(null);
					if (context != ctx || stopped) {
						return;
					}
					Status status = Status.fromThrowable(t);
					log.info("gRPC stream '{}' closed: {} {}", name, status.getCode(),
							status.getDescription() == null ? "" : status.getDescription());
					scheduleReconnect(openedAt);
				}

				@Override
				public void onCompleted() {
					ctx.cancel(null);
					if (context != ctx || stopped) {
						return;
					}
					log.info("gRPC stream '{}' completed by server", name);
					scheduleReconnect(openedAt);
				}
			});
			scheduleRotation(ctx);
		}
		catch (RuntimeException ex) {
			log.warn("Could not open gRPC stream '{}': {}", name, ex.getMessage());
			scheduleReconnect(openedAt);
		}
	}

	private void scheduleRotation(Context.CancellableContext ctx) {
		cancelRotation();
		if (rotateAfter.isZero() || rotateAfter.isNegative()) {
			return;
		}
		rotation = scheduler.schedule(() -> rotate(ctx), rotateAfter.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void rotate(Context.CancellableContext old) {
		synchronized (this) {
			if (stopped || context != old) {
				return;
			}
			log.debug("Renewing gRPC stream '{}' before the proxy timeout", name);
			open();
		}
		scheduler.schedule(() -> old.cancel(null), ROTATION_OVERLAP.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void cancelRotation() {
		ScheduledFuture<?> r = rotation;
		if (r != null) {
			r.cancel(false);
		}
	}

	private void scheduleReconnect(Instant openedAt) {
		if (stopped) {
			return;
		}
		cancelRotation();
		if (Duration.between(openedAt, Instant.now()).compareTo(HEALTHY_LIFETIME) >= 0) {
			nextBackoff = initialBackoff;
		}
		Duration delay = nextBackoff;
		Duration doubled = delay.multipliedBy(2);
		nextBackoff = doubled.compareTo(maxBackoff) > 0 ? maxBackoff : doubled;
		log.info("Reconnecting gRPC stream '{}' in {} ms", name, delay.toMillis());
		scheduler.schedule(this::open, delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	Duration nextBackoff() {
		return nextBackoff;
	}

}
