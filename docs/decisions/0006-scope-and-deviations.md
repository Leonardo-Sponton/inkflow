# 0006 — Scope decisions and deviations from the spec

Status: accepted (2026-10-15)

| Spec item | Decision |
|---|---|
| pt-BR strings | Dropped at the user's request: UI is English only. |
| `Group` element with kinds | Logical groups are a `groupId` on elements; frames, swimlanes, packages and boundaries are container *shapes* whose children reference them (`ShapeNode.parent`). Same behaviour, simpler model. |
| Element `transform` | Elements store explicit geometry (frame / origin + points); scale/rotate is baked in by `TransformElements`. Diagram nodes are not rotated (keeps ports and orthogonal routing exact). The *view* can rotate (two-finger rotation setting). |
| Layers | Model, persistence, hidden/locked handling are in place; there is no layer management UI yet (one default layer). |
| Rendering through Compose `DrawScope` | One `android.graphics` drawer (ADR 0003) for screen, PNG, PDF; text via cached `StaticLayout`. |
| Tile cache | Implemented as 512 px `RenderNode` tiles with compositing layers in a camera-rotated, pixel-snapped tile space (`SceneCache`). Pans record only tiles entering the view; scene edits re-record only tiles under changed elements; elements appended on top (ink) go to an overlay folded in every 64. Pinches scale the tiles. |
| Partial key (Chen) | Drawn with a solid underline (no dashed underline primitive). |
| Total participation (Chen) | Thicker line (4 px) instead of a double line. |
| Screenshot tests (Roborazzi/Paparazzi) | Not added (Robolectric weight); visual checks done on the tablet emulator and covered by the on-device checklist. |
| JUnit 5 | Used for all pure-Kotlin modules; Android modules use JUnit 4. |
| Stretch: DDL import, PlantUML, Mermaid | Implemented. |

## Backlog (out of scope for v1)
Cloud sync, real-time collaboration, BPMN/C4/network diagrams, AI features, handwriting OCR with
downloaded models, code generation from UML beyond PlantUML text, layer manager UI, connector
waypoint insertion by dragging segments, textured pencil brush, release keystore.
