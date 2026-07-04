# NPIC Photo & Signature Scanner

Android app for digitizing admission-form passport photos and signatures for bulk upload to the UPMSP (Uttar Pradesh Madhyamik Shiksha Parishad) portal.

Native Kotlin + Jetpack Compose + CameraX + OpenCV. Min SDK 24, Target SDK 35, Compile SDK 36.

## Documentation

- [`PRD.md`](./PRD.md) — Product requirements (portal-verified against `prereg.upmsp.edu.in`)
- [`DESIGN.md`](./DESIGN.md) — Design system, component specs, motion, and Adobe-Scan-continuity model

## Project structure

```
app/                                       Single-module Android app
  src/main/
    java/com/npic/photoandsignscanner/
      app/                                 Application, MainActivity, navigation
      core/
        theme/                             NpicColors, NpicTypography, NpicShapes, NpicMotion, NpicElevation
        ui/                                18 reusable Composables (buttons, cards, sheets, sliders, overlays)
        util/                              Shared utilities
      data/
        db/                                Room entities, DAOs, database
        storage/                           Internal storage IO
        repo/                              Repositories bridging DB + storage
      domain/
        model/                             Sealed types (NamingMode, ClassNum, FilterPreset, ExportFormat, ...)
        usecase/                           Use cases orchestrating data + business rules
      features/
        camera/                            Unified Photo + Signature camera
        edit/                              Adobe-Scan-continuity edit screen (dark chrome)
        signaturedraw/                     Draw canvas
        save/                              Save dialog + duplicate detection
        gallery/                           Home screen (grid + FAB + filter chips)
        detail/                            Per-student detail view
        export/                            Combined / Photo-only / Signature-only export
    res/                                   Resources (fonts, drawables, strings, colors)
```

## Build

Requires JDK 17, Android SDK 36. On first run, Gradle will download AGP + dependencies.

```bash
./gradlew assembleDebug              # Debug APK
./gradlew installDebug               # Install to a connected device
./gradlew lint                       # Android lint
./gradlew testDebugUnitTest          # Unit tests
```

## License

Private. All rights reserved.
