package io.ferventio.shared.settings

internal object SharedSettingsBackupInputGuard {
    const val MAX_BACKUP_FILE_BYTES = 1_048_576
    const val MAX_BACKUP_JSON_DEPTH = 64

    fun requireWithinLimits(raw: String) {
        require(raw.encodeToByteArray().size <= MAX_BACKUP_FILE_BYTES) {
            "Settings backup exceeds the 1 MiB UTF-8 limit"
        }
        require(raw.length <= MAX_BACKUP_FILE_BYTES) {
            "Settings backup exceeds the maximum character count"
        }

        var depth = 0
        var inString = false
        var escaping = false

        raw.forEach { char ->
            if (inString) {
                when {
                    escaping -> escaping = false
                    char == '\\' -> escaping = true
                    char == '"' -> inString = false
                }
                return@forEach
            }

            when (char) {
                '"' -> inString = true
                '{', '[' -> {
                    depth += 1
                    require(depth <= MAX_BACKUP_JSON_DEPTH) {
                        "Settings backup exceeds the maximum JSON nesting depth"
                    }
                }
                '}', ']' -> if (depth > 0) depth -= 1
            }
        }
    }
}
