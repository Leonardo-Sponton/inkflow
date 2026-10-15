package io.github.leonardosponton.inkflow.domain.flowchart

import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.PathFill
import io.github.leonardosponton.inkflow.domain.diagram.Perimeter
import io.github.leonardosponton.inkflow.domain.diagram.PortDefinition
import io.github.leonardosponton.inkflow.domain.diagram.Ports
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeOutline
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.domain.diagram.TextArea
import io.github.leonardosponton.inkflow.domain.diagram.at
import io.github.leonardosponton.inkflow.domain.diagram.outline

/** ISO 5807-style flowchart symbols, plus swimlanes. */
object FlowchartSymbols {
    val Id = NotationId("flowchart")
    private val STD = Size(160.0, 80.0)
    private const val BAR = 12.0
    private const val CUT = 16.0

    fun key(name: String) = ShapeTypeKey("flowchart.$name")

    private fun def(
        name: String,
        title: String,
        outline: ShapeOutline,
        size: Size = STD,
        text: TextArea = TextArea.Full,
        perimeter: Perimeter = Perimeter.RECTANGLE,
        keywords: List<String> = emptyList(),
        ports: List<PortDefinition> = Ports.Cardinal,
        label: String = "",
        aspect: Boolean = false,
        style: ShapeStyle = ShapeStyle.Default,
    ) = ShapeDefinition(
        key = key(name),
        notation = Id,
        displayName = title,
        defaultSize = size,
        outline = outline,
        ports = ports,
        perimeter = perimeter,
        textArea = text,
        defaultLabel = label,
        keepAspectRatio = aspect,
        keywords = keywords + "flowchart",
        defaultStyle = style,
    )

