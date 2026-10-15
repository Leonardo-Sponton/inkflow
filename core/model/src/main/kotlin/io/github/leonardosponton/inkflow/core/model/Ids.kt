package io.github.leonardosponton.inkflow.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@JvmInline
value class ProjectId(val value: String)

@Serializable
@JvmInline
value class TabId(val value: String)

@Serializable
@JvmInline
value class ElementId(val value: String)

@Serializable
@JvmInline
value class LayerId(val value: String) {
    companion object {
        val Default = LayerId("layer-default")
    }
}

@Serializable
@JvmInline
value class AssetId(val value: String)

/** Source of fresh identifiers; swapped for a deterministic one in tests. */
fun interface IdGenerator {
    fun next(): String
}

object UuidIdGenerator : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}

/** Wall clock in epoch milliseconds; swapped for a fake in tests. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
