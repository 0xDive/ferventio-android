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

Run the same platform-neutral repository and KMP guards used by CI:

```bash
python3 scripts/security/check-repository-secrets.py --root .
python3 scripts/security/test-check-repository-secrets.py
python3 scripts/architecture/check-module-boundaries.py --root .
python3 scripts/architecture/check-kmp-boundaries.py --root .
python3 scripts/localization/check_ui_localization.py

./gradlew \
  -p build-logic \
  clean \
  check \
  --no-configuration-cache \
  --stacktrace
```

Then run the common/shared compilation, KMP host tests and Android release validation used by the publication workflow:

```bash
./gradlew \
  :core:domain:compileCommonMainKotlinMetadata \
  :shared:compileCommonMainKotlinMetadata \
  :core:domain:testAndroidHostTest \
  :shared:testAndroidHostTest \
  :core:database:testDebugUnitTest \
  :app:testFossDebugUnitTest \
  :app:testPlayDebugUnitTest \
  :app:lintFossRelease \
  :app:lintPlayRelease \
  :app:verifyFossNoGooglePushDependencies \
  :app:verifyPlayCrashReportingDependency \
  :app:verifyPlayCrashReportingConfiguration \
  :app:verifyPrivacyPolicyConfiguration \
  --no-configuration-cache \
  --stacktrace
```

The manually dispatched Android Release workflow runs these checks again before signed package creation and publication. Its Gradle Wrapper checksum is intentionally kept in sync with pull-request CI so the publication path cannot silently use a different wrapper artifact.

The Android PR workflow additionally assembles the minified/resource-shrunk FOSS Release APK and Play Release AAB with synthetic CI-only privacy/Firebase values, verifies both packages are valid archives, and verifies the PR FOSS APK remains unsigned when no production keystore is configured.

The iOS KMP workflow must pass simulator Debug, `iosArm64` Debug and Release framework linking, unsigned Debug and Release `generic/platform=iOS` application builds, arm64 binary checks, Compose-resource checks and Release privacy metadata assertions. See [`Multiplatform Release CI Guard`](rc-release-build.md) for the exact Android and iOS contracts.

These unsigned builds prove compile/link/package/configuration compatibility only. They are not publication artifacts and do not validate Android production signing, Apple signing, APNs or physical-device lifecycle behavior.

Run the full [`Multiplatform RC device smoke test`](rc-device-smoke.md) on physical Android and iPhone hardware before RC promotion. Attach the completed checklist to a pull-request or issue comment and keep that comment's `#issuecomment-…` permalink.

The manually dispatched Android Release workflow requires two physical-smoke inputs before it will build or publish:

- `rc_smoke_commit` — the full 40-character SHA that passed the Android + iPhone matrix; it must exactly equal the `main` commit being released;
- `rc_smoke_report_url` — the exact permalink to the completed checklist comment in this repository's pull-request or issue discussion.

The workflow resolves the supplied comment through the GitHub API, verifies that the permalink and parent pull-request/issue match, and requires the comment body to contain the exact tested SHA. Bare PR/issue URLs, unrelated comments and reports that omit the tested SHA are rejected.

The workflow records both values in the job summary and published release notes. If merging the validated PR or making any follow-up change produces a different `main` SHA, rerun the physical-device matrix on that exact release commit and provide the new evidence; a report for an earlier PR head is intentionally rejected.

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
