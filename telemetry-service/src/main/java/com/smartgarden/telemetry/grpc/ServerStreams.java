package com.smartgarden.telemetry.grpc;

import com.smartgarden.telemetry.service.Subscribers;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.function.Function;

/**
 * Binds a gRPC server-streaming call to an in-memory {@link Subscribers} until the client cancels.
 */
final class ServerStreams {

	private ServerStreams() {
	}

	static <T, P> void bind(StreamObserver<P> observer, Subscribers<T> subscribers, Function<T, P> mapper) {
		ServerCallStreamObserver<P> call = (ServerCallStreamObserver<P>) observer;
		Runnable[] unsubscribe = new Runnable[1];
		unsubscribe[0] = subscribers.subscribe(value -> {
			// StreamObserver is not thread-safe.
			synchronized (call) {
				if (call.isCancelled()) {
					unsubscribe[0].run();
					return;
				}
				call.onNext(mapper.apply(value));
			}
		});
		call.setOnCancelHandler(() -> unsubscribe[0].run());
	}

}
