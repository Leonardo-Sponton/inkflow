package io.github.leonardosponton.inkflow.feature.editor

import androidx.lifecycle.viewModelScope
import io.github.leonardosponton.inkflow.domain.project.Naming
import kotlinx.coroutines.launch

/** Project and tab intents. */
internal class TabActions(private val vm: EditorViewModel) {
    private val scope get() = vm.viewModelScope

    fun handle(intent: EditorIntent): Boolean {
        when (intent) {
            is EditorIntent.SelectTab -> vm.openTab(intent.id)
            is EditorIntent.AddTab -> scope.launch {
                val existing = vm.state.value.tabs.map { it.title }
                val id = vm.tabRepository.addTab(vm.projectId, Naming.nextName(Naming.DEFAULT_TAB, existing), intent.profile)
                vm.openTab(id)
            }
            is EditorIntent.RenameTab -> scope.launch {
                vm.tabRepository.renameTab(intent.id, Naming.sanitize(intent.title, Naming.DEFAULT_TAB))
            }
            is EditorIntent.DuplicateTab -> scope.launch {
                vm.flushPersistence()
                vm.openTab(vm.tabRepository.duplicateTab(intent.id))
            }
            is EditorIntent.DeleteTab -> deleteTab(intent)
            is EditorIntent.RestoreTab -> scope.launch {
                vm.tabRepository.restoreTab(intent.id)
                vm.openTab(intent.id)
            }
            is EditorIntent.SetTabColor -> scope.launch { vm.tabRepository.setTabColor(intent.id, intent.color) }
            is EditorIntent.SetTabProfile -> scope.launch { vm.tabRepository.setTabProfile(intent.id, intent.profile) }
            is EditorIntent.SetTabGrid -> vm.activeTab()?.let { t -> scope.launch { vm.tabRepository.setTabGrid(t.id, intent.grid) } }
            is EditorIntent.MoveTab -> moveTab(intent.from, intent.to)
            is EditorIntent.RenameProject -> scope.launch {
                vm.projects.renameProject(vm.projectId, Naming.sanitize(intent.name, Naming.DEFAULT_PROJECT))
            }
            else -> return false
        }
        return true
    }

    private fun deleteTab(intent: EditorIntent.DeleteTab) {
        val tabs = vm.state.value.tabs
        if (tabs.size <= 1) return
        val index = tabs.indexOfFirst { it.id == intent.id }
        val title = tabs.getOrNull(index)?.title.orEmpty()
        if (vm.state.value.activeTabId == intent.id) {
            val neighbor = tabs.getOrNull(index + 1) ?: tabs.getOrNull(index - 1)
            neighbor?.let { vm.openTab(it.id) }
        }
        scope.launch { vm.tabRepository.deleteTab(intent.id) }
        vm.updateState {
            it.copy(message = EditorMessage("“$title” deleted", "Undo", EditorIntent.RestoreTab(intent.id)))
        }
    }

    private fun moveTab(from: Int, to: Int) {
        val tabs = vm.state.value.tabs
        if (from !in tabs.indices || to !in tabs.indices || from == to) return
        val reordered = tabs.toMutableList().apply { add(to, removeAt(from)) }
        vm.updateState { it.copy(tabs = reordered) }
        scope.launch { vm.tabRepository.reorderTabs(vm.projectId, reordered.map { it.id }) }
    }
}
