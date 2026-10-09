@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

private val ArtistArtworkShapes = listOf(
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBurst,
    MaterialShapes.Puffy,
    MaterialShapes.Gem,
    MaterialShapes.Bun,
    MaterialShapes.Slanted,
    MaterialShapes.Flower,
)

@Composable
fun rememberArtistArtworkShape(artistId: String): Shape =
    ArtistArtworkShapes[Math.floorMod(artistId.hashCode(), ArtistArtworkShapes.size)].toShape()
