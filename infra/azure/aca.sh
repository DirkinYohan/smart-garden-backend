#!/usr/bin/env bash
# Smart Garden on Azure Container Apps (Consumption): one Container App per microservice, scale to zero.
#
# Usage (Git Bash, from anywhere):  infra/azure/aca.sh <command>
#   check                 az / docker / gh sessions and the variables required in smart-garden-backend/.env
#   login-registry        docker login ghcr.io with the gh session
#   build <svc|all>       builds and pushes ghcr.io/<owner>/smart-garden-<svc>:<git sha>
#   provision             resource group + Log Analytics + environment + 4 apps + job (main.bicep)
#   deploy <svc>          build + push + update ONLY that Container App (independent deployment)
#   status                replicas, revisions and URL of every app
#   logs <svc> [n]        last n log lines of an app (console logs, Log Analytics)
#   run-job               starts the scheduled analysis job now
#   budget [usd]          monthly budget alert (default 5 USD) sent to the subscription owner's e-mail
#   (firmware: credentials go in firmware-smart-garden/smart_garden_esp32/secrets.h, see secrets.example.h)
#   destroy               deletes the resource group (asks for confirmation)
#
# Services: api-gateway telemetry-service irrigation-service ai-service ai-scheduler
# Secrets are read from smart-garden-backend/.env and only passed to Azure through a temporary 0600 parameters file
# that is deleted right after the deployment. Nothing secret is printed.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$(cd "$HERE/../.." && pwd)"
ROOT="$(cd "$BACKEND/.." && pwd)"
SECRETS="$HERE/.secrets"

RG="${RG:-smart-garden-aca-rg}"
LOCATION="${LOCATION:-eastus}"
OWNER="${GHCR_OWNER:-dirkinyohan}"
REGISTRY="ghcr.io/$OWNER"
TAG="${TAG:-$(cd "$BACKEND" && git rev-parse HEAD)}"
# az receives /subscriptions/... scopes: stop Git Bash from rewriting them as Windows paths.
export MSYS_NO_PATHCONV=1
SERVICES=(api-gateway telemetry-service irrigation-service ai-service ai-scheduler)

export PATH="$PATH:/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:/c/Program Files/GitHub CLI"

log() { printf '\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
env_get() { grep -E "^$1=" "$BACKEND/.env" | tail -1 | cut -d= -f2- | tr -d '\r' || true; }
is_service() { local s; for s in "${SERVICES[@]}"; do [[ "$s" == "$1" ]] && return 0; done; return 1; }

dockerfile_of() {
	case "$1" in
		ai-scheduler) echo "infra/azure/scheduler/Dockerfile" ;;
		*) echo "$1/Dockerfile" ;;
	esac
}

image_of() { echo "$REGISTRY/smart-garden-$1:${2:-$TAG}"; }

REQUIRED=(GATEWAY_DB_HOST GATEWAY_DB_USER GATEWAY_DB_PASSWORD TELEMETRY_DB_HOST TELEMETRY_DB_USER TELEMETRY_DB_PASSWORD
	IRRIGATION_DB_HOST IRRIGATION_DB_USER IRRIGATION_DB_PASSWORD AI_DB_HOST AI_DB_USER AI_DB_PASSWORD
	GEMINI_API_KEY JWT_SECRET ADMIN_PASSWORD MQTT_URL MQTT_USERNAME MQTT_PASSWORD)

