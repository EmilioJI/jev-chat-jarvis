package com.jev.probe.debug

class ProfileAService : AccessibilityProbeBaseService() {
    override val profileName: String = "A_CURRENT_STRICT"
}

class ProfileBService : AccessibilityProbeBaseService() {
    override val profileName: String = "B_HONEST_TOOL_FILTERED"
}

class ProfileCService : AccessibilityProbeBaseService() {
    override val profileName: String = "C_HONEST_TOOL_OPEN"
}
