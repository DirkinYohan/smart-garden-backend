# syntax=docker/dockerfile:1
# Image of the "ai-scheduled-analysis" Container Apps Job. Build context: smart-garden-backend/.
# grpcurl + the .proto contracts (ai-service has no gRPC reflection). It calls AiService/RunScheduledAnalysis over
# the internal network of the environment and exits; the job fails when the call fails.
FROM fullstorydev/grpcurl:v1.9.3-alpine
COPY grpc-contracts/src/main/protobuf /protos
COPY infra/azure/scheduler/run.sh /run.sh
ENTRYPOINT ["/bin/sh", "/run.sh"]
