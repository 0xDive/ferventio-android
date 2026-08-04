# Architecture

Ferventio Android is the client application. Server deployment, PostgreSQL migrations and Firebase Admin credentials belong to [`0xDive/ferventio-backend`](https://github.com/0xDive/ferventio-backend).

## Modules

```text
benchmark -> app -> core:database -> core:domain
                \-> core:domain
```

- `core:domain` contains models and rules. It does not depend on Android framework, networking, Room or UI APIs.
- `core:database` owns Room entities, DAO, migrations and local history. It may depend on `core:domain`.
- `app` is the Android composition root. It owns Compose UI, platform services, network clients, transports and product-flavor wiring.
- `benchmark` contains Macrobenchmark and Baseline Profile code.
- `build-logic` contains typed Gradle verification tasks used by the repository.

The dependency policy is enforced by `scripts/architecture/check-module-boundaries.py`.

## Runtime responsibilities

- **UI:** screens, navigation, accessibility semantics and immutable presentation state
- **Application:** authentication, channel lifecycle, chat orchestration and settings
- **Data:** Twitch and backend clients, local preferences, backup and Room
- **Protocols:** Twitch IRC/EventSub, Ferventio WebSocket and provider parsers
- **Security:** Android Keystore-backed local state, TLS pinning and input/logging guards

Composables must not perform direct network or disk I/O.

## Authentication

The app opens the backend-managed Twitch OAuth flow and receives an opaque backend session plus short-lived Twitch access leases. Twitch refresh tokens remain on the backend. Device-bound state is encrypted with Android Keystore.

Normal Twitch chat and Helix traffic goes directly from the device to Twitch; the backend is not a chat proxy.

## Push transports

- Play builds register Firebase Cloud Messaging tokens with the backend.
- FOSS builds use an authenticated WebSocket transport against the same backend API.

Google and Firebase dependencies are allowed only in Play configurations. `verifyFossNoGooglePushDependencies` prevents accidental inclusion in the FOSS runtime.

## Settings sync

Settings backup and remote sync use explicit format versions and revision checks. Device-bound authentication and push credentials are excluded from exported settings payloads.

## Compatibility

Android and backend release independently. Breaking server changes require a new versioned route or a coordinated release window. Clients ignore unknown JSON fields, and required fields must not change meaning without a contract change.

The backend URL and certificate pins are build-time values, not user-editable runtime settings.
