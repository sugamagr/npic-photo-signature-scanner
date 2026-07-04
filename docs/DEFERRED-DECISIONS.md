# Deferred Decisions Log

This doc has **three sections** per the m1319 overnight brief + m1537 review:

- **Section A** — decisions I took autonomously or you approved during review;
  logged so you can course-correct if anything drifted.
- **Section B** — items still needing your explicit decision, with my
  recommendation and rationale for each.
- **Section C** — v2 backlog. Items explicitly deferred out of v1.0 scope by
  your direction; captured so we don't lose them.

Anchored against `PRD.md` and `DESIGN.md` at the repo root.

---

## Section A — Decisions locked

Every entry here is settled. If you want to reverse one, delete the entry or
annotate it with the new direction.

### A1. `SharedCaptureHolder.draftIdOrMint()` returns the *live* draft id, never rewrites

**Deferred as:** "Draft persistence — how does the id survive Camera → Edit → Save?"

**Choice I took:** the holder mints one UUID at first push and keeps it for the
entire arc. `SourceStore.writePhoto/writeSignature` keys assets by this id so
the record row that lands in Room later points at the same on-disk files.

**Impact:** commit-then-cancel-then-recapture reuses the same source files
(they get overwritten in place) instead of littering `filesDir/sources/`.

**Reverse if:** you want capture sessions to accumulate as separate on-disk
folders for auditability. Would require minting a fresh id on `pushCapture()`.

### A2. `RoomStudentRepository.save()` uses `draft.id` as the record primary key

**Deferred as:** "Should the saved `StudentRecord.id` come from the draft or be
minted at save?"

**Choice I took:** `record.id = draft.id`. Cross-layer contract: Layer 9
(SourceStore) already wrote `sources/{draftId}_*.jpg` at Edit's Next; if the
repo minted a fresh id at save, the record would no longer match its assets.

**Impact:** invariant is now "the id is stable from first draft touch through
persisted record." Deletes cascade cleanly via `SourceStore.deleteFor(id)`.

**Reverse if:** you want DB ids opaque from client-generated draft ids. Would
require a rename step in `save()` that moves `sources/{draftId}_*.jpg` →
`sources/{newId}_*.jpg` atomically before the insert.

### A3. `SQLiteConstraintException` in `save()` re-checks and routes to `DuplicateFound`

**Deferred as:** "TOCTOU race between findByClassSerial and insert — what
should the user see?"

**Choice I took:** on the constraint violation, re-run `findByClassSerial` /
`findByClassName` and hand the existing record back through `DuplicateFound`
so the SaveSheet duplicate-resolution flow takes over. If the re-check finds
nothing (constraint violation on some other index) the throwable propagates.

**Impact:** rapid double-tap on Save never crashes. The second attempt always
routes through the same duplicate dialog the user would have seen if the
first attempt had reached the check gate first.

**Reverse if:** you want the race to surface as a distinct error message
("another save is in progress") instead of pretending it was a duplicate.

### A4. Signature thickness is **retroactive** (canvas-global weight)

**Locked by:** user directive m1537 B5.

**Choice:** the thickness slider is a canvas-wide weight setting, not a
per-stroke lock. Every committed stroke re-renders at the current thickness
on each slider change; new strokes inherit the same width; the rasterizer
reads the slider value at `Done`.

**Where it lives:** `SignatureCanvas` reads `liveWidthPx` from
`state.thicknessPx` for both committed and in-flight strokes.
`SignatureRasterizer` uses the same value when flattening at `Done`.
`DrawStroke.widthPx` is retained on each stroke for backward compatibility
but is ignored by both the live canvas render and the rasterizer.

**Impact:** simpler mental model ("one signature, one weight") matches user
expectation. PRD §4.4 was updated to describe this behavior explicitly.

### A5. Bug#3 Edit tabs live-preview render is preview-only, not source-of-truth

**Deferred as:** "How do Filter/Adjust/Rotate tabs show a rendered preview
without re-running the full pipeline on the 2-4MB source bitmap?"

