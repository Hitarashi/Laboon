package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.splash.Contour
import org.shilpo.laboon.splash.DismissReason
import org.shilpo.laboon.splash.Engine
import org.shilpo.laboon.splash.FrameOutcome
import org.shilpo.laboon.splash.Phase
import org.shilpo.laboon.splash.Shockwave
import org.shilpo.laboon.splash.Tuning

class SplashFramePolicyTest {
    private fun engine(
        isShort: Boolean = false,
        tuning: Tuning = Tuning.Default,
    ) = Engine(
        tuning = tuning,
        isShort = isShort,
        contourSource = Contour.polygon,
    )

    private fun started(isShort: Boolean = false): Engine =
        engine(isShort).apply {
            init(1080f, 2400f, 3f)
            startGather()
        }

    private fun run(
        engine: Engine,
        frames: Int,
        dt: Float = 1f / 60f,
        onFrame: (Int, FrameOutcome) -> Unit = { _, _ -> },
    ): List<FrameOutcome> {
        val outcomes = ArrayList<FrameOutcome>(frames)
        var now = 0L
        repeat(frames) { i ->
            now += (dt * 1000f).toLong()
            val outcome = engine.update(dt, now)
            outcomes.add(outcome)
            onFrame(i, outcome)
        }
        return outcomes
    }

    @Test
    fun defaultTuningIsTheShippedNumbers() {
        val t = Tuning.Default
        assertEquals(56, t.slots.getSlotCount())
        assertEquals(800f, t.timings.GATHER_LOGO_MS, 0f)
        assertEquals(200f, t.timings.igniteDurationMs(false), 0f)
        assertEquals(120f, t.timings.igniteDurationMs(true), 0f)
        assertEquals(420f, t.timings.burstDurationMs(false), 0f)
        assertEquals(280f, t.timings.burstDurationMs(true), 0f)
        assertEquals(0.15f, t.reveal.START_FRACTION, 0f)
        assertEquals(65f, t.burst.FRAMES_TO_CROSS, 0f)
        assertEquals(2.8f, t.look.smooth.STROKE_WIDTH_DP, 0f)
    }

    @Test
    fun phaseSequenceAdvancesWithRepeatedUpdates() {
        val engine = started()
        val seen = LinkedHashSet<Phase>()
        run(engine, frames = 2000) { _, _ -> seen.add(engine.currentPhase) }
        assertEquals(
            listOf(
                Phase.Gather,
                Phase.Ignite,
                Phase.Burst,
                Phase.Idle,
            ),
            seen.toList()
        )
    }

    @Test
    fun burstRevealIsReportedExactlyOnceAtTheStartFractionOfTheBurstWindow() {
        val tuning = Tuning.Default
        val engine = started()
        val threshold = tuning.timings.burstDurationMs(false) * tuning.reveal.START_FRACTION

        var reveals = 0
        var reportedProgress = -1f
        var elapsedAtReveal = -1f
        run(engine, frames = 2000) { _, outcome ->
            if (outcome is FrameOutcome.BurstReveal) {
                reveals++
                reportedProgress = outcome.progress
                elapsedAtReveal = engine.phaseElapsedMs
            }
        }

        assertEquals(1, reveals)
        assertTrue("reveal must land at or after the 15% mark", elapsedAtReveal >= threshold)
        assertTrue(
            "reveal must land within one 60Hz frame of the 15% mark",
            elapsedAtReveal < threshold + 1000f / 60f
        )
        assertEquals(tuning.reveal.START_FRACTION, reportedProgress, 0.03f)
    }

    @Test
    fun dismissIsReportedExactlyOnceAndOnlyWhenIdleWithNoShockwave() {
        val engine = started()
        var finished = 0
        var phaseAtFinish: Phase = Phase.Gather
        var shockwaveAtFinish: Shockwave? = Shockwave()
        var reasonAtFinish: DismissReason? = null
        var sawIdleWithShockwave = false

        run(engine, frames = 3000) { _, outcome ->
            if (engine.currentPhase == Phase.Idle && engine.shockwave != null) {
                sawIdleWithShockwave = true
            }
            if (outcome is FrameOutcome.Finished) {
                finished++
                phaseAtFinish = engine.currentPhase
                shockwaveAtFinish = engine.shockwave
                reasonAtFinish = outcome.reason
            }
        }

        assertEquals(1, finished)
        assertEquals(Phase.Idle, phaseAtFinish)
        assertNull("dismiss must not fire while a shockwave is alive", shockwaveAtFinish)
        assertEquals(DismissReason.Settled, reasonAtFinish)
        assertTrue("the burst shockwave must outlive the burst phase", sawIdleWithShockwave)
    }

