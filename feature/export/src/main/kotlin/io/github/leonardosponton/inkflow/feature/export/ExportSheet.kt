package io.github.leonardosponton.inkflow.feature.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.leonardosponton.inkflow.domain.dbmodel.SqlDialect
import io.github.leonardosponton.inkflow.domain.export.ExportFormat
import io.github.leonardosponton.inkflow.domain.export.ExportOptions
import io.github.leonardosponton.inkflow.domain.export.ExportScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ExportUiState(val options: ExportOptions = ExportOptions(), val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class ExportViewModel
    @Inject
    constructor(private val service: ExportService) : ViewModel() {
        private val _state = MutableStateFlow(ExportUiState())
        val state = _state.asStateFlow()

        fun update(f: (ExportOptions) -> ExportOptions) = _state.update { it.copy(options = f(it.options), message = null) }

        suspend fun fileName(target: ExportTarget) = service.fileName(target, _state.value.options.format)

        /** Writes to a document the user picked with the system file picker (SAF). */
        fun saveTo(context: Context, target: ExportTarget, uri: Uri) = run(context) {
            context.contentResolver.openOutputStream(uri)?.use { service.export(target, _state.value.options, it) }
                ?: ExportResult.Failed("Cannot write file")
        }

        /** Writes to app cache and opens the Android share sheet (no app-side networking). */
        fun share(context: Context, target: ExportTarget) = run(context) {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, service.fileName(target, _state.value.options.format))
            val result = file.outputStream().use { service.export(target, _state.value.options, it) }
            if (result is ExportResult.Done) {
                val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(_state.value.options.format.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            result
        }

        private fun run(context: Context, block: suspend () -> ExportResult) {
            _state.update { it.copy(busy = true, message = null) }
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { block() }
                _state.update {
                    it.copy(
                        busy = false,
                        message = when (result) {
                            ExportResult.Done -> context.getString(R.string.export_done)
                            is ExportResult.Failed -> context.getString(R.string.export_failed, result.message)
                        },
                    )
                }
            }
        }

        companion object {
            const val SHARE_DIR = "exports"
            const val AUTHORITY_SUFFIX = ".exports"
        }
    }

/** Bottom sheet with format, scope and options; saves through SAF or shares. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(target: ExportTarget, hasSelection: Boolean, onDismiss: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val o = state.options
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(o.format.mimeType)) { uri ->
        uri?.let { viewModel.saveTo(context, target, it) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.format), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(selected = o.format == f, onClick = {
                        viewModel.update { it.copy(format = f) }
                    }, label = { Text(f.displayName) })
                }
            }
            if (o.format in IMAGE_FORMATS) ImageOptions(o, hasSelection, viewModel)
            if (o.format == ExportFormat.SQL) {
                Text(stringResource(R.string.dialect), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SqlDialect.entries.forEach { d ->
                        FilterChip(selected = o.sqlDialect == d.name, onClick = {
                            viewModel.update { it.copy(sqlDialect = d.name) }
                        }, label = { Text(d.displayName) })
                    }
                }
            }
            Text(hint(o.format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !state.busy, onClick = {
                    scope.launch { save.launch(viewModel.fileName(target)) }
                }) { Text(stringResource(R.string.save_to_file)) }
                OutlinedButton(enabled = !state.busy, onClick = {
                    viewModel.share(context, target)
                }) { Text(stringResource(R.string.share)) }
            }
            Text(
                stringResource(R.string.offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageOptions(o: ExportOptions, hasSelection: Boolean, vm: ExportViewModel) {
    Text(stringResource(R.string.scope), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = o.scope == ExportScope.TAB, onClick = {
            vm.update { it.copy(scope = ExportScope.TAB) }
        }, label = { Text(stringResource(R.string.scope_tab)) })
        if (hasSelection) {
            FilterChip(selected = o.scope == ExportScope.SELECTION, onClick = {
                vm.update { it.copy(scope = ExportScope.SELECTION) }
            }, label = { Text(stringResource(R.string.scope_selection)) })
        }
        if (o.format == ExportFormat.PDF) {
            FilterChip(selected = o.scope == ExportScope.PROJECT, onClick = {
                vm.update { it.copy(scope = ExportScope.PROJECT) }
            }, label = { Text(stringResource(R.string.scope_project)) })
        }
    }
    if (o.format == ExportFormat.PNG) {
        Text(stringResource(R.string.scale), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SCALES.forEach { s ->
                FilterChip(selected = o.scale == s, onClick = { vm.update { it.copy(scale = s) } }, label = { Text("${s.toInt()}×") })
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.transparent), modifier = Modifier.weight(1f))
        Switch(checked = o.transparent, onCheckedChange = { v -> vm.update { it.copy(transparent = v) } })
    }
}

@Composable
private fun hint(f: ExportFormat) = stringResource(
    when (f) {
        ExportFormat.PNG -> R.string.hint_png
        ExportFormat.PDF -> R.string.hint_pdf
        ExportFormat.SVG -> R.string.hint_svg
        ExportFormat.INKFLOW -> R.string.hint_inkflow
        ExportFormat.SQL -> R.string.hint_sql
        ExportFormat.PLANTUML -> R.string.hint_plantuml
        ExportFormat.MERMAID -> R.string.hint_mermaid
    },
)

private val IMAGE_FORMATS = setOf(ExportFormat.PNG, ExportFormat.PDF, ExportFormat.SVG)
private val SCALES = listOf(1.0, 2.0, 3.0, 4.0)
