# NPIC Photo & Signature Scanner — Design System

**Style direction:** Warm Editorial with custom UI + custom font
**Primary color:** Saffron (India-adjacent, warm, distinctive)
**Type pair:** Fraunces (display / headings) + Inter (body / UI)
**Version:** Locked v1.1 — true-saffron retheme (hue 41°→32°, away from amber/yellow), brand gradient + glow tokens, M3-Expressive spatial springs

Every color, size, and radius in this document maps to a token in `core/theme/`. Nothing is hardcoded in screens. To retheme the app, edit `Color.kt` only.

---

## 1. Design Principles

1. **Warmth over sterility.** Ivory background instead of pure white. Saffron instead of blue. Fraunces headings instead of geometric sans. The app should feel like a well-designed school journal, not a SaaS admin panel.
2. **Camera-first.** The Camera and Edit screens are the app's soul. They get full-bleed layouts, dark chrome, and the tightest interactions.
3. **Everything reused.** No screen invents a button, dialog, or card. If it doesn't exist in `core/ui/`, we add it there first.
4. **One primary action per screen.** Ambiguous CTAs kill fast-capture flow.
5. **Never block the flow on failure.** Compression floor hit → toast, don't dialog. (Edit opens at guide-box or full-image bounds by default; there's no auto-detection to fail — removed per m2154.)
6. **Every touch target ≥ 44dp.** Even the tiny handles are 44dp hit areas around 16dp visual dots.

---

## 2. Color Tokens

### 2.1 Semantic tokens (Color.kt)

```kotlin
object NpicColors {
    // Brand — true saffron (hue ~32°, orange-leaning; the old #F4A300 read yellowish)
    val Saffron         = Color(0xFFF6921E)   // primary
    val SaffronDeep     = Color(0xFFD97812)   // pressed/hover
    val SaffronSoft     = Color(0xFFFDEBD2)   // tinted backgrounds, chips selected (warm peach)
    val SaffronBright   = Color(0xFFFFAD42)   // light end of brand gradient (button/FAB tops)
    val SaffronGlow     = Color(0x66F6921E)   // colored shadow under floating brand elements ONLY

    // Ink (text)
    val Ink            = Color(0xFF1A1613)   // primary text
    val InkMuted       = Color(0xFF5B534C)   // secondary text
    val InkFaint       = Color(0xFF8A8079)   // captions, disabled

    // Surfaces
    val Ivory          = Color(0xFFFAF7F2)   // app background
    val Surface        = Color(0xFFFFFFFF)   // cards, sheets
    val SurfaceRaised  = Color(0xFFFFFDF8)   // slight lift over Ivory
    val Overlay        = Color(0x99000000)   // 60% black for camera dim outside guide box

    // Borders
    val BorderSoft     = Color(0xFFEDE6DA)   // hairlines, dividers
    val BorderStrong   = Color(0xFFD8CDB8)   // input borders

    // Accents
    val Terracotta     = Color(0xFFC1440E)   // destructive, errors
    val TerracottaSoft = Color(0xFFF9E4DA)
    val Sage           = Color(0xFF3B7A57)   // success, saved indicator
    val SageSoft       = Color(0xFFE2EFE6)
    val Indigo         = Color(0xFF2B2D6B)   // reserved: informational links (rare)

    // Camera-mode dark chrome (near-pure black for maximum preview contrast, Adobe Scan match)
    val CameraBg       = Color(0xFF050506)
    val CameraSurface  = Color(0xFF141416)
    val CameraInk      = Color(0xFFF5F5F7)
    val CameraInkMuted = Color(0xFFA0A0A5)
}
```

### 2.2 Usage rules

| Where | Token |
|---|---|
| App background | `Ivory` |
| Card / sheet fill | `Surface` |
| Primary button fill | vertical gradient `SaffronBright → Saffron` (solid `Saffron` when a gradient is impractical) |
| Primary button pressed | `SaffronDeep` (solid) |
| Primary button label | `Ink` (dark text on saffron — AAA at 7.75:1, AA at every gradient pixel) |
| Floating brand elements (Capture FAB) shadow | `SaffronGlow` ambient+spot color at existing elevation |
| Secondary button (outlined) | fill `Surface`, border `BorderStrong`, label `Ink` |
| Text primary | `Ink` |
| Text secondary | `InkMuted` |
| Destructive button | fill `Terracotta`, label `#FFFFFF` |
| Success toast | fill `SageSoft`, icon `Sage`, text `Ink` |
| Error toast | fill `TerracottaSoft`, icon `Terracotta`, text `Ink` |
| Divider / hairline | `BorderSoft`, 1dp |
| Camera screens (top+bottom bars) | fill `CameraBg` at 85% opacity, text `CameraInk` |
| Guide box border | `Saffron` @ 90% opacity, 2dp with 20dp corner brackets |

### 2.3 Contrast validation

