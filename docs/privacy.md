# Ferventio Privacy Policy

**Effective date:** 1 August 2026
**App:** Ferventio for Android

The authoritative policy is published at the configured HTTPS privacy-policy URL. An offline in-app copy can optionally be exposed under **Settings → About → Privacy Policy** for builds that enable it.

Before publishing a release, configure these values in `~/.gradle/gradle.properties` or CI secrets:

```properties
FERVENTIO_PRIVACY_OPERATOR_NAME=Legal name of the distributor or operator
FERVENTIO_PRIVACY_CONTACT=privacy@example.com
FERVENTIO_PRIVACY_POLICY_URL=https://example.com/ferventio/privacy
FERVENTIO_SHOW_PRIVACY_POLICY_IN_APP=false
```

The release build verifies that the operator and contact are present and that the published policy uses HTTPS. Set `FERVENTIO_SHOW_PRIVACY_POLICY_IN_APP=true` only when the offline policy should also be visible in the app UI.

## Data handled on the device

Ferventio may store channel lists, settings, drafts, mentions, filter rules, scroll positions, image caches and local chat history. Local history can include message text, authors, badges, fragments and moderation state. The retention period is controlled by the user.

OAuth tokens are excluded from user backups. The opaque Ferventio server session, device secret and current Twitch access token are encrypted with a key held by Android Keystore. The Twitch refresh token remains only on the configured Ferventio Server. The cached access token lets an already-authorized installation continue direct Twitch operations during a temporary server outage until Twitch expires or revokes that token.

FOSS builds do not automatically upload crash reports. Sanitized reports are kept in private app storage, with a maximum of 20 reports for 30 days, and can only be exported by the user. Production Play builds may use Firebase Crashlytics; debug, benchmark and FOSS builds do not enable automatic Crashlytics collection.

## Network processing

Ferventio communicates with Twitch IRC, EventSub and Helix for chat, account metadata and moderation. It may also request public emote, badge and metadata resources from BetterTTV, FrankerFaceZ, 7TV, IVR.fi and their CDNs. Those providers receive normal network metadata such as the requesting IP address.

Depending on the build, push notifications use Firebase Cloud Messaging or an embedded socket transport. A configured Ferventio Server may process an installation ID, device-bound credentials, push registration, selected channels, Twitch access-token leases, settings-sync snapshots and security/audit metadata.

## Sharing and sale

Ferventio does not sell personal data, display advertising or use the Android Advertising ID. Data is shared only with services required for selected features: Twitch, the configured Ferventio Server, public emote/metadata providers and, for Play releases, Firebase Cloud Messaging and Crashlytics.

A file manually exported through the Android system picker is sent to the app or storage provider selected by the user. Ferventio does not control that provider's subsequent handling.

## User controls

The app provides controls for clearing history and caches, exporting/importing settings, deleting local FOSS crash reports, signing out, revoking the current device and revoking all server sessions. Revoking all sessions removes server auth sessions, credentials, push registrations and pending deliveries; local data remains until deleted separately.

For access, correction or deletion of data retained by a specific Ferventio Server, contact that server's operator using the privacy contact displayed in the app or on the distribution page.

## Changes

Material changes must update the published web copy and, when the in-app copy is enabled, the bundled policy text. The policy version is tied to the effective date shown above.
