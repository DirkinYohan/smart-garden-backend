package com.smartgarden.irrigation.grpc;

import com.smartgarden.irrigation.service.Subscribers;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.function.Function;

final class ServerStreams {

	private ServerStreams() {
	}

	static <T, P> void bind(StreamObserver<P> observer, Subscribers<T> subscribers, Function<T, P> mapper) {
		ServerCallStreamObserver<P> call = (ServerCallStreamObserver<P>) observer;
		Runnable[] unsubscribe = new Runnable[1];
		unsubscribe[0] = subscribers.subscribe(value -> {

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
