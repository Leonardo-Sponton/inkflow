# 0005 — Persistence, autosave and the `.inkflow` format

Status: accepted (2026-10-15)

- Room tables: `projects`, `tabs` (camera, layers JSON, soft-delete flag for undoable tab
  deletion), `elements` (denormalized bounds + JSON payload), `assets`, `undo_log`.
- Autosave: every executed command yields an invertible `SceneChange`; changes are merged per tab
  and written after 400 ms of quiet in one transaction, together with the last 50 undo entries.
  Camera positions are saved debounced; everything is flushed when leaving the editor.
- Only the active tab's elements are loaded (three most recent tab sessions are kept in memory).
- Images live in app-private `files/assets/<project>/<asset>`; thumbnails in `files/thumbnails`.
- `.inkflow` = zip with `manifest.json` (`format`, `schemaVersion`, project, tabs, assets),
  `tabs/<tabId>.json` (elements, same JSON as the database) and `assets/<id>`. `ArchiveMigrations`
  upgrades older manifests step by step; unknown JSON fields are ignored, unknown node content
  falls back to a plain label, unknown shapes render as rectangles.
- Import keeps every id when the project is not on the device (exact round trip, verified by
  `ArchiveRoundTripTest`); otherwise ids are regenerated and references remapped.
