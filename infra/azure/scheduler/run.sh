#!/bin/sh
# Calls RunScheduledAnalysis on ai-service. AI_TARGET defaults to the internal name of the app (h2c on port 80).
set -eu
TARGET="${AI_TARGET:-ai-service:80}"
echo "RunScheduledAnalysis -> ${TARGET}"
exec grpcurl -plaintext -max-time 280 -connect-timeout 240 \
  -import-path /protos -proto smartgarden/ai/v1/ai.proto \
  -rpc-header "x-internal-token: ${INTERNAL_TOKEN:-}" \
  -d '{}' "${TARGET}" smartgarden.ai.v1.AiService/RunScheduledAnalysis
