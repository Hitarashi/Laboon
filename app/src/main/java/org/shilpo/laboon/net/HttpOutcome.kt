package org.shilpo.laboon.net

enum class HttpErrorKind { NETWORK, TIMEOUT, STATUS, MALFORMED }

data class HttpError(
    val kind: HttpErrorKind,
    val statusCode: Int? = null,
    val message: String,
    val cause: Throwable? = null,
)

sealed interface HttpOutcome<out T> {
    data class Success<out T>(val value: T) : HttpOutcome<T>
    data class Failure(val error: HttpError) : HttpOutcome<Nothing>
}

val HttpOutcome<*>.isSuccess: Boolean
    get() = this is HttpOutcome.Success

fun <T> HttpOutcome<T>.getOrNull(): T? = when (this) {
    is HttpOutcome.Success -> value
    is HttpOutcome.Failure -> null
}

fun HttpOutcome<*>.errorOrNull(): HttpError? = when (this) {
    is HttpOutcome.Success -> null
    is HttpOutcome.Failure -> error
}

inline fun <T, R> HttpOutcome<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (HttpError) -> R,
): R = when (this) {
    is HttpOutcome.Success -> onSuccess(value)
    is HttpOutcome.Failure -> onFailure(error)
}
