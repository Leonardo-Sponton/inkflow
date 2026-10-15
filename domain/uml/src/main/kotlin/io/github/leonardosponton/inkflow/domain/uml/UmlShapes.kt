package io.github.leonardosponton.inkflow.domain.uml

import io.github.leonardosponton.inkflow.core.geometry.Side
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.OutlineBuilder
import io.github.leonardosponton.inkflow.domain.diagram.PathBuilder
import io.github.leonardosponton.inkflow.domain.diagram.PathFill
import io.github.leonardosponton.inkflow.domain.diagram.Perimeter
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.PortDefinition
import io.github.leonardosponton.inkflow.domain.diagram.Ports
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeOutline
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.domain.diagram.TextArea
import io.github.leonardosponton.inkflow.domain.diagram.at
import io.github.leonardosponton.inkflow.domain.diagram.outline

/** UML shapes for class, use case, sequence, activity, state, component and deployment diagrams. */
object UmlShapes {
    val Id = NotationId("uml")
    private const val HEAD = 44.0
    private const val TAB_W = 70.0
    private const val TAB_H = 20.0
    private const val FRAGMENT_H = 26.0
    private const val BOX_DEPTH = 14.0
    private const val ICON = 12.0
    private val BLACK = ShapeStyle(fill = ColorArgb.Black)
    private val CONTAINER_FILL = ShapeStyle(fill = ColorArgb(0x08000000), textAlign = TextAlign.START)

    fun key(name: String) = ShapeTypeKey("uml.$name")

    @Suppress("LongParameterList")
    private fun def(
        name: String,
        title: String,
        outline: ShapeOutline,
        size: Size,
        content: () -> NodeContent = { PlainLabel("") },
        text: TextArea = TextArea.Full,
        perimeter: Perimeter = Perimeter.RECTANGLE,
        ports: List<PortDefinition> = Ports.Cardinal,
        style: ShapeStyle = ShapeStyle.Default,
        keywords: List<String> = emptyList(),
        aspect: Boolean = false,
        container: Boolean = false,
        header: Double = 0.0,
    ) = ShapeDefinition(
        key = key(name),
        notation = Id,
        displayName = title,
        defaultSize = size,
        outline = outline,
        ports = ports,
        perimeter = perimeter,
        textArea = text,
        defaultStyle = style,
        keepAspectRatio = aspect,
        isContainer = container,
        headerHeight = header,
        keywords = keywords + "uml",
        createContent = content,
    )

    // ---- class diagram --------------------------------------------------------------------------

    private fun classifier(name: String, title: String, kind: ClassifierKind, sample: UmlClassContent) = def(
        name,
        title,
        outline { rect() },
        Size(200.0, 120.0),
        content = { sample.copy(kind = kind) },
        ports = Ports.Dense,
        keywords = listOf("class diagram", "classifier", kind.name.lowercase()),
    )

    val Class = classifier(
        "class",
        "Class",
        ClassifierKind.CLASS,
        UmlClassContent(
            "Class",
            attributes = listOf(UmlMember("id", "Long", Visibility.PRIVATE)),
            operations = listOf(UmlMember("operation", "void", parameters = "")),
        ),
    )
    val AbstractClass = classifier("abstract_class", "Abstract class", ClassifierKind.ABSTRACT_CLASS, UmlClassContent("AbstractClass"))
    val Interface = classifier(
        "interface",
        "Interface",
        ClassifierKind.INTERFACE,
        UmlClassContent("Interface", operations = listOf(UmlMember("operation", "void", isAbstract = true, parameters = ""))),
    )
    val Enumeration =
        classifier("enum", "Enumeration", ClassifierKind.ENUMERATION, UmlClassContent("Enum", literals = listOf("FIRST", "SECOND")))

    val Package = def(
        "package",
        "Package",
        outline {
            path {
                move(0.0, 0.0)
                line(0.0, 0.0, TAB_W, 0.0)
                line(0.0, 0.0, TAB_W, TAB_H)
                line(0.0, 0.0, 0.0, TAB_H)
                close()
            }
            path {
                move(0.0, 0.0, 0.0, TAB_H)
                line(1.0, 0.0, 0.0, TAB_H)
                line(1.0, 1.0)
                line(0.0, 1.0)
                close()
            }
        },
        Size(360.0, 260.0),
        content = { PlainLabel("package") },
        style = CONTAINER_FILL,
        container = true,
        header = TAB_H + HEADER_PAD,
        keywords = listOf("namespace", "module", "folder"),
    )
    val Note = BasicShapes.Note.copy(key = key("note"), notation = Id, displayName = "Note", keywords = listOf("comment", "uml"))

