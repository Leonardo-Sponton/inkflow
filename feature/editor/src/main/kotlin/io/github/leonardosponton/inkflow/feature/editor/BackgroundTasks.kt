package io.github.leonardosponton.inkflow.feature.editor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Debounced work after edits: validation, gallery thumbnail and "recently edited" stamp. */
internal class BackgroundTasks(private val vm: EditorViewModel) {
    private var validationJob: Job? = null
    private var thumbnailJob: Job? = null
    private var thumbnailDirty = false
    private var touched = false

    fun afterChange(edited: Boolean) {
        scheduleValidation()
        if (!edited) return
        if (!touched) {
            touched = true
            vm.viewModelScope.launch { vm.projects.touch(vm.projectId) }
        }
        if (isFirstTab()) {
            thumbnailDirty = true
            thumbnailJob?.cancel()
            thumbnailJob = vm.viewModelScope.launch {
                delay(THUMBNAIL_MS)
                renderThumbnail()
            }
        }
    }

    private fun isFirstTab() = vm.state.value.tabs.firstOrNull()?.id == vm.state.value.activeTabId

    private fun scheduleValidation() {
        validationJob?.cancel()
        val scene = vm.session.scene
        validationJob = vm.viewModelScope.launch {
            delay(VALIDATION_MS)
            val issues = withContext(Dispatchers.Default) { vm.catalog.validate(scene) }
            vm.issuesChanged(issues)
        }
    }

    suspend fun finalThumbnail() {
        if (thumbnailDirty) renderThumbnail()
    }

    private suspend fun renderThumbnail() {
        if (!isFirstTab()) return
        thumbnailDirty = false
        val scene = vm.session.scene
        val dark = vm.settings.darkCanvas
        val png = vm.renderers.offscreen(vm.projectId) { it.thumbnailPng(scene, dark) } ?: return
        vm.projects.saveThumbnail(vm.projectId, png)
    }

    private companion object {
        const val VALIDATION_MS = 350L
        const val THUMBNAIL_MS = 2_500L
    }
}
