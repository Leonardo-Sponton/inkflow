# Device notes

Findings from running Inkflow on real hardware and emulators. Add to this file whenever a
behaviour can only be judged on the device.

## Galaxy Tab S9 FE+ (SM-X610, Android 16 / One UI 8.5)
- Physical size 1600×2560 (portrait natural orientation).
- Phase 0 build installed and launched fine. Phases 1–8 were developed on the emulator; Phase 9
  performance work was measured on the tablet (2026-10-16). **Pen feel, palm rejection and latency
  still need on-device testing**.
- Display modes 90/60/30 Hz. With *Settings → Display → Motion smoothness → Standard* the panel is
  capped at 60 Hz; *Adaptive* is needed for 90 Hz. The canvas votes
  `REQUESTED_FRAME_RATE_CATEGORY_HIGH` (API 35+) so Adaptive mode runs it at 90 Hz while it animates.
- Macrobenchmarks (benchmark build, `CompilationMode.Partial`, 5 iterations, 60 Hz mode):

  | Benchmark | Single layer cache | Tiled cache + size LOD |
  |---|---|---|
  | Cold start, median TTID | 388 ms | 458 ms (run-to-run spread ~±60 ms) |
  | Pan/zoom 5 000 elements, frame CPU P50/P90/P99 | 7.3 / 13.1 / 98 ms | 8.0 / 15.3 / 31 ms |
  | Open 5 000-element board, P90/P99 | 114 / 685 ms | 25 / 50 ms |
  | Ink strokes on that board, P50/P90 | 23 / 119 ms | 9.8 / 19 ms |

- Where the old cost went (`dumpsys gfxinfo framestats`, finger pans): every crossing of the
  ± 25 % margin re-recorded the whole board on the UI thread (25–45 ms) and re-rasterized a layer
  2.25× the screen on the GPU (50–90 ms; ~15 ms for an *empty* board — the grid alone). Tiles
  spread that over many frames: worst pan frame 260 ms → 16 ms at 14 % zoom, jank 10.9 % → 0.7 %.
- At fit zoom (14 %) the stress board's shapes are ~15 px: level of detail now depends on each
  element's on-screen size (< 24 px → outlined box; ink → 8-segment polyline), not on a zoom
  threshold.

## Emulator `inkflow_tablet` (Pixel Tablet, API 36, SwiftShader)
- Software GPU: an *empty* board already pans at ~48 ms/frame, so absolute frame times are not
  meaningful. Relative results on the 5 000-element board: 250 ms → 48 ms median per pan frame
  after the RenderNode layer cache (= empty-board baseline).
- `adb shell input stylus swipe …` drives stylus strokes; `uiautomator dump` exposes Compose
  semantics for automation.
- Gradle (6 GB heap) + R8 + the emulator exceed 15 GB RAM; use `-Dorg.gradle.jvmargs=-Xmx4g`.
