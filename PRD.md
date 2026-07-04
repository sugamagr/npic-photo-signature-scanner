# NPIC Photo & Signature Scanner — Product Requirements Document

**Status:** Locked v1.0
**Owner:** School admin (user)
**Platform:** Android (native)
**Stack:** Kotlin + Jetpack Compose + CameraX + OpenCV
**Min SDK:** 24 (Android 7.0) — covers 98%+ of devices
**Target SDK:** 34 (Android 14)

---

## 1. Problem

A school admin needs to digitize admission form data. Each printed form has:
1. A **passport-size photo** pasted at a variable position on the form
2. A **handwritten signature** in a signature field

The school portal (Uttar Pradesh Madhyamik Shiksha Parishad — UPMSP, `prereg.upmsp.edu.in`) accepts:
- **Bulk upload** by serial number (format: `{class:02d}{serial:04d}` e.g. `090001`)
- **Manual upload** by student name

Every uploaded image must be a JPEG within a strict **10 KB ≤ size ≤ 30 KB** window. Current phone camera photos are 2–4 MB — unusable without compression.

Existing scanner apps (Adobe Scan, CamScanner) don't:
- Enforce a specific file-size window
- Support signature draw/capture side-by-side with photo
- Understand the school's naming/class taxonomy
- Package exports as portal-ready ZIPs

### 1.1 Portal facts (authoritative, from UPMSP portal HTML)

These are verified facts extracted from the portal's own HTML source (`SchoolCandidate09ImageEntry.aspx`). Any conflict between this PRD and the portal must be resolved in the portal's favor.

| Fact | Value | Source |
|---|---|---|
| Accepted format | JPEG only | `accept="image/jpeg"` on upload input |
| Minimum file size | 10 KB | `data-min-size-kb="10"` |
| Maximum file size | 30 KB | `data-max-size-kb="30"` |
| Upload content | **Photo + Signature COMBINED as a single JPEG** | Field label: "हस्ताक्षर सहित फोटो" (photo WITH signature); sample file `PhotoWithSign.png` |
| Template aspect ratio | 4:5 portrait (216×270 sample) | Sample file dimensions |
| Template layout | Photo top ~75% + Signature strip bottom ~25%, each with thin dark border, white background | Visual inspection of `PhotoWithSign.png` |
| Portal display width | 150 px (on the review page) | `style="width:150px"` on `img_Candidate_photo` |
| Classes supported by portal | 9 and 11 (registration years; 10 and 12 are exam years) | Sidebar menu structure |
| Classes supported by this app | 9, 10, 11, 12 (user preference — captures ahead of time or for internal use) | User decision |
| Bulk upload page | `SchoolCandidate09ImageUpload.aspx` and `SchoolCandidate11ImageUpload.aspx` | Sidebar link "सामूहिक फोटो अपलोड" |
| Serial format seen in portal | 4-digit form number (e.g. `0002`) | `lbl_vc_FormNumber` |

