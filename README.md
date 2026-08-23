# Tide – Light Phone III TIDAL client

A single-APK LightOS tool for TIDAL, built on the official TIDAL SDK. The current milestone (Phase 0) establishes the SDK-first binder RPC seam and TIDAL authentication; saved releases, release detail, and playback follow.

## Project structure
- **:app** – Light SDK tool module. Light UI screens and `TideClient` — the binder RPC client. Declares the `com.lightphone.tide` application ID. No TIDAL or server imports; communicates entirely over typed protocol methods.
- **:server** – Unrestricted Android library merged into the APK at runtime (`debugRuntimeOnly`). Hosts `TideRuntime` (TIDAL auth), `TideServiceMethods` (RPC dispatch), the self-hosted `LightSdkServer`, and the OAuth `WebView` activity.
- **:protocol** – Pure Kotlin/JVM module. Serializable contracts and domain models (`AuthSnapshot`, `ReleaseSummary`, `TideRemoteMethod`) shared between app and server. No Android or TIDAL dependencies.
- Both `:app` and `:server` depend on the included `../light-sdk` composite build (same as the existing `chats` project).

## TIDAL configuration
The SDK reads its configuration from `local.properties` (which is **git‑ignored**). Provide the following keys, matching the official SDK demo:
```
tidal.clientid=<your client id>
tidal.clientsecret=<your client secret>
tidal.clientscopes=<space-separated scopes>
tidal.clientredirecturi=tide://auth
```
A `local.properties.example` file is included as a template.

## Build & install
```bash
# From the tide project root
./gradlew :app:assembleDebug   # builds the APK
# The resulting APK is at app/build/outputs/apk/debug/app-debug.apk
```
Install with `adb install` or via Android Studio.

## Architecture notes
- The tool's `lighttool.toml` points `serverPackage = "com.lightphone.tide"`, so the tool binds to itself — same single-APK pattern as Chats.
- `:app` never touches `TideRuntime` directly. It calls `TideClient` (interface) backed by `BinderTideClient`, which serializes requests through `LightServiceConnection.callRemoteServiceMethod(...)` using `TideRemoteMethod` IDs (e.g. `com.lightphone.tide.auth.snapshot.v1`).
- `:server` registers `TideServiceMethods.dispatch` as `LightSdkServer.customServiceMethodResolver` in `TideBootstrapProvider`, so custom Tide methods are answered alongside built-in Light SDK methods.
- `TideRuntime` owns the singleton returned by `TidalAuth.getInstance(...)` and exposes `currentAuthSnapshot()` returning a protocol `AuthSnapshot`.
- The login flow calls the instance `Auth.initializeLogin(...)` and passes the full redirect URI to `Auth.finalizeLogin(redirectUri)`.
- Missing credentials produce an in-app configuration message while still allowing a credential-free debug build.

## Dependencies
- **TIDAL SDK**
  - `com.tidal.sdk:auth:0.10.1`
  - `com.tidal.sdk:player:0.0.71`
  - `com.tidal.sdk:tidalapi:0.3.53`
- **JitPack** repository is added in the root `settings.gradle.kts` for any transitive dependencies.

## License
Apache-2.0 (same as the surrounding LightOS codebase).
