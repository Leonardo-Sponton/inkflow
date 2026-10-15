package io.github.leonardosponton.inkflow.di

import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationModule
import io.github.leonardosponton.inkflow.domain.dbmodel.DbNotation
import io.github.leonardosponton.inkflow.domain.flowchart.FlowchartNotation
import io.github.leonardosponton.inkflow.domain.uml.UmlNotation

/** Notations shipped with the app (Basic is always included by the catalog). */
object InstalledNotations {
    val all: List<NotationModule> = listOf(FlowchartNotation, UmlNotation, DbNotation)
}
