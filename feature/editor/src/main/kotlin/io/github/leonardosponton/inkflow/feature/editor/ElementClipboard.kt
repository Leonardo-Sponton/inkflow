package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide clipboard so elements can be pasted across tabs and projects. */
@Singleton
class ElementClipboard
    @Inject
    constructor() {
        data class Content(val project: ProjectId, val elements: List<CanvasElement>)

        var content: Content? = null
            private set

        /** Pastes in the same place repeat with a growing offset. */
        var pasteCount: Int = 0

        fun put(project: ProjectId, elements: List<CanvasElement>) {
            content = Content(project, elements)
            pasteCount = 0
        }
    }
