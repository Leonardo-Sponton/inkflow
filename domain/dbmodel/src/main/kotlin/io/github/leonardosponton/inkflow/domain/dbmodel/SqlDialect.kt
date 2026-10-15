package io.github.leonardosponton.inkflow.domain.dbmodel

/** Target database for DDL generation: identifier quoting and type mapping. */
enum class SqlDialect(val displayName: String) {
    POSTGRESQL("PostgreSQL"),
    MYSQL("MySQL"),
    SQLITE("SQLite"),
    ;

    fun quote(identifier: String): String = when (this) {
        MYSQL -> "`" + identifier.replace("`", "``") + "`"
        else -> "\"" + identifier.replace("\"", "\"\"") + "\""
    }

    /** Maps a loosely written column type (e.g. `string`, `uuid`, `serial`) to this dialect. */
    fun type(raw: String): String {
        val t = raw.trim().lowercase()
        val base = t.substringBefore('(').trim()
        val args = if ('(' in t) "(" + t.substringAfter('(').substringBefore(')').replace(" ", "") + ")" else ""
        return when (base) {
            "int", "integer", "int4" -> pick("INTEGER", "INT", "INTEGER")
            "bigint", "int8", "long" -> pick("BIGINT", "BIGINT", "INTEGER")
            "smallint", "int2", "short" -> pick("SMALLINT", "SMALLINT", "INTEGER")
            "serial" -> pick("SERIAL", "INT AUTO_INCREMENT", "INTEGER")
            "bigserial" -> pick("BIGSERIAL", "BIGINT AUTO_INCREMENT", "INTEGER")
            "varchar", "string", "character varying", "nvarchar" -> pick(
                "VARCHAR${args.ifEmpty {
                    DEFAULT_LEN
                }}",
                "VARCHAR${args.ifEmpty { DEFAULT_LEN }}",
                "TEXT",
            )
            "char", "character" -> pick("CHAR${args.ifEmpty { "(1)" }}", "CHAR${args.ifEmpty { "(1)" }}", "TEXT")
            "text", "clob" -> "TEXT"
            "bool", "boolean" -> pick("BOOLEAN", "TINYINT(1)", "INTEGER")
            "date" -> pick("DATE", "DATE", "TEXT")
            "time" -> pick("TIME", "TIME", "TEXT")
            "timestamp", "datetime", "timestamptz" -> pick("TIMESTAMP", "DATETIME", "TEXT")
            "decimal", "numeric", "money" -> pick("NUMERIC$args", "DECIMAL${args.ifEmpty { "(12,2)" }}", "NUMERIC")
            "float", "double", "real", "double precision" -> pick("DOUBLE PRECISION", "DOUBLE", "REAL")
            "uuid" -> pick("UUID", "CHAR(36)", "TEXT")
            "json", "jsonb" -> pick("JSONB", "JSON", "TEXT")
            "blob", "bytea", "binary", "bytes" -> pick("BYTEA", "BLOB", "BLOB")
            else -> raw.trim().uppercase()
        }
    }

    /** Dialect-specific spelling of a default value (booleans differ). */
    fun defaultValue(raw: String, type: String): String {
        val v = raw.trim()
        val isBool = type.trim().lowercase() in setOf("bool", "boolean")
        return when {
            isBool && this != POSTGRESQL && v.equals("true", true) -> "1"
            isBool && this != POSTGRESQL && v.equals("false", true) -> "0"
            else -> v
        }
    }

    private fun pick(pg: String, my: String, lite: String) = when (this) {
        POSTGRESQL -> pg
        MYSQL -> my
        SQLITE -> lite
    }

    private companion object {
        const val DEFAULT_LEN = "(255)"
    }
}