**Choice I took:** `EditViewModel.scheduleLivePreview()` runs `EditRenderer`
on a **1920px preview downsample** (bumped from an earlier 384px after user
feedback on preview quality), not the full source. Cancels prior job on
each mutation. Publishes to `livePreviewBitmap`. The full-res render only
happens at `commitEdits()` time. The crop quad is scaled from source
coordinates into preview coordinates via `CropQuad.scaledBy(factor)` before
the render so `warpPerspective` samples in the right space — this fix
resolved the m1780 gray-blob bug.

**Impact:** tab feedback is instant with high-fidelity preview. There's a
~50ms window on very slow devices where the last live preview may lag the
state; the committed render is always correct.

**Reverse if:** you want full-res live preview. Would require rendering at
source resolution with a longer debounce and a spinner during in-flight
renders.

### A6. Bug#3 Crop tab bypasses live preview and shows the raw source

**Deferred as:** "Should crop-tab show a live-cropped preview or the raw
source with an overlay?"

**Choice I took:** raw source. Crop math happens at commit only; the overlay
draws the crop quad on top of the untouched source. Non-crop tabs use the
live preview.

**Impact:** crop feels immediate and the handles are pixel-accurate. If the
user rotates 90° in the Rotate tab then jumps to Crop, they see the rotated
live preview vanish and the un-rotated source snap back momentarily.

**Reverse if:** you want unified live preview across all tabs. Would require
extending `scheduleLivePreview` to include the crop projection.

### A7. Bug#9 back navigation drops `popUpTo(inclusive=true)` on every forward hop

**Deferred as:** "Back from Save — go to SignaturePrompt, or all the way to
Gallery?"

**Choice I took:** back always goes exactly one step. Camera → Edit → prompt
→ Save each leave their previous screen on the stack. Only after successful
Save do we `popBackStack(Route.Gallery, inclusive=false)` and re-navigate
Camera per PRD §4.8.

**Impact:** predictable Android back-gesture semantics matching user report.
Retake from any point in the funnel is one back-gesture away.