    val Terminator = def(
        "terminator",
        "Terminator",
        outline {
            path {
                move(0.25, 0.0)
                line(0.75, 0.0)
                cubic(at(1.05, 0.0), at(1.05, 1.0), at(0.75, 1.0))
                line(0.25, 1.0)
                cubic(at(-0.05, 1.0), at(-0.05, 0.0), at(0.25, 0.0))
                close()
            }
        },
        text = TextArea.inset(0.15, 0.1),
        keywords = listOf("start", "end", "begin", "stop"),
        label = "Start",
    )
    val Process = def("process", "Process", outline { rect() }, keywords = listOf("step", "action"))
    val PredefinedProcess = def(
        "predefined",
        "Predefined process",
        outline {
            rect()
            line(at(0.0, 0.0, BAR, 0.0), at(0.0, 1.0, BAR, 0.0))
            line(at(1.0, 0.0, -BAR, 0.0), at(1.0, 1.0, -BAR, 0.0))
        },
        text = TextArea(at(0.0, 0.0, BAR + 4, 4.0), at(1.0, 1.0, -BAR - 4, -4.0)),
        keywords = listOf("subroutine", "function", "call"),
    )
    val Decision = def(
        "decision",
        "Decision",
        outline { polygon(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5) },
        size = Size(160.0, 100.0),
        text = TextArea.inset(0.22, 0.22),
        perimeter = Perimeter.DIAMOND,
        keywords = listOf("if", "condition", "branch", "question"),
    )
    val Data = def(
        "data",
        "Data (input/output)",
        outline { polygon(0.2 to 0.0, 1.0 to 0.0, 0.8 to 1.0, 0.0 to 1.0) },
        text = TextArea.inset(0.2, 0.08),
        keywords = listOf("input", "output", "io", "read", "write"),
    )
    val ManualInput = def(
        "manual_input",
        "Manual input",
        outline { polygon(0.0 to 0.3, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0) },
        text = TextArea(at(0.0, 0.3, 6.0, 0.0), at(1.0, 1.0, -6.0, -4.0)),
        keywords = listOf("keyboard", "type"),
    )
    val ManualOperation = def(
        "manual_operation",
        "Manual operation",
        outline { polygon(0.0 to 0.0, 1.0 to 0.0, 0.8 to 1.0, 0.2 to 1.0) },
        text = TextArea.inset(0.2, 0.08),
        keywords = listOf("offline", "human"),
    )
    val Preparation = def(
        "preparation",
        "Preparation",
        outline { polygon(0.15 to 0.0, 0.85 to 0.0, 1.0 to 0.5, 0.85 to 1.0, 0.15 to 1.0, 0.0 to 0.5) },
        text = TextArea.inset(0.15, 0.08),
        keywords = listOf("init", "setup", "loop", "hexagon"),
    )
    val Document = def(
        "document",
        "Document",
        outline {
            path {
                move(0.0, 0.0)
                line(1.0, 0.0)
                line(1.0, 0.85)
                cubic(at(0.75, 0.65), at(0.5, 1.05), at(0.25, 0.92))
                cubic(at(0.15, 0.87), at(0.07, 0.86), at(0.0, 0.9))
                close()
            }
        },
        text = TextArea(at(0.0, 0.0, 6.0, 4.0), at(1.0, 0.82)),
        keywords = listOf("report", "print", "paper"),
    )
    val MultiDocument = def(
        "multi_document",
        "Multiple documents",
        outline {
            path {
                move(0.1, 0.0)
                line(1.0, 0.0)
                line(1.0, 0.7)
                line(0.95, 0.7)
                line(0.95, 0.07)
                line(0.1, 0.07)
                close()
            }
            path {
                move(0.05, 0.07)
                line(0.95, 0.07)
                line(0.95, 0.77)
                line(0.9, 0.77)
                line(0.9, 0.14)
                line(0.05, 0.14)
                close()
            }
            path {
                move(0.0, 0.14)
                line(0.9, 0.14)
                line(0.9, 0.86)
                cubic(at(0.7, 0.7), at(0.45, 1.05), at(0.22, 0.93))
                cubic(at(0.12, 0.88), at(0.06, 0.88), at(0.0, 0.91))
                close()
            }
        },
        text = TextArea(at(0.0, 0.14, 6.0, 4.0), at(0.9, 0.84)),
        keywords = listOf("documents", "reports"),
    )
    val StoredData = def(
        "stored_data",
        "Stored data",
        outline {
            path {
                move(0.12, 0.0)
                line(1.0, 0.0)
                cubic(at(0.88, 0.0), at(0.88, 1.0), at(1.0, 1.0))
                line(0.12, 1.0)
                cubic(at(-0.04, 1.0), at(-0.04, 0.0), at(0.12, 0.0))
                close()
            }
        },
        text = TextArea.inset(0.12, 0.08),
        keywords = listOf("storage", "file"),
    )
    val Database = BasicShapes.Cylinder.copy(
        key = key("database"),
        notation = Id,
        displayName = "Database (direct access)",
        keywords = listOf("database", "disk", "storage", "flowchart"),
    )
    val InternalStorage = def(
        "internal_storage",
        "Internal storage",
        outline {
            rect()
            line(at(0.0, 0.0, 0.0, CUT), at(1.0, 0.0, 0.0, CUT))
            line(at(0.0, 0.0, CUT, 0.0), at(0.0, 1.0, CUT, 0.0))
        },
        text = TextArea(at(0.0, 0.0, CUT + 4, CUT + 2), at(1.0, 1.0, -4.0, -4.0)),
        keywords = listOf("memory", "ram"),
    )
    val Display = def(
        "display",
        "Display",
        outline {
            path {
                move(0.0, 0.5)
                line(0.15, 0.0)
                line(0.85, 0.0)
                cubic(at(1.05, 0.0), at(1.05, 1.0), at(0.85, 1.0))
                line(0.15, 1.0)
                close()
            }
        },
        text = TextArea.inset(0.15, 0.08),
        keywords = listOf("screen", "monitor", "show"),
    )
    val Delay = def(
        "delay",
        "Delay",
        outline {
            path {
                move(0.0, 0.0)
                line(0.7, 0.0)
                cubic(at(1.07, 0.0), at(1.07, 1.0), at(0.7, 1.0))
                line(0.0, 1.0)
                close()
            }
        },
        text = TextArea(at(0.0, 0.0, 6.0, 4.0), at(0.85, 1.0, 0.0, -4.0)),
        keywords = listOf("wait", "pause"),
    )
    val Merge = def(
        "merge",
        "Merge",
        outline { polygon(0.0 to 0.0, 1.0 to 0.0, 0.5 to 1.0) },
        size = Size(90.0, 70.0),
        text = TextArea(at(0.2, 0.05), at(0.8, 0.5)),
        keywords = listOf("combine", "triangle"),
    )
    val Extract = def(
        "extract",
        "Extract",
        outline { polygon(0.5 to 0.0, 1.0 to 1.0, 0.0 to 1.0) },
        size = Size(90.0, 70.0),
        text = TextArea(at(0.2, 0.5), at(0.8, 0.95)),
        keywords = listOf("split", "triangle"),
    )
    val Sort = def(
        "sort",
        "Sort",
        outline {
            polygon(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5)
            line(at(0.0, 0.5), at(1.0, 0.5))
        },
        size = Size(100.0, 100.0),
        perimeter = Perimeter.DIAMOND,
        text = TextArea.inset(0.25, 0.25),
        keywords = listOf("order"),
    )
    val Collate = def(
        "collate",
        "Collate",
        outline {
            polygon(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0)
        },
        size = Size(80.0, 100.0),
        text = TextArea(at(0.2, 0.0), at(0.8, 0.45)),
        keywords = listOf("hourglass", "organize"),
    )
    val Or = def(
        "or",
        "Or",
        outline {
            oval()
            line(at(0.5, 0.0), at(0.5, 1.0))
            line(at(0.0, 0.5), at(1.0, 0.5))
        },
        size = Size(50.0, 50.0),
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea.inset(0.5, 0.5),
        keywords = listOf("plus", "junction"),
    )
    val SummingJunction = def(
        "summing_junction",
        "Summing junction",
        outline {
            oval()
            line(at(0.146, 0.146), at(0.854, 0.854))
            line(at(0.854, 0.146), at(0.146, 0.854))
        },
        size = Size(50.0, 50.0),
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea.inset(0.5, 0.5),
        keywords = listOf("and", "cross", "junction"),
    )
    val OnPageConnector = def(
        "connector",
        "On-page connector",
        outline { oval() },
        size = Size(56.0, 56.0),
        perimeter = Perimeter.ELLIPSE,
        aspect = true,
        text = TextArea.inset(0.15, 0.15),
        label = "A",
        keywords = listOf("reference", "circle", "jump"),
    )
    val OffPageConnector = def(
        "off_page",
        "Off-page connector",
        outline { polygon(0.0 to 0.0, 1.0 to 0.0, 1.0 to 0.65, 0.5 to 1.0, 0.0 to 0.65) },
        size = Size(64.0, 70.0),
        text = TextArea(at(0.05, 0.05), at(0.95, 0.65)),
        label = "1",
        keywords = listOf("page", "reference", "pentagon"),
    )
    val Annotation = def(
        "annotation",
        "Annotation",
        outline {
            path(PathFill.NONE) {
                move(0.0, 0.0, CUT, 0.0)
                line(0.0, 0.0)
                line(0.0, 1.0)
                line(0.0, 1.0, CUT, 0.0)
            }
        },
        text = TextArea(at(0.0, 0.0, 10.0, 4.0), at(1.0, 1.0, -4.0, -4.0)),
        label = "Comment",
        ports = listOf(Ports.West),
        style = ShapeStyle(fill = null, textAlign = TextAlign.START),
        keywords = listOf("comment", "note", "remark"),
    )
    val LoopLimitStart = def(
        "loop_start",
        "Loop limit (start)",
        outline { polygon(0.1 to 0.0, 0.9 to 0.0, 1.0 to 0.3, 1.0 to 1.0, 0.0 to 1.0, 0.0 to 0.3) },
        text = TextArea.inset(0.06, 0.25),
        keywords = listOf("loop", "for", "while", "repeat"),
    )
    val LoopLimitEnd = def(
        "loop_end",
        "Loop limit (end)",
        outline { polygon(0.0 to 0.0, 1.0 to 0.0, 1.0 to 0.7, 0.9 to 1.0, 0.1 to 1.0, 0.0 to 0.7) },
        text = TextArea.inset(0.06, 0.1),
        keywords = listOf("loop", "end", "repeat"),
    )

