package org.shilpo.laboon.theme.contract

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeKeyframeValidatorTest {
    private val entrance = ThemeKeyframeAnimation(
        350,
        listOf(ThemeKeyframe(0f, 0f), ThemeKeyframe(0.7f, 1.1f), ThemeKeyframe(1f, 1f)),
    )

    @Test
    fun acceptsBoundedOvershootWithOrderedEndpoints() {
        assertTrue(ThemeKeyframeValidator.isValid(entrance))
    }

    @Test
    fun rejectsUnorderedOrRepeatedFrameTimes() {
        assertFalse(
            ThemeKeyframeValidator.isValid(
                entrance.copy(
                    frames = listOf(
                        ThemeKeyframe(0f, 0f), ThemeKeyframe(0f, 0.8f), ThemeKeyframe(1f, 1f),
                    )
                )
            )
        )
    }

    @Test
    fun rejectsNonFiniteProgressAndUnboundedDuration() {
        assertFalse(ThemeKeyframeValidator.isValid(entrance.copy(durationMs = Int.MAX_VALUE)))
        assertFalse(
            ThemeKeyframeValidator.isValid(
                entrance.copy(
                    frames = listOf(
                        ThemeKeyframe(0f, 0f),
                        ThemeKeyframe(0.7f, Float.NaN),
                        ThemeKeyframe(1f, 1f),
                    )
                )
            )
        )
    }

    @Test
    fun requiresStartAndEndProgressToMatchTheHostState() {
        assertFalse(
            ThemeKeyframeValidator.isValid(
                entrance.copy(
                    frames = listOf(
                        ThemeKeyframe(0.1f, 0f), ThemeKeyframe(1f, 0.5f),
                    )
                )
            )
        )
    }
}
