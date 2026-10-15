# 0001 — Tech stack and versions

Status: accepted (2026-10-15)

Versions were read from Google Maven / Maven Central on 2026-10-15 (latest **stable** only) and are
pinned in `gradle/libs.versions.toml`.

| Concern | Choice | Version | Reference |
|---|---|---|---|
| Build | Gradle / AGP (built-in Kotlin) | 9.8.1 / 9.4.1 | https://developer.android.com/build/releases/gradle-plugin |
| Language | Kotlin (K2) | 2.4.21 | https://kotlinlang.org/docs/releases.html |
| Annotation processing | KSP | 2.3.12 | https://github.com/google/ksp |
| UI | Compose BOM | 2026.09.00 | https://developer.android.com/develop/ui/compose/bom |
| Adaptive layout | Material 3 Adaptive | 1.3.0 | https://developer.android.com/develop/ui/compose/layouts/adaptive |
| Navigation | Navigation 3 | 1.2.0 | https://developer.android.com/guide/navigation/navigation-3 |
| Stylus ink | Jetpack Ink (`androidx.ink:*`) | 1.0.0 | https://developer.android.com/develop/ui/compose/touch-input/stylus-input/about-ink-api |
| Low latency | graphics-core (front-buffered) | 1.0.4 | https://developer.android.com/jetpack/androidx/releases/graphics |
| Prediction | input-motionprediction | 1.0.0 | https://developer.android.com/jetpack/androidx/releases/input |
| DI | Hilt | 2.60.1 | https://dagger.dev/hilt/ |
| DB | Room (KSP) | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |
| Settings | DataStore Preferences | 1.2.1 | |
| Serialization | kotlinx.serialization | 1.11.0 | |
| Perf | Macrobenchmark / Baseline Profile | 1.5.0 | https://developer.android.com/topic/performance/baselineprofiles/overview |
| Quality | detekt / Spotless (ktlint) / Kover | 1.23.8 / 8.10.4 (1.8.0) / 0.9.11 | |
| Tests | JUnit Jupiter (pure-Kotlin modules), JUnit4 (Android), Turbine | 6.1.3 / 4.13.2 / 1.2.1 | |

SDK levels: `minSdk 29`, `targetSdk 36`, **`compileSdk 37`** — Compose 1.12 / core 1.19 require compiling
against API 37. Compiling against a newer SDK does not change runtime behaviour (that is `targetSdk`).

## Notes
- **S Pen**: Samsung's current guidance for Galaxy Tab is to use the standard Android stylus APIs
  (`MotionEvent` tool types, pressure/tilt/orientation axes, hover events, `BUTTON_STYLUS_PRIMARY`),
  not the legacy Samsung Pen SDK. Applied: tool-type routing, `ACTION_CANCEL` + `FLAG_CANCELED` palm
  rejection, hover preview, side-button eraser, historical points, motion prediction.
  https://developer.samsung.com/galaxy-gamedev/s-pen.html ·
  https://developer.android.com/develop/ui/compose/touch-input/stylus-input
- **Large screens**: Android 16 ignores orientation/resizability restrictions on ≥ 600 dp displays;
  the UI never assumes a fixed orientation. https://developer.android.com/about/versions/16/behavior-changes-16
- **detekt 1.23.8** embeds Kotlin 2.0; its own classpath is pinned to 2.0.21 (it only parses sources,
  no type resolution), see `QualityConventionPlugin`.
- **Navigation 3** is stable (1.2.0) and is the recommended library for new Compose apps; the back
  stack is plain state we own, which fits UDF. Navigation Compose is not used.
- No library requires network at runtime. Non-Jetpack libraries: Hilt (Google), kotlinx (JetBrains),
  Turbine (tests only), detekt/Spotless/Kover (build only).
- **Not used**: Coil (local images decode with `ImageDecoder`), Robolectric/Roborazzi/Paparazzi
  screenshot tests (heavy and slow; instrumented Compose tests on the device instead), MockK (fakes).
