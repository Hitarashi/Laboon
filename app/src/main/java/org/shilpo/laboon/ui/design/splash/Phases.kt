package org.shilpo.laboon.ui.design.splash

sealed interface Phase {
    data object Gather : Phase
    data object Ignite : Phase
    data object Burst : Phase
    data object Idle : Phase
}

enum class DismissReason { Settled }

sealed interface FrameOutcome {
    data object StillRunning : FrameOutcome

    data class BurstReveal(val progress: Float) : FrameOutcome

    data class Finished(val reason: DismissReason) : FrameOutcome
}
