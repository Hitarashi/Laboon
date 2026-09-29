@file:Suppress("PropertyName")

package org.shilpo.laboon.ui.design.splash

data class Tuning(
    val slots: Slots = Slots(),
    val timings: Timings = Timings(),
    val spawn: Spawn = Spawn(),
    val physics: Physics = Physics(),
    val burst: Burst = Burst(),
    val effects: Effects = Effects(),
    val look: Look = Look(),
    val reveal: Reveal = Reveal(),
) {
    companion object {
        val Default: Tuning = Tuning()
    }

    data class Slots(val logo: Int = 56) {
        fun getSlotCount(): Int = logo
    }

    data class Timings(
        val GATHER_LOGO_MS: Float = 800f,
        val IGNITE_FULL_MS: Float = 200f,
        val IGNITE_SHORT_MS: Float = 120f,
        val BURST_FULL_MS: Float = 420f,
        val BURST_SHORT_MS: Float = 280f,
    ) {
        fun igniteDurationMs(isShort: Boolean): Float =
            if (isShort) IGNITE_SHORT_MS else IGNITE_FULL_MS

        fun burstDurationMs(isShort: Boolean): Float =
            if (isShort) BURST_SHORT_MS else BURST_FULL_MS
    }

    data class Spawn(
        val RING_INNER: Float = 140f,
        val RING_WIDTH: Float = 180f,
        val SPEED_BASE: Float = 0.25f,
        val SPEED_VAR: Float = 0.35f,
    )

    data class Physics(
        val SPRING_K_NORMAL: Float = 0.42f,
        val DAMPING_FORMING: Float = 0.80f,
        val DAMPING_BURST: Float = 0.985f,
        val DAMPING_FREE: Float = 0.972f,
        val SPEED_CLAMP_FORMING: Float = 20f,
        val SPEED_CLAMP_BURST: Float = 45f,
        val SPEED_CLAMP_FREE: Float = 9f,
        val FORM_GATHER: Float = 0.15f,
        val DAMP_ON_REGATHER: Float = 0.5f,
        val CONVERGE_DIST: Float = 2f,
        val wD: Float = 0.055f,
        val gD: Float = 0.05f,
        val vD: Float = 0.0022f,
        val bD: Float = 0.020f,
        val MAX_STEP: Float = 1.5f,
    )

    data class Burst(
        val EXPLODE_POWER: Float = 6.0f,
        val MEMBER_BOOST: Float = 1.6f,
        val FLOATER_BOOST: Float = 0.85f,
        val POST_BURST_FRAMES: Int = 130,
        val SHOCKWAVE_RADIUS_FACTOR: Float = 1.2f,
        val SHOCKWAVE_ALPHA_BURST: Float = 1.0f,
        val SNAP_SCALE: Float = 0.85f,
        val SNAP_MS: Float = 40f,
        val FRAMES_TO_CROSS: Float = 65f,
        val STROKE_WIDTH_DP: Float = 2.0f,
    )

    data class Effects(
        val PULSE_WAVE_SPEED: Float = 0.32f,
        val STAR_STAGGER_MS: Float = 60f,
        val STAR_STAGGER_SHORT_MS: Float = 34f,
        val PINCH_FACTOR: Float = 0.06f,
        val MAX_HALO_DP: Float = 28f,
        val LINK_DISTANCE_DP: Float = 150f,
        val LOGO_TARGET_SIZE_DP: Float = 170f,
    ) {
        fun starStaggerMs(isShort: Boolean): Float =
            if (isShort) STAR_STAGGER_SHORT_MS else STAR_STAGGER_MS
    }

    data class Look(
        val smooth: Smooth = Smooth(),
        val links: Links = Links(),
        val glow: Glow = Glow(),
        val halo: Halo = Halo(),
        val flash: Flash = Flash(),
        val cutoffs: Cutoffs = Cutoffs(),
    ) {
        data class Smooth(
            val STROKE_WIDTH_DP: Float = 2.8f,
            val ALPHA_BASE: Float = 0.72f,
            val FORM_THRESHOLD: Float = 0.72f,
            val APPEAR_RANGE: Float = 0.28f,
            val PARTICLE_OUTER_SCALE: Float = 5.5f,
            val PARTICLE_OUTER_DEPTH: Float = 2.5f,
        )

        data class Links(
            val LINE_WIDTH_DP: Float = 1.5f,
            val APPEAR_MIN_FORM: Float = 0.45f,
            val APPEAR_RANGE: Float = 0.55f,
            val RAW_ALPHA_FACTOR: Float = 0.72f,
            val WAVE_DIST_FACTOR: Float = 0.12f,
            val WAVE_BOOST_BIN: Float = 0.4f,
            val WAVE_BOOST_ALPHA: Float = 0.9f,
            val WAVE_BOOST_WIDTH: Float = 1.6f,
        )

        data class Glow(
            val HEIGHT_FACTOR: Float = 0.42f,
            val STOP_MID: Float = 0.22f,
            val SPRITE_ALPHAS: List<Float> = listOf(1f, 0.55f, 0.22f, 0.07f, 0.015f, 0f),
            val SPRITE_STOPS: List<Float> = listOf(0f, 0.08f, 0.2f, 0.42f, 0.72f, 1f),
            val STRENGTH_ALPHA_BASE: Float = 0.34f,
            val STRENGTH_ALPHA_MID: Float = 0.1f,
        )

        data class Halo(
            val STAR_BASE_HEIGHT_FACTOR: Float = 0.035f,
            val STAR_HALO_FACTOR: Float = 1.25f,
            val STAR_BODY_HALO_FACTOR: Float = 1.1f,
            val STAR_FLARE_ALPHA: Float = 0.35f,
            val STAR_SPRITE_ALPHA: Float = 0.45f,
            val OUTER_SPRITE_ALPHA: Float = 0.55f,
            val MEMBER_ALPHA_BASE: Float = 0.95f,
            val FLOATER_ALPHA_BASE: Float = 0.4f,
            val GLOW_MIX_BASE: Float = 0.45f,
            val GLOW_MIX_FACTOR: Float = 0.55f,
        )

        data class Flash(
            val DURATION_MS: Float = 120f,
            val MAX_ALPHA: Float = 0.25f,
            val RADIUS_DP: Float = 200f,
        )

        data class Cutoffs(
            val FORM_GLOW: Float = 0.005f,
            val FORM_LINKS: Float = 0.01f,
            val RAW_ALPHA_LINKS: Float = 0.01f,
            val PARTICLE_ALPHA: Float = 0.005f,
            val STAR_ALPHA: Float = 0.005f,
            val FLASH_ALPHA: Float = 0.005f,
        )
    }

    data class Reveal(
        val START_FRACTION: Float = 0.15f,
        val DURATION_MS: Int = 450,
        val RISE_DP: Float = 24f,
        val FADE_MS: Int = 250,
    )
}