    // ---- use case -------------------------------------------------------------------------------

    val Actor = def(
        "actor",
        "Actor",
        stickFigure(),
        Size(60.0, 110.0),
        content = { PlainLabel("Actor") },
        text = TextArea(at(0.0, 1.0, -50.0, -18.0), at(1.0, 1.0, 50.0, 6.0)),
        style = ShapeStyle(fill = null),
        keywords = listOf("user", "role", "stick", "use case"),
    )
    val UseCase = def(
        "use_case",
        "Use case",
        outline { oval() },
        Size(180.0, 80.0),
        content = { PlainLabel("Use case") },
        text = TextArea.inset(0.14, 0.15),
        perimeter = Perimeter.ELLIPSE,
        keywords = listOf("ellipse", "feature", "goal"),
    )
    val SystemBoundary = def(
        "system",
        "System boundary",
        outline { rect() },
        Size(420.0, 360.0),
        content = { PlainLabel("System") },
        style = CONTAINER_FILL.copy(fill = null),
        container = true,
        header = 32.0,
        keywords = listOf("subject", "boundary", "use case"),
    )

    // ---- sequence -------------------------------------------------------------------------------

    private val lifelinePorts = (1..LIFELINE_PORTS).flatMap { i ->
        val fy = LIFELINE_TOP + (1 - LIFELINE_TOP) * i / (LIFELINE_PORTS + 1)
        listOf(
            PortDefinition("lw$i", at(0.5, fy, 0.0, 0.0), Side.WEST),
            PortDefinition("le$i", at(0.5, fy, 0.0, 0.0), Side.EAST),
        )
    } + PortDefinition("top", at(0.5, 0.0), Side.NORTH)

    val Lifeline = def(
        "lifeline",
        "Lifeline",
        outline {
            path {
                move(0.0, 0.0)
                line(1.0, 0.0)
                line(1.0, 0.0, 0.0, HEAD)
                line(0.0, 0.0, 0.0, HEAD)
                close()
            }
            line(at(0.5, 0.0, 0.0, HEAD), at(0.5, 1.0), dashed = true)
        },
        Size(150.0, 420.0),
        content = { PlainLabel(":Object") },
        text = TextArea(at(0.0, 0.0, 4.0, 2.0), at(1.0, 0.0, -4.0, HEAD - 2)),
        ports = lifelinePorts,
        keywords = listOf("sequence", "object", "participant"),
    )
    val ActorLifeline = def(
        "actor_lifeline",
        "Actor lifeline",
        outline {
            stickFigureInto(this, HEAD * 2)
            line(at(0.5, 0.0, 0.0, HEAD * 2 + 20), at(0.5, 1.0), dashed = true)
        },
        Size(110.0, 420.0),
        content = { PlainLabel("Actor") },
        text = TextArea(at(0.0, 0.0, -20.0, HEAD * 2), at(1.0, 0.0, 20.0, HEAD * 2 + 20)),
        ports = lifelinePorts,
        style = ShapeStyle(fill = null),
        keywords = listOf("sequence", "user", "participant"),
    )
    val Activation = def(
        "activation",
        "Activation bar",
        outline { rect() },
        Size(16.0, 90.0),
        ports = Ports.Dense,
        keywords = listOf("sequence", "execution", "focus"),
    )
    val Fragment = def(
        "fragment",
        "Combined fragment",
        outline {
            rect(PathFill.NONE)
            path(PathFill.NONE) {
                move(0.0, 0.0, 0.0, FRAGMENT_H)
                line(0.0, 0.0, TAB_W, FRAGMENT_H)
                line(0.0, 0.0, TAB_W + 12, FRAGMENT_H - 10)
                line(0.0, 0.0, TAB_W + 12, 0.0)
            }
        },
        Size(420.0, 220.0),
        content = { PlainLabel("alt [condition]") },
        style = CONTAINER_FILL.copy(fill = null),
        container = true,
        header = FRAGMENT_H,
        keywords = listOf("alt", "opt", "loop", "par", "sequence", "frame"),
    )
    val Destroy = def(
        "destroy",
        "Destroy",
        outline {
            line(at(0.0, 0.0), at(1.0, 1.0))
            line(at(1.0, 0.0), at(0.0, 1.0))
        },
        Size(30.0, 30.0),
        aspect = true,
        text = TextArea.inset(0.5, 0.5),
        keywords = listOf("sequence", "delete", "x"),
    )

