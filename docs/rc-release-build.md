# Multiplatform Release CI Guard

The Android and iOS workflows validate Release packaging before a multiplatform RC can be promoted.

## Android

The Android Release guard must:

- enforce module and KMP source-set boundaries plus the UI localization contract;
- compile the core-domain and shared common metadata and run their Android-host KMP tests;
- run release lint for both FOSS and Play variants;
- keep the FOSS release runtime free of Firebase and Google Play Services dependencies;
- keep the Play release runtime wired to Firebase Crashlytics;
- assemble the minified/resource-shrunk FOSS Release APK;
- build the Play Release AAB;
- use synthetic CI-only privacy/Firebase values that still pass the production configuration validators;
- verify the APK and AAB are non-empty valid ZIP archives;
- verify the AAB contains `BundleConfig.pb` and its base manifest;
- verify the PR FOSS Release APK is unsigned when no production keystore is configured.

The manually dispatched production Android Release workflow repeats the repository/KMP/localization checks, common/shared compilation and Android-host KMP tests before signed package creation and publication. This keeps publication validation from being weaker than pull-request CI.

The CI artifacts are compile/package guards only. They must never be published because their privacy/Firebase values are synthetic and they intentionally do not use production signing material.

## iOS

The iOS Release guard must:

- compile and link `FerventioShared` for `iosArm64` with the Release binary;
- build the Xcode application for `generic/platform=iOS` with code signing disabled;
- provide non-empty privacy operator/contact metadata and an HTTPS privacy policy URL;
- verify the resulting application binary contains `arm64`;
- verify Compose resources are present in the Release device app bundle;
- verify the generated `Info.plist` contains the CI privacy metadata.

These guards do not replace the signed physical-device smoke test in [`rc-device-smoke.md`](rc-device-smoke.md). Android signing/distribution, Apple provisioning/APNs/entitlements and real lifecycle/network behavior still require normally signed builds installed on physical devices.
