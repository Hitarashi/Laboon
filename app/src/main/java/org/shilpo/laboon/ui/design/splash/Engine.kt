package org.shilpo.laboon.ui.design.splash

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class Engine(
    val tuning: Tuning = Tuning.Default,
    val isShort: Boolean = false,
    private var contourSource: ContourSource = Contour.svg(Slots.LOGO_PATH, null),
) {
    val particles = ArrayList<Particle>(MAX_MEMBERS)
    var shockwave: Shockwave? = null

    var currentPhase: Phase = Phase.Gather

    var shape: String = Slots.SHAPE_LOGO
    var width: Float = 0f
    var height: Float = 0f
    var density: Float = 1f

    var formStrength: Float = 0f
    var postBurstFrames: Int = 0
    var pulseWave: Float = 0f
    var particleScale: Float = 1f
    var particleAlpha: Float = 1f
    var currentShapeData: Slots.ShapeSlots =
        Slots.empty(tuning.slots.getSlotCount(shape))
    private var slots: List<Offset> = emptyList()
    var phaseElapsedMs: Float = 0f
        private set

    var burstRevealProgress: Float = 0f
        private set
    private var revealReported = false
    private var dismissReported = false

    var customVectorPath: android.graphics.Path? = null
        private set

    val burstWindowMs: Float get() = tuning.timings.burstDurationMs(isShort)
    val igniteWindowMs: Float get() = tuning.timings.igniteDurationMs(isShort)
    val starStaggerMs: Float get() = tuning.effects.starStaggerMs(isShort)

    fun init(w: Float, h: Float) {
        init(w, h, density)
    }

    fun init(w: Float, h: Float, d: Float) {
        width = w
        height = h
        density = d
        particles.clear()

        rebuildSlots()

        val center = Slots.center(w, h)
        val activeMembers = tuning.slots.getSlotCount(shape).coerceIn(12, MAX_MEMBERS)

        for (i in 0 until activeMembers) {
            val r = tuning.spawn.RING_INNER + Random.nextFloat() * tuning.spawn.RING_WIDTH
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val cosA = cos(angle)
            val sinA = sin(angle)
            val startX = center.x + cosA * r
            val startY = center.y + sinA * r

            val speed = tuning.spawn.SPEED_BASE + Random.nextFloat() * tuning.spawn.SPEED_VAR
            val startVx = cosA * speed
            val startVy = sinA * speed

            val depth = Random.nextFloat()
            val seed = Random.nextFloat()
            val isRare = Random.nextFloat() < tuning.physics.wD

            val baseRadius = Particle.baseRadiusFor(depth, seed, isRare)
            val pulse = Particle.pulseFor(seed)
            val breath = Particle.breathFor(pulse)
            val lum = Particle.lumFor(seed, isRare)
            val phaseAngle = Random.nextFloat() * (Math.PI.toFloat() * 2f)

            particles.add(
                Particle(
                    x = startX,
                    y = startY,
                    vx = startVx,
                    vy = startVy,
                    baseRadius = baseRadius,
                    radius = baseRadius,
                    depth = depth,
                    seed = seed,
                    pulse = pulse,
                    phase = phaseAngle,
                    breath = breath,
                    lum = lum,
                    ring = i % 2,
                    isMember = true,
                    isRare = isRare
                )
            )
        }
        bindSlots()
        setPhase(Phase.Gather)
    }

    fun rebuildSlots() {
        if (width <= 0f || height <= 0f) return
        currentShapeData = contourSource.slots(
            width = width,
            height = height,
            density = density,
            totalSlots = tuning.slots.getSlotCount(shape),
            logoTargetSizeDp = tuning.effects.LOGO_TARGET_SIZE_DP,
        ) ?: Slots.empty(tuning.slots.getSlotCount(shape))
        bindSlots()
    }

    fun setCustomVectorPath(path: android.graphics.Path?) {
        customVectorPath = path
        contourSource = Contour.svg(Slots.LOGO_PATH, path)
        rebuildSlots()
    }

    private fun bindSlots() {
        slots = currentShapeData.slots
        if (slots.isEmpty()) return

        val targetMemberCount = minOf(slots.size, MAX_MEMBERS)
        for (i in 0 until minOf(particles.size, MAX_MEMBERS)) {
            particles[i].isMember = (i < targetMemberCount)
            if (i >= targetMemberCount) {
                particles[i].slotIndex = -1
            }
        }

        val memberIndices = (0 until minOf(particles.size, targetMemberCount)).toList()
        if (memberIndices.isEmpty()) return

        val memberPositions = memberIndices.map { Offset(particles[it].x, particles[it].y) }
        val assignment = Slots.zdGreedyCompile(memberPositions, slots)

        for (i in memberIndices.indices) {
            val pIdx = memberIndices[i]
            val slotIdx = assignment.getOrElse(i) { -1 }
            val target = if (slotIdx in slots.indices) {
                slots[slotIdx]
            } else {
                Slots.center(width, height)
            }
            val p = particles[pIdx]
            p.targetX = target.x
            p.targetY = target.y
            p.slotIndex = slotIdx
        }
    }

    fun setPhase(newPhase: Phase) {
        if (currentPhase == Phase.Burst && newPhase == Phase.Burst) return
        currentPhase = newPhase
        phaseElapsedMs = 0f
        particleScale = 1f
        particleAlpha = if (newPhase == Phase.Idle) 0f else 1f
        if (newPhase == Phase.Gather) {
            burstRevealProgress = 0f
            revealReported = false
            dismissReported = false
        }
        when (newPhase) {
            Phase.Gather -> {
                formStrength = tuning.physics.FORM_GATHER
            }

            Phase.Ignite -> {
                formStrength = 1f
            }

            Phase.Burst -> {
                val c = Slots.center(width, height)
                shock(c.x, c.y, tuning.burst.SHOCKWAVE_ALPHA_BURST)
                explode(c, power = tuning.burst.EXPLODE_POWER)
            }

            Phase.Idle -> {
                formStrength = 0f
            }
        }
    }

    fun update(dt: Float, currentTimeMs: Long): FrameOutcome {
        val step = (dt * 60f).coerceIn(0.5f, tuning.physics.MAX_STEP)
        phaseElapsedMs += dt * 1000f

        if (postBurstFrames > 0) postBurstFrames--

        when (currentPhase) {
            Phase.Gather -> {
                val gatherLimit = tuning.timings.GATHER_LOGO_MS
                formStrength = min(1f, phaseElapsedMs / gatherLimit)
                var memberCount = 0
                var totalDist = 0f
                for (i in particles.indices) {
                    val p = particles[i]
                    if (p.isMember && p.slotIndex >= 0) {
                        memberCount++
                        totalDist += hypot(p.x - p.targetX, p.y - p.targetY)
                    }
                }
                val converged =
                    memberCount > 0 && (totalDist / memberCount) < tuning.physics.CONVERGE_DIST
                if (converged || phaseElapsedMs >= gatherLimit) {
                    setPhase(Phase.Ignite)
                }
            }

            Phase.Ignite -> {
                formStrength = 1f
                val igniteLimit = igniteWindowMs
                val igniteProgress = (phaseElapsedMs / igniteLimit).coerceIn(0f, 1f)
                val pinch =
                    1f - sin(igniteProgress * Math.PI.toFloat()) * tuning.effects.PINCH_FACTOR
                val c = Slots.center(width, height)
                for (i in particles.indices) {
                    val p = particles[i]
                    if (!p.isMember || p.slotIndex !in slots.indices) continue
                    val baseSlot = slots[p.slotIndex]
                    p.targetX = c.x + (baseSlot.x - c.x) * pinch
                    p.targetY = c.y + (baseSlot.y - c.y) * pinch
                }
                if (phaseElapsedMs >= igniteLimit) {
                    setPhase(Phase.Burst)
                }
            }

            Phase.Burst -> {
                formStrength = max(0f, 1f - phaseElapsedMs / 200f)
                val burstLimit = burstWindowMs
                val snap =
                    if (phaseElapsedMs <= tuning.burst.SNAP_MS) tuning.burst.SNAP_SCALE else 1f
                particleScale = max(0.2f, 1f - phaseElapsedMs / burstLimit) * snap
                burstRevealProgress = (phaseElapsedMs / burstLimit).coerceIn(0f, 1f)
                if (phaseElapsedMs >= burstLimit) {
                    formStrength = 0f
                    setPhase(Phase.Idle)
                }
            }

            Phase.Idle -> {
            }
        }

        shockwave?.let { sw ->
            val newR = sw.radius + sw.maxRadius / tuning.burst.FRAMES_TO_CROSS * step
            if (newR >= sw.maxRadius) {
                shockwave = null
            } else {
                sw.radius = newR
            }
        }

        if (formStrength > 0.5f) {
            pulseWave = (pulseWave + dt * tuning.effects.PULSE_WAVE_SPEED) % 1.0f
        }

        val isForming = currentPhase == Phase.Gather || currentPhase == Phase.Ignite
        val isBursting = currentPhase == Phase.Burst || postBurstFrames > 0
        val timeSec = currentTimeMs / 1000f
        val center = Slots.center(width, height)

        val dampMember = tuning.physics.DAMPING_FORMING.pow(step)
        val dampFloater = tuning.physics.DAMPING_FREE.pow(step)

        for (i in particles.indices) {
            val p = particles[i]
            Physics.applyFormationForces(
                p = p,
                dt = dt,
                isForming = isForming,
                formStrength = formStrength,
                isBursting = isBursting,
                time = timeSec,
                centerX = center.x,
                centerY = center.y,
                physics = tuning.physics,
                dampMember = dampMember,
                dampFloater = dampFloater
            )

            p.radius =
                p.baseRadius * (1f + sin(timeSec * p.pulse + p.phase) * p.breath) * particleScale

            if (!isForming && !p.isMember) {
                val pad = 40f
                if (p.x < -pad) p.x = width + pad
                if (p.x > width + pad) p.x = -pad
                if (p.y < -pad) p.y = height + pad
                if (p.y > height + pad) p.y = -pad
            }
        }

        return evaluateFrame()
    }

    private fun evaluateFrame(): FrameOutcome {
        if (!revealReported &&
            currentPhase == Phase.Burst &&
            phaseElapsedMs >= burstWindowMs * tuning.reveal.START_FRACTION
        ) {
            revealReported = true
            return FrameOutcome.BurstReveal(burstRevealProgress)
        }
        if (!dismissReported &&
            currentPhase == Phase.Idle &&
            shockwave == null
        ) {
            dismissReported = true
            return FrameOutcome.Finished(DismissReason.Settled)
        }
        return FrameOutcome.StillRunning
    }

    fun explode(
        center: Offset,
        power: Float = tuning.burst.EXPLODE_POWER,
        memberBoost: Boolean = true
    ) {
        postBurstFrames = tuning.burst.POST_BURST_FRAMES
        for (i in particles.indices) {
            val p = particles[i]
            val dx = p.x - center.x
            val dy = p.y - center.y
            val dist = hypot(dx, dy) + 0.1f
            val mult =
                if (memberBoost && p.isMember) tuning.burst.MEMBER_BOOST else tuning.burst.FLOATER_BOOST
            val m = power * mult * (0.5f + Random.nextFloat() * 1.1f)
            p.vx = (dx / dist) * m + (Random.nextFloat() - 0.5f)
            p.vy = (dy / dist) * m + (Random.nextFloat() - 0.5f)
        }
    }

    fun shock(cx: Float, cy: Float, alpha: Float = 1f) {
        val maxR = hypot(width, height) * tuning.burst.SHOCKWAVE_RADIUS_FACTOR
        shockwave = Shockwave(
            x = cx,
            y = cy,
            maxRadius = maxR,
            radius = 0f,
            alpha = alpha
        )
    }

    fun startGather(newShape: String = Slots.SHAPE_LOGO) {
        shape = newShape
        for (i in particles.indices) {
            val p = particles[i]
            p.vx *= tuning.physics.DAMP_ON_REGATHER
            p.vy *= tuning.physics.DAMP_ON_REGATHER
        }
        rebuildSlots()
        setPhase(Phase.Gather)
    }

    companion object {
        const val MAX_MEMBERS: Int = 64
    }
}