    // ---- activity -------------------------------------------------------------------------------

    val Initial =
        def(
            "initial",
            "Initial node",
            outline {
                oval(fill = PathFill.STROKE_COLOR)
            },
            Size(30.0, 30.0),
            perimeter = Perimeter.ELLIPSE,
            aspect = true,
            text = TextArea.inset(0.5, 0.5),
            keywords = listOf("start", "activity", "state"),
        )
    val Final = def(
        "final",
        "Final node",
        outline {
            oval()
            oval(at(0.2, 0.2), at(0.8, 0.8), PathFill.STROKE_COLOR)
        },
        Size(34.0, 34.0),
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea.inset(0.5, 0.5),
        keywords = listOf("end", "activity", "state"),
    )
    val FlowFinal = def(
        "flow_final",
        "Flow final",
        outline {
            oval()
            line(at(0.146, 0.146), at(0.854, 0.854))
            line(at(0.854, 0.146), at(0.146, 0.854))
        },
        Size(30.0, 30.0),
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea.inset(0.5, 0.5),
        keywords = listOf("end", "activity"),
    )
    val Action =
        def("action", "Action", BasicShapes.roundedRect(ROUND), Size(160.0, 70.0), content = {
            PlainLabel("Action")
        }, keywords = listOf("activity", "step"))
    val ActivityDecision = def(
        "decision",
        "Decision / merge",
        outline { polygon(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5) },
        Size(60.0, 60.0),
        perimeter = Perimeter.DIAMOND,
        text = TextArea.inset(0.5, 0.5),
        keywords = listOf("activity", "branch", "merge"),
    )
    val ForkHorizontal =
        def(
            "fork_h",
            "Fork / join (horizontal)",
            outline {
                rect(PathFill.STROKE_COLOR)
            },
            Size(180.0, 8.0),
            ports = Ports.Dense,
            style = BLACK,
            text = TextArea.inset(0.5, 0.5),
            keywords = listOf("fork", "join", "bar", "parallel", "activity"),
        )
    val ForkVertical =
        def(
            "fork_v",
            "Fork / join (vertical)",
            outline {
                rect(PathFill.STROKE_COLOR)
            },
            Size(8.0, 180.0),
            ports = Ports.Dense,
            style = BLACK,
            text = TextArea.inset(0.5, 0.5),
            keywords = listOf("fork", "join", "bar", "parallel", "activity"),
        )
    val ObjectNode =
        def(
            "object_node",
            "Object node",
            outline {
                rect()
            },
            Size(140.0, 60.0),
            content = { PlainLabel(":Object") },
            keywords = listOf("activity", "data"),
        )
    val SendSignal = def(
        "send_signal",
        "Send signal",
        outline { polygon(0.0 to 0.0, 0.8 to 0.0, 1.0 to 0.5, 0.8 to 1.0, 0.0 to 1.0) },
        Size(150.0, 60.0),
        content = { PlainLabel("Send") },
        text = TextArea(at(0.0, 0.0, 6.0, 4.0), at(0.8, 1.0, 0.0, -4.0)),
        keywords = listOf("activity", "signal", "event"),
    )
    val AcceptEvent = def(
        "accept_event",
        "Accept event",
        outline { polygon(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0, 0.2 to 0.5) },
        Size(150.0, 60.0),
        content = { PlainLabel("Receive") },
        text = TextArea(at(0.2, 0.0, 0.0, 4.0), at(1.0, 1.0, -6.0, -4.0)),
        keywords = listOf("activity", "signal", "event"),
    )
    val Partition = def(
        "partition",
        "Partition (swimlane)",
        outline {
            rect()
            line(at(0.0, 0.0, 0.0, 34.0), at(1.0, 0.0, 0.0, 34.0))
        },
        Size(260.0, 520.0),
        content = { PlainLabel("Partition") },
        style = CONTAINER_FILL.copy(textAlign = TextAlign.CENTER),
        container = true,
        header = 34.0,
        ports = emptyList(),
        keywords = listOf("swimlane", "lane", "activity"),
    )

