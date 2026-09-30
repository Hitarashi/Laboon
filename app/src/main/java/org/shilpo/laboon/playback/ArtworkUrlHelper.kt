package org.shilpo.laboon.playback

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

object ArtworkUrlHelper {

    private val APPLE_SIZE_REGEX = Regex("""\d+x\d+bb""")
    private val DEEZER_DIM_REGEX = Regex("""(?<=/)\d+x\d+(?=-|\.)""")
    private val PEERLESS_SIZE_REGEX = Regex("""([?&]size=)\d+""")

    fun toLowQuality(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var result = url
        if (result.contains("{w}x{h}")) {
            result = result.replace("{w}", "160").replace("{h}", "160").replace(".{f}", ".jpg")
        } else if (APPLE_SIZE_REGEX.containsMatchIn(result)) {
            result = APPLE_SIZE_REGEX.replace(result, "160x160bb")
        }

        if (result.contains("coverartarchive.org")) {
            result = result
                .replace("front-500.jpg", "front-250.jpg")
                .replace("front-1200.jpg", "front-250.jpg")
                .replace("/front.jpg", "/front-250.jpg")
        }

        if (result.contains("dzcdn.net") || result.contains("deezer.com")) {
            result = DEEZER_DIM_REGEX.replace(result, "250x250")
        }

        if (result.contains("_600.jpg") || result.contains("_org.jpg")) {
            result = result.replace("_600.jpg", "_230.jpg").replace("_org.jpg", "_230.jpg")
        }

        if (result.contains("/assets/")) {
            result = if (PEERLESS_SIZE_REGEX.containsMatchIn(result)) {
                PEERLESS_SIZE_REGEX.replace(result, "${'$'}1160")
            } else if (result.contains("?")) {
                "$result&size=160"
            } else {
                "$result?size=160"
            }
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

        if (result.contains("coverartarchive.org")) {
            result = result
                .replace("front-250.jpg", "front-1200.jpg")
                .replace("front-500.jpg", "front-1200.jpg")
        }

        if (result.contains("dzcdn.net") || result.contains("deezer.com")) {
            result = DEEZER_DIM_REGEX.replace(result, "1000x1000")
        }

        if (result.contains("_230.jpg") || result.contains("_600.jpg")) {
            result = result.replace("_230.jpg", "_org.jpg").replace("_600.jpg", "_org.jpg")
        }

        if (result.contains("/assets/")) {
            result = if (PEERLESS_SIZE_REGEX.containsMatchIn(result)) {
                PEERLESS_SIZE_REGEX.replace(result, "${'$'}11200")
            } else if (result.contains("?")) {
                "$result&size=1200"
            } else {
                "$result?size=1200"
            }
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
