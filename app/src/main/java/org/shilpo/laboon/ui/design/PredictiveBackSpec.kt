package org.shilpo.laboon.ui.design

data class PredictiveBackSpec(
    val scaleDepth: Float = DEFAULT_SCALE_DEPTH,
    val cornerDp: Float = DEFAULT_CORNER_DP,
    val shiftDp: Float = DEFAULT_SHIFT_DP,
) {
    companion object {
        const val DEFAULT_SCALE_DEPTH: Float = 0.08f
        const val DEFAULT_CORNER_DP: Float = 28f
        const val DEFAULT_SHIFT_DP: Float = 44f
        const val MIN_CLIP_CORNER_DP: Float = 0.5f

        val MainScreen: PredictiveBackSpec = PredictiveBackSpec()
        val HomeTab: PredictiveBackSpec = PredictiveBackSpec(
            scaleDepth = 0.08f,
            cornerDp = 24f,
            shiftDp = 40f,
        )
        val HomeSettings: PredictiveBackSpec = PredictiveBackSpec(
            scaleDepth = 0.10f,
            cornerDp = 32f,
            shiftDp = 56f,
        )

        val Table: List<PredictiveBackSpec> = listOf(MainScreen, HomeTab, HomeSettings)
    }
}

data class PredictiveBackGeometry(
    val scale: Float,
    val translationX: Float,
    val cornerDp: Float,
)

fun predictiveBackGeometry(
    spec: PredictiveBackSpec,
    progress: Float,
    swipeFromRight: Boolean,
    maxShiftPx: Float,
): PredictiveBackGeometry {
    val depth = progress.coerceIn(0f, 1f)
    return PredictiveBackGeometry(
        scale = 1f - depth * spec.scaleDepth,
        translationX = (if (swipeFromRight) -depth else depth) * maxShiftPx,
        cornerDp = depth * spec.cornerDp,
    )
}
