package io.ferventio.shared.settings

import platform.Foundation.NSUserDefaults

/** iOS device-local highlight/ignore storage using the Android-compatible JSON contract. */
class IosAnonymousMessageRulesStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AnonymousMessageRulesStore {
    override fun load(): AnonymousMessageRulesRaw = AnonymousMessageRulesRaw(
        highlights = defaults.stringForKey(ANONYMOUS_HIGHLIGHT_RULES_KEY),
        ignores = defaults.stringForKey(ANONYMOUS_IGNORE_RULES_KEY),
    )

    override fun save(raw: AnonymousMessageRulesRaw) {
        defaults.setObject(raw.highlights, forKey = ANONYMOUS_HIGHLIGHT_RULES_KEY)
        defaults.setObject(raw.ignores, forKey = ANONYMOUS_IGNORE_RULES_KEY)
    }
}
