package org.shilpo.laboon.ui.design

sealed interface ScreenError {
    val message: String

    data class Validation(override val message: String) : ScreenError

    data class Remote(override val message: String) : ScreenError
}

fun mergeScreenErrors(remote: ScreenError?, local: ScreenError?): ScreenError? = remote ?: local
