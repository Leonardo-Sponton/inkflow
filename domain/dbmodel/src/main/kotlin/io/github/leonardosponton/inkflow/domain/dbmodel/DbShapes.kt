package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.domain.diagram.MarkerDefinition
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.PathFill
import io.github.leonardosponton.inkflow.domain.diagram.Perimeter
import io.github.leonardosponton.inkflow.domain.diagram.Ports
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeOutline
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.domain.diagram.TextArea
import io.github.leonardosponton.inkflow.domain.diagram.at
import io.github.leonardosponton.inkflow.domain.diagram.mk
import io.github.leonardosponton.inkflow.domain.diagram.outline

/** Conceptual (Chen) and physical (Crow's Foot) database shapes. */
object DbShapes {
    val Id = NotationId("db")
    private const val GAP = 6.0

    fun key(name: String) = ShapeTypeKey("db.$name")

    private fun chen(
        name: String,
        title: String,
        outline: ShapeOutline,
        size: Size,
        label: String,
        perimeter: Perimeter = Perimeter.RECTANGLE,
        text: TextArea = TextArea.Full,
        underline: Boolean = false,
        keywords: List<String> = emptyList(),
    ) = ShapeDefinition(
        key = key(name),
        notation = Id,
        displayName = title,
        defaultSize = size,
        outline = outline,
        perimeter = perimeter,
        textArea = text,
        defaultLabel = label,
        labelUnderline = underline,
        keywords = keywords + listOf("chen", "er", "conceptual"),
    )

    val Table = ShapeDefinition(
        key = key("table"),
        notation = Id,
        displayName = "Table",
        defaultSize = Size(240.0, 120.0),
        outline = outline { rect() },
        ports = Ports.Dense,
        defaultStyle = ShapeStyle(fill = ColorArgb(0xFFF1F6FE.toInt())),
        keywords = listOf("crow's foot", "relational", "physical", "sql", "entity"),
        createContent = {
            DbTableContent(
                "table",
                listOf(
                    DbColumn("c-id", "id", "bigint", primaryKey = true, notNull = true),
                    DbColumn("c-name", "name", "varchar(120)", notNull = true),
                ),
            )
        },
    )

    val Entity = chen("entity", "Entity", outline { rect() }, Size(150.0, 64.0), "Entity", keywords = listOf("strong"))
    val WeakEntity = chen(
        "weak_entity",
        "Weak entity",
        outline {
            rect()
            path(PathFill.NONE) {
                move(0.0, 0.0, GAP, GAP)
                line(1.0, 0.0, -GAP, GAP)
                line(1.0, 1.0, -GAP, -GAP)
                line(0.0, 1.0, GAP, -GAP)
                close()
            }
        },
        Size(150.0, 64.0),
        "Weak entity",
        text = TextArea(at(0.0, 0.0, GAP * 2, GAP), at(1.0, 1.0, -GAP * 2, -GAP)),
    )
    private val ATTR = Size(130.0, 56.0)
    val Attribute = chen("attribute", "Attribute", outline { oval() }, ATTR, "attribute", Perimeter.ELLIPSE, TextArea.inset(0.12, 0.12))
    val KeyAttribute =
        chen(
            "key_attribute",
            "Key attribute",
            outline {
                oval()
            },
            ATTR,
            "id",
            Perimeter.ELLIPSE,
            TextArea.inset(0.12, 0.12),
            underline = true,
            keywords = listOf("primary", "identifier"),
        )
    val PartialKey = chen(
        "partial_key",
        "Partial key",
        outline { oval(dashed = false) },
        ATTR,
        "number",
        Perimeter.ELLIPSE,
        TextArea.inset(0.12, 0.12),
        underline = true,
        keywords = listOf("discriminator", "weak"),
    )
    val MultivaluedAttribute = chen(
        "multivalued",
        "Multivalued attribute",
        outline {
            oval()
            oval(at(0.0, 0.0, GAP, GAP), at(1.0, 1.0, -GAP, -GAP), PathFill.NONE)
        },
        ATTR,
        "phones",
        Perimeter.ELLIPSE,
        TextArea.inset(0.15, 0.15),
    )
    val DerivedAttribute =
        chen(
            "derived",
            "Derived attribute",
            outline {
                oval(dashed = true)
            },
            ATTR,
            "age",
            Perimeter.ELLIPSE,
            TextArea.inset(0.12, 0.12),
            keywords = listOf("computed"),
        )
    val CompositeAttribute =
        chen("composite", "Composite attribute", outline { oval() }, ATTR, "address", Perimeter.ELLIPSE, TextArea.inset(0.12, 0.12))
    val Relationship = chen(
        "relationship",
        "Relationship",
        outline { polygon(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5) },
        Size(150.0, 80.0),
        "relates",
        Perimeter.DIAMOND,
        TextArea.inset(0.22, 0.22),
        keywords = listOf("diamond", "association"),
    )
    val IdentifyingRelationship = chen(
        "identifying_relationship",
        "Identifying relationship",
        outline {
            polygon(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5)
            path(PathFill.NONE) {
                move(0.5, 0.0, 0.0, GAP * 1.4)
                line(1.0, 0.5, -GAP * 2, 0.0)
                line(0.5, 1.0, 0.0, -GAP * 1.4)
                line(0.0, 0.5, GAP * 2, 0.0)
                close()
            }
        },
        Size(150.0, 80.0),
        "owns",
        Perimeter.DIAMOND,
        TextArea.inset(0.25, 0.25),
        keywords = listOf("weak", "diamond"),
    )

    val all: List<ShapeDefinition> = listOf(
        Table,
        Entity,
        WeakEntity,
        Attribute,
        KeyAttribute,
        PartialKey,
        MultivaluedAttribute,
        DerivedAttribute,
        CompositeAttribute,
        Relationship,
        IdentifyingRelationship,
    )
}

/** Crow's foot end markers (the line arrives from −x; the table edge is at the origin). */
object CrowsFoot {
    private const val BAR1 = -10.0
    private const val BAR2 = -16.0
    private const val FOOT = -14.0
    private const val HALF = 8.0
    private const val CIRCLE_R = 5.0

    fun key(c: Cardinality) = MarkerKey("crow.${c.name.lowercase()}")

    private fun bar(x: Double) = outline { line(mk(x, -HALF), mk(x, HALF)) }.paths

    private val foot = outline {
        path(PathFill.NONE) {
            move(mk(0.0, -HALF))
            line(mk(FOOT, 0.0))
            line(mk(0.0, HALF))
        }
    }.paths

    private fun circle(cx: Double) = outline { oval(mk(cx - CIRCLE_R, -CIRCLE_R), mk(cx + CIRCLE_R, CIRCLE_R), PathFill.FILL) }.paths

    val all: List<MarkerDefinition> = Cardinality.entries.map { c ->
        val paths = when (c) {
            Cardinality.ONE -> bar(BAR1)
            Cardinality.ONE_AND_ONLY_ONE -> bar(BAR1) + bar(BAR2)
            Cardinality.ZERO_OR_ONE -> bar(BAR1) + circle(BAR2 - CIRCLE_R * 2)
            Cardinality.MANY -> foot
            Cardinality.ONE_OR_MANY -> foot + bar(BAR2 - 2)
            Cardinality.ZERO_OR_MANY -> foot + circle(FOOT - CIRCLE_R * 2)
        }
        MarkerDefinition(key(c), "Crow's foot: ${c.displayName}", ShapeOutline(paths))
    }
}
