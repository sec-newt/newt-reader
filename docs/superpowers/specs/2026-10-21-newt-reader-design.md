# Newt Reader — v1 Design

> The first face of Newt Computer: swipe up with two fingers on any
> screen and it's redrawn as large, reflowed, readable text in the Newt look — with the
> screen's links and buttons still working. Kills the zoom in → read → zoom out → tap loop.

Brainstormed with the owner 2026-10-21, right after the on-phone probe proved the screen-reading
engine works on his Pixel 7 (GrapheneOS, Android 17). 

## 🎯 Problem

the owner has low vision but doesn't use speech output. Reading on the phone means
pinch-zoom, read, zoom out to reach a button, zoom back in. He keeps system font size
normal because apps break when it's maxed. He already runs Google **Reading Mode**; what's
wrong with it, in his words (picked from a list):

- **It's read-only.** Links and buttons don't work, so he still bounces back to the zoomed app.
- **It doesn't work in the apps he needs** (Claude app, X, etc.).

## 🎯 Goals

1. **Interactive:** tapping a link or button in Reader does it in the real app.
2. **Everywhere text is shared:** web (Vanadium, Brave), Compose apps (Claude, Newt apps),
   WebView wrappers (Kagi) — anything that exposes an accessibility tree.
3. **Fast to summon** without leaving what he's doing.
4. **Beautiful and readable:** Newt house style, Atkinson Hyperlegible, its own text size.
5. **Private:** no internet, nothing stored, reads only when asked.

## 🚫 Not in v1

- **OCR** (video frames, text in images) → v2. Images show as a labelled placeholder.
- Typing inside Reader (text fields hand off to the app).
- Voice, per-app settings, per-app rules.

## ✅ Evidence it's feasible

