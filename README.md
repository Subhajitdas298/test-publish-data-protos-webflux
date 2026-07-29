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
registry was created, only a second (still scale-to-zero) app.

### Provisioned resources

The Azure resources below already exist in subscription `cfb23074-9c21-4bc5-aecb-4845d97a147e`
("Azure subscription 1"), resource group **`data-protos`** (region `eastus`) — reusing the
same environment and registry as `test-publish-data-protos` to avoid provisioning a second
billable environment/registry:

| Resource                       | Name                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------|
| Resource group                  | `data-protos` (shared)                                                                        |
| Container Registry (Basic)      | `dataprotosacr2477` (`dataprotosacr2477.azurecr.io`) (shared)                                 |
| Container Apps environment      | `cae-data-protos` (shared)                                                                    |
| Container App (min-replicas 0)  | `data-protos-webflux` — `https://data-protos-webflux.gentlepond-37bc0af9.eastus.azurecontainerapps.io` |
| AD app registration (OIDC)      | `gh-actions-data-protos-webflux` (client ID `965b7fda-de7f-44d7-b5be-d9bd054472e1`)            |

The AD app has two federated credentials scoped to this repo's `main` branch — one using
the plain `repo:Subhajitdas298/test-publish-data-protos-webflux:ref:refs/heads/main`
subject and one using the immutable-ID form
`repo:Subhajitdas298@20024190/test-publish-data-protos-webflux@1316021005:ref:refs/heads/main`
(this account has GitHub's immutable-ID OIDC subject format enabled, same as
`test-publish-data-protos`) — so `azure/login` works regardless of which subject format
GitHub actually presents. It also has `AcrPush` on the shared registry and
`Container Apps Contributor` scoped to just the `data-protos-webflux` app (not the whole
resource group). The container app's own system-assigned identity has `AcrPull` on the
registry so it can pull images.

### GitHub repo configuration

The Azure side is fully provisioned. What's left is adding these in this repo's
**Settings → Secrets and variables → Actions** — GitHub Actions secrets require
client-side encryption with the repo's public key to set, which isn't available through
any tool in this session, so this last step needs to be done by hand in the GitHub UI:

**Secrets:**

| Secret                 | Value                                              |
|-------------------------|-----------------------------------------------------|
| `AZURE_CLIENT_ID`       | `965b7fda-de7f-44d7-b5be-d9bd054472e1`              |
| `AZURE_TENANT_ID`       | `86c9c0f2-9014-48a2-99e7-785b23ee2769`              |
| `AZURE_SUBSCRIPTION_ID` | `cfb23074-9c21-4bc5-aecb-4845d97a147e`              |
| `PACKAGES_READ_TOKEN`   | a GitHub PAT with `read:packages`, so the workflow can resolve `test-data-protos` from GitHub Packages |

**Variables:**

| Variable                        | Value                                  |
|-----------------------------------|-------------------------------------------|
| `AZURE_CONTAINER_REGISTRY_NAME` | `dataprotosacr2477`                      |
| `AZURE_RESOURCE_GROUP`          | `data-protos`                            |
| `AZURE_CONTAINER_APP_NAME`      | `data-protos-webflux`                    |

Once those are set, any push to `main` (including a merged PR) triggers
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) and deploys this app to its
own URL alongside the existing `data-protos` app.
