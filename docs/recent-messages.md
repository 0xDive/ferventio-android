# Recent Twitch messages

When the user explicitly enables the feature in Settings → History, Ferventio fills a newly opened
chat with a public recent-message snapshot while the normal live EventSub or anonymous IRC
connection starts in parallel. The feature is disabled by default because it contacts a third-party
service.

## Data flow

1. Opening or making a channel visible schedules one request for its Twitch login.
2. The client requests up to 100 raw IRC rows from the configured Recent Messages endpoint.
3. `TwitchIrcParser` parses the snapshot through the same message rules used by the live IRC
   transport, including Twitch emotes, replies, actions, channel-point messages and moderation
   rows. Rows already marked with `rm-deleted=1` stay deleted even if the matching moderation row
   falls outside the requested window.
4. `RecentMessagesMerge` combines the snapshot with already received live/local rows by Twitch
   message ID. Existing live rows win duplicate IDs and the result is sorted by Twitch timestamp.
5. Historical rows are inserted directly into chat state. They do not pass through `appendMessage`,
   so they cannot create unread counters, sounds, push notifications or reply alerts.
6. When local history is enabled, newly obtained rows are cached in Room for later startup and
   message-context navigation.

The feature is deliberately best-effort. Endpoint failures are written through `SafeLog` and never
interrupt the live chat connection.

## Consent and privacy

The settings page explains that the integration contacts a third-party service, links to the service
home page, and provides an opt-in toggle. Requests disclose the channel login and the device IP
address to the service. Turning the toggle off cancels in-flight snapshot requests and prevents new
ones.

## Configuration

The default endpoint is:

```text
https://recent-messages.robotty.de/api/v2/recent-messages
```

A self-hosted compatible endpoint can be selected at build time:

```properties
FERVENTIO_RECENT_MESSAGES_URL=https://example.org/api/v2/recent-messages
```

The configured URL must not include a channel name. Ferventio appends the validated lowercase
Twitch login and the `limit` query parameter.

## Safety and resource limits

- Twitch logins are restricted to lowercase letters, digits and underscores.
- Request, connect and socket timeouts are finite.
- Responses larger than 2 MiB are rejected.
- At most three snapshot requests run concurrently, with a small randomized start delay.
- Failed channels have a one-minute retry cooldown.
- Only parsed Twitch chat events affect the snapshot; malformed rows are ignored.
