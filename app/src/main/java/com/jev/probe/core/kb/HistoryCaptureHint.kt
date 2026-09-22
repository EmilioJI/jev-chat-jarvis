package com.jev.probe.core.kb

/**
 * Evidence about where a captured screen sits in the conversation timeline.
 *
 * CONSERVATIVE is the safe default for manual reads, window opens and scrolling:
 * a zero-overlap screen may be old history, so it must not be appended.
 *
 * NEWEST_SCREEN is reserved for an automatic analysis triggered by a real
 * content-change event whose newest visible message is from the other person.
 * In that case a zero-overlap screen after a long absence is allowed to advance
 * history instead of being mistaken for a scroll into old messages.
 */
enum class HistoryCaptureHint {
    CONSERVATIVE,
    NEWEST_SCREEN
}
