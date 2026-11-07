// Smart Garden on Azure Container Apps (Consumption). One Container App per microservice, one environment.
// Deployed by infra/azure/provision.sh. Secrets arrive as @secure() parameters and become Container Apps secrets;
// they are never written to this file, to the scripts or to the logs.

targetScope = 'resourceGroup'

param location string = resourceGroup().location

@description('Prefix of the shared resources (environment, Log Analytics).')
param prefix string = 'smartgarden'

@description('ghcr.io/<owner> (lower case).')
param registry string

@description('Image tag (commit SHA) per service: { \'api-gateway\': \'sha\', ... , \'ai-scheduler\': \'sha\' }.')
param tags object

@description('Origins allowed by the api-gateway CORS, comma separated (the Vercel URL).')
param corsAllowedOrigins string

@description('Cron (UTC) of the scheduled AI analysis job. Default: every 60 minutes.')
param analysisCron string = '0 * * * *'

param cooldownSeconds int = 120

param geminiModel string = 'gemini-flash-latest'

param geminiFallbackModels string = 'gemini-flash-lite-latest'

param mqttUrl string

param mqttUsername string

param adminUsername string = 'admin'

@secure()
param internalToken string
@secure()
param jwtSecret string
@secure()
param adminPassword string
@secure()
param mqttPassword string
@secure()
param geminiApiKey string
@secure()
param weatherLat string
@secure()
param weatherLon string

@secure()
@description('Supabase credentials per service: { gateway: { host, user, password }, telemetry: {...}, irrigation: {...}, ai: {...} }.')
param db object

// ---------------------------------------------------------------- logs (minimal retention, daily cap)
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
    workspaceCapping: {
      // ~5 GB/month are free; cap ingestion so logs can never become a cost.
      dailyQuotaGb: json('0.15')
    }
  }
}

// ---------------------------------------------------------------- environment (Consumption only)
resource environment 'Microsoft.App/managedEnvironments@2025-07-01' = {
  name: '${prefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
    zoneRedundant: false
  }
}

// Inside the environment every app is reachable by its name over plain HTTP/2 on port 80.
var telemetryTarget = 'static://telemetry-service:80'
var irrigationTarget = 'static://irrigation-service:80'
var aiTarget = 'static://ai-service:80'

// Shared JVM flags for the 1 GiB containers (heap capped so metaspace, threads and buffers fit).
var javaOpts1g = '-Xms64m -Xmx600m -XX:MaxMetaspaceSize=200m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxDirectMemorySize=96m -XX:+ExitOnOutOfMemoryError'
var javaOpts512m = '-Xms64m -Xmx256m -XX:MaxMetaspaceSize=150m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxDirectMemorySize=48m -XX:+ExitOnOutOfMemoryError'

var commonEnv = [
  { name: 'SPRING_PROFILES_ACTIVE', value: 'docker' }
  { name: 'MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED', value: 'true' }
  { name: 'LOGGING_LEVEL_ROOT', value: 'WARN' }
  { name: 'LOGGING_LEVEL_COM_SMARTGARDEN', value: 'INFO' }
  { name: 'GARDEN_TIMEZONE', value: 'America/Bogota' }
]

