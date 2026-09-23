package com.google.android.accessibility.selecttospeak

import com.jev.probe.debug.AccessibilityProbeBaseService

/**
 * Debug-only reproduction of the historical component identity.
 * No production code should depend on this component.
 */
class SelectToSpeakService : AccessibilityProbeBaseService() {
    override val profileName: String = "D_LEGACY_IDENTITY_OPEN"
}
