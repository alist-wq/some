package com.example.navigation.app

import com.example.navigation.replay.PedestrianEventLabel
import com.example.navigation.replay.PedestrianPoseLabel

data class PedestrianRecordingSetupState(
    val initialPose: PedestrianPoseLabel = PedestrianPoseLabel.UNKNOWN,
    val intendedStart: String = "",
    val intendedDestination: String = "",
    val routeReference: String = "",
    val notes: String = "",
    val consentConfirmed: Boolean = false,
    val anonymized: Boolean = true
) {
    val canStart: Boolean get() = consentConfirmed && routeReference.isNotBlank()
}

data class PedestrianRecordingPresentation(
    val recording: Boolean,
    val currentPose: PedestrianPoseLabel,
    val lastEvent: PedestrianEventLabel? = null,
    val confirmation: String? = null
)

object PedestrianRecordingReducer {
    fun pose(state: PedestrianRecordingPresentation, pose: PedestrianPoseLabel) =
        state.copy(currentPose = pose, confirmation = "Pose saved: ${pose.name}")
    fun event(state: PedestrianRecordingPresentation, event: PedestrianEventLabel) =
        state.copy(lastEvent = event, confirmation = "Event saved: ${event.name}")
}
