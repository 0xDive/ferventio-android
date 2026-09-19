package io.ferventio.shared.settings

import platform.Foundation.NSUserDefaults

/** iOS device-local saved-filter storage using the Android-compatible JSON contract. */
class IosAnonymousSavedFiltersStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AnonymousSavedFiltersStore {
    override fun load(): String? = defaults.stringForKey(ANONYMOUS_SAVED_FILTERS_KEY)

    override fun save(raw: String) {
        defaults.setObject(raw, forKey = ANONYMOUS_SAVED_FILTERS_KEY)
    }
}