**Reverse if:** you want the funnel to be linear-forward-only (no back to
Edit once you've moved to Save). Would restore `popUpTo(inclusive=true)` on
the Edit → Save hop.

### A8. Bug#12 `ClassFilterTile` two-line dominant-count design

**Choice:** two-line tile with count in `titleLarge` bold (dominant) and
class label in `labelSmall` (quiet). Selection surface = SaffronSoft + 2dp
Saffron border + SaffronDeep title color.

**Impact:** scannable at a glance ("Class 12 has 34 students, Class 9 has
0") — count is what teachers look at first, class label is context.

**Reverse if:** you want the class label dominant. Swap the typography —
count small, label large.

### A9. Bug#14 sort icon changed to `Icons.AutoMirrored.Outlined.Sort`

**Choice:** `AutoMirrored.Outlined.Sort` (horizontal lines with descending
length) instead of `SwapVert`. Auto-mirrored variant respects RTL locales.

**Impact:** matches Material-3 Sort convention.

### A10. Bug#15 Search filter runs client-side over `observeAll()`

**Choice:** client-side. `SearchViewModel` combines a `debounce(120ms)`
query flow with `repository.observeAll()` and filters via `filterRecords()`
on `Default`. Ranking priority: name-prefix → name-substr → class-exact →
class-substr → serial-exact → serial-prefix.

**Impact:** simpler ownership (single Flow join), works with the
sub-thousand-record bound expected for a single teacher's roster. No
per-keystroke DB query.

**Reverse if:** the roster grows into the tens of thousands. Would require
`StudentDao.searchByPrefix(query: String): Flow<List<StudentEntity>>` with
SQLite FTS4 indexed on `displayName` + `serial`.

### A11. Detail edit-existing route via `clear()` + `pushCapture()` funnels through DuplicateSheet.replace-existing

**Choice:** replace in place via the duplicate-dialog branch. The existing
record's assets are overwritten, and the duplicate dialog surfaces so the
user can compare old vs new before confirming.

**Impact:** consistent with the capture flow (any save that hits an existing
`(class, serial)` shows the dialog). No accidental silent overwrites.

### A12. Detail import launcher uses `PickVisualMedia(ImageOnly)`, not full SAF

**Choice:** `ActivityResultContracts.PickVisualMedia` with `ImageOnly` type.
Copies Uri to `cache/drafts/{uuid}.jpg` on Dispatchers.IO via
`copyUriToDraftsCache`.

**Impact:** modern Android photo picker (Android 13+ native picker, older
SDKs fall back to SAF). No permission needed. Image-only filter matches
intent.

### A13. Gallery ZeroState is a hand-drawn Canvas emblem (no Lottie)

**Choice:** hand-drawn Canvas emblem — three rounded cards fanned ±8° with
a Saffron accent dot signaling 'signature captured'. Zero external assets.
Layered typography + two hint tiles + primary CTA.

**Impact:** brand-consistent (Saffron accent, SaffronSoft tints, Fraunces
headings), zero binary weight, easy to iterate.

### A14. Layer 11 preview→image-space uses FILL_CENTER inverse against decoded JPEG bounds

**Choice:** manual FILL_CENTER inverse. Rationale: `OutputTransform` API is
fragile across CameraX versions and coupled to the PreviewView lifecycle.
Manual math is 12 lines, deterministic, easy to unit-test.

**Impact:** guide-box → image-space projection is now accurate. Detection
seed is finally usable instead of the pre-Layer-11 identity stub.

### A15. `DeviceTilt` α=0.15 low-pass filter constant

**Choice:** α=0.15 (15% raw + 85% previous). Balances jitter suppression
(device sitting in hand at 60Hz has ±1° noise) against lag when the user
actively levels the phone.

**Impact:** Sage snap-to-level bracket lights up smoothly when the user is
within ±2° of horizontal. Doesn't twitch when hand-held.

### A16. Localization scaffolding NOT extracted; English strings stay inline for v1.0

**Locked by:** user directive m1537 B2 — "english only fine for now will
thign in furture if need hindi".

**Choice:** ~150 hardcoded English strings stay inline. No `strings.xml`,
no `hi-IN` locale, no Devanagari font. If Hindi lands later, we'll extract
in one sweep at that time.

**Impact:** faster path to v1.0 ship; ~1 day of extraction work deferred
until it's actually needed.

**Reverse if:** you decide to support Hindi within v1.0's lifetime. Cost is
1-3 days of extraction + `Noto Sans Devanagari` font wiring.

### A17. Detail Share uses the compressed portal blob

**Locked by:** user directive m1537 B7 — "i need compress share only
everywhere".

**Choice:** the Share icon in Detail's top bar routes to the same Export
pipeline as bulk share — renders the JPEG through the 10–30 KB compressor,
applies the PRD §6.3 filename, and hands the resulting Uri to the system
share sheet. Nothing shares the raw ~2MB source file.

**Impact:** everything shared out of the app is portal-ready. No confusion
about which file the teacher just DM'd to a parent.

### A18. `WRITE_EXTERNAL_STORAGE` declared with `maxSdkVersion="28"`, no runtime prompt

**Locked by:** user directive m1537 B8 — "ok do nothing".

**Choice:** manifest declares the permission with `maxSdkVersion="28"`.
Android 6-9 auto-grants at install; Android 10+ doesn't need it (scoped
storage). No runtime re-request UI.

**Impact:** no scary prompt on first launch. Old install-then-revoke edge
case remains unhandled (extremely rare).

### A19. Draft resume prompt is single-active (newest only)

**Locked by:** user directive m1537 B9 — "ok".

**Choice:** `DraftDao.observeActive()` returns `ORDER BY updatedAt DESC
LIMIT 1`. If the user has two half-finished captures, only the newest is
offered on resume.

**Impact:** simple UI, matches PRD §8.3 wording. Multi-active raises hard
questions about draft eviction and UI space we don't need to answer for
v1.0.

### A20. ZIP filename is human-readable with record count

**Locked by:** user directive m1537 B6a.

**Choice:** `export_{yyyy-MM-dd}_{N}-records.zip` (e.g.
`export_2025-01-30_34-records.zip`). Local date (not UTC) so the filename
matches what the teacher perceives as "today". Singular "1-record" vs
plural "N-records" suffix.

**Where it lives:** `ZipExporter.buildZipFilename(recordCount: Int)`.

### A21. Photo/signature filenames drop the format prefix; extension is `.jpeg`

**Locked by:** user directive m1537 B6b — "no prefix. Serial→090001,
Name→Student_Name_09. bulk AND individual."

**Choice:** every exported blob per record — whether it's a Combined JPEG,
a Photo-only JPEG, or a Signature-only JPEG — uses the same filename:

- Serial mode: `{class:02d}{serial:04d}.jpeg` (e.g. `090001.jpeg`)
- Name mode:   `{Name_Underscored}_{class:02d}.jpeg` (e.g. `Rahul_Kumar_09.jpeg`)

No `photo_` or `signature_` prefix. Extension is `.jpeg` (not `.jpg`) for
portal parity.

**Where it lives:** `GenerateFileName.forExport(record, format,
namingMode)`. The `format` parameter is accepted but ignored — the format
determines the *contents* of the blob, not the *name*.

**Impact:** ZIP contents are `090001.jpeg`, `090002.jpeg`, etc. Teachers
can drop the ZIP straight into the portal's bulk-upload page without
renaming.

### A22. Save-screen Serial input is EXACTLY 4 digits

**Locked by:** user directive m1537 B6c — "when takeing serial number as
input in save screen there should be exactly four digits in input no less
no more".

**Choice:**

- `SaveViewModel.setSerialText` filters to digits only and truncates to
  4 characters (upper bound).
- `SaveUiState.serialNumber` returns non-null only when
  `serialText.length == SERIAL_TEXT_LENGTH` (i.e. exactly 4 digits, lower
  bound).
- New `SaveUiState.serialError` surfaces validation:
  - empty → `null` (no scolding on blank field)
  - length < 4 → "Serial needs 4 digits (e.g. 0001)."
  - `0000` → "Serial can't be 0000."
  - number out of `1..9999` → "Serial must be between 0001 and 9999."
- `SaveSheet` passes `errorText = state.serialError` to `NpicTextField`,
  which paints Terracotta border + label sub-text.
- Auto-serial seeding (on class pick or Serial-mode select) zero-pads via
  `formatSerial(n) = n.toString().padStart(4, '0')` so freshly-picked
  classes show `0001` not `1`.

**Impact:** Save button stays disabled until the user types 4 digits. No
class silently rolls the counter to a short-form serial.

### A23. Save-to-Gallery ships alongside Share (three-button action row)

**Locked by:** user directive m1704/m1706 — "ok fine implement it" with
defaults locked to my recommendations.

**Choice:** the Export sheet now shows three actions: a primary
`Save & Share $N` CTA at the top plus a `Save to Gallery` / `Share` split
row below. Save-to-Gallery writes individual JPEGs (never a ZIP) to
`Pictures/NPIC/` via `MediaStore` with the `IS_PENDING = 1 → 0` two-phase
protocol on SDK 29+ (single-phase fallback on ≤28). Filenames match A21
so MediaStore's auto-suffix on collision doesn't diverge from Share
filenames.

**Where it lives:** `MediaStoreExporter.saveJpeg(displayName, bytes)` +
`ExportViewModel.beginSaveToGallery` / `beginSaveAndShare` /
`beginExport`. The three methods share `state.exporting` guard and use a
common `renderPayloads` helper so the render pipeline runs exactly once
per action.

**Impact:** teachers can save to Gallery for future reference AND share to
the portal, or do either alone. Graceful degradation on `beginSaveAndShare`:
Gallery fails but share succeeds → share-only; only Gallery succeeds →
Saved; both fail → Failed toast.

### A24. Camera chrome uses vertical scrim gradients, not backdrop blur

**Locked by:** user directive m1597 (industry-standard fixes only) after
m1599 device report showed the shutter/mode pills invisible against dark
preview.

**Choice:** replaced `Modifier.blur(24.dp)` on Camera top/bottom bars with
`Brush.verticalGradient` scrims — top scrim fades `cameraBg@0.95 → 0`,
bottom scrim mirrors it. Matches Google Camera / VSCO / Adobe Lightroom
Mobile chrome pattern.

**Where it lives:** `topBarScrim(bg)` / `bottomBarScrim(bg)` helpers in
`features/camera/CameraScreen.kt`. Scrims are applied before window insets
so gradients fade cleanly into the status/nav bar zones.

**Impact:** shutter ring (`CameraInk` white 4dp Stroke) is fully visible
against dark preview. `Modifier.blur` was blurring the composable AND its
descendants (RenderNode.setRenderEffect layer behaviour); Compose has no
first-class backdrop-blur primitive yet.

### A25. `EditRenderer.render()` guarantees non-alias return

**Locked by:** user directive m1597 (industry-standard fixes only) after
diagnosing the blank-viewport bug.

**Choice:** the renderer's docstring promises "source is never mutated;
the final rendered Bitmap is a fresh allocation." Two aliasing paths were
found where the promise didn't hold — no-rotation-no-straighten
`applyRectCrop` on a full-cover quad returned source; the OpenCV path's
in-place `filters.apply` / `adjustments.apply` then mutated source.
`EditRenderer` now runs an identity check at return and copies via
`Bitmap.copy(config, isMutable=true)` if `cropped === source`.

**Where it lives:** `data/imaging/EditRenderer.kt` — the `owned` block at
the return. Comment enumerates both aliasing paths and the failure chain.

**Impact:** caller can always safely `recycle()` the returned bitmap
without corrupting the source. This unblocked the live-preview cycle
that was recycling `previewBitmap` two frames later.

### A26. `EditRenderer` handles the normalized-quad sentinel

**Locked by:** user directive m1597 after finding `initialCropFor()` had
been emitting a degenerate all-zero quad when `capture.guideBoxImageSpace
== null`, which OpenCV translated to a 1×1 output bitmap → gray blob.

**Choice:** `EditState.initialCropFor` now returns a unit-square quad
(0..1) as a normalized sentinel when guideBoxImageSpace is null.
`EditRenderer.render` detects the sentinel via
`isNormalizedSentinel(quad)` (max ordinate < 1.5) and remaps to full
source bounds before `applyCrop`.

**Where it lives:** `domain/model/EditState.kt` (`NORMALIZED = 1f`,
`NORMALIZED_SENTINEL_THRESHOLD = 1.5f`) + `data/imaging/EditRenderer.kt`
sentinel-detection block.

**Impact:** first-render from Camera never produces a 1×1 crop. Fresh
captures show the full photo in the Edit viewport.

### A27. EXIF orientation applied at decode time

**Locked by:** user report m1720 (photos loaded rotated in Edit).

**Choice:** `EditViewModel.decodeSource(path)` reads
`ExifInterface(path).getAttributeInt(TAG_ORIENTATION, ...)` and applies
the appropriate `Matrix` transform (ROTATE_90/180/270 + FLIP_H/V +
TRANSPOSE + TRANSVERSE + NORMAL/UNDEFINED passthrough) via
`Bitmap.createBitmap(source, 0, 0, w, h, matrix, true)`. The rotated
bitmap replaces the source; the original is recycled if a fresh
allocation resulted.

**Where it lives:** `applyExifOrientation(source, path): Bitmap` helper
in `features/edit/EditViewModel.kt`.

**Impact:** rotated captures from CameraX now display upright on the
Edit screen. Coil (used elsewhere for thumbnails) reads EXIF natively,
so this brings BitmapFactory-decoded paths into parity.

---

## Section B — Still needing your decision

Nothing outstanding. Every earlier B-item has been either promoted to
Section A (with your call locked in) or moved to Section C (v2 backlog).

If a new deferral surfaces mid-development, add it here with the same
shape: what's deferred, options, recommendation, rationale.

---

## Section C — v2 backlog

Items explicitly out of v1.0 scope by your direction. Captured so the
work isn't forgotten when v1.0 ships and we plan v2.

### C1. Auto-capture on stable edge detection

**Deferred by:** user directive m1537 B3 — "wil implement but in v2".

**Scope:** watch the OpenCV edge-detection quad for stability, and after
a 500 ms hold at high-confidence convergence, auto-fire the shutter with
a haptic tap. Manual shutter remains available always.

**Estimated cost:** ~1 day. Requires a convergence heuristic (rolling
buffer of quads, IoU stability threshold), a debounce timer, and a
haptic feedback plumb. UI signals could reuse the existing tilt-level
Sage snap indicator.

**Blockers to resolve at v2 planning time:** what heuristic thresholds
work in real classroom lighting (needs on-device tuning), whether we
want an in-app toggle to disable it (probably yes).

### C2. Room DB Migration(1,2) — conditional

**Deferred by:** user directive m1537 B4 — "if easier and will not take
muh time do not otherwise in v2".

**Scope:** the current `NpicDatabase` uses `fallbackToDestructiveMigration()`
which wipes user data on any schema change. Before the school starts
using the app in earnest, we should either:

- freeze the schema at v1.0 and write real `Migration(1,2)` when the
  next change lands, or
- accept destructive migrations during pre-1.0 (fine now, no
  distribution yet).

**Estimated cost:** ~30-60 min to write a stub Migration(1,2) that's a
no-op today, so any future schema change can slot in. Otherwise, v2.

**Recommendation:** probe the writability at v1.0 ship time. If <30 min
of work, land it in v1.0 as insurance. Otherwise, v2 has room.

### C3. Devanagari support (font + locale + normalization)

**Deferred by:** user directive m1537 B2 + B10 — "no hindi names will be
entered. we alwwayws export to opload to portal for now".

**Scope in v2 if Hindi is added:**

- Extract ~150 English strings to `strings.xml`.
- Add `hi-IN` locale variant.
- Bundle `Noto Sans Devanagari` and wire the Compose font fallback so
  Hindi names don't render as tofu boxes.
- Normalize `displayName` to NFKC before writing to the
  `StudentEntity.nameKey` column so duplicate detection collates ZWJ
  and Nukta variants.

**Estimated cost:** 3-5 days of focused work.

**Trigger:** any request to enter a name in Hindi (script other than
Latin/Arabic/etc.).

### C4. Signature Draw process-death SavedStateHandle wiring

**Deferred as:** "if the OS kills the process mid-draw, we lose the
stroke buffer."

**Scope:** wire `SignatureDrawViewModel` to `SavedStateHandle` so committed
strokes survive process death. Currently only in-memory.

**Estimated cost:** ~2 hours. `List<DrawStroke>` serializes cleanly to a
Bundle-safe form.

**Priority:** low. Signature draw sessions are short (<30s typically);
Android rarely kills a foreground process mid-draw.

### C5. Crop overlay coordinate-space transform (image-space ↔ view-space)

**Deferred as:** "quad in `EditState.crop` is logically image-space (from
`PhotoEdgeDetector` output or `guideBoxImageSpace`) but `NpicCropOverlay`
draws + hit-tests in Canvas-local px."

