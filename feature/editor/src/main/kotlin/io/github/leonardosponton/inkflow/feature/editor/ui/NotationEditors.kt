package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.R

/** Structured editor for a node's content; notations add their own branches here. */
@Composable
fun NodeContentEditor(node: ShapeNode, catalog: NotationCatalog, onIntent: (EditorIntent) -> Unit) {
    when (val content = node.content) {
        is PlainLabel -> OutlinedTextField(
            value = content.text,
            onValueChange = { onIntent(EditorIntent.EditContent(node.id, PlainLabel(it))) },
            label = { Text(stringResource(R.string.label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        else -> Unit
    }
    catalog.hashCode()
}

/** Editor for connector semantics (UML relation details, DB cardinalities…). */
@Composable
fun ConnectorSemanticsEditor(connector: Connector, onIntent: (EditorIntent) -> Unit) {
    connector.hashCode()
    onIntent.hashCode()
}
