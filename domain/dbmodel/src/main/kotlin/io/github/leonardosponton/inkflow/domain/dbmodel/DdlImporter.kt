package io.github.leonardosponton.inkflow.domain.dbmodel

/**
 * Small `CREATE TABLE` parser (stretch goal): column names and types, `PRIMARY KEY`, `NOT NULL`,
 * `UNIQUE`, `DEFAULT`, inline `REFERENCES` and table-level `PRIMARY KEY (...)` / `FOREIGN KEY`.
 * Returns tables keyed by a generated id; references point at those ids.
 */
object DdlImporter {
    data class Result(val tables: Map<String, DbTableContent>, val skipped: Int)

    private val createHead = Regex("""create\s+table\s+(?:if\s+not\s+exists\s+)?([`"\[]?[\w.]+[`"\]]?)\s*\(""", RegexOption.IGNORE_CASE)
    private val references = Regex("""references\s+([`"\[]?[\w.]+[`"\]]?)\s*\(\s*([`"\[]?\w+[`"\]]?)\s*\)""", RegexOption.IGNORE_CASE)
    private val defaultValue = Regex("""default\s+('(?:[^']|'')*'|[^\s,]+)""", RegexOption.IGNORE_CASE)
    private val IGNORED = listOf("constraint", "unique", "check", "key ", "index")

    fun parse(sql: String): Result {
        val raw = statements(stripComments(sql))
        val ids = raw.mapIndexed { i, (name, _) -> name.lowercase() to "t$i" }.toMap()
        val pending = mutableListOf<PendingFk>()
        val tables = LinkedHashMap<String, DbTableContent>()
        raw.forEachIndexed { i, (name, parts) -> tables["t$i"] = DbTableContent(name, columns("t$i", parts, pending)) }
        val skipped = pending.count { !link(it, ids, tables) }
        return Result(tables, skipped)
    }

    /** (table name, top-level body parts) for every CREATE TABLE, with balanced parentheses. */
    private fun statements(sql: String): List<Pair<String, List<String>>> = createHead.findAll(sql).map { m ->
        var depth = 1
        var i = m.range.last + 1
        while (i < sql.length && depth > 0) {
            if (sql[i] == '(') {
                depth++
            } else if (sql[i] == ')') {
                depth--
            }
            i++
        }
        m.groupValues[1].unquote() to splitTopLevel(sql.substring(m.range.last + 1, (i - 1).coerceAtLeast(m.range.last + 1)))
    }.toList()

    private fun columns(tableId: String, parts: List<String>, pending: MutableList<PendingFk>): List<DbColumn> {
        var columns = mutableListOf<DbColumn>()
        parts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { p ->
            val lower = p.lowercase()
            when {
                lower.startsWith("primary key") -> columns = markPrimary(columns, list(p))
                lower.startsWith("foreign key") -> tableForeignKey(tableId, p)?.let { pending += it }
                IGNORED.any { lower.startsWith(it) } -> Unit
                else -> columns += column(p, tableId, columns.size, pending)
            }
        }
        return columns
    }

    private fun markPrimary(columns: List<DbColumn>, names: List<String>): MutableList<DbColumn> {
        val keys = names.map { it.lowercase() }
        return columns.map { if (it.name.lowercase() in keys) it.copy(primaryKey = true) else it }.toMutableList()
    }

    private fun tableForeignKey(tableId: String, p: String): PendingFk? {
        val m = references.find(p) ?: return null
        val col = list(p).firstOrNull() ?: return null
        return Triple(tableId, col, m.groupValues[1].unquote() to m.groupValues[2].unquote())
    }

    private fun link(fk: PendingFk, ids: Map<String, String>, tables: MutableMap<String, DbTableContent>): Boolean {
        val (tableId, col, target) = fk
        val parentId = ids[target.first.lowercase()] ?: return false
        val parentCol = tables[parentId]?.columnNamed(target.second) ?: return false
        val child = tables[tableId] ?: return false
        val linked = child.columns.map { if (it.name.equals(col, true)) it.copy(references = ColumnRef(parentId, parentCol.id)) else it }
        tables[tableId] = child.copy(columns = linked)
        return true
    }

    private fun column(def: String, tableId: String, index: Int, pending: MutableList<PendingFk>): DbColumn {
        val tokens = def.split(Regex("\\s+"))
        val name = tokens[0].unquote()
        val typeEnd = tokens.drop(1).indexOfFirst { it.lowercase() in CONSTRAINT_WORDS }.let { if (it < 0) tokens.size - 1 else it }
        val type = tokens.drop(1).take(typeEnd).joinToString(" ").ifBlank { "text" }
        val lower = def.lowercase()
        references.find(def)?.let { pending += Triple(tableId, name, it.groupValues[1].unquote() to it.groupValues[2].unquote()) }
        return DbColumn(
            id = "$tableId-c$index",
            name = name,
            type = type.lowercase(),
            primaryKey = "primary key" in lower,
            notNull = "not null" in lower,
            unique = Regex("\\bunique\\b").containsMatchIn(lower),
            defaultValue = defaultValue.find(def)?.groupValues?.get(1).orEmpty(),
        )
    }

    private fun list(p: String) = p.substringAfter('(').substringBefore(')').split(',').map {
        it.trim().unquote()
    }.filter { it.isNotEmpty() }

    private fun splitTopLevel(body: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val cur = StringBuilder()
        body.forEach { ch ->
            when {
                ch == '(' -> depth++.also { cur.append(ch) }
                ch == ')' -> depth--.also { cur.append(ch) }
                ch == ',' && depth == 0 -> out += cur.toString().also { cur.clear() }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out
    }

    private fun stripComments(sql: String) = sql.lines().joinToString("\n") { it.substringBefore("--") }

    private fun String.unquote() = trim().trim('`', '"', '[', ']').substringAfterLast('.')

    private val CONSTRAINT_WORDS =
        setOf(
            "primary",
            "not",
            "null",
            "unique",
            "default",
            "references",
            "check",
            "constraint",
            "auto_increment",
            "autoincrement",
            "comment",
            "generated",
        )
}

/** (child table id, child column name, parent table name to parent column name). */
private typealias PendingFk = Triple<String, String, Pair<String, String>>
