package org.shilpo.laboon.scrobble

import java.security.MessageDigest

object LastFmSigner {

    fun sign(params: Map<String, String>, apiSecret: String): String {
        val filteredAndSorted = params.entries
            .filter { (key, _) ->
                !key.equals("format", ignoreCase = true) && !key.equals("callback", ignoreCase = true)
            }
            .sortedBy { it.key }

        val builder = StringBuilder()
        for ((key, value) in filteredAndSorted) {
            builder.append(key).append(value)
        }
        builder.append(apiSecret)

        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(builder.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
