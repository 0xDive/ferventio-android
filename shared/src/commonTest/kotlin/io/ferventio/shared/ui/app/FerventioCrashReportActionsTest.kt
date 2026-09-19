package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FerventioCrashReportActionsTest {
    @Test
    fun localCrashReportsAreUnavailableByDefault() {
        assertFalse(FerventioCrashReportActions().localCrashReportsAvailable)
    }

    @Test
    fun localCrashReportsRequireExportAndClearActions() {
        val export = {}
        val clear = {}

        assertFalse(
            FerventioCrashReportActions(onExport = export).localCrashReportsAvailable,
        )
        assertFalse(
            FerventioCrashReportActions(onClear = clear).localCrashReportsAvailable,
        )
        assertTrue(
            FerventioCrashReportActions(
                onExport = export,
                onClear = clear,
            ).localCrashReportsAvailable,
        )
    }
}
