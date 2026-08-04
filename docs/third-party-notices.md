# Ferventio third-party notices

Ferventio includes open-source runtime components. The authoritative, flavor-aware list and full offline license texts are available under **Settings → About → Open-source licenses**.

Current component families include:

- AndroidX, Jetpack Compose, Lifecycle, Room and Profile Installer — Apache License 2.0
- Kotlin standard library — Apache License 2.0
- kotlinx.coroutines — Apache License 2.0
- kotlinx.serialization — Apache License 2.0
- Ktor Client — Apache License 2.0
- OkHttp and Okio — Apache License 2.0
- Coil — Apache License 2.0
- javax.inject and JetBrains annotations — Apache License 2.0
- Firebase Android SDK, Play flavor only — Apache License 2.0
- Protocol Buffers Lite, Play flavor transitive dependency — BSD 3-Clause License

Test-only tools are not shipped in production APKs and are intentionally excluded from the in-app runtime notice list.

The in-app catalog is covered by `FerventioLegalContentTest`, which verifies unique IDs, known license mappings, offline license texts and FOSS/Play separation.
