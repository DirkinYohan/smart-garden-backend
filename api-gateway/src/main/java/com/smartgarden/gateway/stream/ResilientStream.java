package com.smartgarden.gateway.stream;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResilientStream<T> {

	private static final Logger log = LoggerFactory.getLogger(ResilientStream.class);

	private final String name;

	private final BiConsumer<Context.CancellableContext, StreamObserver<T>> opener;

	private final Consumer<T> onMessage;

	private final ScheduledExecutorService scheduler;

	private final Duration initialBackoff;

	private final Duration maxBackoff;

	private volatile Duration nextBackoff;

	private volatile boolean stopped;

	private volatile Context.CancellableContext context;

	public ResilientStream(String name, BiConsumer<Context.CancellableContext, StreamObserver<T>> opener,
			Consumer<T> onMessage, ScheduledExecutorService scheduler, Duration initialBackoff, Duration maxBackoff) {
		this.name = name;
		this.opener = opener;
		this.onMessage = onMessage;
		this.scheduler = scheduler;
		this.initialBackoff = initialBackoff;
		this.maxBackoff = maxBackoff;
		this.nextBackoff = initialBackoff;
	}

	public void start() {
		open();
	}

	public void stop() {
		stopped = true;
		Context.CancellableContext ctx = context;
		if (ctx != null) {
			ctx.cancel(null);
		}
	}

	private void open() {
		if (stopped) {
			return;
		}
		Context.CancellableContext ctx = Context.current().withCancellation();
		context = ctx;
		log.info("Opening gRPC stream '{}'", name);
		try {
			opener.accept(ctx, new StreamObserver<>() {
				@Override
				public void onNext(T value) {
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
					Status status = Status.fromThrowable(t);
					if (!stopped) {
						log.warn("gRPC stream '{}' broken: {} {}", name, status.getCode(),
								status.getDescription() == null ? "" : status.getDescription());
					}
					scheduleReconnect();
				}

				@Override
				public void onCompleted() {
					ctx.cancel(null);
					log.info("gRPC stream '{}' completed by server", name);
					scheduleReconnect();
				}
			});
		}
		catch (RuntimeException ex) {
			log.warn("Could not open gRPC stream '{}': {}", name, ex.getMessage());
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (stopped) {
			return;
		}
		Duration delay = nextBackoff;
		Duration doubled = delay.multipliedBy(2);
		nextBackoff = doubled.compareTo(maxBackoff) > 0 ? maxBackoff : doubled;
		log.info("Reconnecting gRPC stream '{}' in {} s", name, delay.toSeconds());
		scheduler.schedule(this::open, delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	Duration nextBackoff() {
		return nextBackoff;
	}

}
