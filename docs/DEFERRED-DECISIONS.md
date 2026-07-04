# Deferred Decisions Log

This doc has **two sections** per the m1319 overnight brief:

- **Section A** — decisions I took autonomously from the prior "deferred" list so
  you can review them and reverse any you disagree with.
- **Section B** — items still needing your explicit decision, with my
  recommendation and a rationale for each.

Anchored against `PRD.md` and `DESIGN.md` at the repo root.

---

## Section A — Auto-implemented decisions (review these)

Every item here was on the deferred list. I picked a direction, shipped it,
and logged the choice so you can course-correct if my read of intent was wrong.

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

### A4. Bug#10 signature thickness applied *retroactively* to committed strokes

**Deferred as:** "PRD §4.4 v1.0 says thickness is per-stroke; user brief bug
#10 says slider should re-render existing strokes."

**Choice I took:** user brief wins. `SignatureCanvas` reads `liveWidthPx`
from `state.thicknessPx` for both committed and in-flight strokes. Each
`DrawStroke.widthPx` field is kept for `SignatureRasterizer` compatibility
but is ignored by the live canvas render.

**Impact:** thickness slider now behaves like a global "signature weight"
setting. Rasterizer preserves whatever the last committed width was at
`onDone`, which may not match the original stroke intent if the user swept
the slider between strokes.

**Reverse if:** you want v1.0 fidelity — per-stroke width preserved through
the whole edit. Would require adding a mode toggle in the SignatureDraw UI
("apply to all strokes" vs "next stroke only").

### A5. Bug#3 Edit tabs live-preview render is preview-only, not source-of-truth

**Deferred as:** "How do Filter/Adjust/Rotate tabs show a rendered preview
without re-running the full pipeline on the 2-4MB source bitmap?"

**Choice I took:** `EditViewModel.scheduleLivePreview()` runs `EditRenderer`
on the **384px preview downsample**, not the source. Cancels prior job on
each mutation. Publishes to `livePreviewBitmap`. The full-res render only
happens at `commitEdits()` time.

**Impact:** tab feedback is instant. There's a ~250ms window on very slow
devices where the last live preview may lag the state; the committed render
is always correct.

**Reverse if:** you want the tab preview to always match the final pixels.
Would require rendering at full res with a longer debounce and a spinner
during in-flight renders.

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

**Deferred as:** "Class filter chip UX rework — how to visualize count vs
class label?"

**Choice I took:** two-line tile with count in `titleLarge` bold (dominant)
and class label in `labelSmall` (quiet). Selection surface = SaffronSoft +
1.5dp Saffron border + SaffronDeep title color.

