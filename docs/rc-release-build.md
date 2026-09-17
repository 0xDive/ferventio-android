# iOS Release Device CI Guard

The iOS KMP workflow validates both Debug and Release device builds before a multiplatform RC can be promoted.

The Release guard must:

- compile and link `FerventioShared` for `iosArm64` with the Release binary;
- build the Xcode application for `generic/platform=iOS` with code signing disabled;
- provide non-empty privacy operator/contact metadata and an HTTPS privacy policy URL;
- verify the resulting application binary contains `arm64`;
- verify Compose resources are present in the Release device app bundle;
- verify the generated `Info.plist` contains the CI privacy metadata.

This is a compile/link/configuration guard only. It does not replace the signed physical-device smoke test in [`rc-device-smoke.md`](rc-device-smoke.md), because APNs, provisioning, entitlements and real lifecycle/network behavior require an installed signed build.