From the emulator test and the on-phone probe (details in the Newt Computer idea note (owner's vault)):
- A real accessibility service gets the full tree in Vanadium, Brave→X, Kagi, the Claude
  app and Newt Weather — text plus labelled clickable nodes, including off-screen content
  flagged `visibleToUser=false`.
- Chromium splits text at every link and nests footnotes 3 deep; a stitching rule rebuilt
  clean paragraphs (see Stitcher below).
- Compose lazy lists and X's feed only hold near-viewport items → Reader must scroll the app.
- Kagi's WebView is empty until loaded → Reader must wait for content to settle.
- adb-installed builds skip the "restricted settings" gate; Obtainium installs will hit it
  once (App info → ⋮ → Allow restricted settings).

## 🏗️ Architecture

New repo **`sec-newt/newt-reader`**, made from `newt-android-template` (family look via
newt-ui, shared CI `newt-app.yml`, signed releases via Obtainium). Two modules:

```
newt-reader/
  engine/   no UI: read screens, perform actions — reusable by voice commands later
  app/      Reader: service, overlay, tile, floating button, settings
```

**Approach chosen:** Reader draws as an **accessibility overlay** owned by the service
(`TYPE_ACCESSIBILITY_OVERLAY`), not as a normal Activity. The app underneath stays
foreground and live, so follow-mode taps and auto-load scrolling act on a running app.
An Activity would background the app and break both. (No "draw over other apps"
permission needed.)

### engine (pure logic, unit-testable off-device)

| Unit | Does | Depends on |
|---|---|---|
| **Snapshot** | Walk the active window's `AccessibilityNodeInfo` tree into an immutable model: class, text, content description, hint, view id, clickable / scrollable / editable / password / heading, `visibleToUser`, bounds, child order. | Android a11y API (thin adapter); the model itself is plain Kotlin |
| **Stitcher** | Model → ordered **blocks**: `Heading`, `Paragraph` (with inline `Link` spans), `Button`, `ControlRow`, `Field`, `Image`, `ListItem`. | Snapshot model only |
| **Classifier** | Each tappable block → **Follow** or **CloseFirst** (rules below). | Blocks only |
| **Actions** | Tap a node / scroll a container. Re-finds the target before acting (by view id, then text + class, then nearest bounds) because nodes go stale. | Android a11y API |
| **Settle** | Wait until the app stops changing: no `TYPE_WINDOW_CONTENT_CHANGED` / `TYPE_WINDOW_STATE_CHANGED` for **300 ms**, give up at **3 s**. | Event stream from the service |

**Stitcher rules** (from the probe):
- A **paragraph** is a node with at least one direct text-leaf child whose other children
  are all *inline* — clickable (a link) or with < 25 chars of total text. Its text = all leaf
  text beneath it, in order; clickable descendants become inline `Link` spans.
- **Compose separator:** when sibling text nodes are separate composables (not Chromium
  runs), join with a space/newline instead of concatenating ("Running" + "The probe…").
  Heuristic: Chromium runs live under a `WebView` ancestor; everything else gets separators.
- **ControlRow:** ≥ 3 adjacent short clickable siblings (share/like/reply bars, toolbars)
  collapse into one row of labelled chips.
- **Clickable headings** (mobile Wikipedia's section toggles, whose bodies aren't in the tree
  until expanded) stay headings but are tappable (FOLLOW → expands in place). *(Added during planning, 2026-10-21.)*
- Unicode **NFKC normalization** on all text (X's 𝚖𝚊𝚝𝚑 letters → plain).
- Password fields → `Field("••• (hidden)")`, never contents.
- Duplicate labels that Chromium emits twice for one control (e.g. "Save; Save") collapse.

**Classifier rules:**
- **Follow** (Reader stays open, re-reads after): inline links; list items; buttons whose
  label matches *more, next, previous, show, expand, see all, read more, page N, older,
  newer* (case-insensitive, whole word).
- **CloseFirst** (Reader closes, then taps): **everything else** — including Send, Call,
  Play, Post, Delete, text fields, and any button it can't classify. Unknown defaults to
  CloseFirst so no surprise action happens hidden behind Reader.

### app

| Unit | Does |
|---|---|
| **ReaderService** | The `AccessibilityService`. Requests the accessibility button (= two-finger swipe up in gesture nav); receives tile/bubble triggers; owns the overlay. **Ignores events except while Reader is open or a trigger is in progress** — no background reading. |
| **ReaderOverlay** | Full-screen Compose panel in the overlay window; renders blocks. |
| **ReaderTile** | Quick Settings `TileService`. Collapses the shade, waits for it to close (Settle), then triggers — otherwise it would read the shade. |
| **FloatingButton** | Optional draggable Newt bubble on the screen edge (setting, **off by default**). Hidden while Reader is open. |
| **SettingsActivity** | Text size, floating button on/off, and a one-line plain explanation of each plus how to assign the swipe. |

> **Planning change (2026-10-21):** v1 keeps the engine as the package
> `io.github.secnewt.reader.engine` inside `:app`, not a separate Gradle module: the shared CI
> workflow only runs `:app` tasks, so a second module's tests would silently never run. Android
> imports stay confined to `AndroidSnapshot.kt` so it can be extracted later.

## 🔄 Data flow

**Open:** trigger → Settle → Snapshot → Stitch → show overlay **scrolled to the first block
that was on screen** (`visibleToUser`). Content above and below is scrollable; Reader never
dumps you at the top of a long page.

**Tap in Reader:**
- *Follow:* Actions taps the real node → slim "Loading…" bar → Settle → re-read; show the
  new content from its top (or, if the window didn't change, refresh in place).
- *CloseFirst:* close overlay → tap the real node. Blocks of this kind carry a small ↗.
- **Back** inside Reader = `GLOBAL_ACTION_BACK` on the app, then re-read. Back on the first
  page Reader opened with = close.

**Reach the end (auto-load):** when Reader is scrolled within ~1 screen of its last block,
Actions sends `ACTION_SCROLL_FORWARD` to the app's main scrollable (largest scrollable on
screen) → Settle → re-read → **append only blocks not already shown** (match on normalized
text + block type; bounds as tiebreak). If the app didn't change: show a **Load more**
button; if a second try changes nothing: "That's everything this app is showing."

**Close:** Back from the first page, the always-visible Close button, or the same trigger
again. You land in the app wherever it is now.

## 🎨 Look

- Newt house style: Tokyo Night on true black (`#050608`) / Tokyo Night Day in light mode;
  every text color ≥ **7:1**; Atkinson Hyperlegible for reading, Chakra Petch for titles.
- **Reader text size** independent of system font: **A− / A+** in the top bar and pinch;
  remembered; default ≈ 150% of body. Always reflows to screen width — never sideways scroll.
- Blocks: headings larger in Chakra Petch; paragraphs with generous line height;
  links underlined in cyan `#7DCFFF` with ≥ 48 dp tap area; buttons as full-width 56 dp
  Newt buttons (blue→purple outline) in reading order, ↗ on CloseFirst ones;
  ControlRows as compact labelled chips; images as "🖼 Image: <description>" or "🖼 Image";
  fields as "✎ Type in: <field name>" ↗.
- Top bar: app name + page title, A− / A+, Close.

## ⚠️ Errors and edge cases

- **Nothing shared** (banking apps, lock screen, still-loading WebView): retry once after
  Settle; then "This app isn't sharing its text" and auto-close after ~2 s.
- **Stale target** (re-find fails): "That changed — here's the screen now" + re-read.
- **Settle timeout** (app keeps animating, e.g. a playing video): proceed with what's there
  after 3 s.
- **Huge pages** (The Verge was 1,112 nodes): Snapshot and Stitch run off the main thread;
  overlay shows "Reading…" if it takes > 200 ms.
- Reader never presses anything by itself except the auto-load scroll.

## 🔒 Privacy and security

- **No internet permission.** No storage of screen content. Screen text never written to
  logcat (release builds log only counts and timings).
- Settings store only text size and the floating-button switch.
- Accessibility service description, plain: "Reads the screen when you ask, to redraw it
  larger. Taps only what you tap."
- Permission allowlist check in CI pins the manifest to: the accessibility service binding,
  the tile binding, nothing else beyond the template's baseline.

## 🧪 Testing

- **Engine unit tests** on fixtures built from the probe's **public** captures only —
  Vanadium Wikipedia, Vanadium The Verge, Kagi home, Newt Weather. (X feed and Claude chat
  stay out of the public repo.) Cover: stitching (Wikipedia lead = one paragraph with its
  links), Compose separators, ControlRow folding, NFKC, Follow vs CloseFirst classification,
  de-dupe on append, password masking.
- **Overlay screenshot tests** (light, dark, 200% text) + the house-style accessibility checks.
- **Emulator test in CI:** Chrome opens a small bundled test page → broadcast trigger →
  Reader taps a Follow link → assert the second page's heading is read; tap a CloseFirst
  button → assert overlay gone.
- **the owner's phone (acceptance):** Vanadium long article, Brave→X with auto-load, Claude app
  with auto-load up through history, Kagi search results, a Newt app; both the two-finger
  swipe and the tile; floating button when switched on.

## ✅ Done when

the owner can open Reader on each acceptance target with one gesture, read in his theme at his
size without pinch-zooming, follow links/"more" without leaving Reader, and close back to
the app — and says it beats Reading Mode.

## ❓ Open (decide during planning, not blockers)

- Exact default text size (tune on his phone).
- Whether the floating button needs its own "hide for this app" — deferred unless he asks.
- OCR engine for v2 (ML Kit bundled on GrapheneOS unverified; Tesseract fallback).