**Why it works empirically today:** `moveCorner` / `translated` clamp to
`size.width/height` (viewport px), so after any user drag the quad collapses
into view-space. Initial placement is technically wrong but the golden path
(user always drags at least once before Save) hides it. Detection quads
happen to land inside viewport when the image aspect matches the guide.

**Real failure mode:** a fresh capture where `guideBoxImageSpace` is
non-null (Camera set it) and `initialCropFor` places corners at image-space
coords like (1600, 3200) that never intersect the 360dp viewport — user
sees no handles on entry. Doesn't repro on the A35 test device because our
capture flow always seeds a viewport-fittable quad, but is a landmine for
different aspect ratios or `PickVisualMedia` imports.

**Correct fix (~3-4h):** apply a bidirectional transform in EditScreen
ImageViewport: store quad in image-space (source of truth for Save's
`EditRenderer.render`), transform via `sourceW/viewW` + `sourceH/viewH`
scale factors for display, transform back on `onQuadChange`. Requires
threading source dims + overlay layout size through `NpicCropOverlay`.

**Priority:** high for v1.1 (hidden data-integrity bug on non-standard
imports). Deferred out of v1.0 because current golden path works and the
architectural plumbing is non-trivial.

**Anchored source finding:** qc-round-10 Oracle #3 BLOCKER-I2.