**Impact:** scannable at a glance ("Class 12 has 34 students, Class 9 has
0") — count is what teachers look at first, class label is context.

**Reverse if:** you want the class label dominant (matches typical filter
chip UI). Swap the typography — count small, label large.

### A9. Bug#14 sort icon changed to `Icons.AutoMirrored.Outlined.Sort`

**Deferred as:** "Sort icon needs to feel intuitive."

**Choice I took:** `AutoMirrored.Outlined.Sort` (horizontal lines with
descending length) instead of `SwapVert`. Auto-mirrored variant respects RTL
locales.

**Impact:** matches Material-3 Sort convention. If you preferred SwapVert's
"two directions" hint, this reads as "sort" instead.

**Reverse if:** you want more explicit direction indication.

### A10. Bug#15 Search filter runs client-side over `observeAll()`

**Deferred as:** "Search — DAO LIKE query or client-side filter?"

**Choice I took:** client-side. `SearchViewModel` combines a
`debounce(120ms)` query flow with `repository.observeAll()` and filters via
`filterRecords()` on `Default`. Ranking priority: name-prefix → name-substr
→ class-exact → class-substr → serial-exact → serial-prefix.

**Impact:** simpler ownership (single Flow join), works with the
sub-thousand-record bound expected for a single teacher's roster. No
per-keystroke DB query.

**Reverse if:** the roster grows into the tens of thousands. Would require
`StudentDao.searchByPrefix(query: String): Flow<List<StudentEntity>>` with
SQLite FTS4 indexed on `displayName` + `serial`.

### A11. Detail edit-existing route via `clear()` + `pushCapture()` funnels
through DuplicateSheet.replace-existing

**Deferred as:** "Edit Photo / Edit Signature from Detail — replace in place
or route through duplicate dialog?"

**Choice I took:** replace in place via the duplicate-dialog branch. The
existing record's assets are overwritten, and the duplicate dialog surfaces
so the user can compare old vs new before confirming.

**Impact:** consistent with the capture flow (any save that hits an existing
`(class, serial)` shows the dialog). No accidental silent overwrites.

**Reverse if:** you want Edit-from-Detail to be a silent update without any
dialog. Would require a "skipDuplicateCheck" flag on the SaveSheet path.

### A12. Detail import launcher uses `PickVisualMedia(ImageOnly)`, not full SAF

**Deferred as:** "Import Photo/Signature from Detail — photo picker or
generic file picker?"

**Choice I took:** `ActivityResultContracts.PickVisualMedia` with
`ImageOnly` type. Copies Uri to `cache/drafts/{uuid}.jpg` on Dispatchers.IO
via `copyUriToDraftsCache`.

**Impact:** modern Android photo picker (Android 13+ native picker, older
SDKs fall back to SAF). No permission needed. Image-only filter matches
intent.

**Reverse if:** you want to accept PDF or other formats too.

### A13. `A14 Duplicate-to-another-class` is a Toast stub

**Deferred as:** "Detail overflow → duplicate to another class."

**Choice I took:** Toast "coming soon". PRD §4.9 doesn't require it; DESIGN
mentions it once as a convenience action. Not blocking.

**Impact:** menu item is present so the interaction discoverability is
there; tapping just shows a message.

**Reverse if:** you want a real implementation — spec exactly what the
duplicate should do (fresh id? same id under new class? re-run Save
duplicate check?).

### A14. Gallery Delete-all + Overflow Settings both route through confirm
dialogs / Toast stubs

**Deferred as:** "Overflow menu — full spec or minimum viable?"

**Choice I took:** Overflow menu shows three rows (Select all, Delete all,
Settings). Select all works. Delete all routes through the same confirm
dialog as multi-select delete. Settings toasts "coming soon".

**Impact:** destructive actions always require a confirm. Settings entry
point is discoverable but non-functional.

**Reverse if:** you want to hide the Settings row entirely until it lands.

### A15. Bug#11 Gallery ZeroState is a hand-drawn Canvas emblem (no Lottie)

**Deferred as:** "Beautiful empty state — vector illustration or
placeholder icon?"

**Choice I took:** hand-drawn Canvas emblem — three rounded cards fanned
±8° with a Saffron accent dot signaling 'signature captured'. Zero external
assets. Layered typography + two hint tiles + primary CTA.

**Impact:** brand-consistent (Saffron accent, SaffronSoft tints, Fraunces
headings), zero binary weight, easy to iterate.

**Reverse if:** you want a proper illustrator-drawn asset. Would require
importing an SVG or Lottie file — I didn't touch app/build.gradle deps.

### A16. Layer 11 preview→image-space uses FILL_CENTER inverse against decoded
JPEG bounds

**Deferred as:** "PreviewView.getOutputTransform() vs manual FILL_CENTER
math."

**Choice I took:** manual FILL_CENTER inverse. Rationale: OutputTransform
API is fragile across CameraX versions and coupled to the PreviewView
lifecycle. Manual math is 12 lines, deterministic, easy to unit-test.

**Impact:** guide-box → image-space projection is now accurate. Detection
seed is finally usable instead of the pre-Layer-11 identity stub.

**Reverse if:** OutputTransform lands as stable API in a future CameraX
release and you want to align with framework surface. Would replace
`PreviewGuide.toImageSpace()` internals.

### A17. Layer 13 `DeviceTilt` α=0.15 low-pass filter constant

**Deferred as:** "Accelerometer smoothing — how aggressive?"

**Choice I took:** α=0.15 (15% raw + 85% previous). Balances jitter
suppression (device sitting in hand at 60Hz has ±1° noise) against lag when
the user actively levels the phone.

**Impact:** Sage snap-to-level bracket lights up smoothly when the user is
within ±2° of horizontal. Doesn't twitch when hand-held.

**Reverse if:** you want more responsive tilt (α higher, e.g. 0.3) or more
stable (α lower, e.g. 0.05).

---

## Section B — Still needing your decision

I did **not** implement these. Each one has a real choice space that I
shouldn't autonomously make without you seeing it first.

### B1. Crop-handle magnifier bubble on Edit drag

**What DESIGN says:** DESIGN mentions an Adobe-Scan-style 88dp circular
bubble above the actively-dragged crop handle showing a 2× zoom of the
source pixels around the corner.

**Why I stopped:** requires plumbing the source `Bitmap` from EditViewModel
→ EditScreen ImageViewport → NpicCropOverlay signature. That's a ripple
touching 3 files and changing the overlay's public API from
`(quad, aspectLock)` to `(quad, aspectLock, sourceBitmap?)`. Non-blocking
polish that isn't in PRD §13 acceptance list.

**Options:**
1. **Full 2× zoom magnifier** — matches DESIGN spec, best UX. ~120 LOC ripple.
2. **Numeric bubble ("324, 892 px")** — half the work, less useful UX.
3. **Skip for v1.0** — mark as post-launch polish.

**My recommendation:** #3 for v1.0 ship, then #1 in a follow-up polish
sprint. The crop tool already works precisely; the magnifier is a delight
feature, not a correctness feature.

### B2. Localization + Devanagari font fallback

**What PRD says:** English-only for v1.0.

**Current state:** ~150 hardcoded English strings across every screen. No
`strings.xml`. No Devanagari font loaded — student names entered in Hindi
would render as tofu boxes.

**Options:**
1. **Extract to strings.xml + wire `hi-IN` locale + add Noto Sans Devanagari
   font** — full localization. 3-5 days of work.
2. **Extract to strings.xml + English-only for v1.0, Hindi later** — sets up
   the scaffolding but ships English-only. 1 day of work.
3. **Skip entirely for v1.0** — every string stays inline.

**My recommendation:** #2. Even if you're not shipping Hindi in v1.0, having
`R.string.*` referenced from code prevents "we'll fix it later" from
becoming "we can't fix it without rewriting every screen". Also unblocks
easier copy iteration.

### B3. Auto-capture

**What PRD says:** PRD §12 open questions — deferred to v1.1+.

**Current state:** manual shutter only.

**Options:**
1. **Ship v1.0 without it** — matches PRD.
2. **Add now** — needs edge-detection convergence heuristic + 500ms hold
   timer + haptic feedback.

**My recommendation:** #1. It's already deferred; keeping v1.0 scope tight
matters more than shipping a bonus feature.

### B4. Room DB migration flow beyond fresh install

**What PRD says:** silent on migrations.

**Current state:** version=1 schema. Any change breaks existing installs
(destructive migration or manual export/reimport).

**Options:**
1. **Freeze schema at v1.0** — bump version + write real migrations for
   every future change.
2. **Allow destructive migrations for now** — user re-captures if we ship a
   schema change pre-1.0.
3. **Ship v1.0 with an export button** — user backs up JSON, we restore on
   fresh install.

**My recommendation:** #1 once you tag v1.0. Between now and tag, #2 is
fine (school hasn't distributed the app yet).

### B5. Signature thickness: retroactive vs per-stroke

**What A4 says:** I chose retroactive per your bug #10.

**Why this needs your call:** PRD §4.4 v1.0 says per-stroke. Your bug report
says retroactive. They're contradictory. I picked user brief because it was
newer + explicit, but this is a scope decision, not a bug.

**Options:**
1. **Keep retroactive** (current) — thickness is a global "signature
   weight."
2. **Revert to per-stroke** (PRD §4.4) — each stroke keeps the width it was
   drawn at.
3. **Mode toggle** — user picks per-signature which behavior applies.

**My recommendation:** keep #1 (retroactive). Simpler mental model, matches
your bug report.

### B6. ZIP file naming for multi-share

**What PRD says:** `export_{timestamp}.zip`.

**Current state:** `cacheDir/exports/export_{ts}.zip` where `ts` is
`System.currentTimeMillis()`.

**Options:**
1. **Keep epoch ms** (current) — sortable, ugly.
2. **Human-readable ISO date** — `export_2025-01-30T14-32-05.zip`.
3. **Include record count** — `export_2025-01-30_34-records.zip`.

**My recommendation:** #3. When teachers see the file in their portal
upload history, "34 records from Jan 30" is more useful than a bare
timestamp.

### B7. Detail Share menu action

**What PRD says:** PRD §4.9 mentions it. DESIGN doesn't spell out what it
shares.

**Current state:** the Share icon in Detail's top bar routes to the Export
flow (multi-share for single record). No dedicated "share JUST this photo"
shortcut.

**Options:**
1. **Route to Export flow with pre-selected record** (current) — user picks
   format.
2. **Share raw source directly** — skips compression, skips format picker,
   dumps the on-disk file into an ACTION_SEND. Faster path for "just DM this
   to the parent."
3. **Both, via a submenu** — Share as portal JPEG / Share raw source.

**My recommendation:** #1 for now. Adding raw-source share means education
about "the file you're about to share is 3MB not the 20KB portal blob," and
that risk isn't worth the shortcut.

### B8. WRITE_EXTERNAL_STORAGE runtime request

**What PRD says:** PRD §10 — declare only, no runtime request needed since
Android 6+ auto-grants for install-time permissions (SDK ≤28) that we now
declare via `maxSdkVersion="28"`.

**Current state:** declared in manifest with maxSdk 28. No runtime prompt
code path.

**Options:**
1. **Do nothing** (current) — SDK ≤28 auto-grants at install; ≥29 doesn't
   need it (scoped storage).
2. **Add runtime check** — safer for old devices that install through
   sideload after storage permission was manually revoked.

**My recommendation:** #1. The scenarios where a runtime re-request is
useful are vanishingly rare and adding permission UI adds a scary prompt on
first launch.

### B9. Draft resume prompt: single-active vs multi-active

**What PRD says:** PRD §8.3 — resume-prompt AlertDialog on Gallery reload.

**Current state:** single-active. `DraftDao.observeActive()` returns
`ORDER BY updatedAt DESC LIMIT 1`. If the user has two half-finished
captures, only the newest is offered on resume.

**Options:**
1. **Single-active** (current) — simple, matches PRD wording.
2. **Multi-active list** — Gallery shows a "drafts" section above records.

**My recommendation:** #1 for v1.0. Multi-active raises hard questions
about draft eviction and UI space.

### B10. Devanagari name normalization for `findByClassName` duplicate check

**What PRD says:** duplicate detection by name matches after "trim + lowercase
+ whitespace normalization."

**Current state:** `StudentDao.findByClassName` uses SQLite
`LOWER(TRIM(REPLACE(displayName, '  ', ' ')))` which handles ASCII correctly
but is a no-op for Devanagari case (no case in Devanagari script) and
doesn't normalize Nukta forms or ZWJ variants.

**Options:**
1. **Leave it** (current) — English-name schools work correctly; Hindi-name
   schools may get false negatives on duplicates ("राम" != "राम " with ZWJ).
2. **Normalize to NFKC in Kotlin before DB comparison** — cover Nukta/ZWJ.
3. **Case-insensitive compare via ICU collator** — heaviest, best fidelity.

**My recommendation:** #2 when you ship Hindi. Until then #1 is fine.

---

## How to extend this doc

When you review Section A and want to reverse a choice, delete or annotate
the entry with the new direction. When you resolve a Section B item, move
it to Section A with the choice you made. When new deferrals crop up during
future development, add them to Section B with your recommendation.

Each entry should keep the same shape: What was deferred, what I/we chose,
impact, reverse-conditions (Section A) or Options + Recommendation (Section
B). That keeps the doc scannable when it grows.