    @Test
    fun reportedBurstProgressIsMonotonicAndBounded() {
        val engine = started()
        var previous = -1f
        var first = -1f
        var sampled = 0
        var reported: Float? = null
        run(engine, frames = 2000) { _, outcome ->
            if (engine.currentPhase == Phase.Burst) {
                val p = engine.burstRevealProgress
                assertTrue("progress must be bounded, got $p", p in 0f..1f)
                assertTrue("progress must not go backwards: $previous -> $p", p >= previous)
                if (first < 0f) first = p
                previous = p
                sampled++
            }
            if (outcome is FrameOutcome.BurstReveal) reported = outcome.progress
        }
        assertTrue("burst phase should have been sampled", sampled > 0)
        assertTrue("progress must start at the top of the burst window, got $first", first < 0.1f)
        assertTrue(
            "progress must reach the end of the burst window, got $previous",
            previous > 0.9f
        )
        assertNotNull(reported)
        assertTrue((reported as Float) in 0f..1f)
    }

    @Test
    fun largeDtInsideTheBurstWindowStillReportsTheRevealExactlyOnce() {
        val engine = started()
        var reveals = 0
        run(engine, frames = 600, dt = 0.25f) { _, outcome ->
            if (outcome is FrameOutcome.BurstReveal) reveals++
        }
        assertEquals(1, reveals)
    }

    @Test
    fun largeDtThatSwallowsTheWholeBurstWindowReportsDismissOnceAndNeverARevealAfterIt() {
        val engine = started()
        var finished = 0
        var revealAfterFinish = 0
        var done = false
        run(engine, frames = 600, dt = 3f) { _, outcome ->
            if (outcome is FrameOutcome.Finished) {
                finished++
                done = true
            }
            if (done && outcome is FrameOutcome.BurstReveal) revealAfterFinish++
        }
        assertEquals(1, finished)
        assertEquals(0, revealAfterFinish)
    }

    @Test
    fun isShortPicksTheShortBurstWindowAndThePolicyIsDecidedByTheEngine() {
        val tuning = Tuning.Default

        val full = started(isShort = false)
        val short = started(isShort = true)

        assertEquals(tuning.timings.BURST_FULL_MS, full.burstWindowMs, 0f)
        assertEquals(tuning.timings.BURST_SHORT_MS, short.burstWindowMs, 0f)
        assertTrue(full.burstWindowMs > short.burstWindowMs)

        assertEquals(
            tuning.reveal.START_FRACTION * tuning.timings.BURST_FULL_MS,
            revealElapsed(started(isShort = false)),
            18f,
        )
        assertEquals(
            tuning.reveal.START_FRACTION * tuning.timings.BURST_SHORT_MS,
            revealElapsed(started(isShort = true)),
            18f,
        )

        assertEquals(
            tuning.timings.BURST_FULL_MS - tuning.timings.BURST_SHORT_MS,
            burstPhaseLengthMs(started(isShort = false)) -
                    burstPhaseLengthMs(started(isShort = true)),
            20f,
        )
    }

    private fun revealElapsed(engine: Engine): Float {
        var result = -1f
        run(engine, frames = 3000) { _, outcome ->
            if (outcome is FrameOutcome.BurstReveal) result = engine.phaseElapsedMs
        }
        return result
    }

    private fun burstPhaseLengthMs(engine: Engine): Float {
        var result = -1f
        var elapsed = 0f
        var burstStart = -1f
        var previousPhase = engine.currentPhase
        run(engine, frames = 3000) { _, _ ->
            elapsed += 1000f / 60f
            if (engine.currentPhase == Phase.Burst && previousPhase != Phase.Burst) {
                burstStart = elapsed
            }
            if (engine.currentPhase != Phase.Burst && burstStart > 0f && result < 0f) {
                result = elapsed - burstStart
            }
            previousPhase = engine.currentPhase
        }
        return result
    }