### C6. `BuildConfig.DEBUG` gate for production `Log.*` calls

**Deferred as:** "we log file paths + bitmap dimensions in a few dozen
places; harmless in dev but a supply-chain smell in a release APK."

**Scope:** wrap every `Log.d/i/w/e` in `if (BuildConfig.DEBUG) { ... }`
or introduce a thin `Logger` façade that no-ops in release. ~30 sites
across `EditViewModel`, `CameraViewModel`, `OpenCvBridge`,
`PhotoEdgeDetector`, `SignatureInkIsolator`, `NpicDatabase`,
`RoomDraftRepository`, `MediaStoreExporter`, `DuplicateAssetsUseCase`.

**Estimated cost:** ~1 hour, mostly search-and-replace.

**Priority:** low. Zero user-visible impact. Deferred because v1.0 is not
shipping to Play Store — sideloaded APKs are debug-signed anyway.

**Anchored source finding:** qc-round-10 Oracle #4 LOW-S1.

### C7. `SettingsViewModel` inlines `NpicDatabase` — extract `ClearAllDataUseCase`

**Deferred as:** "the Settings VM imports Room's DAO layer directly instead
of going through a domain-layer use case."

**Scope:** create `domain/usecase/ClearAllDataUseCase(studentRepo,
draftRepo, sourceStore, cacheDir)` and inject that into `SettingsViewModel`
instead of `NpicDatabase`. Removes the `data.db` import from the features
layer.

