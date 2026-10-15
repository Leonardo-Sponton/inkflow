package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.dbmodel.Cardinality
import io.github.leonardosponton.inkflow.domain.dbmodel.ChenLink
import io.github.leonardosponton.inkflow.domain.dbmodel.DbColumn
import io.github.leonardosponton.inkflow.domain.dbmodel.DbIndex
import io.github.leonardosponton.inkflow.domain.dbmodel.DbNotation
import io.github.leonardosponton.inkflow.domain.dbmodel.DbRelationship
import io.github.leonardosponton.inkflow.domain.dbmodel.DbTableContent
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.uml.ClassifierKind
import io.github.leonardosponton.inkflow.domain.uml.UmlClassContent
import io.github.leonardosponton.inkflow.domain.uml.UmlMember
import io.github.leonardosponton.inkflow.domain.uml.UmlRelation
import io.github.leonardosponton.inkflow.domain.uml.UmlRelationKind
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.R
import java.util.UUID

/** Structured editor for a node's content; each notation's content type gets its own form. */
@Composable
fun NodeContentEditor(node: ShapeNode, catalog: NotationCatalog, onIntent: (EditorIntent) -> Unit) {
    val update = { c: NodeContent -> onIntent(EditorIntent.EditContent(node.id, c)) }
    when (val content = node.content) {
        is PlainLabel -> OutlinedTextField(
            value = content.text,
            onValueChange = { update(PlainLabel(it)) },
            label = { Text(stringResource(R.string.label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        is UmlClassContent -> UmlClassEditor(node.id, content, update)
        is DbTableContent -> TableEditor(node.id, content, update)
        else -> Text(catalog.shapes.resolve(node.shape).displayName, style = MaterialTheme.typography.bodySmall)
    }
}

// ---- UML ------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UmlClassEditor(id: ElementId, c: UmlClassContent, update: (UmlClassContent) -> Unit) {
    Field(stringResource(R.string.name), c.name) { update(c.copy(name = it)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClassifierKind.entries.forEach { k ->
            FilterChip(selected = c.kind == k, onClick = {
                update(c.copy(kind = k))
            }, label = { Text(k.name.lowercase().replace('_', ' ')) })
        }
    }
    Field(stringResource(R.string.stereotype), c.stereotype) { update(c.copy(stereotype = it)) }
    if (c.kind == ClassifierKind.ENUMERATION) {
        Section(stringResource(R.string.literals))
        StringList("$id-lit", c.literals, stringResource(R.string.add_literal)) { update(c.copy(literals = it)) }
        return
    }
    Section(stringResource(R.string.attributes))
    MemberList("$id-attr", c.attributes, operation = false) { update(c.copy(attributes = it)) }
    Section(stringResource(R.string.operations))
    MemberList("$id-op", c.operations, operation = true) { update(c.copy(operations = it)) }
    Text(
        stringResource(R.string.member_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberList(key: String, members: List<UmlMember>, operation: Boolean, update: (List<UmlMember>) -> Unit) {
    members.forEachIndexed { i, m ->
        // Local text keeps exactly what was typed; the parsed member is what gets stored.
        var text by remember("$key-$i") { mutableStateOf(m.signature) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { t ->
                    text = t
                    val parsed = UmlMember.parse(t, keepFlags = m).let {
                        if (operation &&
                            it.parameters == null
                        ) {
                            it.copy(parameters = "")
                        } else {
                            it
                        }
                    }
                    update(members.toMutableList().also { it[i] = parsed })
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                update(
                    members.filterIndexed {
                        j,
                        _,
                        ->
                        j != i
                    },
                )
            }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = m.isStatic, onClick = {
                update(members.toMutableList().also { it[i] = m.copy(isStatic = !m.isStatic) })
            }, label = { Text("static") })
            FilterChip(selected = m.isAbstract, onClick = {
                update(members.toMutableList().also { it[i] = m.copy(isAbstract = !m.isAbstract) })
            }, label = { Text("abstract") })
        }
    }
    TextButton(onClick = {
        update(members + if (operation) UmlMember("operation", "void", parameters = "") else UmlMember("attribute", "String"))
    }) {
        Text(stringResource(if (operation) R.string.add_operation else R.string.add_attribute))
    }
}

@Composable
private fun StringList(key: String, items: List<String>, addLabel: String, update: (List<String>) -> Unit) {
    items.forEachIndexed { i, s ->
        var text by remember("$key-$i") { mutableStateOf(s) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { t ->
                text = t
                update(items.toMutableList().also { it[i] = t })
            }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                update(
                    items.filterIndexed {
                        j,
                        _,
                        ->
                        j != i
                    },
                )
            }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete)) }
        }
    }
    TextButton(onClick = { update(items + "VALUE") }) { Text(addLabel) }
}

// ---- database -------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableEditor(id: ElementId, t: DbTableContent, update: (DbTableContent) -> Unit) {
    Field(stringResource(R.string.table_name), t.name) { update(t.copy(name = it)) }
    Section(stringResource(R.string.columns))
    t.columns.forEachIndexed { i, c ->
        ColumnEditor("$id-$i", c, onChange = { nc -> update(t.copy(columns = t.columns.toMutableList().also { it[i] = nc })) }) {
            update(
                t.copy(
                    columns = t.columns.filterIndexed { j, _ -> j != i },
                    indexes = t.indexes.map { ix ->
                        ix.copy(
                            columnIds =
                                ix.columnIds - c.id,
                        )
                    },
                ),
            )
        }
        HorizontalDivider()
    }
    TextButton(onClick = {
        update(t.copy(columns = t.columns + DbColumn(UUID.randomUUID().toString(), "column${t.columns.size + 1}", "varchar(100)")))
    }) { Text(stringResource(R.string.add_column)) }
    Section(stringResource(R.string.indexes))
    t.indexes.forEachIndexed { i, ix ->
        var cols by remember("$id-ix-$i") { mutableStateOf(ix.columnIds.mapNotNull { cid -> t.column(cid)?.name }.joinToString(", ")) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Field(stringResource(R.string.name), ix.name) { n ->
                    update(
                        t.copy(
                            indexes = t.indexes.toMutableList().also {
                                it[i] =
                                    ix.copy(name = n)
                            },
                        ),
                    )
                }
                OutlinedTextField(cols, { v ->
                    cols = v
                    val ids = v.split(',').mapNotNull { name -> t.columnNamed(name.trim())?.id }
                    update(t.copy(indexes = t.indexes.toMutableList().also { it[i] = ix.copy(columnIds = ids) }))
                }, label = { Text(stringResource(R.string.index_columns)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FilterChip(selected = ix.unique, onClick = {
                    update(
                        t.copy(
                            indexes = t.indexes.toMutableList().also {
                                it[i] =
                                    ix.copy(unique = !ix.unique)
                            },
                        ),
                    )
                }, label = { Text("unique") })
            }
            IconButton(onClick = {
                update(
                    t.copy(
                        indexes = t.indexes.filterIndexed {
                            j,
                            _,
                            ->
                            j != i
                        },
                    ),
                )
            }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete)) }
        }
    }
    TextButton(onClick = {
        update(t.copy(indexes = t.indexes + DbIndex("idx_${t.name}_${t.indexes.size + 1}", emptyList())))
    }) { Text(stringResource(R.string.add_index)) }
    Field(stringResource(R.string.comment), t.comment) { update(t.copy(comment = it)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnEditor(key: String, c: DbColumn, onChange: (DbColumn) -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyedField("$key-n", stringResource(R.string.name), c.name, Modifier.weight(1f)) { onChange(c.copy(name = it)) }
                KeyedField("$key-t", stringResource(R.string.type), c.type, Modifier.weight(1f)) { onChange(c.copy(type = it)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = c.primaryKey, onClick = { onChange(c.copy(primaryKey = !c.primaryKey)) }, label = { Text("PK") })
                FilterChip(selected = c.notNull, onClick = { onChange(c.copy(notNull = !c.notNull)) }, label = { Text("NOT NULL") })
                FilterChip(selected = c.unique, onClick = { onChange(c.copy(unique = !c.unique)) }, label = { Text("UNIQUE") })
                if (c.isForeignKey) FilterChip(selected = true, onClick = { onChange(c.copy(references = null)) }, label = { Text("FK ✕") })
            }
            KeyedField("$key-d", stringResource(R.string.default_value), c.defaultValue, Modifier.fillMaxWidth()) {
                onChange(c.copy(defaultValue = it))
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, stringResource(R.string.delete)) }
    }
}

// ---- connector semantics --------------------------------------------------------------------------

@Composable
fun ConnectorSemanticsEditor(connector: Connector, onIntent: (EditorIntent) -> Unit) {
    when (val s = connector.semantics) {
        is UmlRelation -> UmlRelationEditor(connector.id, s, onIntent)
        is DbRelationship -> DbRelationshipEditor(connector.id, s, onIntent)
        is ChenLink -> ChenLinkEditor(connector.id, s, onIntent)
        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UmlRelationEditor(id: ElementId, r: UmlRelation, onIntent: (EditorIntent) -> Unit) {
    val set = { nr: UmlRelation -> onIntent(EditorIntent.EditConnector(id) { UmlRelation.applyTo(it, nr) }) }
    Section(stringResource(R.string.uml_relation))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UmlRelationKind.entries.forEach { k ->
            FilterChip(selected = r.kind == k, onClick = {
                set(r.copy(kind = k))
            }, label = { Text(k.displayName, style = MaterialTheme.typography.labelSmall) })
        }
    }
    if (r.kind == UmlRelationKind.TRANSITION) {
        KeyedField("$id-ev", stringResource(R.string.event), r.event, Modifier.fillMaxWidth()) { set(r.copy(event = it)) }
        KeyedField("$id-gd", stringResource(R.string.guard), r.guard, Modifier.fillMaxWidth()) { set(r.copy(guard = it)) }
        KeyedField("$id-ac", stringResource(R.string.action), r.action, Modifier.fillMaxWidth()) { set(r.copy(action = it)) }
        return
    }
    KeyedField("$id-nm", stringResource(R.string.name), r.name, Modifier.fillMaxWidth()) { set(r.copy(name = it)) }
    if (r.kind.isMessage) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyedField("$id-sm", stringResource(R.string.source_multiplicity), r.sourceMultiplicity, Modifier.weight(1f)) {
            set(r.copy(sourceMultiplicity = it))
        }
        KeyedField("$id-tm", stringResource(R.string.target_multiplicity), r.targetMultiplicity, Modifier.weight(1f)) {
            set(r.copy(targetMultiplicity = it))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyedField("$id-sr", stringResource(R.string.source_role), r.sourceRole, Modifier.weight(1f)) { set(r.copy(sourceRole = it)) }
        KeyedField("$id-tr", stringResource(R.string.target_role), r.targetRole, Modifier.weight(1f)) { set(r.copy(targetRole = it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DbRelationshipEditor(id: ElementId, r: DbRelationship, onIntent: (EditorIntent) -> Unit) {
    val set = { nr: DbRelationship -> onIntent(EditorIntent.EditConnector(id) { DbNotation.applyRelationship(it, nr) }) }
    Section(stringResource(R.string.relationship))
    Text(stringResource(R.string.child_side), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Cardinality.entries.forEach { c ->
            FilterChip(selected = r.sourceCardinality == c, onClick = {
                set(r.copy(sourceCardinality = c))
            }, label = { Text(c.displayName, style = MaterialTheme.typography.labelSmall) })
        }
    }
    Text(stringResource(R.string.parent_side), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Cardinality.entries.forEach { c ->
            FilterChip(selected = r.targetCardinality == c, onClick = {
                set(r.copy(targetCardinality = c))
            }, label = { Text(c.displayName, style = MaterialTheme.typography.labelSmall) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.identifying), modifier = Modifier.weight(1f))
        Switch(checked = r.identifying, onCheckedChange = { set(r.copy(identifying = it)) })
    }
    KeyedField("$id-rn", stringResource(R.string.name), r.name, Modifier.fillMaxWidth()) { set(r.copy(name = it)) }
}

@Composable
private fun ChenLinkEditor(id: ElementId, l: ChenLink, onIntent: (EditorIntent) -> Unit) {
    val set = { nl: ChenLink -> onIntent(EditorIntent.EditConnector(id) { DbNotation.applyChen(it, nl) }) }
    Section(stringResource(R.string.participation))
    KeyedField("$id-card", stringResource(R.string.cardinality), l.cardinality, Modifier.fillMaxWidth()) { set(l.copy(cardinality = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.total_participation), modifier = Modifier.weight(1f))
        Switch(checked = l.totalParticipation, onCheckedChange = { set(l.copy(totalParticipation = it)) })
    }
}

// ---- fields ---------------------------------------------------------------------------------------

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) = KeyedField(label, label, value, Modifier.fillMaxWidth(), onChange)

/** Text field that keeps its own text while typing (so formatting round trips never fight the cursor). */
@Composable
private fun KeyedField(key: String, label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(value) }
    var sent by remember(key) { mutableStateOf(value) }
    if (value != sent) {
        // Changed from outside (undo, another editor): show the stored value.
        text = value
        sent = value
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            sent = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