// ---------------------------------------------------------------- api-gateway (public, scales to zero)
module gateway 'modules/service.bicep' = {
  name: 'api-gateway'
  params: {
    name: 'api-gateway'
    location: location
    environmentId: environment.id
    image: '${registry}/smart-garden-api-gateway:${tags['api-gateway']}'
    cpu: '0.5'
    memory: '1Gi'
    minReplicas: 0
    maxReplicas: 2
    cooldownPeriod: cooldownSeconds
    external: true
    targetPort: 8080
    transport: 'auto'
    managementPort: 8080
    concurrentRequests: 30
    env: concat(commonEnv, [
      { name: 'JAVA_OPTS', value: javaOpts1g }
      { name: 'TELEMETRY_GRPC_TARGET', value: telemetryTarget }
      { name: 'IRRIGATION_GRPC_TARGET', value: irrigationTarget }
      { name: 'AI_GRPC_TARGET', value: aiTarget }
      // Unary calls may have to wait for a cold start of irrigation-service or ai-service.
      { name: 'GRPC_DEADLINE', value: '90s' }
      { name: 'GRPC_STREAMS_ON_DEMAND', value: 'true' }
      { name: 'NOTIFY_AI_ON_CONFIG_CHANGE', value: 'true' }
      { name: 'CORS_ALLOWED_ORIGINS', value: corsAllowedOrigins }
      { name: 'ADMIN_USERNAME', value: adminUsername }
      { name: 'SPRINGDOC_ENABLED', value: 'false' }
      // Public app: expose only the health endpoint.
      { name: 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE', value: 'health' }
    ])
    secrets: {
      'internal-token': { env: 'INTERNAL_TOKEN', value: internalToken }
      'jwt-secret': { env: 'JWT_SECRET', value: jwtSecret }
      'admin-password': { env: 'ADMIN_PASSWORD', value: adminPassword }
      'db-host': { env: 'GATEWAY_DB_HOST', value: db.gateway.host }
      'db-user': { env: 'GATEWAY_DB_USER', value: db.gateway.user }
      'db-password': { env: 'GATEWAY_DB_PASSWORD', value: db.gateway.password }
    }
  }
}

// ---------------------------------------------------------------- telemetry-service (internal, ALWAYS ON)
// It is the MQTT consumer: it must stay connected to the broker to receive the ESP32 data in real time.
module telemetry 'modules/service.bicep' = {
  name: 'telemetry-service'
  params: {
    name: 'telemetry-service'
    location: location
    environmentId: environment.id
    image: '${registry}/smart-garden-telemetry-service:${tags['telemetry-service']}'
    cpu: '0.25'
    memory: '0.5Gi'
    minReplicas: 1
    maxReplicas: 1
    cooldownPeriod: 300
    external: false
    targetPort: 9091
    transport: 'http2'
    managementPort: 8081
    env: concat(commonEnv, [
      { name: 'JAVA_OPTS', value: javaOpts512m }
      { name: 'MQTT_URL', value: mqttUrl }
      { name: 'MQTT_USERNAME', value: mqttUsername }
      { name: 'MQTT_CLIENT_ID', value: 'smartgarden-backend-telemetry' }
      { name: 'MQTT_CLEAN_SESSION', value: 'false' }
    ])
    secrets: {
      'internal-token': { env: 'INTERNAL_TOKEN', value: internalToken }
      'mqtt-password': { env: 'MQTT_PASSWORD', value: mqttPassword }
      'db-host': { env: 'TELEMETRY_DB_HOST', value: db.telemetry.host }
      'db-user': { env: 'TELEMETRY_DB_USER', value: db.telemetry.user }
      'db-password': { env: 'TELEMETRY_DB_PASSWORD', value: db.telemetry.password }
    }
  }
}

// ---------------------------------------------------------------- irrigation-service (internal, scales to zero)
module irrigation 'modules/service.bicep' = {
  name: 'irrigation-service'
  params: {
    name: 'irrigation-service'
    location: location
    environmentId: environment.id
    image: '${registry}/smart-garden-irrigation-service:${tags['irrigation-service']}'
    cpu: '0.5'
    memory: '1Gi'
    minReplicas: 0
    maxReplicas: 1
    cooldownPeriod: cooldownSeconds
    external: false
    targetPort: 9092
    transport: 'http2'
    managementPort: 8082
    env: concat(commonEnv, [
      { name: 'JAVA_OPTS', value: javaOpts1g }
      { name: 'TELEMETRY_GRPC_TARGET', value: telemetryTarget }
    ])
    secrets: {
      'internal-token': { env: 'INTERNAL_TOKEN', value: internalToken }
      'db-host': { env: 'IRRIGATION_DB_HOST', value: db.irrigation.host }
      'db-user': { env: 'IRRIGATION_DB_USER', value: db.irrigation.user }
      'db-password': { env: 'IRRIGATION_DB_PASSWORD', value: db.irrigation.password }
    }
  }
}

// ---------------------------------------------------------------- ai-service (internal, scales to zero)
module ai 'modules/service.bicep' = {
  name: 'ai-service'
  params: {
    name: 'ai-service'
    location: location
    environmentId: environment.id
    image: '${registry}/smart-garden-ai-service:${tags['ai-service']}'
    cpu: '0.5'
    memory: '1Gi'
    minReplicas: 0
    maxReplicas: 1
    cooldownPeriod: cooldownSeconds
    external: false
    targetPort: 9093
    transport: 'http2'
    managementPort: 8083
    env: concat(commonEnv, [
      { name: 'JAVA_OPTS', value: javaOpts1g }
      { name: 'TELEMETRY_GRPC_TARGET', value: telemetryTarget }
      { name: 'IRRIGATION_GRPC_TARGET', value: irrigationTarget }
      // No @Scheduled and no config stream: the job and the gateway wake the service when needed.
      { name: 'AI_SCHEDULER_ENABLED', value: 'false' }
      { name: 'AI_CONFIG_WATCH_ENABLED', value: 'false' }
      // Calls to irrigation-service / telemetry-service may wait for a cold start.
      { name: 'AI_GRPC_DEADLINE', value: '60s' }
      { name: 'GEMINI_MODEL', value: geminiModel }
      { name: 'GEMINI_FALLBACK_MODELS', value: geminiFallbackModels }
    ])
    secrets: {
      'internal-token': { env: 'INTERNAL_TOKEN', value: internalToken }
      'gemini-api-key': { env: 'GEMINI_API_KEY', value: geminiApiKey }
      'weather-lat': { env: 'WEATHER_LAT', value: weatherLat }
      'weather-lon': { env: 'WEATHER_LON', value: weatherLon }
      'db-host': { env: 'AI_DB_HOST', value: db.ai.host }
      'db-user': { env: 'AI_DB_USER', value: db.ai.user }
      'db-password': { env: 'AI_DB_PASSWORD', value: db.ai.password }
    }
  }
}

// ---------------------------------------------------------------- scheduled AI analysis (Container Apps Job)
// Replaces @Scheduled: wakes ai-service with RunScheduledAnalysis, then the service goes back to sleep.
resource analysisJob 'Microsoft.App/jobs@2025-07-01' = {
  name: 'ai-scheduled-analysis'
  location: location
  properties: {
    environmentId: environment.id
    configuration: {
      triggerType: 'Schedule'
      scheduleTriggerConfig: {
        cronExpression: analysisCron
        parallelism: 1
        replicaCompletionCount: 1
      }
      // Cold start of ai-service + Gemini.
      replicaTimeout: 300
      replicaRetryLimit: 1
      secrets: [
        { name: 'internal-token', value: internalToken }
      ]
    }
    template: {
      containers: [
        {
          name: 'grpcurl'
          image: '${registry}/smart-garden-ai-scheduler:${tags['ai-scheduler']}'
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
          env: [
            { name: 'AI_TARGET', value: 'ai-service:80' }
            { name: 'INTERNAL_TOKEN', secretRef: 'internal-token' }
          ]
        }
      ]
    }
  }
  dependsOn: [ ai ]
}

output gatewayUrl string = 'https://${gateway.outputs.fqdn}'
output environmentDomain string = environment.properties.defaultDomain
