package org.shilpo.laboon.playback

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

object ArtworkUrlHelper {

    private val APPLE_SIZE_REGEX = Regex("""\d+x\d+bb""")

    fun toLowQuality(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var result = url
        if (result.contains("{w}x{h}")) {
            result = result.replace("{w}", "160").replace("{h}", "160").replace(".{f}", ".jpg")
        } else if (APPLE_SIZE_REGEX.containsMatchIn(result)) {
            result = APPLE_SIZE_REGEX.replace(result, "160x160bb")
        }

        return result
    }

    fun toHighQuality(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var result = url
        if (result.contains("{w}x{h}")) {
            result = result.replace("{w}", "1200").replace("{h}", "1200").replace(".{f}", ".jpg")
        } else if (APPLE_SIZE_REGEX.containsMatchIn(result)) {
            result = APPLE_SIZE_REGEX.replace(result, "1200x1200bb")
        }

        return result
    }

    fun preload(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        val loader = SingletonImageLoader.get(context)
        val low = toLowQuality(url)
        val high = toHighQuality(url)

        if (!low.isNullOrBlank()) {
            val lowReq = ImageRequest.Builder(context)
                .data(low)
                .build()
            loader.enqueue(lowReq)
        }

        if (!high.isNullOrBlank() && high != low) {
            val highReq = ImageRequest.Builder(context)
                .data(high)
                .build()
            loader.enqueue(highReq)
        }
    }
}