All text/background pairs verified WCAG AA (≥ 4.5:1 for body, ≥ 3:1 for large). Actual measured ratios exceed AA and clear AAA for normal text (≥ 7:1) in every non-status pairing:
- Ink on Ivory: 16.8:1 ✓ AAA
- Ink on Saffron (#F6921E): 7.75:1 ✓ AAA
- Ink on SaffronBright (#FFAD42): 9.68:1 ✓ AAA (gradient light end — the darker gradient stop is Saffron itself, so the full gradient stays ≥ 7.75:1)
- Ink on SaffronDeep (#D97812): 5.69:1 ✓ AA (AAA for large text)
- InkMuted on Ivory: 7.1:1 ✓ AAA
- InkFaint on Ivory: 3.6:1 ✓ AA large only — use only for non-critical metadata
- CameraInk on CameraBg (near-pure black): 18.7:1 ✓ AAA
- CameraInkMuted on CameraBg: 8.5:1 ✓ AAA
- Ivory on Terracotta: 5.5:1 ✓ AA

---

## 3. Type System (Type.kt)

Fraunces = display / titles / numeric emphasis. Inter = UI, labels, body.

```kotlin
val NpicTypography = Typography(
    // Fraunces — display
    displayLarge  = TextStyle(font = Fraunces,  size = 40.sp, lineHeight = 44.sp, weight = 500, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(font = Fraunces,  size = 32.sp, lineHeight = 36.sp, weight = 500, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(font = Fraunces,  size = 26.sp, lineHeight = 32.sp, weight = 500),
    headlineMedium= TextStyle(font = Fraunces,  size = 22.sp, lineHeight = 28.sp, weight = 500),
    titleLarge    = TextStyle(font = Fraunces,  size = 20.sp, lineHeight = 26.sp, weight = 600),

    // Inter — UI
    titleMedium   = TextStyle(font = Inter,     size = 16.sp, lineHeight = 22.sp, weight = 600),
    titleSmall    = TextStyle(font = Inter,     size = 14.sp, lineHeight = 20.sp, weight = 600),
    bodyLarge     = TextStyle(font = Inter,     size = 16.sp, lineHeight = 24.sp, weight = 400),
    bodyMedium    = TextStyle(font = Inter,     size = 14.sp, lineHeight = 20.sp, weight = 400),
    bodySmall     = TextStyle(font = Inter,     size = 12.sp, lineHeight = 16.sp, weight = 400),
    labelLarge    = TextStyle(font = Inter,     size = 14.sp, lineHeight = 20.sp, weight = 600, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(font = Inter,     size = 12.sp, lineHeight = 16.sp, weight = 500, letterSpacing = 0.3.sp),
    labelSmall    = TextStyle(font = Inter,     size = 11.sp, lineHeight = 14.sp, weight = 500, letterSpacing = 0.4.sp)
)
```

**Usage rules:**
- All screen titles (top app bar center) → `headlineMedium` in Fraunces
- All section headings → `titleLarge` in Fraunces
- All button labels → `labelLarge` in Inter
- All body copy → `bodyMedium` in Inter
- Numeric data (serials, sizes, timestamps) → tabular-lining variant of Inter
- Empty-state hero text → `displayMedium` in Fraunces

---

## 4. Shape & Spacing

### 4.1 Corner radii (Shape.kt)

```kotlin
object NpicShapes {
    val none        = RoundedCornerShape(0.dp)
    val xs          = RoundedCornerShape(6.dp)   // chips, small pills
    val sm          = RoundedCornerShape(10.dp)  // inputs, small buttons
    val md          = RoundedCornerShape(14.dp)  // buttons
    val lg          = RoundedCornerShape(20.dp)  // cards
    val xl          = RoundedCornerShape(28.dp)  // bottom sheets top corners
    val full        = RoundedCornerShape(999.dp) // FABs, avatars
}
```

### 4.2 Spacing scale (4dp grid)

`4, 8, 12, 16, 20, 24, 32, 40, 56, 72`

Only these values are allowed. Anything else needs justification.

### 4.3 Elevation

Warm Editorial rejects heavy shadows. We use:
- **Level 0** — no elevation, hairline border only
- **Level 1** — 1dp shadow, spread 0, blur 4, alpha 0.06 (cards)
- **Level 2** — 2dp shadow, spread 0, blur 12, alpha 0.08 (dialogs, sheets)
- **Level 3** — 3dp shadow, spread 0, blur 24, alpha 0.10 (FABs, floating)

Never both border + shadow — pick one per element.

---

## 5. Motion & Micro-interactions

```kotlin
object NpicMotion {
    val fast      = tween<Float>(150, easing = EaseOutCubic)  // taps, chip toggles
    val standard  = tween<Float>(220, easing = EaseOutCubic)  // screen transitions
    val slow      = tween<Float>(320, easing = EaseOutCubic)  // sheet open, dialog
    val emphasized= tween<Float>(400, easing = EaseInOutCubic)// hero moments (save success)

    // Spatial springs (M3 Expressive) — for scale/offset/size/bounds ONLY.
    // Color and alpha animations always use the tweens above; never bounce a color.
    val springSnappy = spring<T>(dampingRatio = 0.7f,  stiffness = 400f) // press feedback, selections
    val springSmooth = spring<T>(dampingRatio = 0.9f,  stiffness = 400f) // layout/size, tab pill slides
    val springBouncy = spring<T>(dampingRatio = 0.5f,  stiffness = 300f) // hero moments only
}
```

**Rules:**
- Buttons: 96% scale on press with `springSnappy` (tiny physical overshoot on release). No color flash — use the pressed color token.
- Every spring respects reduce-motion via the `...OrSnap(reduce)` builders in `Motion.kt`.
- Screen transitions: horizontal slide + fade, 220ms. Camera → Edit uses a shared-element transition on the captured thumbnail.
- Sheets: rise 320ms with a subtle overshoot (spring `dampingRatio = 0.85, stiffness = 320`).
- Filter thumbnails on tap: pulse to 105% then settle to 100% over 220ms + saffron ring appears.
- Save success: a 400ms confetti-free celebration — the saved thumbnail flies from the Save button to the Gallery icon in the top bar, then Gallery icon count badge bumps by +1 with a soft scale from 1.0→1.15→1.0.

---

## 6. Component Specifications

Each component below is a `@Composable` in `core/ui/`. Screens compose these; nothing is bespoke.

### 6.1 NpicButton

Three variants: `Primary`, `Secondary`, `Destructive`, `Ghost`.

- Height: 52dp (primary), 44dp (secondary/ghost)
- Padding: horizontal 20dp
- Corner: `NpicShapes.md` (14dp)
- Text: `labelLarge` (Inter 14sp weight 600)
- Primary: fill vertical gradient SaffronBright→Saffron, ink Ink, pressed solid SaffronDeep, disabled 40% alpha
- Secondary: fill Surface, border BorderStrong 1dp, ink Ink
- Destructive: fill Terracotta, ink white
- Ghost: transparent, ink Ink, pressed BorderSoft
- Loading state: label crossfades to a 20dp saffron progress ring (destructive uses white ring)
- Icon slot (leading): 20dp icon, 8dp gap

### 6.2 NpicIconButton

- 44dp hit area, 24dp icon
- Tint: `Ink` (light context) or `CameraInk` (dark context)
- Ripple: bounded, `Saffron` @ 20% alpha in light, `CameraInk` @ 15% in dark
- Toggle variant: selected state shows a `SaffronSoft` background circle (36dp)

### 6.3 NpicTopBar

- Height: 56dp (standard), 96dp (large — used on Gallery only)
- Fill: Ivory (blends with background) with a 1dp bottom hairline `BorderSoft` when content is scrolled
- Left: back arrow icon button (or nothing on Home)
- Center: title in `headlineMedium` Fraunces
- Right: up to 2 icon buttons
- On Camera screens: fill CameraBg @ 85% + backdrop blur (Android 12+), text CameraInk

### 6.4 NpicBottomBar

Reserved for Edit Screen and Detail Screen. Height 72dp, fill Surface, top hairline `BorderSoft`. Contains up to 2 actions, spaced with 12dp gap.

### 6.5 NpicChip (filter chip / class chip)

- Height 34dp, corner `xs` (6dp) — pill-shaped feeling but not full-round, matches editorial feel
- Padding: horizontal 14dp
- Unselected: fill Surface, border BorderStrong 1dp, ink InkMuted, `labelMedium`
- Selected: fill SaffronSoft, border Saffron 1dp, ink Ink (weight 600)
- Count suffix (e.g., "Class 9 · 12"): `InkFaint` inline
- Selection animation: fill crossfade 150ms

### 6.6 NpicCard

- Fill: Surface
- Corner: `NpicShapes.lg` (20dp)
- Border: `BorderSoft` 1dp, NO shadow (editorial calm)
- Padding: 16dp default, 20dp on Detail screen
- Header slot (optional): 20dp icon + title `titleMedium`
- Press ripple: `SaffronSoft` bounded

### 6.7 NpicThumbnail (gallery grid cell)

- Square, no rounded corners on the image itself — the wrapper rounds at `NpicShapes.md` (14dp)
- 1dp border BorderSoft
- Signature indicator: 24dp saffron circle bottom-right, offset -8dp from edges, containing a 14dp signature icon in white
- Selected state (multi-select): 3dp Saffron ring outside + white check on saffron circle top-right
- Label below: name/serial in `bodySmall`, single line ellipsis, 4dp top gap
- Sub-label: class chip small (24dp height) in `SaffronSoft` fill

### 6.8 NpicTextField

- Height: 52dp
- Corner: `sm` (10dp)
- Fill: Surface
- Border: 1dp `BorderStrong` default, 2dp `Saffron` focused, 2dp `Terracotta` error
- Label: floating, `labelMedium` in `InkMuted`, animates up on focus (24dp travel, 150ms)
- Helper/error text: `labelSmall`, 6dp below field
- Numeric variant: right-aligned tabular-lining, saffron caret

### 6.9 NpicSegmentedControl

Used for Class picker and Naming Mode picker in Save dialog.
- Height: 44dp
- Corner: `sm` (10dp) outer, `xs` (6dp) inner segments
- Fill: Ivory
- Border: 1dp BorderStrong
- Selected segment: fill Surface, 1dp BorderStrong all around, small shadow (Level 1), ink Ink weight 600
- Unselected segments: transparent, ink InkMuted
- Slide animation: 220ms EaseOutCubic when selection changes

### 6.10 NpicSlider

Used for adjustment sliders and pen thickness.
- Track: 4dp height, fill BorderStrong, active-portion fill Saffron
- Thumb: 24dp Saffron circle with 2dp white ring, Level 2 elevation
- Value bubble: appears above thumb during drag, `labelSmall`, fill Ink, ink Ivory
- Tick at center (0) for adjustment sliders (bipolar -50..+50): a 2dp saffron vertical line

### 6.11 NpicBottomSheet

- Corner: top `xl` (28dp), bottom 0
- Fill: Surface
- Handle: 40×4dp `BorderStrong` pill, 12dp from top
- Padding: 24dp horizontal, 20dp top (after handle), 32dp bottom + safe area
- Elevation: Level 2
- Overlay: `Overlay` (60% black) behind sheet
- Rise animation: 320ms spring

### 6.12 NpicDialog

Used for confirmations only (destructive actions, resume drafts). NEVER used for anything the user can just fix inline.
- Width: min(560dp, screen - 48dp)
- Corner: `lg` (20dp)
- Fill: Surface
- Elevation: Level 2
- Padding: 24dp
- Header: `titleLarge` Fraunces
- Body: `bodyMedium` Inter
- Buttons: right-aligned, secondary + primary; destructive uses Destructive variant

### 6.13 NpicToast (snackbar)

- Corner: `md` (14dp)
- Fill: Ink @ 92%, ink Ivory
- Padding: 16dp horizontal, 12dp vertical
- Max width: screen - 32dp, bottom offset 24dp + safe area
- Duration: 3s standard, 5s if action present
- Success/error variants use SageSoft/TerracottaSoft with dark ink

### 6.14 NpicEmptyState

- Illustration: 200dp square saffron-toned line illustration (custom SVG per state)
- Title: `displayMedium` Fraunces
- Body: `bodyMedium` InkMuted, max 320dp wide, center-aligned
- Optional CTA: NpicButton Primary
- Vertical spacing: 24dp between blocks

### 6.15 NpicCropOverlay

The crown jewel — this is what makes or breaks the app.
- 4 corner handles ONLY: 16dp Saffron circle with 3dp white ring, 44dp hit area. Edge midpoint handles are intentionally omitted — the 4-corner model matches user testing for passport-photo crops.
- Quad outline: 2dp Saffron line between handles
- Grid inside quad (rule-of-thirds): 1dp Saffron @ 30% alpha, shown only during drag
- Outside quad: 60% black overlay
- Interior drag (a drag starting inside the quad but outside any corner's hit region) translates the whole box, clamped to source bounds.
- Pinch-to-zoom (1×–4×) is available on the Crop tab to place handles precisely; the overlay and image transform together so the quad stays pixel-locked to the source at every zoom level. Zoom resets to 1× when leaving the Crop tab.

### 6.16 NpicCameraOverlay

- Guide box: 2dp Saffron, corner brackets 24dp long × 3dp thick at each corner (like Halide / ProCam)
- Outside guide box: `Overlay` (60% black)
- Center reticle: none (would clutter for passport photo alignment)
- Level indicator (Android sensor): thin saffron line across guide box that tilts with device — 1dp, 40% alpha, snaps to green (Sage) when within ±2° of level
- Bottom hint text (fades in on first launch, dismissible): "Align the photo inside the box" — `bodyMedium`, CameraInk @ 80%

### 6.17 NpicCaptureFab

Wide rounded-square capture button pinned to the bottom of the Gallery. Full pixel-precise spec lives in §7.1.

- Shape: `NpicShapes.lg` (20dp) rounded square
- Size: 72dp tall × min(240dp, 60% screen width) wide, minimum 200dp
- Fill: vertical gradient `SaffronBright → Saffron`, pressed solid `SaffronDeep`
- Elevation: Level 3 (3dp shadow, blur 24) with `SaffronGlow` ambient+spot color — the one colored shadow in the app
- Contents: 28dp camera icon + "Capture" label (Inter labelLarge), 8dp gap, both in `Ink`
- Above-FAB gradient: 32dp Ivory→transparent to soften the boundary against the scrolling grid
- Press animation: scale to 96% over 150ms EaseOutCubic
- Long-press: no separate action — reserved for future "batch mode"
- Accessibility: `contentDescription = "Capture new student photo"`

### 6.18 NpicFilterPreview

Filter thumbnail strip on Edit Screen.
- Cell: 72dp wide × 96dp tall
- Image: 72dp square, corner `sm` (10dp)
- Label: `labelMedium`, `Ink`, single line, 8dp top gap
- Selected: 2dp Saffron ring around image + label weight 700
- Spacing: 12dp between cells
- Applied via a real-time GPU-shader preview at low res (192×192 max) so the strip renders in <300ms

---

## 7. Screen-Specific Design Decisions

### 7.1 Home = Gallery + Persistent Capture FAB

There is no dedicated Home screen. Gallery is the launch destination, and a persistent wide rounded-square Capture button lives at the bottom.

**NpicCaptureFab spec:**
- Shape: rounded square, corner `NpicShapes.lg` (20dp) — squircle-feeling, not a circle, not a pill
- Size: 72dp tall × 200dp wide (min); on ≥ 400dp screen width, scales to `min(240dp, 60% of screen width)` for visual weight without dominating
- Position: bottom-center, 24dp above safe-area inset
- Fill: vertical gradient SaffronBright→Saffron with Level 3 elevation (3dp shadow, blur 24, SaffronGlow tinted)
- Pressed: SaffronDeep fill, scale to 96% via springSnappy
- Contents (centered horizontally, 8dp horizontal gap between icon and label):
  - 28dp camera icon in `Ink` (dark on saffron for AA contrast)
  - "Capture" label in `labelLarge` (Inter 14sp weight 600), `Ink`
- Above the FAB: a 32dp Ivory-to-transparent vertical gradient (bottom fade) so grid rows never abruptly hit the FAB
- Accessibility: `contentDescription = "Capture new student photo"`, hit target 72×220dp (already ≥ 44dp)

**Selection-mode swap:**
- When the user long-presses a thumbnail, the FAB is replaced (crossfade 220ms) by a bottom action bar: 72dp tall, Surface fill, top hairline `BorderSoft`, containing two half-width buttons — `Export` (Primary) and `Delete` (Destructive), 12dp gap between them, 20dp side padding.
- Exiting selection mode (Cancel or back gesture) crossfades the FAB back in.

**Grid interaction with FAB:**
- Grid content padding-bottom = 120dp so the last row is always fully scrollable above the FAB.
- On scroll-down (finger moves up, revealing more content), the FAB stays fixed — it does not hide-on-scroll. Reason: the user's dominant intent is "capture another one," and hiding the primary action to reveal one more row of thumbnails is a bad tradeoff.

**Empty state co-existence:**
- When Gallery is empty, the empty-state illustration + copy sits centered in the safe scroll area, and the Capture FAB is still visible at the bottom. The empty-state CTA text is dropped (redundant with FAB) — copy becomes just "No students yet. Tap Capture to add your first."

### 7.2 Camera (unified, mode-switched via text pills)

**Background:** `CameraBg #050506` (near-pure black, maximum preview contrast, Adobe Scan match).

**Full-bleed camera preview** fills the entire screen. All chrome floats over the preview with translucent backdrops.

**Top bar (56dp):**
- Fill: `CameraBg` at 85% opacity + 24dp backdrop blur (Android 12+); solid CameraBg below
- Left: back arrow icon in `CameraInk` (24dp). Tap → confirm "Discard capture in progress?" if a draft exists, else navigates back to Gallery.
- Center: empty (no title — the mode pills below the shutter serve as the label)
- Right: flash toggle icon (24dp `CameraInk`), cycles Auto/On/Off with tiny label caption below (`labelSmall`, `CameraInkMuted`)
- Right (further): session capture stack thumbnail (44dp rounded square, corner `sm`, thin `CameraInk @ 20%` border, showing last captured item + `Saffron` count badge top-right, `labelSmall` `Ink` text)

**Live preview area** (full-bleed):
- The camera preview covers the entire screen behind all chrome
- **NpicCameraOverlay** on top, mode-aware:
  - **Photo mode:** Guide box = centered 3:4 portrait rectangle, 70% of screen width, 2dp `Saffron @ 90%` border with 24dp × 3dp corner brackets on all 4 corners
  - **Signature mode:** Guide box = centered 3:1 landscape rectangle, 85% of screen width, same border/bracket style
  - Outside guide box: 60% black overlay (`Overlay` token)
  - Below-guide hint text on first entry (fades in 400ms after screen mount, dismissible with a tap): "Align the {photo/signature} inside the box" — `bodyMedium`, `CameraInk @ 80%`, positioned 20dp below the guide box
  - Level indicator (device tilt): 1dp `Saffron @ 40%` horizontal line across guide box, snaps to `Sage` when within ±2° of level

**Mode selector row (above the shutter):**
- Position: 24dp above the bottom control row, centered horizontally
- 32dp horizontal spacing between the two labels
- Each label: `titleMedium` (Inter 16sp), 44dp tall tap target with 24dp side padding
  - **Selected:** `Saffron` ink, weight 700
  - **Unselected:** `CameraInkMuted`, weight 500
- **No pill background, no chip capsule, no border** — text only, Adobe Scan match
- Selection change animates:
  - Ink color crossfades over 220ms `EaseOutCubic`
  - Guide box overlay reshapes from 3:4 to 3:1 (or vice versa) with a 220ms `EaseInOutCubic` animation
  - Below-guide hint text updates with a 150ms crossfade
- **Additional affordance in Signature mode only:** a small "Draw instead" text link appears 12dp below the mode pills — `labelMedium`, `Saffron` ink weight 600, no underline. Tap opens Draw Signature screen (§7.9) directly. In Photo mode, this link is hidden.

**Bottom control row (96dp tall):**
- Fill: `CameraBg` at 85% + backdrop blur
- Layout: 3 zones (left 56dp, center-flex, right 56dp)
- **Left zone (56dp):** Gallery import icon (24dp `CameraInk`, photo-stack outline). Tap opens system photo picker (`ActivityResultContracts.PickVisualMedia`). Selected image goes directly to Edit screen with a rectangular full-image crop as the starting quad. (No auto-detection runs on captures either — removed per m2154.)
- **Center zone:** Shutter button
  - 72dp hollow circle, 4dp `CameraInk` ring
  - Inner fill: transparent by default
  - 96dp hit target (larger than visual)
  - Press: scale to 92% over 100ms
  - During capture (100ms after press until Edit screen mounts, ~500ms): inner fill animates from transparent to `Saffron` via a filling arc (progress-ring style), then holds at full fill until transition begins
- **Right zone (56dp):** reserved (empty in v1.0; slot exists for future auto-capture toggle)

**After capture, animation:**
1. **t=0ms:** shutter tap → preview freezes on current frame
2. **t=0–200ms:** shutter inner fill animates to `Saffron` (progress ring style)
3. **t=200ms:** guide box overlay fades out (100ms)
4. **t=200–350ms:** all bottom chrome (mode pills, shutter, control row) crossfades out
5. **t=200–450ms:** frozen preview letterboxes into Edit screen viewport target (shared-element transition per §7.3.1)
6. **t=350ms:** Edit screen top bar + tool tabs slide in from off-screen edges
7. **t=450ms:** `NpicCropOverlay` fades in over the letterboxed image with the initial crop quad — guide-box bounds for camera captures, full-image bounds for Photo Picker imports (per m2154 removal of auto-detection)

**Background never changes color throughout** — `CameraBg` stays constant from Camera → Edit, which is what sells the "we never left" feeling.

### 7.3 Edit Screen (Adobe Scan continuity, dark chrome)

**Principle:** The Edit screen is a direct visual continuation of the Camera. The background stays dark, the image doesn't jump, only the surrounding chrome changes. The user should feel they never left "shooting mode" — they're just adjusting what they shot.

**Background:** `CameraBg #0E0E10` full-bleed. Same as Camera.

**Top bar (56dp, matches Camera exactly):**
- Fill `CameraBg` at 85% opacity + 24dp backdrop blur (Android 12+); solid CameraBg on older devices
- Left: back arrow icon in `CameraInk` (24dp). Tap = confirm dialog "Discard changes?" (not silent back — we're in a capture session with unsaved crop/filter/adjust state)
- Center: title in Fraunces `headlineMedium`, `CameraInk` — "Edit Photo" or "Edit Signature"
- Right: `Next` text button — `labelLarge` (Inter 14sp weight 600), `Saffron` ink, 16dp horizontal padding, no fill. Tapping = advance to next step (signature prompt if editing photo, save dialog if editing signature or re-editing from gallery).

**Image viewport (~55% screen height):**
- Image letterboxed on `CameraBg` — same visual language as Adobe Scan
- 20dp inset from screen edges (so handles never touch screen edges)
- Background outside the crop quad: 60% black overlay (same as Camera guide-box outside area — reinforces "still shooting")
- `NpicCropOverlay` on top with `OverlayMode.OnDark`:
  - Corner handles ONLY: 16dp `Saffron` circles with 3dp `CameraInk` (white) ring, 44dp hit area. No edge midpoint handles.
  - Quad outline: 2dp `Saffron` continuous line between handles
  - Rule-of-thirds grid inside quad during drag: 1dp `Saffron` @ 30% alpha
  - Interior drag translates the whole box uniformly (clamped to source bounds)
  - Pinch-to-zoom the source (1×–4×) is available on the Crop tab; overlay + image share the same transform so the quad stays pixel-locked

**Tool tabs row (56dp tall):**
- Fill: `CameraSurface #1A1A1D`
- 4 tabs, equal width: `Crop | Filter | Adjust | Rotate`
- Each tab: 24dp icon (line style, custom icon set) + `labelMedium` label BELOW icon (Adobe Scan pattern), 8dp gap between icon and label
- Inactive: icon + label in `CameraInkMuted`
- Active: icon + label wrapped in a `SaffronSoft`-tinted pill capsule (Adobe Scan's "selected pill" pattern):
  - Pill fill: `SaffronSoft` at 22% alpha over `CameraSurface` (visible tint without washing out)
  - Pill corner: `NpicShapes.sm` (10dp)
  - Icon and label go to `Saffron` ink, label weight 700
  - Pill padding: 8dp horizontal, 4dp vertical
- Tap animation: 150ms EaseOutCubic pill crossfade
- Horizontally scrollable if we later add more tools

**Tool content (NOT a panel — critical design detail):**

**Principle:** In Adobe Scan, when you tap Filters, the filter thumbnails appear directly in the dark space between the image and the tool tabs. There is NO panel container, NO card, NO fill change, NO hairline divider. The image shrinks slightly, and the filter row occupies the reclaimed dark letterbox space. Your eye reads image + filter previews as one continuous space, not two sections.

We replicate this exactly.

**Rules for all four tools:**
- Tool content renders directly on `CameraBg` (same background as the image area)
- NO `CameraSurface` fill, NO border, NO hairline between image and tool content
- NO wrapper container, NO padding around the section as a whole
- Only the tool tabs row (below tool content) uses `CameraSurface` — that IS a distinct interactive region
- Image viewport height animates down (from ~55% to ~40% screen height) to make room, using `EaseOutCubic` 220ms
- Tool content fades in over the reclaimed space, 200ms
- Height of tool content region: dynamic per tool (Crop ~64dp, Filter ~120dp, Adjust ~200dp, Rotate ~140dp)
- Padding within the tool region: 16dp horizontal only, no top/bottom padding (the natural spacing from image viewport bottom and tool tabs top provides breathing room)

**Tool: Crop**
- One row, 64dp tall
- Left: "Reset crop" ghost text button — `Saffron` ink, `labelLarge`, no fill, no border, 40dp hit area. Returns the crop quad to its initial seed: guide-box bounds for camera captures, full-image bounds for imports.
- Right: aspect chip row `Free · 3:4 · 3:1` — 32dp tall chips, `NpicShapes.xs` corner
  - Unselected: transparent fill, `CameraInkMuted @ 40%` border 1dp, `CameraInkMuted` ink
  - Selected: `SaffronSoft @ 22%` fill, `Saffron` border 1dp, `Saffron` ink weight 600
- Row items vertically centered

**Tool: Filter (the Adobe Scan pattern, exact)**
- Horizontal scrollable strip of 8 filter thumbnails, ~120dp tall
- Each thumbnail cell: 84dp wide × 116dp tall (image + label stacked)
- Thumbnail image: 84dp square, `NpicShapes.sm` corner (10dp)
  - Content: live GPU-rendered preview of the current source image with that filter applied, at 192×192 max working resolution for performance
  - The thumbnail preview reflects the user's current crop and adjustments — filters are previewed on top of what they've already done, not on the raw source (so filters feel WYSIWYG)
- Label below thumbnail: `labelMedium` `CameraInk`, single line, ellipsize if truncated (rare — longest is "Faded Rescue"), 8dp top gap
- Selected state:
  - 2dp `Saffron` ring around the image only (drawn outside the 10dp corner so it wraps around, not over pixels)
  - Label weight jumps to 700, ink stays `CameraInk`
  - Optional (nice touch): a tiny 12dp `Saffron` checkmark on the top-right of the thumbnail
- Inter-cell spacing: 12dp
- Strip padding: 16dp left/right start-padding
- Snap-scrolling to align the tapped cell to the center of the viewport
- Long-tap on a thumbnail: shows a tiny tooltip with the filter description ("Neutral WB, contrast +15, sharpen +20") for 2s — power-user affordance, not required for beginners

**Tool: Adjust**
- 5 rows, each 40dp tall, 4dp vertical inter-row gap → ~220dp total
- Row layout:
  - Icon (20dp `CameraInk`) — 8dp right margin
  - Label `labelMedium` `CameraInk` — fixed width 88dp
  - `NpicSlider.OnDark` — flex, takes remaining width
  - Numeric value `labelMedium` `CameraInk` tabular-lining — fixed width 32dp, right-aligned
- Icons per slider: brightness (sun), contrast (half-filled circle), sharpness (upward triangle), saturation (droplet), warmth (temperature dial)
- Slider mode `OnDark`:
  - Track: 4dp height, inactive fill `CameraInkMuted @ 30%`, active fill `Saffron`
  - Thumb: 24dp `Saffron` circle with 2dp `CameraInk` ring, Level 2 elevation
  - Center tick at 0 (bipolar -50..+50): 2dp `Saffron @ 60%` vertical line, 8dp tall
  - Value bubble during drag: fill `CameraInk`, ink `CameraBg`, `labelSmall`, positioned 32dp above thumb

**Tool: Rotate**
- Row 1 (56dp tall): two 56dp × 56dp square icon buttons for rotate 90° CCW and 90° CW
  - No fill — buttons blend into `CameraBg`
  - No border
  - Icon 28dp `Saffron`
  - Pressed: `SaffronSoft @ 22%` fill appears (circular ripple), icon stays `Saffron`, 96% scale
  - Positioned: centered horizontally with 32dp gap between the two buttons
- 12dp gap
- Row 2 (48dp tall): Straighten slider — `NpicSlider.OnDark` bipolar (-15° to +15°)
  - Label `labelMedium` `CameraInk` — fixed width 88dp — "Straighten"
  - Slider flex
  - Numeric readout in `Saffron` ink at right — fixed width 48dp — shows degrees to one decimal (e.g. "+2.3°")

**Bottom safe-area:** just `CameraBg` fill, no bottom bar. Progression happens via the `Next` button in the top-right, matching Adobe Scan's "Save PDF" position.

### 7.3.0.a Tool switching animation

When the user taps a different tool tab (e.g. from Crop to Filter):
1. Current tool content fades out over 120ms (`EaseInCubic`)
2. Image viewport height animates to accommodate the new tool's content height (220ms `EaseInOutCubic`)
3. New tool content fades in over 180ms starting at t=100ms
4. Tool tab selection pill (`SaffronSoft` capsule) slides horizontally to the new tab over 220ms `EaseOutCubic`

The image itself does NOT re-render during the transition — the crop quad and any applied edits remain visually stable. Only the tool content region changes.

### 7.3.1 Camera → Edit shared-element transition

**Goal:** The user watches the image reposition, not the screen change. Zero perceived navigation.

**Sequence (250ms total):**
1. **Shutter tap (t=0ms):** Camera preview freezes on the current frame. Guide box overlay fades out over 100ms.
2. **t=100ms:** Camera top bar + bottom control bar (shutter, mode toggle, gallery entry) crossfade out. Simultaneously, Edit screen composables mount off-screen with:
   - Top bar and tool tabs pre-rendered but positioned above the top edge (translated -56dp for top bar, -56dp for tabs from their target position)
   - Tool panel pre-rendered below the bottom edge (translated +180dp from target)
   - `NpicCropOverlay` alpha=0 (invisible)
3. **t=100ms to t=250ms:** The frozen preview image scales and translates to the Edit viewport target rect (letterboxed 20dp inset, ~55% screen height). Uses `EaseInOutCubic` on both scale and offset.
4. **Simultaneously (same 150ms window):** Edit chrome slides in — top bar drops down, tabs drop down, tool panel rises up. All eased with `EaseOutCubic`.
5. **t=250ms:** `NpicCropOverlay` crossfades in over 100ms with the initial crop quad already positioned (guide-box bounds for captures, full-image bounds for imports; auto-detection removed per m2154). Rule-of-thirds grid is hidden until first drag.

**Implementation approach:**
- Compose Navigation with shared-element transitions (Compose 1.7+) OR a custom `AnimatedContent` wrapping both screens with a `SharedTransitionScope`. Fall back to manual `Modifier.animateBounds` if shared-element proves unstable.
- The image is a single `ImageBitmap` passed via ViewModel to avoid re-decoding.
- Background remains `CameraBg` throughout — this is what makes the illusion work. If the background flashed to Ivory at any point, the trick collapses.

**Fallback:** On devices where the shared-element runtime fails (rare), we degrade to a 220ms crossfade of both screens, with background pre-transitioned to `CameraBg` on both sides. Continuity feel is preserved even without the shared element.

### 7.3.2 Edit → Signature Camera / Draw transition

Same principle in reverse:
- `Next` tap → Signature Prompt sheet rises (already dark chrome, so no background change)
- Selecting `Capture` → Signature Camera screen slides in from right, but the top bar `CameraBg` continuity means the transition reads as chrome-only
- Selecting `Draw` → Draw Signature screen slides in from right, top bar stays `CameraBg`, canvas area is white (see §7.9)

### 7.3.3 Re-editing from Gallery (state)

When the Edit screen is opened from Gallery (Detail screen → tap photo or signature card), there's no shared-element from Camera. Instead:
- Gallery is on Ivory chrome; Detail is on Ivory
- Tapping the photo/signature card triggers a screen slide with an intentional chrome color animation — background animates from Ivory to `CameraBg` over 220ms via `animateColorAsState`, so the user sees "entering edit mode" as a mode change
- The card's photo also does a shared-element into the Edit viewport for smoothness

### 7.4 Save Dialog (Bottom Sheet)

- Handle
- Title: "Save student" — `titleLarge` Fraunces, 4dp bottom gap
- Subtitle: "Class selection is required" — `bodySmall` InkMuted
- 24dp gap
- **Class row:** label "Class" `labelMedium`, NpicSegmentedControl below with `9 | 10 | 11 | 12`
- 20dp gap
- **Naming mode row:** label "Save by" `labelMedium`, NpicSegmentedControl `Serial | Name`
- 16dp gap
- **Value input:** NpicTextField, contextual label ("Serial number" or "Student name"), helper text shows generated filename in real time in InkFaint (`labelSmall`), e.g., "Will be saved as: 090001"
- 24dp gap
- **Preview row:** two thumbnails side by side, 80dp tall each, 12dp gap. Photo (left) + Signature (right, shows dashed BorderStrong placeholder + "No signature" if not captured)
- 32dp gap
- Buttons row: Cancel (Ghost, left) + Save (Primary, right, flex)

### 7.5 Duplicate Sheet (Modal Bottom Sheet, N-way preview)

**Purpose (m2502):** Show every existing record that shares this class+serial (or class+name) alongside the incoming capture, and let the user Keep all / Replace one / Keep existing. The sheet handles both the 2-way case (one existing + incoming) and the N-way case (multiple prior Keep-all siblings) with one layout.

- Handle
- Title `titleLarge` Fraunces: `"Duplicate found"` when one existing record, `"N duplicates found"` when more than one. No warning icon in the header — the sheet's presence IS the warning; a Terracotta icon here would misfire in the neutral "Keep all is legitimate" flow.
- Body `bodyMedium` `InkMuted`: `"Keep all, replace one, or drop the new capture?"`
- 20dp gap
- **DuplicateCardRow** — horizontal `Row` inside `LazyRow`-style scroll (`horizontalScroll(rememberScrollState())` with 12dp inter-card gap and 20dp start/end padding). Contents in order:
  1. All existing records (in `duplicateIndex` ascending order — index 0, then (2), (3), …), each rendered as an **ExistingDuplicateCard**
  2. Exactly one **IncomingDuplicateCard** at the end
- **Right-edge fade:** When `scrollState.canScrollForward`, draw a 24dp horizontal gradient (Transparent → Surface, left-to-right across the rightmost 24dp band) over the row via `drawWithContent`. Fade disappears once the last card is fully on-screen. This is the Adobe Scan filter-strip affordance — signals "more to the right" without stealing a full arrow icon.

**DuplicatePreviewCard** (shared shell for both variants):
- Fixed width **140dp**, height wraps content
- Corner `NpicShapes.md` (14dp)
- Fill `Surface`
- Border: 1dp `BorderSoft` unselected; **3dp `Saffron`** selected
- Padding: 12dp internal
- Stack (top to bottom, 8dp gap between blocks):
  - **Photo well** — 96dp square, corner `sm` (10dp), fill `SaffronSoft @ 35%` placeholder + "No photo" `labelSmall InkFaint` if `photoPath` blank; else Coil `AsyncImage(File)` with `ContentScale.Crop`
  - **Signature strip** — 32dp tall, corner `sm` (10dp), fill `SaffronSoft @ 20%` placeholder + "No signature" if `signaturePath` blank; else `AsyncImage(File)` with `ContentScale.Fit`
  - **Title** — `labelMedium` weight 700 `Ink`, single line ellipsize
  - **Subtitle** — `bodySmall InkMuted`, single line ellipsize

**ExistingDuplicateCard:**
- Title: `record.displaySerialLabel` — `"090001"` for the original, `"090001_2"` / `"090001_3"` for successive Keep-both siblings. This keeps the sheet's card label lockstep with the Gallery, Detail, and export filename.
- Subtitle: `record.displayName.ifBlank { "Class ${classNum.label}" }`
- Selection: `selectable(selected, role = Role.RadioButton, onClick = { onSelect(id) })` inside a `selectableGroup()` — tapping the currently-selected card toggles selection back to null.

**IncomingDuplicateCard:**
- Title: `"New (just captured)"`
- Subtitle: `displayName.ifBlank { "Class ${classNum.label}" }`
- Border: always 3dp `Saffron` (visually highlighted so the user sees which card is new)
- Interaction: **not a Replace target** — `role = null`, `onClick = null`, but wrapped in `.focusable()` and given an explicit `semantics { contentDescription = "New capture. Class N. …. Not selectable." }` so TalkBack and D-pad users perceive it and cannot get stuck trying to activate it.

**Selection state:**
- `selectedExistingId: String?` = null by default, backed by `rememberSaveable` so rotation preserves the user's choice mid-decision.
- Only ExistingDuplicateCards are selectable. The IncomingDuplicateCard is never a selection target.

- 24dp gap
- **Buttons row** (12dp horizontal gap between siblings):
  - **Ghost** `"Keep existing"` (left) — dismisses the sheet without saving the new capture. Routes through `dismissDuplicateKeepingExisting` so draft assets are cleaned and `completedRecordId` is set to `existing.first().id`; swipe-down uses the same handler.
  - **Destructive** `"Replace"` (middle) — enabled only when `selectedExistingId != null`; deletes the selected existing record and inserts the incoming capture at that record's `duplicateIndex` slot.
  - **Primary Saffron** `"Keep all"` (right, `weight = 1f` — takes remaining width to earn visual priority) — inserts the incoming capture as a NEW row with the next-available `duplicateIndex` for this (classNum, serial) or (classNum, nameKey) group. Does not move the monotonic class counter. Label is "Keep all" (renamed from "Keep both" in m2506) because the sheet supports N-way duplicates (existing[0..k] + incoming); "both" would be a lie when there are 3+ cards. Internal identifiers (`resolveDuplicateKeepingAll`, `onKeepAll`, `saveAsDuplicate`, `pendingAction == "keepAll"`) mirror this label — renamed in m2507 for full code↔UX symmetry.

**Filename semantics (see DAO invariant + m2503 H5):** Existing sibling filenames stay clean (`090001.jpeg`) when re-exported solo; only WITHIN a single batch export do collisions get an `_N` suffix (`090001.jpeg`, `090001_2.jpeg`, `090001_3.jpeg`, …). Underscore matches the Name-mode pattern (`Rahul_Kumar_09.jpeg`) and passes UPMSP's filename parser — parentheses and spaces would risk rejection.

### 7.6 Gallery

- Top bar (large 96dp variant): Fraunces `displayMedium` "Gallery" left-aligned, search & sort icon buttons right
- Below: horizontal scrollable NpicChip row for class filters, 12dp side padding, 8dp inter-chip gap. "All" chip first, then per-class with counts
- Grid: 3 columns on phones (< 600dp width), 4 columns on tablets. 12dp gutters. NpicThumbnail cells.
- Sticky section headers when sorted by date: `titleSmall` InkMuted, 16dp horizontal padding, 40dp tall, Ivory background
- Selection mode: top bar swaps to `titleLarge` "N selected" + Cancel (left) and Select all (right, ghost). Bottom action bar rises (Level 2 shadow) with Export (Primary) + Delete (Destructive) buttons at 50% width each

### 7.7 Detail Screen

- Top bar: back, title = student name/serial in Fraunces `headlineMedium`, overflow menu (Edit, Delete, Duplicate to another class)
- Content scrollview:
  - Metadata card: 2×2 grid of `labelMedium` key + `bodyMedium` value pairs (Class, ID, Captured, Size)
  - 20dp gap
  - Photo card: NpicCard containing 8:10 aspect image (respects passport ratio), tap to open Edit
  - 16dp gap
  - Signature card: NpicCard containing 3:1 aspect signature image, or empty state (dashed border + "Add signature" CTA with two ghost buttons "Capture" / "Draw")
- Bottom bar: Export (Primary, full width)

### 7.8 Export Format Sheet (Bottom Sheet)

Reached from Detail screen bottom bar or Gallery selection-mode Export action.

- NpicBottomSheet chrome
- Handle
- Title: "Export" — `titleLarge` Fraunces, 4dp bottom gap
- Subtitle: shows count of items being exported — "1 student" or "12 students" — `bodySmall` InkMuted
- 24dp gap
- **Three format cards stacked vertically, equal size, radio-selectable:**
  - Each card: NpicCard variant, 72dp tall, 16dp internal padding, 12dp inter-card gap
  - Left: 40dp saffron-tinted icon container (`SaffronSoft` fill, `NpicShapes.md` corner) with 24dp icon
  - Middle: two-line stack — title (`titleMedium` Ink) + subtitle (`bodySmall` InkMuted)
  - Right: NpicRadio (24dp Saffron radio, filled when selected; 2dp Saffron ring on card when selected)
  - Cards:
    1. **Combined (photo + signature)** — icon: combined layout glyph, subtitle: "Portal-ready · matches UPMSP template" — **pre-selected by default**
    2. **Photo only** — icon: single photo glyph, subtitle: "Passport photo without signature"
    3. **Signature only** — icon: signature glyph, subtitle: "Handwritten signature without photo"
- 20dp gap
- **Missing-signature warning** (visible only if applicable): NpicCard with `TerracottaSoft` fill, warning icon 20dp Terracotta, `bodySmall` Ink — "N items don't have a signature. They'll be skipped." with a "Show list" text button that expands to a scrollable list of affected student names
- 24dp gap
- Buttons row: Cancel (Ghost, left) + Export (Primary, right, flex, label "Export N items")

**Behavior:**
- Selection state persists per-session (if user always picks Photo only, remember for next time within the app session; forget on cold start).
- On Export tap: sheet dismisses, a loading NpicToast appears ("Preparing N files..."), then Android Share Sheet opens with the ZIP or single file.

### 7.9 Draw Signature

- Top bar: Close (X) left, "Draw your signature" center (Fraunces), Done (Primary text, saffron ink) right
- Canvas: fills middle, pure white (#FFFFFF), 3:1 aspect ratio letterboxed with Ivory around
- Baseline hint: 1dp `BorderSoft` horizontal line at 75% height of canvas — dashed, "Sign above the line" `labelMedium` InkFaint below
- Bottom toolbar (72dp, Surface, top hairline BorderSoft):
  - Undo (icon button, disabled when no history)
  - Redo (icon button, disabled when no redo history)
  - Thickness slider (flex, 2px-12px, live preview dot on left)
  - Clear (icon button, terracotta tint, tap opens confirm dialog)

### 7.10 Update Sheet (Modal Bottom Sheet, m2508)

**Purpose:** The app self-updates from a GitHub-hosted `version.json` manifest (see `docs/RELEASE.md`). This sheet is the only user-facing surface for the entire update flow — check-in, download progress, verify, permission grant, install, failure retry.

**Trigger:** `MainActivity` fires `UpdateViewModel.checkForUpdates()` once per Activity composition via `LaunchedEffect(Unit)`. The sheet mounts whenever `UpdateUiState` is anything other than `Idle`, `Checking`, or `UpToDate`.

**Placement:** Sits at the top of the composition tree, OUTSIDE the `NavHost`, so a pending update surfaces regardless of the active route. Never renders on top of Camera / Edit dark chrome — those flows own the screen while active; the check runs during Gallery time.

**Chrome:** Standard `NpicBottomSheet` — `SurfaceRaised` fill, `NpicShapes.xl` top corners, 40×4dp `BorderStrong` handle, 60% black `Overlay` scrim. NO custom scrim override (unlike Export m2501).

**Header row (56dp visual):**
- 44dp `SaffronSoft` squircle (`NpicShapes.sm`) containing a 24dp `SaffronDeep` download-arrow icon
- 12dp gap
- Column:
  - Title `headlineMedium` Fraunces, `Ink`:
    - Normal state → `"Update available"`
    - Failed state → `"Update failed"` (single copy swap; no Terracotta ink in the title itself — the reason line below carries the error color)
  - Subtitle `bodyMedium` Inter, `InkMuted`: `"v{running} → v{remote}"` (e.g. `v0.1.0 → v0.2.0`)

**"What's new" section** (visible only when `manifest.changelog` is non-blank AND state is not `Failed`):
- 1dp `BorderSoft` hairline above
- Section heading `titleSmall` Fraunces, `Ink`: `"What's new"`
- 8dp gap
- Changelog body `bodyMedium` Inter, `Ink`, raw text (server owns the bullet formatting via `\u2022 …\n\u2022 …` in `version.json`)

**Status line** (always visible):
- 1dp `BorderSoft` hairline above
- 12dp gap
- `labelMedium` Inter, `InkFaint`
- Content by state:
  - `Available` → `"{X.X} MB · Released YYYY-MM-DD"` (date omitted if manifest is missing it)
  - `Downloading` → `"{downloaded MB} of {total MB}"`
  - `Verifying` → `"{X.X} MB · Verifying"`
  - `ReadyToInstall` → `"{X.X} MB · Ready to install"`
  - `NeedsInstallPermission` → `"One-time permission required"`
  - `Installing` → `"{X.X} MB · Installing"`
  - `Failed` → `"{X.X} MB"` (reason line renders separately in Terracotta below)

**Progress region** (visible only in `Downloading` / `Verifying` / `Installing`):
- `Downloading`: full-width 6dp `LinearProgressIndicator`, `Saffron` fill on `BorderSoft` track, `NpicShapes.full` corner
- `Verifying` / `Installing`: 18dp `Saffron` `CircularProgressIndicator` (2dp stroke) + `bodyMedium` `InkMuted` label ("Verifying download…" / "Installing…")

**Failure reason line** (visible only in `Failed`):
- `bodyMedium` Inter, `Terracotta`
- Content mapped from `UpdateDownloader.Reason` + PackageInstaller status codes:
  - `CHECKSUM` → `"The downloaded file was corrupted. Try again."`
  - `NETWORK` → `"Download failed. Check your connection and try again."`
  - `CANCELLED` → `"Download was cancelled."`
  - Samsung `STATUS_FAILURE_BLOCKED` → `"Samsung Auto Blocker is preventing this update. Turn it off in Settings → Security and privacy → Auto Blocker, then try again."`
  - Other install failures → `"Install failed. {system message}"`

**Action row (bottom):**
- Horizontal `Arrangement.spacedBy(NpicSpacing.sm)`
- Left: `NpicButton` Ghost `"Later"` — hidden when `blocking = true` OR state is `Installing`; disabled during `Downloading` / `Verifying`
- Right: single Primary button (`weight = 1f` — expands to fill), label + behavior by state:
  - `Available` → `"Update now"` (SaffronBright→Saffron gradient, active)
  - `Downloading` / `Verifying` → `"Downloading…"` with saffron progress ring (`loading = true`), disabled
  - `ReadyToInstall` → `"Install"` (active)
  - `NeedsInstallPermission` → `"Allow installs"` — tapping launches `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package; viewModel transitions state back to `ReadyToInstall` optimistically so returning to the app auto-retries
  - `Installing` → `"Installing…"` with progress ring, disabled
  - `Failed` (recoverable) → `"Try again"` (retries download or resurfaces the install if APK is already staged)

**Blocking mode (`forceUpdate = true` OR running versionCode < `minSupportedVersion`):**
- "Later" ghost button is removed
- `onDismiss` on the sheet is a no-op — swipe-down and back-gesture ignored
- The user MUST update; only exit path is uninstall

**Motion:**
- Sheet enter/exit uses M3 `ModalBottomSheet` default spring
- Progress bar updates in place — no cross-fade between button and bar (would flicker with fast connections)
- Status-line text updates via natural Compose recomposition; no explicit animation

**Accessibility:**
- All state transitions announce via the natural `Text` recomposition (TalkBack picks up the new content automatically)
- Progress bar is not marked with `Modifier.progressSemantics(fraction)` because the numeric status line above already voices the same info more accurately (`"5.2 MB of 43.6 MB"` beats `"12 percent"`)
- Failure reason is a plain `Text` — no `LiveRegionMode` because the visible state change alone is enough context; `LiveRegionMode.Assertive` on transient error text was rejected as too shouty for the check-on-launch pattern

**Anti-patterns explicitly rejected:**
- No full-screen "Update required" takeover — the sheet is dismissable by default; only `blocking = true` locks it, and that's a rare server-side flag
- No progress notification in the system tray — DownloadManager already shows one, adding a second is noise
- No "Delta / incremental update" language — we always ship full APKs (delta patches would double the ship complexity for a ~45 MB app that updates monthly)
- No "Restart to apply" step — the OS handles that; PackageInstaller kills the running process during install and the user opens the new APK themselves

---

## 8. Iconography

- Icon set: Custom minimal-line 24dp icons drawn on a 24×24 grid, 1.75dp stroke, rounded caps. NOT Material Icons — those feel too system-Android. Icons include:
  - `capture`, `flash-on`, `flash-off`, `flash-auto`, `gallery`, `flip-camera`
  - `crop`, `filter`, `adjust`, `rotate-left`, `rotate-right`, `straighten`
  - `signature-draw`, `signature-camera`
  - `undo`, `redo`, `clear`
  - `sort`, `filter-list`, `search`, `select`, `close`, `back`, `more`
  - `share`, `download`, `delete`, `duplicate`, `edit`
  - `check`, `warning`, `info`, `success`

All icons live in `core/ui/icons/` as `ImageVector` values. No PNGs.

---

## 9. Accessibility

- All interactive elements have `contentDescription` set. Icon-only buttons have descriptive labels.
- Minimum tap target 44×44dp (48dp preferred).
- Text scales with system font scale up to 130% without breaking layouts. Above 130%, top bar titles switch to `headlineSmall` and long labels ellipsize with `...`.
- Color is never the only signal: selected chips also change fill+border weight; error inputs get an error icon; destructive buttons have distinct terracotta plus a warning icon.
- All contrast pairs ≥ WCAG AA (validated above).
- TalkBack focus order matches visual order top-to-bottom, left-to-right.
- Camera screen provides shutter as a large focused element; capture completion announces "Photo captured, opening editor" via a live region. (Prior "Detected photo edges" announcement removed with auto-detection per m2154.)

---

## 10. Empty States

Each empty state has a bespoke saffron-toned line illustration + copy:

| State | Copy |
|---|---|
| Gallery empty | Title "Nothing captured yet" / Body "Tap Start Capturing to add your first student." / CTA "Start Capturing" |
| Filter returns 0 | Title "No students in Class {X}" / Body "Try another class or capture new photos." / no CTA |
| Search returns 0 | Title "No matches" / Body "Try a different name or serial." |
| Detail — signature missing | Title "No signature yet" / Body "Add one to complete this student's record." / CTAs "Capture" and "Draw" |

---

## 11. Design-System Enforcement Rules

These are enforced via code review, not comments. If you find any of these in the codebase, they're a bug:

1. No hex color literals in feature code. Colors come from `NpicColors`.
2. No hardcoded font families in feature code. Text uses `MaterialTheme.typography.<x>`.
3. No hardcoded corner radii — use `NpicShapes`.
4. No spacing values outside the 4dp grid tokens.
5. No shadow definitions outside the 4 elevation levels.
6. Every dialog/sheet/toast/button used in a feature must be an existing component in `core/ui/`.
7. Feature files import from `core/ui/` and `core/theme/` only — never from other features.
8. Colors are keyed by semantic name (Saffron, not "orange"). Changing the theme must NOT require touching feature code.
9. **In the capture flow (Camera, Edit, Signature Camera, Signature Draw), tool content NEVER lives inside a `CameraSurface` panel wrapper.** Tool content renders directly on `CameraBg` at the same visual level as the image. The image and its tools share one continuous dark canvas. Only three things get `CameraSurface` fill: (a) the tool tabs row itself, (b) the Signature Prompt sheet, (c) the retake/discard confirmation dialogs. Everything else in the capture flow — filter thumbnails, adjust sliders, rotate buttons, crop tools — sits on `CameraBg` with no wrapper. This is the rule that produces the Adobe Scan "we never left the camera" feeling.
10. In the capture flow, screen backgrounds are always `CameraBg`. Any color transition to Ivory happens only when the user exits to Gallery, Detail, or Save (post-signature). Never mid-flow.

---

## 12. Reference Inspirations (documented for accountability)

I anchored specific decisions to real references so we can debate them concretely rather than in vague adjectives:

- **Warm ivory background + editorial serif headings:** Substack app + NYT Cooking iOS app
- **Saffron primary with dark ink text:** Retuned Airbnb 2014 palette (their "Rausch" pink → our Saffron), but warmer
- **Filter strip pattern:** Adobe Scan iOS filters row + Halide's grade panel
- **Crop handles + pinch-zoom:** Adobe Scan crop screen + iOS Photos crop tool (4 corners only, whole-box drag, pinch-to-zoom the source)
- **Wide guide box for signature:** iOS Mail's inline signature drawing sheet
- **Gallery grid + long-press multi-select action bar:** Samsung Gallery One UI 6 + Google Photos
- **Sheet-first save/duplicate flow (not center dialog):** Notion iOS "Move to" sheet + Linear's action sheets
- **Big single-primary CTAs on Home:** Cash App home + Robinhood pre-login
- **Combined-export template:** UPMSP portal's own `PhotoWithSign.png` sample (216×270, 4:5 portrait, photo top 75% + signature bottom 25%, thin dark borders on each). This is not a design choice — it's a hard constraint. Our combined export must visually match the portal template so admin review passes at a glance.

**None of these apps individually looks like ours.** The synthesis is: Adobe Scan's utility + Substack's warmth + Samsung Gallery's power-user gallery + a saffron-inflected palette that reads distinctly Indian-school-appropriate, with export outputs that mechanically match the UPMSP portal's expected template.
