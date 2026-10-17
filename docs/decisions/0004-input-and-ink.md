# 0004 — Input model and ink

Status: accepted (2026-10-15)

- All gesture logic is a pure-Kotlin state machine (`domain:canvas/input/GestureStateMachine`),
  fed by `InkCanvasView`, which converts `MotionEvent`s (including historical samples) into
  `InputEvent`s. Time-based events (hold, long press) use explicit deadlines (`nextDeadline`,
  `onTimer`) so the machine stays deterministic and unit-testable.
- **Pen draws, finger navigates.** Fingers act like the pen only when finger drawing is enabled or
  when they start on a selectable element with the select/lasso tool (moving things by hand).
- **Palm rejection**: fingers are ignored while the stylus is down and for 400 ms after it was last
  seen (hovering counts); `ACTION_CANCEL` and `FLAG_CANCELED` (API 33+) cancel the stroke.
- **Side button** (`BUTTON_STYLUS_PRIMARY`) = eraser or lasso (setting); the eraser tip
  (`TOOL_TYPE_ERASER`) always erases. A mouse (DeX) acts like the pen without palm guarding.
- **Wet ink** is Jetpack Ink's `InProgressStrokesView` (front-buffered via graphics-core) with
  `MotionEventPredictor`; strokes are authored directly in world coordinates through the
  `motionEventToWorldTransform`, then converted to domain `InkStroke`s (origin + float offsets).
  The wet stroke is removed only once a frame containing the committed element was drawn, so there
  is no flicker at hand-off.
- **Brushes**: pen = `StockBrushes.pressurePen()`, highlighter = `highlighter()`, pencil-like =
  `marker()`. The textured `pencilUnstable` brush is an unstable Ink API and needs a texture store;
  it can replace `marker()` later.
- **Ink-to-shape**: holding the pen still ~500 ms after moving triggers `ShapeRecognizer` (interface;
  the shipped `GeometricShapeRecognizer` uses RDP corners, radial deviation and straightness).
- **Handwriting-to-text** (OCR) is out of scope for v1: no fully offline model is bundled. Text
  fields accept S Pen handwriting through the system keyboard (Samsung Keyboard handwriting).
- **Ink storage** uses the app's own compact encoding (Base64 of little-endian floats, 6 channels per
  sample) instead of `ink-storage`, so the domain model stays pure Kotlin.
