# 0003 — One android.graphics scene drawer for screen, PNG and PDF

Status: accepted (2026-10-15)

The scene layer is drawn through a single `SceneDrawer` that targets `android.graphics.Canvas`
(obtained via `drawIntoCanvas { it.nativeCanvas }` inside a Compose `Canvas`). The same code draws
into a `Bitmap` for PNG and into `PdfDocument` pages for PDF, so exports look exactly like the
screen. Jetpack Ink's `CanvasStrokeRenderer` also targets `android.graphics.Canvas`.

Text is laid out with `StaticLayout` behind a cached `TextMetrics` implementation (instead of Compose
`TextMeasurer`), because the same metrics must work outside composition (exports, auto-sizing).

Wet ink uses Ink's `InProgressStrokesView` (front-buffered low-latency rendering from graphics-core)
hosted in the editor's input view; finished strokes are converted to domain `InkStroke`s, committed
through a command, and removed from the wet layer after the scene has drawn them (no flicker).