    @Test
    fun theShockwaveOutlivesTheBurstSoTheBurstWindowDoesNotMoveTheDismiss() {
        val tuning = Tuning.Default
        val shockwaveMs = 1000f * tuning.burst.FRAMES_TO_CROSS / 60f
        assertTrue(
            "shockwave lifetime must exceed the full burst window",
            shockwaveMs > tuning.timings.BURST_FULL_MS
        )

        val full = burstToFinishMs(started(isShort = false))
        val short = burstToFinishMs(started(isShort = true))
        assertEquals(shockwaveMs, full, 20f)
        assertEquals(
            "dismiss is gated by the shockwave, not by the burst window",
            full,
            short,
            1f,
        )
    }

    private fun burstToFinishMs(engine: Engine): Float {
        var elapsed = 0f
        var burstStart = -1f
        var result = -1f
        var previousPhase = engine.currentPhase
        run(engine, frames = 3000) { _, outcome ->
            elapsed += 1000f / 60f
            if (engine.currentPhase == Phase.Burst && previousPhase != Phase.Burst) {
                burstStart = elapsed
            }
            previousPhase = engine.currentPhase
            if (outcome is FrameOutcome.Finished) result = elapsed - burstStart
        }
        return result
    }

    @Test
    fun startGatherRearmsTheOneShotPolicyForAFreshCycle() {
        val engine = started()
        var reveals = 0
        var finished = 0
        run(engine, frames = 3000) { _, outcome ->
            when (outcome) {
                is FrameOutcome.BurstReveal -> reveals++
                is FrameOutcome.Finished -> finished++
                else -> Unit
            }
        }
        assertEquals(1, reveals)
        assertEquals(1, finished)

        engine.startGather()
        assertEquals(0f, engine.burstRevealProgress, 0f)

        var revealsAfter = 0
        var finishedAfter = 0
        run(engine, frames = 3000) { _, outcome ->
            when (outcome) {
                is FrameOutcome.BurstReveal -> revealsAfter++
                is FrameOutcome.Finished -> finishedAfter++
                else -> Unit
            }
        }
        assertEquals(1, revealsAfter)
        assertEquals(1, finishedAfter)
    }

    @Test
    fun injectedTuningChangesTheBurstWindowTheEngineReports() {
        val tuned = Tuning.Default.copy(
            timings = Tuning.Default.timings.copy(BURST_FULL_MS = 1000f)
        )
        val engine = engine(tuning = tuned).apply {
            init(1080f, 2400f, 3f)
            startGather()
        }
        assertEquals(1000f, engine.burstWindowMs, 0f)
        var revealed = -1f
        var sinceBurst = 0f
        var previousPhase = engine.currentPhase
        run(engine, frames = 3000) { _, outcome ->
            if (engine.currentPhase == Phase.Burst) {
                if (previousPhase != Phase.Burst) sinceBurst = 0f else sinceBurst += 1000f / 60f
            }
            previousPhase = engine.currentPhase
            if (outcome is FrameOutcome.BurstReveal) revealed = sinceBurst
        }
        assertTrue("reveal should follow the injected 1000ms window", revealed in 150f..168f)
    }

    @Test
    fun onlyTheRevealAndTheFinishFramesAreNotStillRunning() {
        val engine = started()
        val outcomes = run(engine, frames = 2000)
        val reveals = outcomes.filterIsInstance<FrameOutcome.BurstReveal>()
        val finishes = outcomes.filterIsInstance<FrameOutcome.Finished>()
        assertEquals(1, reveals.size)
        assertEquals(1, finishes.size)
        assertTrue(
            "the reveal must precede the dismiss",
            outcomes.indexOf(reveals[0]) < outcomes.indexOf(finishes[0])
        )
        outcomes.forEachIndexed { i, o ->
            val expected: FrameOutcome =
                when (i) {
                    outcomes.indexOf(reveals[0]) -> reveals[0]
                    outcomes.indexOf(finishes[0]) -> finishes[0]
                    else -> FrameOutcome.StillRunning
                }
            assertSame("frame $i", expected, o)
        }
    }
}
