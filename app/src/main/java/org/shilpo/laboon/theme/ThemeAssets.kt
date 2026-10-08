package org.shilpo.laboon.theme

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal fun themeBitmapPixelCount(bytes: ByteArray): Long {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions" }
    val sample = themeBitmapSampleSize(bounds.outWidth, bounds.outHeight)
    return (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample)
}

private fun themeBitmapSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while ((width.toLong() / sample) * (height.toLong() / sample) > 4_194_304L) sample *= 2
    return sample
}

internal fun decodeThemeBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)

    val sampleSize = themeBitmapSampleSize(bounds.outWidth, bounds.outHeight)
    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )?.asImageBitmap()
}.getOrNull()
