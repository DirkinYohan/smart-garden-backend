package com.smartgarden.ai.grpc;

import com.smartgarden.ai.service.AnalysisService;
import com.smartgarden.ai.service.StatsService;
import com.smartgarden.contracts.ai.v1.GetStatsRequest;
import com.smartgarden.contracts.ai.v1.Stats;
import com.smartgarden.ai.service.WeatherService;
import com.smartgarden.contracts.ai.v1.AiServiceGrpc;
import com.smartgarden.contracts.ai.v1.Analysis;
import com.smartgarden.contracts.ai.v1.GetLatestAnalysisRequest;
import com.smartgarden.contracts.ai.v1.GetWeatherRequest;
import com.smartgarden.contracts.ai.v1.ListAnalysesRequest;
import com.smartgarden.contracts.ai.v1.ListAnalysesResponse;
import com.smartgarden.contracts.ai.v1.RequestAnalysisRequest;
import com.smartgarden.contracts.ai.v1.StreamAnalysesRequest;
import com.smartgarden.contracts.ai.v1.Weather;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

/**
 * gRPC adapter. Exceptions are translated to gRPC status codes by {@link GrpcErrorHandler}.
 */
@Service
public class AiGrpcService extends AiServiceGrpc.AiServiceImplBase {

	private static final int DEFAULT_LIMIT = 10;

	private static final int MAX_LIMIT = 100;

	private final AnalysisService analyses;

	private final WeatherService weather;

	private final StatsService stats;

	public AiGrpcService(AnalysisService analyses, WeatherService weather, StatsService stats) {
		this.analyses = analyses;
		this.weather = weather;
		this.stats = stats;
	}

	@Override
	public void getLatestAnalysis(GetLatestAnalysisRequest request, StreamObserver<Analysis> responseObserver) {
		responseObserver.onNext(ProtoMapper.analysis(analyses.latest()));
		responseObserver.onCompleted();
	}

	@Override
	public void listAnalyses(ListAnalysesRequest request, StreamObserver<ListAnalysesResponse> responseObserver) {
		int limit = request.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(request.getLimit(), MAX_LIMIT);
		ListAnalysesResponse.Builder response = ListAnalysesResponse.newBuilder();
		analyses.list(limit).forEach(a -> response.addAnalyses(ProtoMapper.analysis(a)));
		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void requestAnalysis(RequestAnalysisRequest request, StreamObserver<Analysis> responseObserver) {
		responseObserver.onNext(ProtoMapper.analysis(analyses.requestAnalysis()));
		responseObserver.onCompleted();
	}

	@Override
	public void getWeather(GetWeatherRequest request, StreamObserver<Weather> responseObserver) {
		responseObserver.onNext(ProtoMapper.weather(weather.current()));
		responseObserver.onCompleted();
	}

	@Override
	public void getStats(GetStatsRequest request, StreamObserver<Stats> responseObserver) {
		responseObserver.onNext(ProtoMapper.stats(stats.stats(request.getDays() == 0 ? 7 : request.getDays())));
		responseObserver.onCompleted();
	}

	@Override
	public void streamAnalyses(StreamAnalysesRequest request, StreamObserver<Analysis> responseObserver) {
		ServerStreams.bind(responseObserver, analyses.subscribers(), ProtoMapper::analysis);
	}

}