    // ---- state machine --------------------------------------------------------------------------

    val State = def(
        "state",
        "State",
        BasicShapes.roundedRect(ROUND),
        Size(160.0, 70.0),
        content = { PlainLabel("State") },
        keywords = listOf("state machine", "status"),
    )
    val CompositeState = def(
        "composite_state",
        "Composite state",
        outline {
            path(PathFill.FILL) { roundedInto(this) }
            line(at(0.0, 0.0, 0.0, 30.0), at(1.0, 0.0, 0.0, 30.0))
        },
        Size(360.0, 240.0),
        content = { PlainLabel("Composite") },
        style = CONTAINER_FILL.copy(textAlign = TextAlign.CENTER),
        container = true,
        header = 30.0,
        keywords = listOf("state machine", "region", "superstate"),
    )
    val InitialState = Initial.copy(
        key = key("initial_state"),
        displayName = "Initial pseudostate",
        keywords = listOf("state machine", "start", "uml"),
    )
    val FinalState = Final.copy(key = key("final_state"), displayName = "Final state", keywords = listOf("state machine", "end", "uml"))
    val Choice = ActivityDecision.copy(key = key("choice"), displayName = "Choice", keywords = listOf("state machine", "junction", "uml"))
    val History = def(
        "history",
        "History",
        outline { oval() },
        Size(36.0, 36.0),
        content = { PlainLabel("H") },
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea.inset(0.1, 0.1),
        keywords = listOf("state machine", "shallow", "deep"),
    )

    // ---- component / deployment -----------------------------------------------------------------

    val Component = def(
        "component",
        "Component",
        outline {
            rect()
            path {
                move(1.0, 0.0, -ICON * 2, ICON / 2)
                line(1.0, 0.0, -ICON / 2, ICON / 2)
                line(1.0, 0.0, -ICON / 2, ICON * 2)
                line(1.0, 0.0, -ICON * 2, ICON * 2)
                close()
            }
            path {
                move(1.0, 0.0, -ICON * 2 - 4, ICON)
                line(1.0, 0.0, -ICON * 2 + 4, ICON)
                line(1.0, 0.0, -ICON * 2 + 4, ICON + 4)
                line(1.0, 0.0, -ICON * 2 - 4, ICON + 4)
                close()
            }
            path {
                move(1.0, 0.0, -ICON * 2 - 4, ICON + 8)
                line(1.0, 0.0, -ICON * 2 + 4, ICON + 8)
                line(1.0, 0.0, -ICON * 2 + 4, ICON + 12)
                line(1.0, 0.0, -ICON * 2 - 4, ICON + 12)
                close()
            }
        },
        Size(200.0, 90.0),
        content = { PlainLabel("«component»\nComponent") },
        text = TextArea(at(0.0, 0.0, 6.0, 4.0), at(1.0, 1.0, -ICON * 3, -4.0)),
        ports = Ports.Dense,
        keywords = listOf("module", "service", "component"),
    )
    val ProvidedInterface = def(
        "provided_interface",
        "Provided interface (lollipop)",
        outline { oval() },
        Size(24.0, 24.0),
        content = { PlainLabel("") },
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea(at(0.0, 1.0, -60.0, 0.0), at(1.0, 1.0, 60.0, 20.0)),
        keywords = listOf("lollipop", "interface", "component"),
    )
    val RequiredInterface = def(
        "required_interface",
        "Required interface (socket)",
        outline { path(PathFill.NONE) { arc(at(0.0, 0.0), at(1.0, 1.0), 270.0, -180.0) } },
        Size(28.0, 28.0),
        content = { PlainLabel("") },
        text = TextArea(at(0.0, 1.0, -60.0, 0.0), at(1.0, 1.0, 60.0, 20.0)),
        keywords = listOf("socket", "interface", "component"),
    )
    val Port =
        def(
            "port",
            "Port",
            outline {
                rect()
            },
            Size(16.0, 16.0),
            aspect = true,
            text = TextArea.inset(0.5, 0.5),
            keywords = listOf("component", "interface"),
        )
    val Node = def(
        "node",
        "Node (device)",
        outline {
            path {
                move(0.0, 0.0, 0.0, BOX_DEPTH)
                line(0.0, 0.0, BOX_DEPTH, 0.0)
                line(1.0, 0.0)
                line(1.0, 1.0, 0.0, -BOX_DEPTH)
                line(1.0, 1.0, -BOX_DEPTH, 0.0)
                line(0.0, 1.0)
                close()
            }
            polyline(at(0.0, 0.0, 0.0, BOX_DEPTH), at(1.0, 0.0, -BOX_DEPTH, BOX_DEPTH), at(1.0, 0.0))
            line(at(1.0, 0.0, -BOX_DEPTH, BOX_DEPTH), at(1.0, 1.0, -BOX_DEPTH, 0.0))
        },
        Size(260.0, 180.0),
        content = { PlainLabel("«device»\nServer") },
        text = TextArea(at(0.0, 0.0, 8.0, BOX_DEPTH + 6), at(1.0, 0.0, -BOX_DEPTH - 8, BOX_DEPTH + 52)),
        container = true,
        header = 0.0,
        keywords = listOf("deployment", "server", "machine", "execution environment"),
    )
    val Artifact = def(
        "artifact",
        "Artifact",
        BasicShapes.foldedNoteOutline(14.0),
        Size(170.0, 80.0),
        content = { PlainLabel("«artifact»\napp.jar") },
        keywords = listOf("deployment", "file", "jar", "binary"),
    )

