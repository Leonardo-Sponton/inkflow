# Changelog

## [Unreleased]

### Phase 8 — Export / import
- PNG (1–4×, transparent), PDF (tab, selection or one page per tab; ink as exact outlines), SVG.
- `.inkflow` archive (zip: versioned manifest, one JSON per tab, assets) with migration hook;
  import keeps ids (exact round trip) or re-identifies when the project already exists.
- SQL DDL (PostgreSQL/MySQL/SQLite), PlantUML and Mermaid text exports; SQL DDL import.
- Save through the Storage Access Framework or share through the Android share sheet.

### Phases 5–7 — Notations
- Flowchart: full ISO 5807 symbol set, horizontal/vertical swimlanes, Yes/No on decision branches.
- UML: class/use case/sequence/activity/state/component/deployment shapes; classes edited as data;
  14 relation kinds with correct markers/dashes; validation (structural misuse, duplicates, cycles).
- Database: Chen (entities, attribute kinds, relationships) and Crow's Foot tables with typed
  columns; auto foreign key offer; validation; DDL generation tested on a real SQLite engine.

### Phases 1–4 — Core
- Home gallery (search, sort, favorites, thumbnails), tab strip with drag reorder, colors, profiles,
  undoable delete; Room persistence with debounced autosave and persisted undo history.
- Infinite canvas: Double world coordinates, quadtree culling, adaptive grids, minimap, fit, view
  rotation (optional), level of detail.
- Jetpack Ink wet layer with motion prediction, palm rejection, side-button eraser, lasso, partial
  and stroke erasers, ink-to-shape.
- Diagram engine: data-driven shapes and markers, ports, attached connectors with obstacle-avoiding
  orthogonal routing, waypoints, labels, containers, snapping and smart guides, properties panel,
  searchable palette with drag and drop, clipboard across projects, keyboard shortcuts.

### Phase 0 — Setup
- Gradle convention plugins (`build-logic/`), version catalog, 23-module skeleton.
- detekt + ktlint (Spotless) + Kover coverage rules + Android Lint wired into `check`.
- `verifyNoNetworkPermission` fails `check` if any merged manifest asks for network access.
- ADRs 0001–0003, `scripts/ci.sh`, pre-commit hook.
