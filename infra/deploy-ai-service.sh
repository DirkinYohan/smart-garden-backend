#!/usr/bin/env bash
# Manual deployment of ONLY ai-service (build, push to ghcr.io, update its Container App). Without CI.
exec "$(dirname "$0")/azure/aca.sh" deploy ai-service
