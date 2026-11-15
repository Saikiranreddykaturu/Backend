targetScope = 'resourceGroup'

param environmentName string
param location string
param pipelineIdentityPrincipalId string
param postgresAdminUsername string
@secure()
param postgresAdminPassword string
@secure()
param jwtSecret string
param acrSkuName string
param postgresSkuName string
param postgresSkuTier string
param redisSkuName string
param redisSkuFamily string
param redisSkuCapacity int

var resourceToken = uniqueString(subscription().id, resourceGroup().id, location, environmentName)
var databaseName = 'azdb${resourceToken}'
var acrPushRoleId = '8311e382-0749-4cb8-b61a-304f252e45ec'
var acrPullRoleId = '7f951dda-4ed3-4687-a7ca-43fe172d538d'
var keyVaultSecretsOfficerRoleId = 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7'

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'azla${resourceToken}'
  location: location
  properties: {
    retentionInDays: 30
    sku: {
      name: 'PerGB2018'
    }
  }
}

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: 'azenv${resourceToken}'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
  }
}

resource appIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'azid${resourceToken}'
  location: location
}

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: 'azacr${resourceToken}'
  location: location
  sku: {
    name: acrSkuName
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
  }
}

resource registryPullAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, appIdentity.id, acrPullRoleId)
  scope: registry
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: appIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource registryPushAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, pipelineIdentityPrincipalId, acrPushRoleId)
  scope: registry
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPushRoleId)
    principalId: pipelineIdentityPrincipalId
    principalType: 'ServicePrincipal'
  }
}

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: 'azpg${resourceToken}'
  location: location
  sku: {
    name: postgresSkuName
    tier: postgresSkuTier
  }
  properties: {
    administratorLogin: postgresAdminUsername
    administratorLoginPassword: postgresAdminPassword
    version: '17'
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource postgresDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: databaseName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource postgresAzureServicesFirewall 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'azfw${resourceToken}'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource redis 'Microsoft.Cache/redis@2024-11-01' = {
  name: 'azrd${resourceToken}'
  location: location
  properties: {
    sku: {
      name: redisSkuName
      family: redisSkuFamily
      capacity: redisSkuCapacity
    }
    enableNonSslPort: false
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
    redisConfiguration: {
      'maxmemory-policy': 'allkeys-lru'
    }
  }
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'azkv${resourceToken}'
  location: location
  properties: {
    tenantId: tenant().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    publicNetworkAccess: 'Enabled'
  }
}

resource keyVaultSecretsOfficerAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, appIdentity.id, keyVaultSecretsOfficerRoleId)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', keyVaultSecretsOfficerRoleId)
    principalId: appIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource databaseUrlSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'azurl${resourceToken}'
  properties: {
    value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${databaseName}?sslmode=require'
  }
}

resource databaseUsernameSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'azusr${resourceToken}'
  properties: {
    value: postgresAdminUsername
  }
}

resource databasePasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'azpwd${resourceToken}'
  properties: {
    value: postgresAdminPassword
  }
}

resource jwtSecretValue 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'azjwt${resourceToken}'
  properties: {
    value: jwtSecret
  }
}

resource redisPasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'azrps${resourceToken}'
  properties: {
    value: redis.listKeys().primaryKey
  }
}

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'azapp${resourceToken}'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${appIdentity.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: [
        {
          server: registry.properties.loginServer
          identity: appIdentity.id
        }
      ]
      secrets: [
        {
          name: 'db-url'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/${databaseUrlSecret.name}'
          identity: appIdentity.id
        }
        {
          name: 'db-username'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/${databaseUsernameSecret.name}'
          identity: appIdentity.id
        }
        {
          name: 'db-password'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/${databasePasswordSecret.name}'
          identity: appIdentity.id
        }
        {
          name: 'jwt-secret'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/${jwtSecretValue.name}'
          identity: appIdentity.id
        }
        {
          name: 'redis-password'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/${redisPasswordSecret.name}'
          identity: appIdentity.id
        }
      ]
      ingress: {
        external: true
        targetPort: 80
        transport: 'auto'
        corsPolicy: {
          allowedOrigins: ['http://localhost:5173']
          allowedMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS']
          allowedHeaders: ['authorization', 'content-type']
          allowCredentials: true
          maxAge: 3600
        }
      }
    }
    template: {
      containers: [
        {
          name: 'app'
          image: 'mcr.microsoft.com/azuredocs/containerapps-helloworld:latest'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            {
              name: 'DB_URL'
              secretRef: 'db-url'
            }
            {
              name: 'DB_USERNAME'
              secretRef: 'db-username'
            }
            {
              name: 'DB_PASSWORD'
              secretRef: 'db-password'
            }
            {
              name: 'JWT_SECRET'
              secretRef: 'jwt-secret'
            }
            {
              name: 'REDIS_HOST'
              value: redis.properties.hostName
            }
            {
              name: 'REDIS_PORT'
              value: '6380'
            }
            {
              name: 'REDIS_SSL_ENABLED'
              value: 'true'
            }
            {
              name: 'REDIS_PASSWORD'
              secretRef: 'redis-password'
            }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
  dependsOn: [
    registryPullAssignment
    keyVaultSecretsOfficerAssignment
    registryPushAssignment
    postgresDatabase
    postgresAzureServicesFirewall
  ]
}

output registryName string = registry.name
output containerAppName string = app.name
output databaseName string = databaseName
output postgresServerName string = postgres.name