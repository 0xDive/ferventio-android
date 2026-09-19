# Multiplatform RC device smoke test

Run this checklist on at least one physical Android device and one physical iPhone before promoting a Compose Multiplatform migration build to release-candidate status. The final release-evidence run is performed after the migration PR is merged, using the exact `main` commit that will be released.

Record the tested commit SHA, app version/build number, device model, OS version, backend environment and whether the Android build is FOSS or Play. Start from a clean install once per platform, then repeat the lifecycle/recovery section with an existing authenticated session.

## Build prerequisites

- The migration PR has been merged and Android CI plus iOS KMP CI are green for the exact `main` commit under test.
- iOS CI has passed both the simulator build and the unsigned `generic/platform=iOS` arm64 device build.
- Physical iPhone testing uses a normally signed development/ad-hoc build so APNs and entitlements are exercised; the unsigned CI device build only proves compile/link compatibility.
- Production-like backend/privacy configuration is set without committing credentials or signing material.

## Startup and authentication

- Launch from a clean install and verify the shared startup/authentication UI renders correctly.
- Sign in through Twitch/backend authorization and verify the app reaches the expected workspace.
- Force-quit and relaunch; the authenticated session and workspace restore without an auth loop.
- Reauthorize the same account and verify chat resumes without duplicate connections.
- Sign out and verify authenticated chat/push state is cleared; relaunch stays signed out.
- Enter signed-out/anonymous mode, add/select a channel, relaunch and verify the local workspace restores.

## Live chat and workspace

- Receive live chat messages on the selected channel; emotes, badges, links and replies render.
- Switch channels repeatedly while messages are active; no messages appear under the wrong channel.
- Add a channel and remove another channel in quick succession; the final workspace and live subscriptions match the final channel set.
- Exercise split/channel selection if the account has a multi-split layout; the selected channel and persisted layout remain consistent after relaunch.
- Use Diagnostics → Reconnect while authenticated; chat disconnects/reconnects once and resumes without duplicate message delivery.
- Confirm the reconnect action is unavailable in anonymous mode.

## Network and lifecycle recovery

- With live chat connected, disable network connectivity and restore it; chat and push/backend synchronization recover without an auth loop.
- Trigger offline → online while immediately backgrounding the app; no authenticated transport restarts while backgrounded.
- Return to foreground; exactly the current foreground generation recovers chat, with no duplicate EventSub session.
- Rapidly background → foreground → background → foreground while connected; the final state is connected only for the latest active scene.
- Leave the app backgrounded long enough to cross the access-lease refresh window, then foreground it; authentication refresh and chat recovery complete once.

## Settings, history and sync

- Change appearance presets, chat settings, User Card moderation order/presets and history limits; relaunch and verify persistence.
- If another signed-in device is available, change synced settings there and refresh/restore on the device under test; the latest revision wins without reverting the workspace.
- Verify recent history restores after relaunch and that search/context navigation returns the expected message/channel.
- Import or restore a settings backup/revision that changes the channel set; chat subscriptions and visible workspace follow the restored revision.

## Notifications and device integrations

### Android

- FOSS: verify the build has no Google push dependency and the configured non-Play notification path still behaves as expected.
- Play: verify notification permission, FCM registration and a notification tap into the expected destination.

### iPhone

- Request notification permission and verify APNs registration succeeds on a signed physical-device build.
- Receive a background notification and open it; navigation is correct and foreground presentation does not create duplicate chat recovery.
- Open a channel-target notification and verify the shared workspace selects the intended channel, including a payload that resolves the target by channel login when practical.
- Open a message-target notification for a message that is not currently in the live buffer; verify history context loads, the correct channel opens and message navigation targets the requested message.
- Open Settings and Mentions/attention notification targets and verify the corresponding shared surfaces open instead of only switching channels.
- If the backend fixture supports a moderation target, open it and verify the intended channel is selected for contextual moderation.
- While a message-target context load is still in flight, open a newer notification when practical; the newer target must win and the older suspended navigation must not overwrite it afterward.
- Deny notification permission, relaunch, and verify the app remains usable and Settings can open system notification settings.

## Moderation and account-sensitive actions

Use a test broadcaster/moderator account only.

- Open User Card and verify timeout, ban/unban, warn and delete-message availability matches the current role/target.
- Execute one safe moderation mutation and verify the timeline/state updates once.
- If Polls/Predictions are enabled for the test account, perform a non-destructive create/end flow and verify EventSub reconciliation.

## Release blockers

Do not promote the commit to RC if any of these occur:

- crash or startup failure on either physical platform;
- authentication loop, credentials restored after sign-out, or recovery while the app is backgrounded;
- duplicate EventSub/chat connections or duplicate message delivery after reconnect/foreground/network recovery;
- stale channel/workspace state after rapid add/remove, restore or cross-device sync;
- missing Compose resources/localization on the physical iPhone build;
- broken notification registration/navigation on the signed device build, including stale notification navigation overwriting a newer target;
- settings/history data loss or a newer synced revision being replaced by an older one.

Attach the completed checklist and tested commit SHA to a pull-request or issue comment and keep that exact comment permalink (`#issuecomment-…`) as the smoke-report evidence. The report comment must explicitly contain the full tested SHA.

The Android Release workflow resolves the permalink through the GitHub API, verifies that the comment exists in this repository, verifies that its permalink/thread match the supplied URL, and verifies that the comment body contains the same tested SHA. A bare PR/issue URL is not accepted.

The release workflow only accepts smoke evidence for the exact `main` commit being released. Do not use a PR-head smoke report as final release evidence when the merge produces a different SHA. If any follow-up change is committed after the final smoke run, run the Android + iPhone matrix again on that new exact `main` commit; do not reuse earlier evidence.
