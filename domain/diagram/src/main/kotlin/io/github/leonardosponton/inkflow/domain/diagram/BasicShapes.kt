package io.github.leonardosponton.inkflow.domain.diagram

import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign

/** Generic geometric shapes available on every tab (also the targets of ink-to-shape). */
object BasicShapes {
    private val N = NotationId.Basic
    private const val R = 10.0
    private const val FOLD = 16.0

    fun key(name: String) = ShapeTypeKey("basic.$name")

    /** Rounded corners drawn with quadratic curves; [r] is a fixed offset in world units. */
    fun roundedRect(r: Double, fill: PathFill = PathFill.FILL) = outline {
        path(fill) {
            move(0.0, 0.0, r, 0.0)
            line(1.0, 0.0, -r, 0.0)
            quad(at(1.0, 0.0), at(1.0, 0.0, 0.0, r))
            line(1.0, 1.0, 0.0, -r)
            quad(at(1.0, 1.0), at(1.0, 1.0, -r, 0.0))
            line(0.0, 1.0, r, 0.0)
            quad(at(0.0, 1.0), at(0.0, 1.0, 0.0, -r))
            line(0.0, 0.0, 0.0, r)
            quad(at(0.0, 0.0), at(0.0, 0.0, r, 0.0))
            close()
        }
    }

    val Rectangle =
        ShapeDefinition(key("rectangle"), N, "Rectangle", Size(160.0, 90.0), outline { rect() }, keywords = listOf("box", "square"))
    val RoundedRectangle = ShapeDefinition(key("rounded"), N, "Rounded rectangle", Size(160.0, 90.0), roundedRect(R))
    val Ellipse = ShapeDefinition(
        key("ellipse"),
        N,
        "Ellipse",
        Size(150.0, 100.0),
        outline { oval() },
        perimeter = Perimeter.ELLIPSE,
        textArea = TextArea.inset(0.15, 0.15),
        keywords = listOf("circle", "oval"),
    )
    val Circle = Ellipse.copy(key = key("circle"), displayName = "Circle", defaultSize = Size(100.0, 100.0), keepAspectRatio = true)
    val Diamond = ShapeDefinition(
        key("diamond"),
        N,
        "Diamond",
        Size(140.0, 100.0),
        outline { polygon(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5) },
        perimeter = Perimeter.DIAMOND,
        textArea = TextArea.inset(0.25, 0.25),
        keywords = listOf("rhombus"),
    )
    val Triangle = ShapeDefinition(
        key("triangle"),
        N,
        "Triangle",
        Size(120.0, 100.0),
        outline { polygon(0.5 to 0.0, 1.0 to 1.0, 0.0 to 1.0) },
        textArea = TextArea(at(0.25, 0.45), at(0.75, 0.95)),
    )
    val Parallelogram = ShapeDefinition(
        key("parallelogram"),
        N,
        "Parallelogram",
        Size(160.0, 80.0),
        outline { polygon(0.2 to 0.0, 1.0 to 0.0, 0.8 to 1.0, 0.0 to 1.0) },
        textArea = TextArea.inset(0.2, 0.1),
    )
    val Hexagon = ShapeDefinition(
        key("hexagon"),
        N,
        "Hexagon",
        Size(160.0, 90.0),
        outline { polygon(0.15 to 0.0, 0.85 to 0.0, 1.0 to 0.5, 0.85 to 1.0, 0.15 to 1.0, 0.0 to 0.5) },
        textArea = TextArea.inset(0.15, 0.1),
    )
    val Cylinder = ShapeDefinition(
        key("cylinder"),
        N,
        "Cylinder",
        Size(110.0, 130.0),
        cylinderOutline(),
        textArea = TextArea(at(0.0, 0.0, 6.0, 30.0), at(1.0, 1.0, -6.0, -8.0)),
        keywords = listOf("database", "storage"),
    )
    val Note = ShapeDefinition(
        key("note"),
        N,
        "Note",
        Size(160.0, 100.0),
        foldedNoteOutline(FOLD),
        defaultStyle = ShapeStyle(fill = ColorArgb.StickyYellow, textAlign = TextAlign.START),
        textArea = TextArea(at(0.0, 0.0, 8.0, 8.0), at(1.0, 1.0, -FOLD, -8.0)),
        keywords = listOf("comment", "annotation"),
    )
    val Text = ShapeDefinition(
        key("text"),
        N,
        "Text",
        Size(160.0, 40.0),
        ShapeOutline.Empty,
        defaultLabel = "Text",
        defaultStyle = ShapeStyle(fill = null),
    )
    val Frame = ShapeDefinition(
        key("frame"),
        N,
        "Frame",
        Size(480.0, 320.0),
        outline {
            rect()
            line(at(0.0, 0.0, 0.0, 32.0), at(1.0, 0.0, 0.0, 32.0))
        },
        isContainer = true,
        headerHeight = 32.0,
        defaultLabel = "Frame",
        defaultStyle = ShapeStyle(fill = ColorArgb(0x0F1A73E8), textAlign = TextAlign.START),
        keywords = listOf("group", "container", "section"),
    )

    val all: List<ShapeDefinition> = listOf(
        Rectangle,
        RoundedRectangle,
        Ellipse,
        Circle,
        Diamond,
        Triangle,
        Parallelogram,
        Hexagon,
        Cylinder,
        Note,
        Text,
        Frame,
    )

    /** Note with a folded top-right corner of [fold] world units. */
    fun foldedNoteOutline(fold: Double) = outline {
        path {
            move(0.0, 0.0)
            line(1.0, 0.0, -fold, 0.0)
            line(1.0, 0.0, 0.0, fold)
            line(1.0, 1.0)
            line(0.0, 1.0)
            close()
        }
        polyline(at(1.0, 0.0, -fold, 0.0), at(1.0, 0.0, -fold, fold), at(1.0, 0.0, 0.0, fold))
    }

    /** Vertical cylinder with elliptical caps of fixed height. */
    fun cylinderOutline(cap: Double = 12.0) = outline {
        path {
            move(0.0, 0.0, 0.0, cap)
            arc(at(0.0, 0.0), at(1.0, 0.0, 0.0, 2 * cap), 180.0, 180.0)
            line(1.0, 1.0, 0.0, -cap)
            arc(at(0.0, 1.0, 0.0, -2 * cap), at(1.0, 1.0), 0.0, 180.0)
            close()
        }
        path(PathFill.NONE) { arc(at(0.0, 0.0), at(1.0, 0.0, 0.0, 2 * cap), 0.0, 180.0) }
    }
}
