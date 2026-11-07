#!/usr/bin/env bash
# Manual deployment of ONLY api-gateway (build, push to ghcr.io, update its Container App). Without CI.
exec "$(dirname "$0")/azure/aca.sh" deploy api-gateway
