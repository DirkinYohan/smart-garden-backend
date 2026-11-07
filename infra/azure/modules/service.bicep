// One microservice = one Azure Container App (own image, scaling, secrets and revisions).

@description('Container App name; also the DNS name other apps use inside the environment.')
param name string

param location string

param environmentId string

@description('Full image reference, tagged with the commit SHA (never latest).')
param image string

@description('vCPU, e.g. \'0.25\' or \'0.5\' (string because Bicep has no decimals).')
param cpu string

@description('Memory matching the vCPU (0.25 -> 0.5Gi, 0.5 -> 1Gi).')
param memory string

param minReplicas int

param maxReplicas int

@description('Seconds without traffic before scaling in (and to zero).')
param cooldownPeriod int = 120

@description('true = public HTTPS ingress (only the api-gateway).')
param external bool

@description('Port the ingress forwards to (gRPC port for the internal services).')
param targetPort int

@allowed([ 'auto', 'http', 'http2' ])
param transport string

@description('Actuator port used by the startup / readiness / liveness probes.')
param managementPort int

@description('Concurrent requests per replica before the HTTP scaler adds a replica.')
param concurrentRequests int = 20

@description('Plain environment variables: [{ name, value }].')
param env array = []

@secure()
@description('Secrets of THIS app only: { \'secret-name\': { env: \'ENV_VAR\', value: \'...\' } }.')
param secrets object = {}

var secretList = [for s in items(secrets): {
  name: s.key
  value: s.value.value
}]

var secretEnv = [for s in items(secrets): {
  name: s.value.env
  secretRef: s.key
}]

resource app 'Microsoft.App/containerApps@2025-07-01' = {
  name: name
  location: location
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      secrets: secretList
      ingress: {
        external: external
        targetPort: targetPort
        transport: transport
        // Inside the environment the internal apps are called over plain HTTP/2 (h2c) on port 80.
        allowInsecure: !external
        traffic: [
          {
            latestRevision: true
            weight: 100
          }
        ]
      }
    }
    template: {
      containers: [
        {
          name: name
          image: image
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          env: concat(env, secretEnv)
          probes: [
            {
              // Java cold start: allow up to 3 minutes before the container is declared failed.
              type: 'Startup'
              httpGet: {
                path: '/actuator/health/liveness'
                port: managementPort
              }
              initialDelaySeconds: 3
              periodSeconds: 2
              failureThreshold: 90
              timeoutSeconds: 3
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health/readiness'
                port: managementPort
              }
              periodSeconds: 10
              failureThreshold: 3
              timeoutSeconds: 3
            }
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: managementPort
              }
              periodSeconds: 30
              failureThreshold: 3
              timeoutSeconds: 5
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
        cooldownPeriod: cooldownPeriod
        pollingInterval: 15
        rules: [
          {
            name: 'http'
            http: {
              metadata: {
                concurrentRequests: string(concurrentRequests)
              }
            }
          }
        ]
      }
    }
  }
}

output fqdn string = app.properties.configuration.ingress.fqdn