    private const val LANE_HEADER = 36.0
    private val LANE_FILL = ColorArgb(0x0A000000)

    val VerticalLane = ShapeDefinition(
        key = key("lane_vertical"),
        notation = Id,
        displayName = "Swimlane (vertical)",
        defaultSize = Size(260.0, 560.0),
        outline = outline {
            rect()
            line(at(0.0, 0.0, 0.0, LANE_HEADER), at(1.0, 0.0, 0.0, LANE_HEADER))
        },
        isContainer = true,
        headerHeight = LANE_HEADER,
        defaultLabel = "Lane",
        defaultStyle = ShapeStyle(fill = LANE_FILL),
        keywords = listOf("swimlane", "lane", "column", "actor", "flowchart"),
        ports = emptyList(),
    )
    val HorizontalLane = ShapeDefinition(
        key = key("lane_horizontal"),
        notation = Id,
        displayName = "Swimlane (horizontal)",
        defaultSize = Size(720.0, 220.0),
        outline = outline {
            rect()
            line(at(0.0, 0.0, LANE_HEADER, 0.0), at(0.0, 1.0, LANE_HEADER, 0.0))
        },
        isContainer = true,
        headerHeight = LANE_HEADER,
        headerOnLeft = true,
        defaultLabel = "Lane",
        defaultStyle = ShapeStyle(fill = LANE_FILL),
        keywords = listOf("swimlane", "lane", "row", "actor", "flowchart"),
        ports = emptyList(),
    )

    fun isLane(key: ShapeTypeKey) = key == VerticalLane.key || key == HorizontalLane.key

    val all: List<ShapeDefinition> = listOf(
        Terminator,
        Process,
        PredefinedProcess,
        Decision,
        Data,
        ManualInput,
        ManualOperation,
        Preparation,
        Document,
        MultiDocument,
        StoredData,
        Database,
        InternalStorage,
        Display,
        Delay,
        Merge,
        Extract,
        Sort,
        Collate,
        Or,
        SummingJunction,
        OnPageConnector,
        OffPageConnector,
        Annotation,
        LoopLimitStart,
        LoopLimitEnd,
        VerticalLane,
        HorizontalLane,
    )
}
