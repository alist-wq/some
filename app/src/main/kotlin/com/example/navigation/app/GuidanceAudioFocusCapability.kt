package com.example.navigation.app

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import com.example.navigation.core.MonotonicTimestamp

/** Platform-neutral focus result, kept small so lifecycle policy remains unit-testable. */
enum class GuidanceAudioFocusResult { GRANTED, DELAYED, FAILED }
enum class GuidanceAudioFocusEvent { GAINED, TRANSIENT_LOSS, PERMANENT_LOSS }

interface GuidanceAudioFocusPlatform : AutoCloseable {
    fun requestFocus(listener: (GuidanceAudioFocusEvent, MonotonicTimestamp) -> Unit): GuidanceAudioFocusResult
    fun abandonFocus()
    fun setOutputRouteListener(listener: (available: Boolean, timestamp: MonotonicTimestamp) -> Unit)
    override fun close() = Unit
}

/**
 * Read-only output state for presentation. Audio focus is never treated as a navigation,
 * trust, or route-progress signal.
 */
data class GuidanceOutputCapability(
    val audio: AudioInterruptionState,
    val timestamp: MonotonicTimestamp,
    val guidanceOutputActive: Boolean,
    val visualOnly: Boolean,
    val reason: String? = null
)

/**
 * Owns focus only while actual app guidance output is active. Callers explicitly opt into
 * visual-only continuation; focus loss never pauses, reroutes, or changes estimation state.
 */
class GuidanceAudioFocusOwner(private val platform: GuidanceAudioFocusPlatform) : AutoCloseable {
    private var outputActive = false
    private var visualOnly = false
    private var outputRouteAvailable: Boolean? = null
    private var state = GuidanceOutputCapability(
        AudioInterruptionState.AVAILABLE, MonotonicTimestamp(0), guidanceOutputActive = false, visualOnly = false
    )

    init {
        platform.setOutputRouteListener(::onOutputRouteChanged)
    }

    fun setGuidanceOutputActive(active: Boolean, timestamp: MonotonicTimestamp): GuidanceOutputCapability {
        outputActive = active
        if (!active) {
            platform.abandonFocus()
            return update(AudioInterruptionState.AVAILABLE, timestamp, null)
        }
        if (visualOnly) return update(AudioInterruptionState.SILENT, timestamp, "Visual guidance selected")
        if (outputRouteAvailable == false) {
            return update(AudioInterruptionState.ROUTE_INTERRUPTED, timestamp, "No audio output route is available")
        }
        return requestFocus(timestamp)
    }

    fun continueSilently(timestamp: MonotonicTimestamp): GuidanceOutputCapability {
        visualOnly = true
        platform.abandonFocus()
        return update(AudioInterruptionState.SILENT, timestamp, "Visual guidance selected")
    }

    fun restoreAudio(timestamp: MonotonicTimestamp): GuidanceOutputCapability {
        visualOnly = false
        return if (outputActive) requestFocus(timestamp) else update(AudioInterruptionState.AVAILABLE, timestamp, null)
    }

    fun snapshot(): GuidanceOutputCapability = state

    override fun close() {
        platform.abandonFocus()
        platform.close()
    }

    private fun requestFocus(timestamp: MonotonicTimestamp): GuidanceOutputCapability = when (platform.requestFocus(::onFocusChanged)) {
        GuidanceAudioFocusResult.GRANTED -> update(AudioInterruptionState.AVAILABLE, timestamp, null)
        GuidanceAudioFocusResult.DELAYED -> update(AudioInterruptionState.FOCUS_LOST, timestamp, "Audio focus is delayed")
        GuidanceAudioFocusResult.FAILED -> update(AudioInterruptionState.UNAVAILABLE, timestamp, "Audio focus request was denied")
    }

    private fun onFocusChanged(event: GuidanceAudioFocusEvent, timestamp: MonotonicTimestamp) {
        if (!outputActive || visualOnly) return
        when (event) {
            GuidanceAudioFocusEvent.GAINED -> update(AudioInterruptionState.AVAILABLE, timestamp, null)
            GuidanceAudioFocusEvent.TRANSIENT_LOSS -> update(AudioInterruptionState.FOCUS_LOST, timestamp, "Audio focus is temporarily lost")
            GuidanceAudioFocusEvent.PERMANENT_LOSS -> update(AudioInterruptionState.FOCUS_LOST, timestamp, "Audio focus is permanently lost")
        }
    }

    private fun onOutputRouteChanged(available: Boolean, timestamp: MonotonicTimestamp) {
        outputRouteAvailable = available
        if (outputActive && !visualOnly) update(
            if (available) AudioInterruptionState.AVAILABLE else AudioInterruptionState.ROUTE_INTERRUPTED,
            timestamp,
            if (available) null else "No audio output route is available"
        )
    }

    private fun update(audio: AudioInterruptionState, timestamp: MonotonicTimestamp, reason: String?): GuidanceOutputCapability =
        GuidanceOutputCapability(audio, timestamp, outputActive, visualOnly, reason).also { state = it }
}

/** Android AudioManager bridge. It reports platform capability only and performs no playback. */
class AndroidGuidanceAudioFocusPlatform(private val audioManager: AudioManager) : GuidanceAudioFocusPlatform {
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build()
    private var request: AudioFocusRequest? = null
    private var listener: ((Boolean, MonotonicTimestamp) -> Unit)? = null
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = reportOutputRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = reportOutputRoute()
    }

    override fun requestFocus(listener: (GuidanceAudioFocusEvent, MonotonicTimestamp) -> Unit): GuidanceAudioFocusResult {
        val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            val event = when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> GuidanceAudioFocusEvent.GAINED
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> GuidanceAudioFocusEvent.TRANSIENT_LOSS
                else -> GuidanceAudioFocusEvent.PERMANENT_LOSS
            }
            listener(event, now())
        }
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
        return when (audioManager.requestAudioFocus(requireNotNull(request))) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> GuidanceAudioFocusResult.GRANTED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> GuidanceAudioFocusResult.DELAYED
            else -> GuidanceAudioFocusResult.FAILED
        }
    }

    override fun abandonFocus() {
        request?.let(audioManager::abandonAudioFocusRequest)
        request = null
    }

    override fun setOutputRouteListener(listener: (Boolean, MonotonicTimestamp) -> Unit) {
        this.listener = listener
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        reportOutputRoute()
    }

    override fun close() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    private fun reportOutputRoute() {
        val available = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).isNotEmpty()
        listener?.invoke(available, now())
    }

    private fun now() = MonotonicTimestamp(SystemClock.elapsedRealtimeNanos())
}
