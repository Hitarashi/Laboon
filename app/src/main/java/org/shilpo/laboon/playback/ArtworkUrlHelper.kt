package org.shilpo.laboon.playback

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

object ArtworkUrlHelper {

    private val APPLE_SIZE_REGEX = Regex("""\d+x\d+bb""")

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
        val high = toHighQuality(url)

        if (!high.isNullOrBlank()) {
            val highReq = ImageRequest.Builder(context)
                .data(high)
                .build()
            loader.enqueue(highReq)
        }
    }
}