cmd_check() {
	az account show --query "{subscription:name, state:state}" -o table || die "run: az login --use-device-code"
	docker info --format '{{.ServerVersion}}' >/dev/null || die "Docker Desktop is not running"
	gh auth status >/dev/null 2>&1 || die "run: gh auth login --web --scopes write:packages,workflow"
	local missing=() k
	for k in "${REQUIRED[@]}"; do [[ -n "$(env_get "$k")" ]] || missing+=("$k"); done
	((${#missing[@]} == 0)) || die "missing in .env: ${missing[*]}"
	log "OK: Azure, Docker, GitHub and .env are ready (tag $TAG)"
}

cmd_login_registry() {
	gh auth token | docker login ghcr.io -u "$(gh api user -q .login)" --password-stdin >/dev/null
	log "docker is logged in to ghcr.io"
}

build_one() {
	local svc="$1" image; image="$(image_of "$svc")"
	log "build + push $image"
	docker buildx build --platform linux/amd64 -f "$BACKEND/$(dockerfile_of "$svc")" \
		--label "org.opencontainers.image.source=https://github.com/DirkinYohan/smart-garden-backend" \
		--label "org.opencontainers.image.revision=$TAG" \
		-t "$image" --push "$BACKEND"
}

cmd_build() {
	local target="${1:?service or all}" s
	if [[ "$target" == all ]]; then
		for s in "${SERVICES[@]}"; do build_one "$s"; done
	else
		is_service "$target" || die "unknown service: $target"
		build_one "$target"
	fi
}

internal_token() {
	local t; t="$(env_get INTERNAL_TOKEN)"
	if [[ -z "$t" ]]; then
		mkdir -p "$SECRETS"
		if [[ ! -s "$SECRETS/internal-token" ]]; then
			(umask 077; openssl rand -hex 32 > "$SECRETS/internal-token")
		fi
		t="$(cat "$SECRETS/internal-token")"
	fi
	printf '%s' "$t"
}

# Writes the secure parameters to a 0600 file (deleted by the caller). Values never reach stdout.
write_params() {
	local file="$1" cors="$2"
	(umask 077; python - "$(cygpath -w "$file")" "$(cygpath -w "$BACKEND/.env")" <<'PY'
import json, os, sys
e = {}
with open(sys.argv[2], encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\r\n")
        if line and not line.lstrip().startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            e[k.strip()] = v
e.update({k: v for k, v in os.environ.items() if k.startswith("P_")})
def g(k, d=""):
    return e.get(k, d)
p = {
  "registry": g("P_REGISTRY"), "tags": json.loads(g("P_TAGS")), "corsAllowedOrigins": g("P_CORS"),
  "mqttUrl": g("MQTT_URL"), "mqttUsername": g("MQTT_USERNAME"),
  "geminiModel": g("GEMINI_MODEL") or "gemini-flash-latest",
  "geminiFallbackModels": g("GEMINI_FALLBACK_MODELS") or "gemini-flash-lite-latest",
  "adminUsername": g("ADMIN_USERNAME") or "admin",
  "internalToken": g("P_INTERNAL_TOKEN"), "jwtSecret": g("JWT_SECRET"), "adminPassword": g("ADMIN_PASSWORD"),
  "mqttPassword": g("MQTT_PASSWORD"), "geminiApiKey": g("GEMINI_API_KEY"),
  "weatherLat": g("WEATHER_LAT") or "4.711", "weatherLon": g("WEATHER_LON") or "-74.0721",
  "db": {s: {"host": g(f"{p}_DB_HOST"), "user": g(f"{p}_DB_USER"), "password": g(f"{p}_DB_PASSWORD")}
         for s, p in (("gateway", "GATEWAY"), ("telemetry", "TELEMETRY"), ("irrigation", "IRRIGATION"), ("ai", "AI"))},
}
doc = {"$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#",
       "contentVersion": "1.0.0.0", "parameters": {k: {"value": v} for k, v in p.items()}}
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(doc, f)
PY
	)
}

# Image tag currently running in an app (keeps the other services untouched on a re-provision).
current_tag() {
	local svc="$1" img
	if [[ "$svc" == ai-scheduler ]]; then
		img="$(az containerapp job show -g "$RG" -n ai-scheduled-analysis --query "properties.template.containers[0].image" -o tsv 2>/dev/null || true)"
	else
		img="$(az containerapp show -g "$RG" -n "$svc" --query "properties.template.containers[0].image" -o tsv 2>/dev/null || true)"
	fi
	[[ -n "$img" ]] && echo "${img##*:}" || echo "$TAG"
}

cmd_provision() {
	cmd_check
	local cors="${CORS_ALLOWED_ORIGINS_PROD:-$(cat "$HERE/.cors" 2>/dev/null || echo "http://localhost:4200")}"
	log "resource group $RG ($LOCATION)"
	az group create -n "$RG" -l "$LOCATION" -o none
	local tags="{" s first=1
	for s in "${SERVICES[@]}"; do
		((first)) || tags+=","
		tags+="\"$s\":\"$(current_tag "$s")\""; first=0
	done
	tags+="}"
	mkdir -p "$SECRETS"
	PARAMS_FILE="$SECRETS/params.json"; local params="$PARAMS_FILE"
	trap 'rm -f "${PARAMS_FILE:-}"' EXIT
	P_REGISTRY="$REGISTRY" P_TAGS="$tags" P_CORS="$cors" P_INTERNAL_TOKEN="$(internal_token)" write_params "$params" "$cors"
	log "deploying main.bicep (tags: $tags) – takes a few minutes"
	az deployment group create -g "$RG" -n "smartgarden-$(date +%Y%m%d%H%M%S)" \
		-f "$(cygpath -w "$HERE/main.bicep")" -p "@$(cygpath -w "$params")" --query "properties.outputs" -o json
	rm -f "$params"
	trap - EXIT
}

cmd_deploy() {
	local svc="${1:?service}"
	is_service "$svc" || die "unknown service: $svc"
	build_one "$svc"
	log "updating ONLY $svc to $(image_of "$svc")"
	if [[ "$svc" == ai-scheduler ]]; then
		az containerapp job update -g "$RG" -n ai-scheduled-analysis --image "$(image_of "$svc")" -o none
	else
		az containerapp update -g "$RG" -n "$svc" --image "$(image_of "$svc")" \
			--query "properties.latestRevisionName" -o tsv
	fi
}

cmd_status() {
	az containerapp list -g "$RG" --query "[].{app:name, replicasMin:properties.template.scale.minReplicas, image:properties.template.containers[0].image, ingress:properties.configuration.ingress.external, fqdn:properties.configuration.ingress.fqdn}" -o table
	local s
	for s in api-gateway telemetry-service irrigation-service ai-service; do
		printf '%-20s replicas now: %s\n' "$s" \
			"$(az containerapp replica list -g "$RG" -n "$s" --query "length(@)" -o tsv 2>/dev/null || echo "?")"
	done
	az containerapp job execution list -g "$RG" -n ai-scheduled-analysis \
		--query "[0:3].{run:name, status:properties.status, start:properties.startTime}" -o table 2>/dev/null || true
}

cmd_logs() {
	local svc="${1:?service}"
	az containerapp logs show -g "$RG" -n "$svc" --tail "${2:-80}" --format text
}

cmd_run_job() { az containerapp job start -g "$RG" -n ai-scheduled-analysis --query name -o tsv; }

cmd_budget() {
	local amount="${1:-5}" email
	email="$(az account show --query user.name -o tsv)"
	local sub; sub="$(az account show --query id -o tsv)"
	local start; start="$(date -u +%Y-%m-01)"
	log "budget of $amount USD/month (alerts at 50%, 80% and 100%) -> $email"
	az rest --method put \
		--url "https://management.azure.com/subscriptions/$sub/providers/Microsoft.Consumption/budgets/smart-garden-monthly?api-version=2023-05-01" \
		--body "{\"properties\":{\"category\":\"Cost\",\"amount\":$amount,\"timeGrain\":\"Monthly\",\"timePeriod\":{\"startDate\":\"${start}T00:00:00Z\"},\"notifications\":{\"a50\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":50,\"contactEmails\":[\"$email\"]},\"a80\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":80,\"contactEmails\":[\"$email\"]},\"f100\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":100,\"thresholdType\":\"Forecasted\",\"contactEmails\":[\"$email\"]}}}}" \
		--query "properties.amount" -o tsv
}

cmd_destroy() {
	read -r -p "Delete resource group $RG and everything in it? Type the name to confirm: " answer
	[[ "$answer" == "$RG" ]] || die "cancelled"
	az group delete -n "$RG" --yes --no-wait
	log "deletion started"
}

case "${1:-}" in
	check) cmd_check ;;
	login-registry) cmd_login_registry ;;
	build) shift; cmd_build "$@" ;;
	provision) cmd_provision ;;
	deploy) shift; cmd_deploy "$@" ;;
	status) cmd_status ;;
	logs) shift; cmd_logs "$@" ;;
	run-job) cmd_run_job ;;
	budget) shift; cmd_budget "$@" ;;
	destroy) cmd_destroy ;;
	*) sed -n '2,19p' "$0"; exit 1 ;;
esac
