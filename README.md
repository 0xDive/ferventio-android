# Ferventio Android

Ferventio is a native Android client for Twitch chat, moderation and multi-channel workflows.

> **Beta:** `0.0.1` is the first public test release. Expect defects and changes before `1.0.0`.

The server component lives in [`0xDive/ferventio-backend`](https://github.com/0xDive/ferventio-backend).

## Features

- Twitch chat, replies, moderation events and user cards
- Twitch, BetterTTV, FrankerFaceZ and 7TV emotes
- Multi-channel workspaces and notification rules
- Local Room history with configurable retention
- Light, dark and AMOLED themes
- Separate FOSS and Play distributions

## Distributions

| Build | Push | Crash reporting | Google runtime dependencies |
| --- | --- | --- | --- |
| FOSS | Ferventio WebSocket transport | Local, user-exported reports | No |
| Play | Firebase Cloud Messaging | Firebase Crashlytics | Play flavor only |

Both distributions use the same application ID. Sign them with the same app-signing identity if users must switch between distributions without reinstalling.

## Build from source

Requirements:

- Eclipse Temurin JDK 25
- Android SDK Platform 36
- Git

```bash
git clone https://github.com/0xDive/ferventio-android.git
cd ferventio-android

cp gradle.properties.example gradle.properties
./gradlew :app:assembleFossDebug --no-configuration-cache
```

The debug APK is written to:

```text
app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Play builds require the public Firebase Android configuration listed in [`gradle.properties.example`](gradle.properties.example).

## Configuration

Build-time values are read from Gradle properties. For local production values, prefer `~/.gradle/gradle.properties` so secrets and signing material stay outside the repository.

Never commit:

- `local.properties`
- a secret-bearing `gradle.properties`
- signing keystores or passwords
- `google-services.json`
- Firebase service-account files
- production tokens or private keys

## Tests

```bash
./gradlew \
  :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :app:testFossDebugUnitTest \
  :app:testPlayDebugUnitTest \
  :app:lintFossDebug \
  :app:lintPlayDebug \
  :app:verifyFossNoGooglePushDependencies \
  :app:assembleFossDebug \
  :app:assemblePlayDebug \
  --no-configuration-cache \
  --stacktrace

python3 scripts/architecture/check-module-boundaries.py --root .
./scripts/security/run-security-checks.sh
```

Device tests are run separately through the instrumentation workflow.

## Project structure

```text
app/            Android app, Compose UI, transports and product flavors
core/domain/    Platform-independent models and rules
core/database/  Room database, migrations and local history
benchmark/      Macrobenchmark and Baseline Profile generation
build-logic/    Typed Gradle verification tasks
```

See [`docs/architecture.md`](docs/architecture.md) for module boundaries and the Android/backend contract.

## Beta feedback

Use GitHub Issues for reproducible bugs and focused feature requests. Include the app version, FOSS or Play flavor, Android version, device model and clear reproduction steps. Remove tokens, usernames, private messages and other personal data from logs and screenshots.

Security reports must be submitted privately as described in [`SECURITY.md`](SECURITY.md).

## Contributing and releases

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — development workflow
- [`docs/releasing.md`](docs/releasing.md) — signing, versioning and release checks

## License

Ferventio is available under the [MIT License](LICENSE). Third-party notices are listed in [`docs/third-party-notices.md`](docs/third-party-notices.md).

Ferventio is an independent project and is not affiliated with or endorsed by Twitch Interactive, Inc.
