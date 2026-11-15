# GitHub Actions Azure Setup

This repository deploys the React frontend and Spring Boot API as one image to Azure Container Apps. PostgreSQL Flexible Server, Azure Cache for Redis, Azure Container Registry, Key Vault, and Log Analytics are provisioned by a separate workflow.

## 1. Configure Azure OIDC

Prerequisites: Azure CLI, PowerShell 7 or Windows PowerShell, and an Azure account allowed to create managed identities, resource groups, and role assignments.

Run `az login` first, then run the bootstrap script from the repository root. It uses the currently selected Azure subscription, defaults the identity resource group to `rg-ecommerce-github-identity`, and defaults the app resource group to `rg-ecommerce-production`. Those names can be overridden with script parameters. The identity and app resource groups must remain different.

```powershell
.\.azure\setup-azure-auth-for-pipeline.ps1 `
  -Location "eastus2"
```

If the Git repository has an `origin` pointing to GitHub, the script reads `OWNER/REPOSITORY` from it. This checkout currently has no `origin`, so pass `-Repository "OWNER/REPOSITORY"` or enter it when prompted. The location must be an Azure region supported for the resources in your subscription; change `eastus2` if needed.

The script creates a user-assigned managed identity in the separate identity resource group, adds a GitHub federated credential for `repo:OWNER/REPOSITORY:environment:production`, and grants the identity `Contributor` and `User Access Administrator` on the application resource group. The latter is needed because the Bicep deployment creates scoped role assignments. The Azure account running the script must have permission to grant these roles. Azure login credentials are handled by `az login`; no client secret is created or entered.

Copy the printed client ID, tenant ID, subscription ID, and principal ID into the GitHub variables below. Do not create a client secret; Actions authenticates with OIDC.

## 2. Create the GitHub Environment

In repository Settings > Environments, create `production`. Add required reviewers and prevent self-review if that policy is available for the repository. Both infrastructure and application deployments target this environment and use its approval rules.

Add these environment variables:

| Variable | Value |
| --- | --- |
| `AZURE_CLIENT_ID` | Client ID printed by the bootstrap script |
| `AZURE_TENANT_ID` | Tenant ID printed by the bootstrap script |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID used for the bootstrap script |
| `AZURE_PIPELINE_IDENTITY_PRINCIPAL_ID` | Principal ID printed by the bootstrap script |
| `AZURE_RESOURCE_GROUP` | Application resource group name |
| `AZURE_LOCATION` | Azure region selected for the deployment |
| `AZURE_DEPLOYMENT_NAME` | Stable ARM deployment name, for example `ecommerce-production` |
| `AZURE_CONTAINER_REGISTRY_NAME` | `registryName` output from the infrastructure deployment |
| `AZURE_CONTAINER_APP_NAME` | `containerAppName` output from the infrastructure deployment |
| `AZURE_IMAGE_NAME` | Image repository name, for example `ecommerce-api` |
| `POSTGRES_ADMIN_USERNAME` | PostgreSQL administrator login name |
| `ACR_SKU_NAME` | Supported Container Registry SKU, such as `Basic` |
| `POSTGRES_SKU_NAME` | Region-supported PostgreSQL compute SKU |
| `POSTGRES_SKU_TIER` | PostgreSQL tier matching the selected SKU |
| `REDIS_SKU_NAME` | Supported Azure Cache for Redis SKU |
| `REDIS_SKU_FAMILY` | Redis SKU family matching the selected SKU |
| `REDIS_SKU_CAPACITY` | Redis capacity matching the selected SKU; defaults to `1` if omitted |

Add these environment secrets:

| Secret | Value |
| --- | --- |
| `POSTGRES_ADMIN_PASSWORD` | Strong PostgreSQL administrator password |
| `JWT_SECRET` | Random signing key of at least 32 bytes |

Choose region and SKUs supported by the target subscription. The checked-in `infra/main.parameters.json` contains placeholders and is not sufficient by itself; the infrastructure workflow supplies these environment values as parameter overrides.

## 3. Provision Azure Resources

Run Actions > Infrastructure > Run workflow and approve the `production` environment deployment. The target resource group must already exist; the bootstrap script creates it using your signed-in Azure account. The workflow identity is intentionally scoped to that group and does not have subscription-level permission to create resource groups. The workflow deploys `infra/main.bicep`; its outputs include the ACR and Container App names. Add those output values to `AZURE_CONTAINER_REGISTRY_NAME` and `AZURE_CONTAINER_APP_NAME` in the same GitHub environment before deploying the application.

## 4. Deploy the Application

Merge or push to `master`. The CI job runs Maven tests (excluding the one-off `SchemaExportTest`), frontend lint, and the Vite production build. Pull requests run validation only and never deploy. After validation, the production deployment builds and pushes the image to ACR and updates the Container App.

Protect `master` with a required status check for `Build and test`. The production GitHub environment approval is required for every infrastructure or application deployment.