**Estimated cost:** ~45 min.

**Priority:** low. Pure architectural cleanup — user-facing behavior stays
identical. Deferred because the layer bleed is contained to one file and
we've already reworked SettingsViewModel through `DraftRepository` (fix
MINOR-D5 landed in qc-round-10).

**Anchored source finding:** qc-round-10 Oracle #5 MINOR-A6.

### C8. `LocalAppSettings` — `staticCompositionLocalOf` vs `compositionLocalOf`

**Deferred as:** "AppSettings can change at runtime (user toggles a switch)
but `NpicTheme` provides it via `staticCompositionLocalOf`."

**Why it works empirically:** `staticCompositionLocalOf` DOES propagate
updates — it just skips subtree invalidation as an optimization when the
value is stable. Since we re-provide with a fresh `AppSettings` instance
on every `settingsFlow` emission, the whole subtree recomposes anyway,
so the "static" optimization is moot.

**Correct fix:** switch to `compositionLocalOf` for semantic honesty.
Sub-tree invalidation would then be scoped instead of theme-wide.

**Priority:** very low. Behavioral difference is invisible for our
current consumers (`Haptics`, `handleExportResult`). Deferred as
pure semantics.

**Anchored source finding:** qc-round-10 Oracle #5 MINOR-A11.

### C9. Unique constraint on `(students.classNum, students.nameKey)`

