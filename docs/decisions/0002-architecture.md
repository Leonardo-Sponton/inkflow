# 0002 — Module graph and layering

Status: accepted (2026-10-15)

`presentation (feature:*) → domain:* ← data:*`, with `:app` wiring everything through Hilt.

```
core:model ← core:geometry ← domain:diagram ← domain:canvas ← domain:{uml, dbmodel, flowchart, project, export}
core:common ────────────────┘
domain:* ← data:{local, files} ← data:repository ← app
domain:canvas ← feature:canvas-render ← feature:{palette, export} ← feature:editor ← app
domain:project ← feature:{home, settings} ← app
```

- `core:*` and `domain:*` (except `core:designsystem`) are **pure Kotlin/JVM** modules: no Android
  imports, unit-tested with JUnit Jupiter, ≥ 80 % line coverage on `domain:*` + `core:geometry`
  (Kover rule wired into `check`).
- **Domain models carry `@Serializable`.** kotlinx.serialization is a pure Kotlin library; duplicating
  every element type as a DTO in `data:` would double the model for no isolation benefit. The wire
  format is still owned by `data:files` (schema version + migrations).
- **Notations are plug-ins.** `domain:diagram` defines `NotationModule` (shapes, markers, content
  layouters, serializers, validators). `domain:uml|dbmodel|flowchart` each provide one; `:app` collects
  them with a Hilt multibinding into the `ShapeRegistry`. The renderer only knows generic path specs
  and layout primitives, so adding BPMN/C4 later does not touch it.
