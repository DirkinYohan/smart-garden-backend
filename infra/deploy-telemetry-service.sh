#!/usr/bin/env bash
# Manual deployment of ONLY telemetry-service (build, push to ghcr.io, update its Container App). Without CI.
exec "$(dirname "$0")/azure/aca.sh" deploy telemetry-service
