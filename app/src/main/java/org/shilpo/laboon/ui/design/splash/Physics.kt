package org.shilpo.laboon.ui.design.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Physics {
    fun noiseField(x: Float, y: Float, seed: Float): Float {
        val phase = seed * PI.toFloat() * 2f
        return sin(x * 1.37f + y * 0.71f + phase) * 0.5f +
                sin(x * 2.13f - y * 1.17f + phase * 1.83f) * 0.3f +
                sin(x * 0.61f + y * 1.93f + phase * 0.47f) * 0.2f
    }

    fun formationK(formStrength: Float, physics: Tuning.Physics): Float =
        physics.SPRING_K_NORMAL * formStrength

    fun damping(
        isForming: Boolean,
        isBursting: Boolean,
        step: Float,
        physics: Tuning.Physics
    ): Float = when {
        isBursting -> physics.DAMPING_BURST.pow(step)
        isForming -> physics.DAMPING_FORMING.pow(step)
        else -> physics.DAMPING_FREE.pow(step)
    }

    fun driftMagnitude(depth: Float, physics: Tuning.Physics): Float =
        physics.bD * (0.5f + depth * 0.5f)

    fun speedClamp(
        isBursting: Boolean,
        isForming: Boolean,
        physics: Tuning.Physics
    ): Float = when {
        isBursting -> physics.SPEED_CLAMP_BURST
        isForming -> physics.SPEED_CLAMP_FORMING
        else -> physics.SPEED_CLAMP_FREE
    }

    fun applyFormationForces(
        p: Particle,
        dt: Float,
        isForming: Boolean,
        formStrength: Float,
        physics: Tuning.Physics,
        isBursting: Boolean = false,
        time: Float = 0f,
        dampMember: Float = -1f,
        dampFloater: Float = -1f
    ): Particle {
        val step = (dt * 60f).coerceIn(0.5f, physics.MAX_STEP)
        val isMember = p.isMember || (p.targetX != 0f || p.targetY != 0f)

        if (!isBursting && isMember && (isForming || formStrength > 0f)) {
            val tx = p.targetX
            val ty = p.targetY

            val dx = tx - p.x
            val dy = ty - p.y
            val distSq = dx * dx + dy * dy

            if (distSq < 0.25f) {
                p.x = tx
                p.y = ty
                p.vx = 0f
                p.vy = 0f
                p.isLocked = true
                return p
            }

            p.isLocked = false
            val springForce = formationK(formStrength, physics)
            val friction = if (dampMember >= 0f) dampMember else damping(
                isForming = true,
                isBursting = false,
                step = step,
                physics = physics
            )

            p.vx = (p.vx + dx * springForce) * friction
            p.vy = (p.vy + dy * springForce) * friction

            val m = speedClamp(false, isForming, physics)
            val speedSq = p.vx * p.vx + p.vy * p.vy
            if (speedSq > m * m && speedSq > 0f) {
                val scale = m / sqrt(speedSq)
                p.vx *= scale
                p.vy *= scale
            }

            p.x += p.vx * step
            p.y += p.vy * step
        } else {
            p.isLocked = false
            val angle =
                noiseField(
                    (p.x + p.y * 0.7f) * physics.vD,
                    time * physics.gD,
                    p.seed
                ) * PI.toFloat()
            val drift = driftMagnitude(p.depth, physics)
            val noiseAx = cos(angle) * drift
            val noiseAy = sin(angle) * drift

            val damp =
                if (isBursting) physics.DAMPING_BURST.pow(step) else (if (dampFloater >= 0f) dampFloater else damping(
                    isForming = false,
                    isBursting = false,
                    step = step,
                    physics = physics
                ))
            p.vx = (p.vx + noiseAx * step) * damp
            p.vy = (p.vy + noiseAy * step) * damp

            val m = speedClamp(isBursting, isForming, physics)
            val speedSq = p.vx * p.vx + p.vy * p.vy
            if (speedSq > m * m && speedSq > 0f) {
                val scale = m / sqrt(speedSq)
                p.vx *= scale
                p.vy *= scale
            }

            p.x += p.vx * step
            p.y += p.vy * step
        }
        return p
    }
}
