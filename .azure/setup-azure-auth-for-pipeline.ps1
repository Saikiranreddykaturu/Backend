param(
    [string]$Repository,

    [string]$SubscriptionId,

    [string]$IdentityResourceGroup = 'backend',

    [string]$TargetResourceGroup = 'prod',

    [Parameter(Mandatory = $true)]
    [string]$Location,

    [string]$IdentityName = 'gh-production-deployer'
)

$ErrorActionPreference = 'Stop'

if (-not $Repository -and $env:GITHUB_REPOSITORY) {
    $Repository = $env:GITHUB_REPOSITORY
}

if (-not $Repository) {
    $originUrl = git remote get-url origin 2>$null
    if ($LASTEXITCODE -eq 0 -and $originUrl -match 'github\.com[:/](?<owner>[^/]+)/(?<repo>[^/]+?)(?:\.git)?$') {
        $Repository = "$($Matches.owner)/$($Matches.repo)"
    }
}

if (-not $Repository) {
    $Repository = Read-Host 'GitHub repository in OWNER/REPOSITORY format'
}

if ($Repository -notmatch '^[^/]+/[^/]+$') {
    throw 'Repository must use OWNER/REPOSITORY format.'
}

if (-not $SubscriptionId) {
    $SubscriptionId = (& az account show --query id --output tsv).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $SubscriptionId) {
        throw 'Sign in with az login and select a subscription, or pass -SubscriptionId explicitly.'
    }
}

if ($IdentityResourceGroup -eq $TargetResourceGroup) {
    throw 'The pipeline identity must be created in a separate resource group.'
}

function Invoke-Az {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & az @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Azure CLI command failed: az $($Arguments -join ' ')"
    }
}

Invoke-Az account set --subscription $SubscriptionId
Invoke-Az group show --name $IdentityResourceGroup --output none
Invoke-Az group show --name $TargetResourceGroup --output none

$identityJson = & az identity create `
    --name $IdentityName `
    --resource-group $IdentityResourceGroup `
    --location $Location `
    --output json
if ($LASTEXITCODE -ne 0) {
    throw 'Could not create the user-assigned managed identity.'
}
$identity = $identityJson | ConvertFrom-Json

$subject = "repo:${Repository}:environment:production"
Invoke-Az identity federated-credential create `
    --name github-production `
    --identity-name $IdentityName `
    --resource-group $IdentityResourceGroup `
    --issuer 'https://token.actions.githubusercontent.com' `
    --subject $subject `
    --audiences 'api://AzureADTokenExchange' `
    --output none

$targetScope = (az group show --name $TargetResourceGroup --query id --output tsv).Trim()
Invoke-Az role assignment create `
    --assignee-object-id $identity.principalId `
    --assignee-principal-type ServicePrincipal `
    --role Contributor `
    --scope $targetScope `
    --output none
Invoke-Az role assignment create `
    --assignee-object-id $identity.principalId `
    --assignee-principal-type ServicePrincipal `
    --role 'User Access Administrator' `
    --scope $targetScope `
    --output none

$tenantId = (az account show --query tenantId --output tsv).Trim()
Write-Output "AZURE_CLIENT_ID=$($identity.clientId)"
Write-Output "AZURE_TENANT_ID=$tenantId"
Write-Output "AZURE_SUBSCRIPTION_ID=$SubscriptionId"
Write-Output "AZURE_PIPELINE_IDENTITY_PRINCIPAL_ID=$($identity.principalId)"