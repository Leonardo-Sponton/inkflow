package io.github.leonardosponton.inkflow.core.common

/** Logging facade so pure-Kotlin modules can log without Android. */
interface Logger {
    fun debug(tag: String, message: String)

    fun warn(tag: String, message: String, error: Throwable? = null)

    object None : Logger {
        override fun debug(tag: String, message: String) = Unit

        override fun warn(tag: String, message: String, error: Throwable?) = Unit
    }
}
