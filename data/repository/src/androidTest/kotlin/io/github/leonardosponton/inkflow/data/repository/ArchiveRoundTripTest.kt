package io.github.leonardosponton.inkflow.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leonardosponton.inkflow.core.common.Logger
import io.github.leonardosponton.inkflow.core.common.Outcome
import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.Clock
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.UuidIdGenerator
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.data.files.FileStore
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import io.github.leonardosponton.inkflow.domain.dbmodel.DbNotation
import io.github.leonardosponton.inkflow.domain.dbmodel.DbShapes
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.flowchart.FlowchartNotation
import io.github.leonardosponton.inkflow.domain.uml.UmlNotation
import io.github.leonardosponton.inkflow.domain.uml.UmlShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArchiveRoundTripTest {
    private lateinit var db: InkflowDatabase
    private lateinit var files: FileStore
    private lateinit var projects: RoomProjectRepository
    private lateinit var scenes: RoomSceneRepository
    private lateinit var assets: FileAssetStore
    private lateinit var archiver: ZipProjectArchiver
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InkflowDatabase::class.java).build()
        root = File(context.cacheDir, "archive-test").apply { deleteRecursively() }
        files = FileStore(root)
        val json = SceneJson(NotationCatalog(listOf(FlowchartNotation, UmlNotation, DbNotation)).serializers)
        val io = Dispatchers.IO
        projects = RoomProjectRepository(db, files, json, Clock.System, UuidIdGenerator, io)
        scenes = RoomSceneRepository(db, json, Logger.None, io)
        assets = FileAssetStore(db, files, UuidIdGenerator, Clock.System, io)
        archiver = ZipProjectArchiver(db, files, json, UuidIdGenerator, io)
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun stroke(): InkStroke {
        val b = StrokePoints.Builder()
        repeat(10) { b.add(it * 3f, it * 2f, it * 10f, 0.7f, 0.2f, 1.1f) }
        return InkStroke(ElementId("ink"), Vec2(10.0, 10.0), InkBrush(size = 3f), b.build())
    }

    @Test
    fun exportDeleteImportRestoresTheProjectIdentically() = runBlocking {
        val pid = projects.createProject("Round trip", "First", DiagramProfile.UML)
        val tab1 = projects.observeProject(pid).first()!!.tabs.single().id
        val tab2 = projects.addTab(pid, "Second", DiagramProfile.DATABASE)
        projects.setTabColor(tab2, TabColor.GREEN)
        projects.saveCamera(tab1, Camera(Vec2(5.0, 6.0), 1.5, 0.25))
        val asset = assets.save(pid, byteArrayOf(1, 2, 3, 4), "image/png")
        val cls = ShapeNode(ElementId("cls"), UmlShapes.Class.key, Rect(0.0, 0.0, 200.0, 120.0), UmlShapes.Class.createContent())
        val note = ShapeNode(ElementId("note"), UmlShapes.Note.key, Rect(300.0, 0.0, 400.0, 80.0), PlainLabel("hello"))
        val edge = Connector(ElementId("edge"), Endpoint.Attached(cls.id), Endpoint.Attached(note.id), route = listOf(Vec2(200.0, 60.0), Vec2(300.0, 40.0)))
        val img = ImageElement(ElementId("img"), Rect(0.0, 300.0, 50.0, 350.0), asset)
        scenes.applyChange(tab1, SceneChange.of(Scene.Empty, listOf(cls, note, edge, stroke(), img)))
        val table = ShapeNode(ElementId("tbl"), DbShapes.Table.key, Rect(0.0, 0.0, 240.0, 120.0), DbShapes.Table.createContent())
        scenes.applyChange(tab2, SceneChange.of(Scene.Empty, listOf(table)))
        val before = projects.observeProject(pid).first()!!
        val scenesBefore = before.tabs.associate { it.id to scenes.load(it.id).elements }

        val bytes = ByteArrayOutputStream().also { archiver.export(pid, it) }.toByteArray()
        projects.deleteProject(pid)
        assertNull(projects.observeProject(pid).first())

        val imported = archiver.import(ByteArrayInputStream(bytes))
        assertTrue(imported is Outcome.Ok)
        assertEquals(pid, (imported as Outcome.Ok).value)
        val after = projects.observeProject(pid).first()!!
        assertEquals(before.copy(updatedAt = 0), after.copy(updatedAt = 0))
        after.tabs.forEach { assertEquals(scenesBefore.getValue(it.id), scenes.load(it.id).elements) }
        assertTrue(assets.read(pid, asset)!!.contentEquals(byteArrayOf(1, 2, 3, 4)))

        // Importing again while the original exists makes an independent copy.
        val copy = archiver.import(ByteArrayInputStream(bytes)) as Outcome.Ok
        assertTrue(copy.value != pid)
        val copyTabs = projects.observeProject(copy.value).first()!!.tabs
        assertEquals(2, copyTabs.size)
        val copiedFirst = scenes.load(copyTabs.first().id)
        assertEquals(5, copiedFirst.size)
        assertTrue(copiedFirst.elements.keys.none { it.value in setOf("cls", "note", "edge") })
        val copiedImg = copiedFirst.elements.values.filterIsInstance<ImageElement>().single()
        assertTrue(assets.read(copy.value, copiedImg.asset)!!.contentEquals(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun rejectsFilesThatAreNotArchives() = runBlocking {
        val r = archiver.import(ByteArrayInputStream("not a zip".toByteArray()))
        assertTrue(r is Outcome.Err)
    }
}
