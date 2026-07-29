# test-publish-data-protos-webflux

A minimal Spring Boot (v4) **WebFlux** application, built with Java 24 and Gradle, that
serves precomputed test data using the protobuf message types from
[`test-data-protos`](https://github.com/Subhajitdas298/test-data-protos) and publishes it
over a fully open (unauthenticated) reactive REST API — as raw protobuf binary or as JSON.

This is the reactive counterpart of
[`test-publish-data-protos`](https://github.com/Subhajitdas298/test-publish-data-protos):
same data, same API shape, same architecture — rebuilt end-to-end on Spring WebFlux /
Project Reactor instead of Spring MVC, with a fully non-blocking request path from the
controller down to the file read.

There is no database — the repository layer builds the dataset from a precomputed binary
array bundled as a resource (`src/main/resources/data/dataset.bin`), and every layer
(repository + both services) publishes a `Mono` built with Reactor's `.cache()` operator,
so the file is only read and the protobuf message only built once — on the first
subscription from either endpoint — and every subsequent request (and every concurrent
in-flight request) replays that cached signal instead of recomputing it.

## Architecture

- **`DataRepository`** (repository layer) — reads `data/dataset.bin` (2,600,000
  precomputed `double`s, stored as big-endian 8-byte values) into a `DoubleBuffer` and
  builds the raw `Root` protobuf message from it. The blocking file read and message
  construction is wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`
  so it never runs on a Netty event-loop thread, and the resulting `Mono<Root>` is built
  with `.cache()` so it is only ever computed once.
- **`ProtoDataService`** — maps the repository's `Mono<Root>` to the serialized protobuf
  bytes (`Mono<byte[]>`), itself cached.
- **`JsonDataService`** — maps the repository's `Mono<Root>` to its JSON representation
  (`Mono<String>`), itself cached.
- **`DataController`** — exposes both services on a single URL as reactive endpoints
  (`Mono<byte[]>` / `Mono<String>`), differentiated purely by the `Accept` header (HTTP
  content negotiation). Nothing in the request path blocks.

## Data shape

The dataset (a `Root` protobuf message) consists of:

- **10 days** of data (`DataEntry.dates`, one `DateRecord` per day)
- Each day has **26 fields** (`a`–`z`, matching the proto definition)
- Each field contains **10,000 precomputed `double` records**, read in order from
  `data/dataset.bin`

That's `10 * 26 * 10,000 = 2,600,000` values, read from the bundled file once and reused
for every request.

## API

There is a single endpoint. The representation is chosen purely by the `Accept` header
(standard HTTP content negotiation) — there is no separate path for JSON.

| Method | Path        | `Accept` header          | Response                                              |
|--------|-------------|---------------------------|--------------------------------------------------------|
| GET    | `/api/data` | `application/x-protobuf` | Raw protobuf binary — serialized bytes of the `Root` message. Decode with `Root.parseFrom(bytes)`. |
| GET    | `/api/data` | `application/json`       | The same dataset as JSON, using protobuf's standard JSON mapping (via `JsonFormat`). |

No authentication, no request parameters.

## Dependency on `test-data-protos`

This project depends on the Java package published from
[`Subhajitdas298/test-data-protos`](https://github.com/Subhajitdas298/test-data-protos) to
GitHub Packages:

```kotlin
implementation("com.github.subhajitdas298:test-data-protos:1.0.1")
```

GitHub Packages requires authentication to *resolve* Maven artifacts, even for public
repositories. Before building, export a GitHub personal access token with `read:packages`
scope:

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-personal-access-token>
```

`build.gradle.kts` reads these two environment variables to authenticate against
`https://maven.pkg.github.com/Subhajitdas298/test-data-protos`.

> Note: the `test-data-protos` package is published only when a GitHub Release is cut on
> that repository. Make sure version `1.0.1` (or whatever version you point at) has
> actually been published before building this project.

## Running

Requires Java 24 (the Gradle wrapper will auto-provision it via the Foojay toolchain
resolver if it's not already installed).

```bash
./gradlew bootRun
```

Then:

```bash
curl http://localhost:8080/api/data -H "Accept: application/x-protobuf" --output data.pb
curl http://localhost:8080/api/data -H "Accept: application/json"
```

## Tech stack

- Spring Boot 4
- Spring WebFlux (`spring-boot-starter-webflux`) — reactive, non-blocking, Netty-based
- Project Reactor (`Mono`) end-to-end, including a `.cache()`-backed reactive
  read-through cache in place of Spring's (synchronous) cache abstraction
- Java 24
- Gradle (Kotlin DSL)
- `com.github.subhajitdas298:test-data-protos` (protobuf-generated Java models)
- `protobuf-java-util` (protobuf binary serialization + `JsonFormat` for JSON)

## Deployment (Azure Container Apps)

[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) builds the jar, builds a
Docker image (see [`Dockerfile`](Dockerfile)), pushes it to Azure Container Registry, and
updates the Azure Container App to the new image, on every push to `main` (which includes
merging a PR into `main`) and via manual `workflow_dispatch`.

It authenticates to Azure with OIDC (`azure/login`, no client secret stored in GitHub) —
same pattern as `test-publish-data-protos`.

This app is deployed as its **own Container App**, `data-protos-webflux`, sitting
alongside (not replacing) the existing `data-protos` app, in the **same** resource group,
Container Apps environment, and Container Registry — so no new billable environment or
registry is created, only a second (still scale-to-zero) app.

### Azure resources to create once

Run these once, from a machine/session with `az` installed and logged in to the same
subscription and resource group as `test-publish-data-protos`. Nothing here has been
provisioned yet from this session — it had no `az` CLI or Azure credentials available, so
these commands need to be run by someone with access, the same way the original
`data-protos` app's resources were set up.

```bash
SUBSCRIPTION_ID=<your-subscription-id>          # cfb23074-9c21-4bc5-aecb-4845d97a147e for the existing setup
RESOURCE_GROUP=data-protos                      # reuse the existing resource group
LOCATION=eastus
ACR_NAME=dataprotosacr2477                      # reuse the existing registry
ENVIRONMENT_NAME=cae-data-protos                # reuse the existing Container Apps environment
APP_NAME=data-protos-webflux                    # new, separate container app

az account set --subscription "$SUBSCRIPTION_ID"

# Placeholder container app — the workflow only ever updates its image afterwards.
# min-replicas 0 (scale-to-zero) keeps this within the Container Apps Consumption
# free monthly grant: the app costs nothing while idle and cold-starts on the next request.
az containerapp create \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --environment "$ENVIRONMENT_NAME" \
  --image mcr.microsoft.com/k8se/quickstart:latest \
  --target-port 8080 \
  --ingress external \
  --min-replicas 0 --max-replicas 1

# Let the app pull from the shared ACR using its own managed identity
az containerapp identity assign --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --system-assigned
PRINCIPAL_ID=$(az containerapp identity show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query principalId -o tsv)
ACR_ID=$(az acr show --name "$ACR_NAME" --resource-group "$RESOURCE_GROUP" --query id -o tsv)
az role assignment create --assignee "$PRINCIPAL_ID" --role AcrPull --scope "$ACR_ID"
az containerapp registry set --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --server "$ACR_NAME.azurecr.io" --identity system
```

### Azure AD app registration for GitHub OIDC

This repo needs its **own** app registration and federated credential — OIDC federated
credentials are scoped to a specific `repo:<owner>/<repo>:ref:<ref>` subject, so the
existing `gh-actions-data-protos` registration used by `test-publish-data-protos` cannot
be reused as-is.

```bash
APP_ID=$(az ad app create --display-name "gh-actions-data-protos-webflux" --query appId -o tsv)
az ad sp create --id "$APP_ID"

az ad app federated-credential create --id "$APP_ID" --parameters '{
  "name": "github-main-branch",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:Subhajitdas298/test-publish-data-protos-webflux:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'
```

> **Note:** if this GitHub org/repo has the "use unique repository/owner ID in the
> subject claim" OIDC setting enabled (as `test-publish-data-protos` does), the actual
> subject GitHub sends is `repo:<owner>@<owner_id>/<repo>@<repo_id>:ref:refs/heads/main`
> instead of the plain name form above — check the workflow's `azure/login` step for an
> `AADSTS700213` error to find the exact subject it presented, then update the federated
> credential to match.

```bash
# Let the CI identity push images to the shared ACR and update this container app only
az role assignment create --assignee "$APP_ID" --role AcrPush --scope "$ACR_ID"
CONTAINERAPP_ID=$(az containerapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query id -o tsv)
az role assignment create --assignee "$APP_ID" --role "Container Apps Contributor" --scope "$CONTAINERAPP_ID"

TENANT_ID=$(az account show --query tenantId -o tsv)
echo "AZURE_CLIENT_ID=$APP_ID"
echo "AZURE_TENANT_ID=$TENANT_ID"
echo "AZURE_SUBSCRIPTION_ID=$SUBSCRIPTION_ID"
```

### GitHub repo configuration

**Settings → Secrets and variables → Actions → Secrets:**

| Secret                 | Value                                              |
|-------------------------|-----------------------------------------------------|
| `AZURE_CLIENT_ID`       | the new `$APP_ID` printed above                    |
| `AZURE_TENANT_ID`       | `86c9c0f2-9014-48a2-99e7-785b23ee2769` (same tenant as `data-protos`) |
| `AZURE_SUBSCRIPTION_ID` | `cfb23074-9c21-4bc5-aecb-4845d97a147e` (same subscription as `data-protos`) |
| `PACKAGES_READ_TOKEN`   | a GitHub PAT with `read:packages`, so the workflow can resolve `test-data-protos` from GitHub Packages |

**Settings → Secrets and variables → Actions → Variables:**

| Variable                        | Value                                  |
|-----------------------------------|-------------------------------------------|
| `AZURE_CONTAINER_REGISTRY_NAME` | `dataprotosacr2477` (shared with `data-protos`) |
| `AZURE_RESOURCE_GROUP`          | `data-protos` (shared with `data-protos`) |
| `AZURE_CONTAINER_APP_NAME`      | `data-protos-webflux`                    |

These can't be set via the GitHub tools available to this session (setting an Actions
secret requires client-side encryption with the repo's public key, and provisioning the
Azure resources above requires an authenticated `az` CLI, neither of which this session
has), so add them yourself in the GitHub UI after running the `az` commands above. Once
set, any push to `main` (including a merged PR) triggers the workflow and deploys this
app to its own URL alongside the existing `data-protos` app.
