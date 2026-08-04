# Releasing Android

## Versioning

Public releases use semantic versions and annotated tags:

```text
android-v0.0.1
android-v0.0.2
```

`0.0.x` is early beta and may contain breaking changes. Android `versionCode` is a separate distribution counter and must increase for every published APK or AAB. Room schema versions, backup format versions and backend API versions are independent compatibility contracts and must not be reset.

## Release signing

Keep signing material outside the repository.

```bash
mkdir -p ~/.ferventio/signing
chmod 700 ~/.ferventio/signing

keytool -genkeypair \
  -v \
  -keystore ~/.ferventio/signing/ferventio-release.p12 \
  -storetype PKCS12 \
  -alias ferventio \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000
```

Add the following to `~/.gradle/gradle.properties` or protected CI secrets:

```properties
FERVENTIO_KEYSTORE_FILE=/absolute/path/ferventio-release.p12
FERVENTIO_KEYSTORE_PASSWORD=replace-me
FERVENTIO_KEY_ALIAS=ferventio
FERVENTIO_KEY_PASSWORD=replace-me
```

FOSS and Play builds must use the same final app-signing identity if users need to switch distributions without uninstalling. When Play App Signing is enabled, record which key is the app-signing key and which is only the upload key.

Back up the keystore, alias, passwords and certificate SHA-256 in at least two encrypted locations.

## Release configuration

Before building, set and validate:

- production backend URL
- current and backup certificate pins
- privacy operator, contact and HTTPS policy URL
- Play Firebase public configuration
- release signing properties
- monotonic `versionCode` and the intended `versionName`

Use [`gradle.properties.example`](../gradle.properties.example) as the property reference. Never commit production signing credentials.

## Validation

```bash
./gradlew \
  :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :app:testFossDebugUnitTest \
  :app:testPlayDebugUnitTest \
  :app:lintFossRelease \
  :app:lintPlayRelease \
  :app:verifyFossNoGooglePushDependencies \
  :app:verifyPlayCrashReportingDependency \
  :app:verifyPrivacyPolicyConfiguration \
  :app:verifyFerventioServerCertificatePins \
  --no-configuration-cache \
  --stacktrace

python3 scripts/architecture/check-module-boundaries.py --root .
./scripts/security/run-security-checks.sh
```

Run device tests and the relevant benchmark/profile workflow on a clean emulator or device before publication.

## Build and verify

```bash
./gradlew :app:assembleFossRelease --no-configuration-cache --stacktrace
./gradlew :app:bundlePlayRelease --no-configuration-cache --stacktrace
```

Artifacts:

```text
app/build/outputs/apk/foss/release/app-foss-release.apk
app/build/outputs/bundle/playRelease/app-play-release.aab
```

Verify the signatures:

```bash
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/foss/release/app-foss-release.apk

jarsigner -verify -verbose -certs \
  app/build/outputs/bundle/playRelease/app-play-release.aab
```

Publish checksums with the release. Keep mapping files and native symbols private but retained for crash analysis.

Create the signed tag only after the final artifacts have passed validation.
