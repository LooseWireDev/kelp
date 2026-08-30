# Kelp – Light Phone III TIDAL client

A sideloadable Light Phone III tool for TIDAL, following the proven [phono](https://github.com/jonathancaudill/phono) playback recipe. Kelp uses the Light SDK for its application lifecycle, screens, navigation, UI, service connection, and audio capability. It supports TIDAL sign-in, catalog browsing (collection, playlists, mixes, search), and full-length streaming playback. A paid TIDAL subscription is required — Kelp, like phono, will never work around that.

## Light SDK status

Kelp's `:app` module follows the current Light tool scaffold:

- applies `com.thelightphone.light-sdk` and the SDK's KSP processor;
- declares identity, permissions, orientation, server package, and capabilities in `app/lighttool.toml` instead of a hand-written manifest;
- uses `@InitialScreen`, `LightScreen`, `LightViewModel`, SDK navigation, and Light UI components;
- treats restricted Android APIs as lint errors;
- opts into the SDK-owned `detached-audio` capability for playback; and
- builds against Java 17, Android API 36, and the adjacent SDK checkout based directly on official `lightphone/light-sdk` v0.1.1.

The current TIDAL integration is intentionally **sideload-only**. The official Light builder extracts one tool module and builds it against a pinned SDK; it ignores sibling modules. It also does not currently allowlist the official TIDAL Android SDK or provide a generic OAuth browser flow. Kelp therefore keeps TIDAL auth and API code in a runtime-only `:server` module and self-hosts the Light binder service inside the same APK. This works for local sideloading but is not eligible for Light's hosted build/signing pipeline in its present form.

Do not describe the current APK as a hosted-builder-compatible community tool. Reaching that state requires an upstream Light SDK decision: allowlist the TIDAL SDK and add an SDK-safe OAuth flow, or provide equivalent TIDAL methods from LightOS.

## Project structure
- **:app** – Light SDK tool module. Light UI screens and `TideClient`, the binder RPC client. The Light plugin derives the `com.loosewire.kelp` application ID and manifest from `lighttool.toml`. No TIDAL or server imports; communicates entirely over typed protocol methods.
- **:server** – Unrestricted Android library merged into both debug and release APKs as a runtime-only dependency. Hosts `TideRuntime` (TIDAL auth), `TideServiceMethods` (RPC dispatch), the self-hosted `LightSdkServer`, and the OAuth `WebView` activity.
- **:protocol** – Pure Kotlin/JVM module. Serializable contracts and domain models (`AuthSnapshot`, `ReleaseSummary`, `TideRemoteMethod`) shared between app and server. No Android or TIDAL dependencies.
- `:app`, `:protocol`, and `:server` consume the adjacent `../light-sdk` composite build.

## TIDAL configuration
The SDK reads its public-client configuration from `local.properties` (which is **git‑ignored**). Kelp uses OAuth PKCE and does not embed a client secret:
```
tidal.clientid=<your client id>
tidal.clientscopes=<space-separated scopes>
tidal.clientredirecturi=kelp://auth
```
A `local.properties.example` file is included as a template.

## How playback works (two sign-ins)

Kelp keeps two TIDAL sessions, chained in one WebView login flow:

1. **Developer-app PKCE** (your `local.properties` credentials) via the official auth module — required by TIDAL's v2 Public API, which only accepts registered developer apps. All catalog calls use this.
2. **First-party PKCE** with TIDAL's official Android client id — required for full-length playback. Stream entitlement is keyed on the OAuth client, and developer tokens only resolve 30-second previews, which was the previous ceiling. `TidalStreamingAuth` mirrors phono's own implementation (itself matching orpheusdl/python-tidal).

Playback is owned end-to-end: `TidalStreamResolver` calls the private `tracks/{id}/playbackinfopostpaywall` endpoint (`assetpresentation=FULL`), decodes BTS progressive or sanitized clear-DASH manifests, and hands them to Media3 ExoPlayer. The official TIDAL Player module is not used. Stream quality starts at LOSSLESS and falls back HIGH → LOW.

**Keeping the first-party client id current:** TIDAL rotates these periodically. The working procedure (same as phono/orpheusdl): decompile the latest official `com.aspiro.tidal` APK (apktool/jadx), read `DefaultClearHiResV2ClientId` out of `assets/secrets.properties`, and update `TidalStreamingAuth.CLIENT_ID`. If playback suddenly returns 401s for everyone, this is the first thing to check.

Sign out from **Settings → TIDAL** — it clears both sessions and WebView cookies, dropping back to the full chained login.

## Build & install

Prerequisites: JDK 17, Android SDK Platform 36, and the Light SDK checked out at `../light-sdk`. The SDK checkout should use the local `codex/kelp-official-sdk` branch, which is based directly on official v0.1.1 and contains only the small compatibility extensions Kelp needs for tool-owned RPCs, composite SDK dependencies, and its server-owned OAuth activity.

```bash
# From the kelp project root
./gradlew :app:assembleDebug   # builds the APK
# The resulting APK is at app/build/outputs/apk/debug/app-debug.apk
```
Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture notes
- The tool's `lighttool.toml` points `serverPackage = "com.loosewire.kelp"`, so the sideload APK binds to its embedded service. A conventional LightOS-hosted tool would use `com.lightos` instead.
- `:app` never touches `TideRuntime` directly. It calls `TideClient` (interface) backed by `BinderTideClient`, which serializes requests through `LightServiceConnection.callRemoteServiceMethod(...)` using `TideRemoteMethod` IDs (e.g. `com.loosewire.kelp.auth.snapshot.v1`).
- `:server` registers `TideServiceMethods.dispatch` as `LightSdkServer.customServiceMethodResolver` in `TideBootstrapProvider`, so custom Kelp methods are answered alongside built-in Light SDK methods.
- `TideRuntime` owns the singleton returned by `TidalAuth.getInstance(...)` (developer-app catalog session) plus `TidalStreamingAuth` (first-party playback session), and exposes `currentAuthSnapshot()` returning a protocol `AuthSnapshot`.
- The login flow chains developer-app PKCE then first-party PKCE in one WebView; shared cookies usually carry the session into step two.
- `TidePlayerController` keeps queue/shuffle/repeat policy in Kelp and plays Media3 `ExoPlayer`; all player calls are marshaled to the main thread.
- Missing credentials produce an in-app configuration message while still allowing a credential-free debug build.
- The endpoint, scope, Home-feed, playback, offline, and attribution decisions are recorded in [`docs/TIDAL_ARCHITECTURE.md`](docs/TIDAL_ARCHITECTURE.md).

## Dependencies
- **TIDAL SDK**
  - `com.tidal.sdk:auth:0.10.1`
  - `com.tidal.sdk:tidalapi:0.3.53`
- **Media3 1.5.0** (`media3-common`, `media3-exoplayer`, `media3-exoplayer-dash`), strict-pinned because the Light SDK brings a newer version.
- **OkHttp 4.12.0** for first-party streaming auth + playbackinfo.
- **JitPack** repository is added in the root `settings.gradle.kts` for any transitive dependencies.

## Release builds

**Local:**

```bash
scripts/build-release.sh          # tests + minified release APK into dist/
scripts/build-release.sh --skip-tests
```

The script runs the unit suites (`:protocol:test`, `:app:testDebugUnitTest`, `:server:testDebugUnitTest`), builds `:app:assembleRelease` (R8), copies the APK to `dist/kelp-v<versionName>-vc<versionCode>.apk`, and prints its SHA-256. Version is read from `app/lighttool.toml`. The script auto-detects JDK 17 and an Android SDK; override with `JAVA_HOME` / `ANDROID_HOME`. Without a release key in the environment, the APK is signed with the shared light-sdk dev key; set `TIDE_RELEASE_KEYSTORE`, `TIDE_RELEASE_STORE_PASSWORD`, `TIDE_RELEASE_KEY_ALIAS`, `TIDE_RELEASE_KEY_PASSWORD` to sign with the release key instead.

**GitHub CI/CD** (`.github/workflows/`):

- `build.yml` runs the tests + debug assemble on every push to `main` and on PRs.
- `release.yml` runs when a `v*` tag is pushed: it verifies the tag matches `versionName` in `app/lighttool.toml`, builds with the release key from Actions secrets (`TIDE_RELEASE_*`), and publishes a GitHub Release with the APK, checksums, signer certificate digest, and install notes.

Release procedure:

```bash
# 1. bump versionCode/versionName in app/lighttool.toml, commit
# 2. tag and push
git tag v0.2.0 && git push origin v0.2.0
```

The upgrade path requires the install key to match: APKs signed with the light-sdk dev key (older local installs) must be uninstalled before installing a GitHub Release build.

## License
Apache-2.0 (same as the surrounding LightOS codebase).