**Implication:** The primary export format is Combined (photo + signature stacked in the portal's exact template layout). Photo-only and Signature-only exports remain available for manual/corrective portal uploads and are equal-weight in the export sheet, but Combined is the pre-selected default.

---

## 2. Success Criteria

| Metric | Target |
|---|---|
| Time to capture + save one photo+signature pair | ≤ 20 seconds |
| Exported JPEG size | 10 KB ≤ size ≤ 30 KB, **100%** of exports |
| App cold start to camera-ready | ≤ 1.5 s |
| Zero data loss on background/kill during capture flow | Enforced via draft persistence |

> **Removed per m2154:** Auto edge-detection success rate and manual re-crop-after-detection metrics — the two OpenCV auto-detects were removed. The Edit screen now always opens with user-adjustable crop bounds (guide-box quad for camera captures, full-image for Photo Picker imports).

---

## 3. User Flow (Golden Path)

The Camera has TWO modes selectable via text pills: `Photo` and `Signature`. Neither is required to have the other — the user can save a student with just a photo, just a signature, or both. At least one is required to save.

**Primary flow (photo first, most common):**
```
[Gallery (launch)] ──tap Capture FAB──▶ [Camera: mode = Photo (default)]
                                             │
                                             │ align in guide box, tap shutter
                                             ▼
                                         [Edit at guide-box bounds]
                                             │
                                             ▼
                                         [Edit Screen (dark chrome)]
                                             │ crop handles + filters + sliders
                                             │ tap Next
                                             ▼
                                         [Signature Prompt: Capture / Draw / Skip]
                                             │
                               ┌─────────────┼─────────────┐
                               ▼             ▼             ▼
                     [Camera: mode=Sig] [Draw Canvas]   (skip)
                               │             │             │
                               │ shutter     │ done        │
                               ▼             ▼             │
                     [Edit at guide-box bounds]           │
                               │             │             │
                               ▼             ▼             │
                        [Edit Screen (sig)] ───────────────┤
                                             │             │
                                             ▼             ▼
                                         [Save Dialog]
                                             │ class (mandatory) + naming mode + value
                                             │ at least one of photo/signature required
                                             │
                                             ├─ if duplicate ──▶ [Duplicate Dialog]
                                             │                       │ Keep current (default) / Keep existing
                                             │                       ▼
                                             ▼                   [saved]
                                         [Saved → back to Camera for next capture]
```

**Alternate entry — signature only:** User can switch the Camera mode pill from `Photo` to `Signature` at launch and start with signature. The Signature Prompt after signature-edit is replaced by a "Add photo?" prompt with `Capture / Import / Skip` options. Same Save dialog with the same at-least-one validation.

**Alternate entry — draw signature immediately:** In signature mode, a small "Draw instead" text link below the mode pills opens the Draw Signature canvas directly, bypassing camera capture.

**Key principle:** The Gallery IS the home screen. After save, the flow returns to the **Camera** (not the Gallery) so the user can capture the next form immediately. Backing out of the Camera returns to the Gallery, which now shows the newly-saved student thumbnail. The Camera remembers whichever mode was last used within the session.

---

## 4. Screens

### 4.1 Home (= Gallery)
- **Decision:** There is no separate Home screen. The app opens directly into the Gallery. Capture is always available via a persistent bottom action.
- **Persistent Capture Action:** A wide rounded-square button pinned to the bottom of the Gallery (see DESIGN.md §7.1 for exact spec). Icon: camera, label: "Capture". Tapping it navigates to Camera (Photo).
- **Rationale:** Fewer screens, zero taps to see existing work, one obvious tap to capture the next one — matches your fast-batch workflow better than a landing page.

### 4.2 Camera Screen (unified — mode is Photo or Signature)

There is ONE Camera screen with a mode selector, not two. The user can freely switch between capturing Photo and Signature without leaving the Camera flow.

- **Live preview:** Full-screen rear camera, 4:3 aspect ratio.
- **Overlay (mode-dependent):**
  - **Photo mode:** Guide box = centered rectangle, **3:4 aspect ratio** portrait, 70% of screen width. 2dp Saffron border with 24dp corner brackets.
  - **Signature mode:** Guide box = **3:1 landscape wide rectangle**, 85% of screen width, centered vertically. Same saffron style. Below-guide hint text: "Place the signature strip inside the box" — visible on first entry, dismissible.
  - Dimmed area outside guide box (60% black overlay).
- **Top bar (`CameraBg` at 85% + backdrop blur):**
  - Back arrow (left) — returns to Gallery
  - Flash toggle (right, cycles Auto/On/Off)
  - Session capture stack thumbnail (right, with saffron count badge) — quick view of items captured in this session
- **Mode selector row (above the shutter, on `CameraBg`):**
  - Horizontal text pills: `Photo` and `Signature`
  - Selected: `Saffron` ink, `titleMedium` weight 700
  - Unselected: `CameraInkMuted`, `titleMedium` weight 500
  - No pill background, no chip capsule — text-only, matches Adobe Scan
  - 32dp horizontal spacing between labels, tap target 44dp tall × label-width + 24dp padding
  - Selection change animates: saffron color crossfade 220ms + overlay guide box swaps (photo↔signature aspect) with a 220ms `EaseInOutCubic` animation
- **Bottom control row (72dp, on `CameraBg`):**
  - **Left:** Gallery import icon (24dp `CameraInk`) — tap opens system photo picker, selected image goes through the same Edit → Save flow as a captured image (skipping perspective-correction if unnecessary; but user can still re-crop). Handles the "I already have a phone photo of this form" case.
  - **Center:** Shutter — 72dp Saffron hollow circle with 4dp `CameraInk` ring, 96dp hit area. Scale to 92% on press. During capture (post-shutter, pre-navigation), inner fill animates from transparent to `Saffron` over 200ms.
  - **Right:** (Reserved for potential auto-capture toggle later. In v1.0: shows the last-captured thumbnail with count badge, redundant with the top bar's stack — moved here for reachability.)
- **Shutter action (mode-independent):**
  1. Freeze preview, capture full-res JPEG to `cache/drafts/`
  2. Trigger shared-element transition into Edit Screen with the initial crop quad seeded from the guide-box rectangle (image space). User adjusts the four corners manually. See §7 for the removed auto-detect pipelines that this replaces.
- **Import from gallery flow:** Same as capture but no guide-box rectangle exists, so Edit opens with the crop quad at full-image bounds. User adjusts as needed.
- **No auto-capture in v1.0.** No auto edge / ink detection either — removed per m2154.

### 4.3 Signature Capture — Alternative "Draw" Entry

If the user is in Signature mode on the Camera screen but wants to draw instead of capture, they can also enter Draw Signature via a small "Draw instead" text link below the mode selector (Saffron ink, `labelMedium`). This opens the Draw Signature screen (§4.4) directly.

Additionally, after Photo capture, the Signature Prompt sheet (see §3 flow) still offers `Capture / Draw / Skip` as before.

### 4.4 Draw Signature Screen
- Full-screen white canvas (respects safe areas)
- **Top bar:** Close (X, left), title "Draw Signature", Done (right, disabled until any stroke)
- **Bottom toolbar:**
  - Thickness slider (2px – 12px, default 4px). **Global thickness** — the slider acts as a canvas-wide signature-weight setting: every committed stroke re-renders at the current thickness on each slider change, and new strokes inherit the same width. There is no per-stroke thickness lock; the user can adjust weight at any point before pressing Done and the entire signature adopts the new value. This is a deliberate deviation from an earlier per-stroke-freeze proposal — the retroactive model is easier to reason about and matches the "one signature, one weight" mental model teachers actually expect.
  - Undo button
  - Redo button
  - Clear button (with confirmation dialog)
- **Pen:** black (#1A1613 — theme ink color), pressure not required
- **Canvas aspect:** 3:1 landscape stored aspect; render fits screen with letterboxing if needed
- **Output:** Rasterized PNG-then-flatten-to-JPEG at 1500×500px, white background. The rasterizer reads the current global thickness at the moment `Done` is pressed and renders every stroke at that width.

### 4.5 Edit Screen (Reusable — used post-capture AND from gallery)
The single most important reusable component. Adobe-Scan-parity.

**Layout (top to bottom):**
1. **Top bar:** Back (with unsaved-changes confirm), title ("Edit Photo" / "Edit Signature"), Save (right)
2. **Image viewport:** ~60% of screen height
   - Displays the source image with the current crop quad overlaid
   - 4 draggable corner handles only (16dp saffron circles with white ring). Edge midpoint handles are intentionally omitted — testing showed the 4-corner-only model matches user expectation for passport-photo crops and reduces accidental adjacent-handle drags on small displays.
   - The whole crop box is draggable — a drag that starts inside the quad and outside any corner hit region translates all four corners uniformly, clamped to source bounds.
   - Pinch-to-zoom on the Crop tab lets the user zoom into the source (1×–4×) to place handles precisely; zoom resets when leaving the Crop tab.
3. **Tool tabs:** Row of 4 icon+label tabs — `Crop`, `Filter`, `Adjust`, `Rotate`
4. **Active tool panel:** ~20% height
   - **Crop:** Reset crop button (returns to the initial seed: guide-box quad for camera captures, full-image bounds for imports), aspect-lock toggle (free / 3:4 / 3:1)
   - **Filter:** Horizontal scrollable row of preset thumbnails (see §5)
   - **Adjust:** 5 sliders (Brightness, Contrast, Sharpness, Saturation, Warmth) each -50 to +50, default 0
   - **Rotate:** 90° CCW button, 90° CW button, fine straighten slider (-90° to +90°)
5. **Bottom bar:** Cancel (left, discards), Next/Save (right)

**Behavior:**
- All edits are non-destructive until Save. Source image preserved in cache.
- Filter + Adjust are compounded (filter applied first, then adjust deltas).
- Save re-renders final image at full resolution with all edits baked in.

### 4.6 Save Dialog (Modal Bottom Sheet)
Appears after signature step (or skip). **Blocking**: cannot dismiss without Save/Cancel.

**Fields:**
1. **Class** (required, segmented control): `9 | 10 | 11 | 12`
2. **Naming mode** (segmented control): `Serial | Name`
3. If Serial: numeric input (4 digits, auto-populated with next serial for selected class, editable). Preview: `090001`
4. If Name: text input (letters/spaces only, auto-trim, auto-title-case). Preview: `Rahul_Kumar_09.jpg`
5. **Preview strip:** photo thumbnail + signature thumbnail side by side. Each thumbnail:
   - If present: shows the captured image
   - If missing: shows a dashed BorderStrong placeholder with "No photo" or "No signature" text and a small Saffron "Add" text button that navigates to the appropriate Camera mode / Draw canvas without dismissing the draft

**Buttons:** Cancel (returns to signature step, keeps draft), Save

**Validation:**
- Class required
- **At least ONE of {photo, signature} must be present** — the Save button is disabled until this passes. Empty state message under the preview strip: "Add a photo or signature to save."
- Serial: 1–9999
- Name: 2–50 chars, letters/spaces/hyphens/periods only

### 4.7 Duplicate Dialog
Triggered when saving with same class+serial or class+name as an existing entry.

**Layout:**
- Title: "Duplicate found"
- Body: "A student with this {serial | name} already exists in Class {X}. Which one should be kept?"
- Two large preview cards side by side:
  - **New (current)** — radio selected by default, "Just captured" label, capture time
  - **Existing** — radio, "Saved {relative time ago}" label
- Buttons: Cancel (returns to save dialog), Keep selected (destructive if replacing)

### 4.8 Gallery Screen (= Home)
Samsung-Gallery-inspired but flat (per your decision). This is the app's launch screen.

- **Top bar:** Title "Gallery" (Fraunces), search icon, sort menu (Date newest / Date oldest / Name A→Z / Name Z→A / Class 9→12 / Class 12→9), overflow menu (Select all, Delete all, Settings)
- **Filter chip row (below top bar):** `All (n) | Class 9 (n) | Class 10 (n) | Class 11 (n) | Class 12 (n)` — horizontally scrollable
- **Grid:** 3-column square thumbnails on phones, 4-column on tablets. Each cell shows:
  - Photo thumbnail (primary)
  - Signature indicator (small icon overlay bottom-right if signature exists)
  - Name / serial label below thumbnail
- **Grid bottom padding:** 120dp so the last row is never occluded by the Capture FAB.
- **Persistent Capture FAB:** Wide rounded-square button pinned to bottom-center of the screen with safe-area padding. See DESIGN.md §7.1 for full spec. Tapping opens Camera (Photo).
- **Empty state:** Saffron illustration + "No students yet. Tap Capture to add your first." — Capture FAB is still visible below.
- **Long-press:** Enters selection mode. Cells get checkmarks. Top bar changes to "N selected" with Cancel (left) and Select All (right). The Capture FAB is replaced by a bottom action bar containing `Export` and `Delete`. Exiting selection mode restores the FAB.
- **Tap (normal mode):** Navigate to Detail screen.
- **After save:** The Save flow returns to Camera (Photo) so the user can keep capturing. The Gallery is only re-entered when the user explicitly backs out.

### 4.9 Detail Screen
- **Top bar:** Back, title = student name/serial (Fraunces), overflow (Edit, Delete, Share, Duplicate to another class)
- **Content:**
  - Metadata card: Class, ID, Captured on, File sizes (only counted for pieces that exist)
  - **Photo card** (large, ~50% width): tap to open Edit Screen for photo
    - If no photo: shows dashed BorderStrong placeholder + "No photo yet" text + two CTAs — `Capture` (opens Camera in Photo mode, returns to Detail on save) and `Import` (opens system photo picker → Edit → returns to Detail)
  - **Signature card** (below): tap to open Edit Screen for signature
    - If no signature: shows dashed placeholder + "No signature yet" text + three CTAs — `Capture` / `Draw` / `Import`
- **Bottom bar:** Export button (opens format chooser: Combined (default) / Photo only / Signature only). Combined is disabled with "Requires photo and signature" tooltip if either is missing.

### 4.10 Export Format Chooser (Bottom Sheet)
- 3 radio options, all equal-weight:
  - `Combined (photo + signature)` — **pre-selected default**, with small "Portal-ready" subtitle
  - `Photo only`
  - `Signature only`
- If multiple items selected in gallery: shows count "Exporting N items"
- If any selected item is missing a signature and Combined or Signature-only is selected: pre-flight warning "N items don't have a signature. They'll be skipped." with a "Show list" expander
- Export button triggers ZIP generation (if multi) or direct share (if single) via Android Share Sheet

---

## 5. Filter Presets (Final)

| # | Name | Photo default? | Sig default? | Effect Summary |
|---|---|---|---|---|
| 0 | **Auto** | ✅ (default) | ✅ (default) | Context-aware: routes to School ID for photo, Ink Boost for signature. Shown as "Auto" in UI. |
| 1 | Original | | | No processing. |
| 2 | Color Boost | | | Saturation +15, Contrast +10, skin-tone preserve. |
| 3 | Document B&W | | | Grayscale + threshold-adaptive contrast + background whitening. |
| 4 | Passport | | | Warmth +5, Contrast +10, mild unsharp mask (radius 1.2, amount 0.4), Saturation −5. |
| 5 | **School ID** (custom) | | | Neutral WB, Contrast +15, Sharpen +20 (unsharp mask radius 1.0 amount 0.6), Saturation +5. Tuned for printed passport photos on ivory admission forms. |
| 6 | **Faded Print Rescue** (custom) | | | Contrast +25, Shadow lift +10 (curves adjust), Sharpen +15. For old/washed printed photos. |
| 7 | **Ink Boost** (custom) | | | Grayscale, adaptive-threshold, Contrast +40, Sharpen +25. For faint signature ink. |

**Auto routing logic (deterministic, no ML):**
- If capture flow was Photo camera → applies School ID preset.
- If capture flow was Signature camera or Signature draw → applies Ink Boost preset.
- User can switch to any other preset; the switch is remembered per-item (not globally).

---

## 5.5 Source Image Storage (pre-export "original")

The word "original" in this PRD does NOT mean the raw CameraX JPEG. It means a **perspective-corrected, right-sized master** we can re-edit indefinitely without touching the compressed export.

### 5.5.1 Photo source

- Input: raw CameraX JPEG (typically 2–4 MB at 4000×3000).
- Immediately after user confirmation on the Edit screen (on Save), we produce the source:
  1. Apply the user-confirmed 4-point perspective transform (`warpPerspective`) so the photo is a clean upright rectangle.
  2. Resize so the **long side = 1600 px**, preserving aspect ratio (typical output ~1200×1600 for portrait passport photos).
  3. Encode JPEG **quality 92**.
- Written to `sources/{studentId}_photo.jpg`.
- Typical size: **250–400 KB**.

### 6.0.2 Signature source

- Camera signature: same pipeline as photo, but long side = **1500 px**, JPEG q92. Typical ~80–150 KB.
- Drawn signature: rasterized directly from the Compose canvas at 1500×500 px on white background, JPEG q92. Typical ~40–90 KB.
- Written to `sources/{studentId}_signature.jpg`.

### 6.0.3 Non-destructive editing model

- The source image is written **once** and never overwritten by subsequent edits.
- All user edits (crop quad, filter preset, adjustments, rotation) are serialized to `photoEditsJson` / `signatureEditsJson` columns on the Room entity.
- When the user re-opens a student from Gallery and taps the photo, the Edit screen loads:
  - Source image from disk
  - Applies the saved `EditState` to produce the working preview
- When the user exports, the compression pipeline (§6) reads the source + applies the current `EditState` fresh, so the compressed 10–30 KB output is regenerated on demand. The `cache/` copy is best-effort only and safe to delete.
- If the user re-crops later, the crop is applied against the perspective-corrected source, NOT against a previously baked crop — so no cumulative quality loss.

### 6.0.4 Storage math

- Photo source (~350 KB) + Signature source (~120 KB) + Room row (~2 KB) ≈ **~470 KB per student**.
- 1 GB of internal storage ≈ **~2,100 students**.
- The `cache/` directory is safe-to-purge (final compressed exports); we clear it automatically on low-storage broadcasts.

### 6.0.5 Why not store raw CameraX JPEG

- ~10× the storage cost for zero visible re-edit benefit.
- The raw frame includes ~60–70% surrounding form paper that is irrelevant after the first crop.
- Perspective-correcting once at capture time is cheaper than doing it lazily on every re-open.

### 6.0.6 Exception: draft-in-progress

- Between capture and Save, the working image lives in `cache/drafts/` at the raw resolution CameraX gave us. This is only so re-cropping on the Edit screen can access more pixels if the user zooms in. On Save, we produce the 1600px source and delete the draft.

---

## 6. Compression Algorithm — Exact Spec

Every exported JPEG must satisfy: **10240 bytes ≤ size ≤ 30720 bytes** (10 KB–30 KB).

**Algorithm (per file):**

```
function compress(bitmap, min=10240, max=30720):
    # Phase 1: Binary-search JPEG quality at original resolution
    lo, hi = 30, 95
    best = null
    for iteration in 1..10:                       # log2(65) ≈ 7, cap at 10
        mid = (lo + hi) / 2
        bytes = encode_jpeg(bitmap, quality=mid)
        if bytes.length > max:
            hi = mid - 1
        elif bytes.length < min:
            lo = mid + 1
        else:
            return bytes                          # in window, done

    # Phase 2: If quality search couldn't hit window at this resolution
    final_bytes = encode_jpeg(bitmap, quality=hi)  # tightest under-max we found
    if final_bytes.length > max:
        # Still too big even at low quality → downscale by 0.85 and retry from Phase 1
        return compress(bitmap.scale(0.85), min, max)
    if final_bytes.length < min:
        # Too small even at high quality → image is trivially compressible.
        # Upscale strategy: encode at quality 95 and pad with a benign chroma-noise strip
        # OR (simpler) — accept and allow < min. See §6.1 for decision.
        return handle_under_min(bitmap)
```

### 6.1 Under-min handling (edge case)

Signatures on white backgrounds can compress to < 10 KB even at quality 95. Options considered:

- **Option A (chosen):** Accept files < 10 KB with a warning toast. Rationale: the portal's stated min is likely a soft floor; forcing padding creates fake bytes that could confuse the portal or admin. We'll log this in export metadata.
- Option B: Encode at quality 95 + upscale bitmap 1.5× before encoding (real detail, more bytes).
- Option C: Add invisible metadata padding.

**v1.0 uses Option A but logs it.** If the portal actually rejects <10 KB files, we ship Option B in v1.1.

### 6.2 Combined (Photo + Signature) layout — matches UPMSP portal template exactly

The output must visually replicate the portal's `PhotoWithSign.png` sample so that inspection-review passes at a glance.

- **Canvas:** 800 × 1000 px (4:5 portrait), white background (#FFFFFF)
- **Outer padding:** 24 px on all sides (safe zone; keeps borders visible)
- **Photo box (top region):**
  - Bounding area: x=24, y=24, width=752, height=584 (~75% of canvas height minus padding)
  - Photo scaled to fit inside preserving aspect ratio, centered inside the bounding area
  - Any leftover space filled with white
  - Thin dark border around the photo box: 2 px, color `#1A1613` (Ink)
- **Gap:** 24 px vertical gap between photo box and signature box
- **Signature strip (bottom region):**
  - Bounding area: x=24, y=632, width=752, height=344 (~25% of canvas height minus gap)
  - Signature scaled to fit inside preserving aspect ratio, centered inside the bounding area
  - Any leftover space filled with white
  - Thin dark border: 2 px, color `#1A1613`
- **No text labels** between or inside boxes (portal template has none)
- **Encoding:** JPEG through the same compression algorithm (§6). Chroma subsampling 4:2:0 (default), progressive off (better for small file sizes).
- **Fallback if signature is missing at export time:** The export refuses and shows a dialog: "This student has no signature. Add signature or export photo only?" with two options.

### 6.3 Filename scheme (in exported ZIP or single share)

The record's naming mode determines the stem; the export format determines the blob inside but NOT the filename. Every export — Combined, Photo-only, Signature-only, single-share, or bulk ZIP — uses the same rule per record:

| Naming mode | Filename pattern | Example |
|---|---|---|
| Serial | `{class:02d}{serial:04d}.jpeg` | `090001.jpeg` |
| Name | `{Name_Underscored}_{class:02d}.jpeg` | `Rahul_Kumar_09.jpeg` |

**Rationale:** The portal's bulk-upload page expects filenames to be the form number directly (verified from sample `FormNumber` value `0002` on the portal). We drop the `photo_` / `signature_` prefixes that earlier drafts of this spec called for — the format is metadata the user picks in the export sheet; it doesn't need to appear in the filename. Extension is `.jpeg` (not `.jpg`) for portal parity.

**Serial format is always exactly 4 digits.** The Save screen's serial input rejects anything shorter or longer, so `serial:04d` in the pattern is a formatter, not a truncation.

**ZIP bundling name (when multi-record):** `export_{yyyy-MM-dd}_{N}-records.zip` (e.g. `export_2025-01-30_34-records.zip`). Human-readable date + count so teachers can identify old exports in their portal upload history.

---

## 7. Image-Detection Pipelines (OpenCV)

> **§7.1 and §7.2 REMOVED per m2154 — kept below for reference.** User feedback: "your auto detect is not working at all, remove that it is actually creating more issue". Both auto-detects (photo edge detection + signature ink isolation) were surgically removed in commit `b1e68e7`. Edit screen now opens with the crop quad seeded from the guide-box rectangle (fresh camera captures) or full-image bounds (Photo Picker imports), and the user drags the four corners manually. The perspective-correction step in §7.3 stays — it runs at Save time on the user-confirmed quad. §7.4 photo/signature source storage rules stay the same.

### 7.1 Photo pipeline — edge detection **[REMOVED per m2154]**

1. Convert to grayscale.
2. Bilateral filter (d=9, sigmaColor=75, sigmaSpace=75) — preserves edges, removes noise.
3. Canny edge detection (auto-thresholded via median: lo=0.66*median, hi=1.33*median).
4. Morphological close (kernel 5×5) — bridges small gaps in the photo border.
5. `findContours` (RETR_EXTERNAL, CHAIN_APPROX_SIMPLE).
6. Filter contours: area > 5% of image, area < 95% of image.
7. For each candidate, `approxPolyDP` with epsilon = 0.02 * arcLength. Keep only quads (4 vertices).
8. **Score each quad** by:
   - Overlap ratio with guide-box region (weight 0.5)
   - Aspect ratio proximity to expected 3:4 (weight 0.3)
   - Convexity & area rank (weight 0.2)
9. Pick highest-scoring quad. If no quad scores > 0.6, fall back to guide-box coordinates.
10. Return the 4 corner points in image space, ordered TL, TR, BR, BL.

### 7.2 Signature pipeline — ink isolation **[REMOVED per m2154]**

Signatures rarely have clean rectangular field borders in real forms. Even when they do, the signature ink itself is what we care about, not the field box. So we skip edge detection entirely and instead isolate the ink.

**Pipeline:**
1. Take the raw capture and immediately crop to the guide-box region (3:1 landscape rectangle, 85% screen width). This is our working area — everything outside is discarded.
2. Convert to grayscale.
3. **Gaussian blur** (kernel 3×3) to reduce paper texture noise.
4. **Adaptive threshold** (ADAPTIVE_THRESH_GAUSSIAN_C, blockSize=51, C=10) → produces a binary image where ink=black, paper=white. Adaptive because the paper background lighting varies across the signature strip.
5. **Morphological dilate** (kernel 3×3, 1 iteration) — thickens strokes slightly so faint ink is more robust to bounding-box computation.
6. **Find connected components** of the black (ink) pixels. Discard components smaller than 0.1% of the working area (removes speckle noise, dust, printed line artifacts).
7. **Compute the union bounding box** of all remaining ink components. Add 8% padding on all sides (proportional to bbox dimensions).
8. Clamp bounding box to the working area.
9. Return the bounding box as `(x, y, width, height)` — a rectangle, not a quad. (Signatures don't need perspective correction; the signature strip is essentially flat.)

**Fallback:** If no components remain after step 6 (blank capture or excessive noise), use the entire guide-box region as the crop with a warning banner on the Edit screen: "Couldn't detect ink automatically. Adjust the crop manually."

### 7.3 Perspective correction (photos only) — **STAYS**

Once corners are confirmed on the Edit screen for a photo, apply `getPerspectiveTransform` + `warpPerspective` to a rectangle sized to the max side lengths of the quad. This becomes the source for filters/adjustments.

Signatures use a plain rectangular crop — no perspective transform.

### 7.4 Source image storage differences

- **Photo source:** perspective-corrected, long side 1600 px, JPEG q92 (§5.5.1)
- **Signature source:** cropped to user-adjusted rectangle, long side 1500 px, JPEG q92 (§5.5.2)

---

## 8. Data Model

### 8.1 Room entities

```kotlin
@Entity(
    tableName = "students",
    indices = [
        Index(value = ["classNum", "serial"], unique = false),
        Index(value = ["classNum", "nameKey"], unique = false)
    ]
)
data class StudentEntity(
    @PrimaryKey val id: String,               // UUID
    val classNum: Int,                        // 9..12
    val namingMode: String,                   // "SERIAL" | "NAME"
    val serial: Int?,                         // required if SERIAL
    val name: String?,                        // required if NAME
    val nameKey: String?,                     // normalized name for duplicate check (lowercase, no spaces)
    val photoPath: String?,                   // null if no photo (signature-only save)
    val photoEditsJson: String?,              // serialized EditState (crop quad, filter, adjust). null iff photoPath is null.
    val photoSource: String?,                 // "CAMERA" | "IMPORT" | null
    val signaturePath: String?,               // null if no signature
    val signatureEditsJson: String?,
    val signatureSource: String?,             // "CAMERA" | "DRAW" | "IMPORT" | null
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        // Invariant enforced at save-time and by validation, but asserted here for safety.
        require(photoPath != null || signaturePath != null) {
            "At least one of photoPath or signaturePath must be present."
        }
    }
}

@Entity(tableName = "class_counters")
data class ClassCounterEntity(
    @PrimaryKey val classNum: Int,            // 9..12
    val lastSerial: Int                       // last-used serial for this class
)
```

Duplicate check queries:
- Serial mode: `SELECT * FROM students WHERE classNum=? AND serial=?`
- Name mode: `SELECT * FROM students WHERE classNum=? AND nameKey=?`

### 8.2 Files on disk

```
/data/data/<pkg>/files/
    sources/
        {studentId}_photo.jpg        # original captured JPEG (kept for re-editing)
        {studentId}_signature.jpg    # original captured/drawn signature
    cache/
        {studentId}_photo_final.jpg  # compression output, deleted on export
        {studentId}_signature_final.jpg
```

### 8.3 Draft persistence

The capture flow persists a `DraftEntity` at each step so that if the app is killed:
- After photo capture → draft saved with photo cache path
- After signature step → draft saved with both paths
- On next launch, if a draft exists, prompt: "Resume capture in progress?" (Resume / Discard)

---

## 9. Architecture

### 9.1 Package layout

```
com.npic.photoscanner/
    app/                    # Application class, DI setup
    core/
        theme/              # Color.kt, Type.kt, Shape.kt, Motion.kt, Elevation.kt
        ui/                 # Reusable Compose components (buttons, cards, dialogs)
        util/               # Extensions, formatters
    data/
        db/                 # Room database, DAOs, entities
        storage/            # FileManager, image I/O
        repo/               # StudentRepository, DraftRepository
    domain/
        model/              # Student, EditState, FilterPreset, ExportFormat
        usecase/            # SaveStudent, DetectEdges, CompressJpeg, ExportZip, GenerateFileName
    features/
        camera/             # CameraScreen (photo/signature variants) + ViewModel
        edit/               # EditScreen + ViewModel (reused post-capture and from gallery)
        signaturedraw/      # DrawScreen + ViewModel
        save/               # SaveDialog + ViewModel
        gallery/            # GalleryScreen + ViewModel
        detail/             # DetailScreen + ViewModel
        export/             # ExportFormatSheet + ExportService
```

### 9.2 Key sealed classes

```kotlin
sealed interface NamingMode {
    data class Serial(val value: Int) : NamingMode
    data class Name(val value: String) : NamingMode
}

enum class ClassNum(val value: Int) { NINE(9), TEN(10), ELEVEN(11), TWELVE(12) }

enum class FilterPreset { AUTO, ORIGINAL, COLOR_BOOST, DOCUMENT_BW, PASSPORT, SCHOOL_ID, FADED_RESCUE, INK_BOOST }

data class Adjustments(
    val brightness: Int = 0,   // -50..50
    val contrast: Int = 0,
    val sharpness: Int = 0,
    val saturation: Int = 0,
    val warmth: Int = 0
)

data class CropQuad(val tl: Offset, val tr: Offset, val br: Offset, val bl: Offset)

data class EditState(
    val quad: CropQuad,
    val filter: FilterPreset,
    val adjustments: Adjustments,
    val rotationDegrees: Float = 0f
)

enum class ExportFormat { PHOTO_ONLY, SIGNATURE_ONLY, BOTH_COMBINED }
```

### 9.3 Reusability rules

- Every reused visual element lives in `core/ui/` as a `@Composable` with no ViewModel dependency.
- Screens compose these + provide their own ViewModel.
- Edit Screen accepts a `mode: EditMode` (`Photo` or `Signature`) — no code duplication.
- Compression logic is one class (`JpegCompressor`) used by both post-save persistence and export.

---

## 10. Permissions

- `CAMERA` — requested on first Camera screen entry with rationale dialog
- `WRITE_EXTERNAL_STORAGE` (SDK ≤ 28 only) — for ZIP save fallback if Share Sheet target requires it
- No storage permission needed on SDK ≥ 29 (scoped storage + Share Sheet handles all)

Denied camera → show blocked state with "Open Settings" CTA.

---

## 11. Non-Goals (v1.0)

Explicitly out of scope so we don't scope-creep:
- Cloud sync / login accounts
- OCR / text extraction
- PDF export (portal wants JPEGs)
- Multi-page batch capture
- Auto shutter / stability detection
- Watermarking
- iOS version
- Tablet-specific layouts beyond grid column count
- Analytics/crash reporting (add in v1.1 if requested)
- App lock / biometric protection

---

## 12. Open Questions (parked, not blocking v1.0)

1. Should we add an "Auto shutter" mode in v1.1? (Adobe Scan has this; skipped in v1.0 for reliability.)
2. Should we support importing existing photos from device gallery instead of only camera? (Deferred.)
3. If the portal actually rejects sub-10KB files, ship Option B upscaling for §6.1. (Waiting on real-world test.)
4. Should we allow custom class labels beyond 9–12 (e.g., "9A", "9B")? (Deferred.)

---

## 13. Acceptance Checklist (v1.0 Done Definition)

- [ ] Full golden path works: Home → Camera → Edit → Signature → Save → back to Camera
- [ ] All 8 filter presets render correctly and non-destructively
- [ ] All 5 adjustment sliders affect the preview live (< 100ms latency)
- [ ] Every export lands in [10 KB, 30 KB] window OR logs the under-10 case
- [ ] Serial mode auto-increments per class and prefills the next value
- [ ] Duplicate dialog appears for class+serial and class+name matches
- [ ] Gallery: sort, filter chips, long-press select, multi-select export all work
- [ ] Detail screen: photo & signature tap → Edit screen with existing state loaded
- [ ] Export ZIP: correct filenames per §6.3 (`090001.jpeg`-style stems, no prefix, `.jpeg` extension), all files in the 10–30 KB window, ZIP name is `export_{yyyy-MM-dd}_{N}-records.zip`
- [ ] Combined export renders photo-top signature-bottom on white canvas
- [ ] Draft resume works after app kill mid-flow
- [ ] Rotation, low-light, and busy-background test forms all produce usable outputs
- [ ] Every color, font size, spacing value in the app traces to `core/theme/` files
