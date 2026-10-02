package org.shilpo.laboon.lyrics

import java.lang.Character.UnicodeScript

object Romanizer {
    private val transliterator: Any? by lazy {
        try {
            val clazz = Class.forName("android.icu.text.Transliterator")
            val method = clazz.getMethod("getInstance", String::class.java)
            method.invoke(null, "Any-Latin; Latin-ASCII")
        } catch (_: Throwable) {
            try {
                val clazz = Class.forName("com.ibm.icu.text.Transliterator")
                val method = clazz.getMethod("getInstance", String::class.java)
                method.invoke(null, "Any-Latin; Latin-ASCII")
            } catch (_: Throwable) {
                null
            }
        }
    }

    private val transliterateMethod by lazy {
        try {
            transliterator?.javaClass?.getMethod("transliterate", String::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    fun needsRomanization(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val script = UnicodeScript.of(codePoint)
            if (script != UnicodeScript.LATIN &&
                script != UnicodeScript.COMMON &&
                script != UnicodeScript.INHERITED &&
                script != UnicodeScript.UNKNOWN
            ) {
                return true
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    fun romanize(text: String): String? {
        if (text.isBlank() || !needsRomanization(text)) return null
        return try {
            val inst = transliterator ?: return null
            val method = transliterateMethod ?: return null
            val result = method.invoke(inst, text) as? String
            result?.trim()?.takeIf { it.isNotBlank() && it != text.trim() }
        } catch (_: Throwable) {
            null
        }
    }
}
