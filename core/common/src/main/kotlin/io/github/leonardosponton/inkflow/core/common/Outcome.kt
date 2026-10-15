package io.github.leonardosponton.inkflow.core.common

/** Explicit success/failure that crosses layer boundaries instead of exceptions. */
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>

    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(f: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Ok -> Outcome.Ok(f(value))
    is Outcome.Err -> this
}

fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Ok)?.value

inline fun <T, E> Outcome<T, E>.onErr(f: (E) -> Unit): Outcome<T, E> = also { if (it is Outcome.Err) f(it.error) }
