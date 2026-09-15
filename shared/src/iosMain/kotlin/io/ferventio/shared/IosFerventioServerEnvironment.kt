package io.ferventio.shared

import platform.Foundation.NSBundle

/** Reads the same backend URL injected into the iOS app Info.plist for auth/push composition. */
internal fun currentIosFerventioServerUrl(): String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("FerventioServerURL") as? String)
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf(String::isNotEmpty)
