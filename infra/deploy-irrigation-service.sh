#!/usr/bin/env bash
# Manual deployment of ONLY irrigation-service (build, push to ghcr.io, update its Container App). Without CI.
exec "$(dirname "$0")/azure/aca.sh" deploy irrigation-service
