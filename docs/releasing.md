# Releasing

## Versioning

Public Android releases use semantic versions and annotated tags:

```text
android-v0.0.1
android-v0.0.2
```

`0.0.x` is early beta and may contain breaking changes. Android `versionCode` is a separate distribution counter and must increase for every published APK or AAB. Room schema versions, backup format versions and backend API versions are independent compatibility contracts and must not be reset.

For a multiplatform RC, record the exact commit SHA tested on Android and iPhone. Do not treat a green simulator build as physical-device validation.

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

Apple signing certificates, provisioning profiles and App Store Connect credentials must likewise remain outside the repository. Physical iPhone smoke testing must use a normally signed development/ad-hoc build so APNs and device entitlements are exercised.

## Release configuration

Before building, set and validate:

- production backend URL
- privacy operator, contact and HTTPS policy URL
- Play Firebase public configuration
- Android release signing properties
- Apple signing/provisioning configuration for physical-device validation
- monotonic Android `versionCode` and the intended `versionName`
- intended iOS marketing/build versions

Use [`gradle.properties.example`](../gradle.properties.example) as the Android property reference. Never commit production signing credentials.

## Automated validation

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
  --no-configuration-cache \
  --stacktrace

python3 scripts/architecture/check-module-boundaries.py --root .
./scripts/security/run-security-checks.sh
```

The iOS KMP workflow must pass simulator Debug, `iosArm64` Debug and Release framework linking, unsigned Debug and Release `generic/platform=iOS` application builds, arm64 binary checks, Compose-resource checks and Release privacy metadata assertions. See [`iOS Release Device CI Guard`](rc-release-build.md) for the exact contract.

These unsigned device builds prove architecture/link/configuration compatibility only; they do not validate signing, APNs or physical-device lifecycle behavior.

Run the full [`Multiplatform RC device smoke test`](rc-device-smoke.md) on physical Android and iPhone hardware before RC promotion.

## Android build and verification

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

## iOS compile guard

Generate the Xcode project and verify simulator plus Debug/Release device architectures locally when needed:

```bash
cd iosApp
xcodegen generate
cd ..

xcodebuild \
  -project iosApp/Ferventio.xcodeproj \
  -scheme Ferventio \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/ios-derived \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build

xcodebuild \
  -project iosApp/Ferventio.xcodeproj \
  -scheme Ferventio \
  -configuration Debug \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build/ios-device-derived \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build

xcodebuild \
  -project iosApp/Ferventio.xcodeproj \
  -scheme Ferventio \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build/ios-release-device-derived \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  "FERVENTIO_PRIVACY_OPERATOR_NAME=Ferventio RC" \
  "FERVENTIO_PRIVACY_CONTACT=privacy@example.com" \
  "FERVENTIO_PRIVACY_POLICY_URL=https://example.com/privacy" \
  FERVENTIO_SHOW_PRIVACY_POLICY_IN_APP=YES \
  build
```

The Xcode pre-build phase selects and links the matching Kotlin framework (`iosSimulatorArm64` or `iosArm64`) and Debug/Release binary from `SDK_NAME` plus `CONFIGURATION`.

Publish checksums with public Android artifacts. Keep mapping files and native symbols private but retained for crash analysis.

Create the signed release tag only after the final artifacts and physical-device checklist have passed validation.
