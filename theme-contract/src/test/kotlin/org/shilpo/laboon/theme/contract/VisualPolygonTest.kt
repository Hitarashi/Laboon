package org.shilpo.laboon.theme.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualPolygonTest {
    @Test
    fun parsesNormalizedPolygon() {
        assertEquals(
            listOf(VisualPoint(0f, 0f), VisualPoint(1f, 0f), VisualPoint(0.5f, 1f)),
            VisualPolygon.parse("0,0;1,0;0.5,1")
        )
    }

    @Test
    fun rejectsMalformedOrUnboundedCoordinates() {
        listOf(
            "0,0;1,0",
            "0,0;1,0;NaN,1",
            "0,0;1,0;2,1",
            "0,0;1,0;0.5,1,2",
            List(65) { "0,0" }.joinToString(";")
        ).forEach {
            assertNull(VisualPolygon.parse(it))
        }
    }
}
