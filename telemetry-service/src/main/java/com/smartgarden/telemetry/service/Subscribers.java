package com.smartgarden.telemetry.service;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal in-memory fan-out used to feed the gRPC server streams. A failing subscriber is removed.
 */
public class Subscribers<T> {

	private static final Logger log = LoggerFactory.getLogger(Subscribers.class);

	private final Set<Consumer<T>> consumers = new CopyOnWriteArraySet<>();

	public Runnable subscribe(Consumer<T> consumer) {
		consumers.add(consumer);
		return () -> consumers.remove(consumer);
	}

	public void publish(T value) {
		for (Consumer<T> consumer : consumers) {
			try {
				consumer.accept(value);
			}
			catch (RuntimeException ex) {
				log.debug("Removing failed subscriber: {}", ex.getMessage());
				consumers.remove(consumer);
			}
		}
	}

	public int size() {
		return consumers.size();
	}

}