    val all: List<ShapeDefinition> = listOf(
        Class,
        AbstractClass,
        Interface,
        Enumeration,
        Package,
        Note,
        Actor,
        UseCase,
        SystemBoundary,
        Lifeline,
        ActorLifeline,
        Activation,
        Fragment,
        Destroy,
        Initial,
        Final,
        FlowFinal,
        Action,
        ActivityDecision,
        ForkHorizontal,
        ForkVertical,
        ObjectNode,
        SendSignal,
        AcceptEvent,
        Partition,
        State,
        CompositeState,
        InitialState,
        FinalState,
        Choice,
        History,
        Component,
        ProvidedInterface,
        RequiredInterface,
        Port,
        Node,
        Artifact,
    )

    private const val HEADER_PAD = 24.0
    private const val ROUND = 14.0
    private const val LIFELINE_PORTS = 12
    private const val LIFELINE_TOP = 0.12

    private fun stickFigure() = outline { stickFigureInto(this, null) }

    /** Stick figure filling the frame (or the top [height] units when given). */
    private fun stickFigureInto(b: OutlineBuilder, height: Double?) {
        fun y(f: Double) = if (height == null) at(0.0, f) else at(0.0, 0.0, 0.0, height * f)

        fun p(fx: Double, fy: Double) = y(fy).copy(fx = fx)
        b.oval(p(0.3, 0.0), p(0.7, 0.22), PathFill.NONE)
        b.line(p(0.5, 0.22), p(0.5, 0.6))
        b.line(p(0.1, 0.35), p(0.9, 0.35))
        b.line(p(0.5, 0.6), p(0.15, 0.85))
        b.line(p(0.5, 0.6), p(0.85, 0.85))
    }

    private fun roundedInto(b: PathBuilder) {
        val r = ROUND
        b.move(0.0, 0.0, r, 0.0)
        b.line(1.0, 0.0, -r, 0.0)
        b.quad(at(1.0, 0.0), at(1.0, 0.0, 0.0, r))
        b.line(1.0, 1.0, 0.0, -r)
        b.quad(at(1.0, 1.0), at(1.0, 1.0, -r, 0.0))
        b.line(0.0, 1.0, r, 0.0)
        b.quad(at(0.0, 1.0), at(0.0, 1.0, 0.0, -r))
        b.line(0.0, 0.0, 0.0, r)
        b.quad(at(0.0, 0.0), at(0.0, 0.0, r, 0.0))
        b.close()
    }
}
