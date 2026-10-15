package io.github.leonardosponton.inkflow.domain.uml

import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Visibility(val symbol: Char) {
    PUBLIC('+'),
    PRIVATE('-'),
    PROTECTED('#'),
    PACKAGE('~'),
    ;

    companion object {
        fun of(c: Char): Visibility? = entries.firstOrNull { it.symbol == c }
    }
}

/** An attribute or operation of a classifier. [parameters] is kept as written ("id: Long, name: String"). */
@Serializable
data class UmlMember(
    val name: String,
    val type: String = "",
    val visibility: Visibility = Visibility.PUBLIC,
    val isStatic: Boolean = false,
    val isAbstract: Boolean = false,
    val parameters: String? = null,
    val defaultValue: String = "",
) {
    val isOperation: Boolean get() = parameters != null

    /** UML text form, e.g. `- balance: Decimal = 0` or `+ deposit(amount: Decimal): void`. */
    val signature: String
        get() = buildString {
            append(visibility.symbol).append(' ').append(name)
            if (parameters != null) append('(').append(parameters).append(')')
            if (type.isNotBlank()) append(": ").append(type)
            if (defaultValue.isNotBlank()) append(" = ").append(defaultValue)
        }

    companion object {
        /**
         * Parses what the user typed: optional visibility symbol, optional `static`/`abstract`
         * keywords, a name, optional `(parameters)`, optional `: type`, optional `= default`.
         */
        fun parse(text: String, keepFlags: UmlMember? = null): UmlMember {
            var rest = text.trim()
            val visibility = rest.firstOrNull()?.let { Visibility.of(it) }
            if (visibility != null) rest = rest.drop(1).trim()
            val (flags, body) = modifiers(rest, keepFlags?.isStatic ?: false, keepFlags?.isAbstract ?: false)
            val hasDefault = '=' in body && '(' !in body
            val defaultValue = if (hasDefault) body.substringAfter('=').trim() else ""
            val decl = if (hasDefault) body.substringBefore('=').trim() else body
            val open = decl.indexOf('(')
            val close = decl.lastIndexOf(')')
            val params = if (open in 0 until close) decl.substring(open + 1, close).trim() else null
            val head = if (params != null) decl.substring(0, open) + decl.substring(close + 1) else decl
            return UmlMember(
                name = head.substringBefore(':').trim(),
                type = if (':' in head) head.substringAfter(':').trim() else "",
                visibility = visibility ?: keepFlags?.visibility ?: Visibility.PUBLIC,
                isStatic = flags.first,
                isAbstract = flags.second,
                parameters = params,
                defaultValue = defaultValue,
            )
        }

        /** Strips leading `static`/`abstract` keywords; returns (static, abstract) and the rest. */
        private fun modifiers(text: String, static: Boolean, abstract: Boolean): Pair<Pair<Boolean, Boolean>, String> {
            var rest = text
            var s = static
            var a = abstract
            while (true) {
                rest = when {
                    rest.startsWith("static ") -> rest.removePrefix("static ").trim().also { s = true }
                    rest.startsWith("abstract ") -> rest.removePrefix("abstract ").trim().also { a = true }
                    else -> return (s to a) to rest
                }
            }
        }
    }
}

enum class ClassifierKind(val stereotype: String?) {
    CLASS(null),
    ABSTRACT_CLASS(null),
    INTERFACE("interface"),
    ENUMERATION("enumeration"),
}

/** A class, interface, abstract class or enumeration, edited as data and rendered automatically. */
@Serializable
@SerialName("uml.class")
data class UmlClassContent(
    val name: String = "Class",
    val kind: ClassifierKind = ClassifierKind.CLASS,
    val stereotype: String = "",
    val attributes: List<UmlMember> = emptyList(),
    val operations: List<UmlMember> = emptyList(),
    val literals: List<String> = emptyList(),
) : NodeContent {
    override val summary: String get() = name

    /** Stereotype line shown above the name, e.g. «interface». */
    val stereotypeLabel: String?
        get() = stereotype.ifBlank { kind.stereotype.orEmpty() }.takeIf { it.isNotBlank() }?.let { "«$it»" }

    val nameItalic: Boolean get() = kind == ClassifierKind.ABSTRACT_CLASS || kind == ClassifierKind.INTERFACE
}
