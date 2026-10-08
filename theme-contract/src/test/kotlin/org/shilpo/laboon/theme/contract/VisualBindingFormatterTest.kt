package org.shilpo.laboon.theme.contract

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualBindingFormatterTest {
    @Test
    fun interpolatesKnownKeysAndHidesMissingValues() {
        assertEquals(
            "Track · ",
            VisualBindingFormatter.format(
                "{{track.title}} · {{unknown}}",
                mapOf("track.title" to "Track")
            )
        )
    }

    @Test
    fun replacementValuesAreLiteralAndNotRecursivelyEvaluated() {
        assertEquals(
            "{{other}}$1",
            VisualBindingFormatter.format("{{track.title}}", mapOf("track.title" to "{{other}}$1"))
        )
        assertEquals("{literal}", VisualBindingFormatter.format("{literal}", emptyMap()))
    }
}