**Deferred as:** "duplicate detection for Name mode is currently a check-
then-insert TOCTOU. Serial mode has a UNIQUE index closing the race; Name
mode does not."

**Scope:** add `Index(value = ["classNum", "nameKey"], unique = true)` to
`StudentEntity` + schema v3 migration + `SQLiteConstraintException` catch
on the Name path mirroring Serial's backstop in `RoomStudentRepository.save()`.

**Estimated cost:** ~1 hour including schema migration + tests.

**Priority:** low. Concurrent-save-on-same-name is vanishingly rare (one
teacher, one device, one form at a time). Deferred because it needs a v3
schema bump and the current check-then-insert works for the actual usage
pattern.

**Anchored source finding:** qc-round-10 Oracle #2 MINOR-D7.

### C10. `ExportViewModel` — split IO decode from CPU-bound compress dispatchers

**Deferred as:** "`renderPayloads` runs the entire pipeline on
`Dispatchers.IO` including the CPU-bound `combinedRenderer.render` +
`jpegCompressor.compress` calls. That pins one IO thread per record
during compression."

**Scope:** split into three stages: decode (IO), render + compress
(Default), MediaStore write (IO). Use `withContext(Dispatchers.Default)`
around the CPU work.

**Estimated cost:** ~30 min.

**Priority:** very low. Batch exports on A35 5G measure ~250ms per
record end-to-end; even mispinning IO threads has zero user-visible
impact at the scale we're at (typical bulk export ≤50 records).

**Anchored source finding:** qc-round-10 Oracle #1 MINOR-C5.

---

## How to extend this doc

When a Section B item resolves, promote it to Section A with the choice
you made. When a new deferral surfaces, add it to Section B. When a
Section A choice needs reversing, delete the entry or annotate it with
the new direction. When something's explicitly v2, park it in Section C
so we don't rediscover it late.

Each entry keeps the same shape: what was deferred, what was chosen,
where it lives in the code, impact, and (Section A only) reverse-conditions.
That keeps the doc scannable as it grows.
