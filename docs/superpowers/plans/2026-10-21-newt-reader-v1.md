# Newt Reader v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An accessibility service that, on a two-finger swipe up (or a Quick Settings tile, or an optional floating button), redraws the current app's screen as large reflowed text in the Newt look, with the screen's links and buttons still working.

**Architecture:** A pure-Kotlin **engine** (`ScreenNode` tree → `Stitcher` → `Block`s, plus `Classifier`, `Merge`, `RefMatcher`, `Settle`) tested on real captured trees; a **`ReaderController`** state machine that talks to the phone only through a `ScreenPort` interface (tested with a fake); and thin **Android glue** (`ReaderService`, `ServicePort`, `OverlayHost`, tile, floating button) that draws a Compose overlay of type `TYPE_ACCESSIBILITY_OVERLAY` over the live app.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, `io.github.secnewt:newt-ui` (theme, fonts, `neonOutline`, `SectionHeading`), kotlinx-coroutines, JUnit 4, Robolectric, Roborazzi via `newtScreenshot()` from `newt-test`, `org.json` (tests only), reactivecircus emulator runner in CI.

**Spec:** `docs/superpowers/specs/2026-10-21-newt-reader-design.md`. Read it before starting any task.

## Global Constraints

- Package `io.github.secnewt.reader`; app name "Newt Reader"; `minSdk = 33`, `targetSdk = 35`, `compileSdk = 37` (set by the template; don't change). The debug build's id is `io.github.secnewt.reader.debug`.
- **No new permissions.** `config/allowed-permissions.txt` stays as it is. An accessibility service, a tile service and an accessibility overlay need no `<uses-permission>`. No `INTERNET`, ever.
- **Only new dependencies:** `kotlinx-coroutines-android` (main) and `kotlinx-coroutines-test` (tests), both version `1.10.2`.
- **Privacy:** screen text is never stored and never logged in release builds. Settings store only text size and the floating-button switch. The debug-only `DebugHooks` may log the first heading (used by the emulator test).
- **Deviation from the spec, on purpose:** the spec says two Gradle modules (`engine/`, `app/`). The shared CI workflow (`newt-app.yml`) only runs `:app` tasks (`recordRoborazziDebug`, the APK permission audit at `app/build/outputs/...`), so a second module's tests would silently never run. v1 keeps the engine as the package `io.github.secnewt.reader.engine` with **no Android imports except in `AndroidSnapshot.kt`**. Extract it to a module when voice commands need it.
- Tunables (from the spec, verbatim): Settle quiet **300 ms**, give up at **3 s**; inline/short text threshold **< 25 chars**; text size default **1.5×**, range **1.0–3.0**, step **0.1**; "nothing shared" message auto-closes after **~2 s**; buttons **56 dp** tall, chips/links **≥ 48 dp**.
- Messages (exact): `This app isn't sharing its text` · `That changed — here's the screen now` · `That's everything this app is showing.` · `Load more` · `Loading more…`
- Follow words (whole word, case-insensitive): *more, next, previous, prev, show, expand, see all, read more, page N, older, newer*.
- House style: follow `NEWT_STYLE.md`: 7:1 text contrast, Atkinson Hyperlegible for reading, Chakra Petch (`NewtFonts.Display`) for titles, headings marked `semantics { heading() }`, every screen gets `newtScreenshot()` tests in dark, light and `fontScale = 2f`.
- **Builds and tests run in CI.** The loop: push the branch → `gh pr checks --watch` → read failures with `gh run view <id> --log-failed`. (A JDK + SDK exists at `~/.cache/newt-a11y-test`, but Gradle needs a GitHub Packages token for `newt-ui`, so CI is the source of truth.) All work happens on branch **`v1-reader`** with one PR. Jerry merges ("merge it"); never merge yourself.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_015peYJuoga8nGbz6VrP1kBs
  ```

## File Structure

```
app/src/main/java/io/github/secnewt/reader/
  engine/ScreenNode.kt       Bounds, ScreenNode, Screen, NodeRef, withPaths()
  engine/Text.kt             nfkc(), clean()
  engine/Blocks.kt           Block (sealed), Span, Control, TapKind
  engine/Classifier.kt       button label → FOLLOW / CLOSE_FIRST
  engine/Stitcher.kt         Screen → List<Block>
  engine/Merge.kt            append only unseen blocks
  engine/RefMatcher.kt       find a NodeRef again in a fresh tree
  engine/Settle.kt           wait for the app to stop changing
  engine/AndroidSnapshot.kt  AccessibilityNodeInfo → ScreenNode; live path walk
  reader/ScreenPort.kt       what the controller needs from the phone
  reader/ReaderState.kt      ReaderState, Footer, findTarget()
  reader/ReaderController.kt the state machine
  reader/ReaderPrefs.kt      text size + floating button (SharedPreferences)
  reader/ServicePort.kt      ScreenPort on a real AccessibilityService
  service/ReaderService.kt   the AccessibilityService
  service/OverlayOwner.kt    lifecycle owner so Compose works in a service window
  service/OverlayHost.kt     full-screen Reader window
  service/FloatingButton.kt  optional draggable bubble
  service/ReaderTileService.kt Quick Settings tile
  ui/ReaderScreen.kt         the Reader overlay UI + ReaderRoute
  ui/BlockView.kt            one block on screen
  ui/SettingsScreen.kt       launcher screen (replaces WelcomeScreen)
  MainActivity.kt            hosts SettingsScreen
app/src/main/res/xml/reader_service.xml
app/src/main/res/drawable/ic_tile.xml
app/src/debug/java/io/github/secnewt/reader/service/DebugHooks.kt     broadcasts for the emulator test
app/src/release/java/io/github/secnewt/reader/service/DebugHooks.kt   no-op
app/src/debug/java/io/github/secnewt/reader/TestPageActivity.kt
app/src/debug/AndroidManifest.xml
app/src/debug/assets/page1.html, page2.html
scripts/emulator-reader-test.sh
app/src/test/resources/fixtures/{wikipedia,verge,kagi-home,weather}.json
app/src/test/java/io/github/secnewt/reader/
  engine/Fixtures.kt, TextTest.kt, StitcherTest.kt, StitcherFixtureTest.kt,
  engine/ClassifierTest.kt, MergeTest.kt, RefMatcherTest.kt, SettleTest.kt
  reader/FakePort.kt, ReaderControllerTest.kt, ReaderPrefsTest.kt, ReaderStateTest.kt
  ui/ReaderScreenTest.kt, ui/SettingsScreenTest.kt   (WelcomeScreen + its test are deleted)
```

---

### Task 1: Engine model, text cleaning, fixtures

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/io/github/secnewt/reader/engine/ScreenNode.kt`, `engine/Text.kt`
- Create: `app/src/test/resources/fixtures/{wikipedia,verge,kagi-home,weather}.json`
- Test: `app/src/test/java/io/github/secnewt/reader/engine/Fixtures.kt`, `engine/TextTest.kt`

**Interfaces:**
- Produces: `Bounds(left, top, right, bottom)` with `.area`; `ScreenNode(cls, text?, desc?, hint?, viewId?, clickable, scrollable, editable, password, heading, visible, bounds, path, children)` with `.label`, `.isLeaf`, `.walk()`, `.ref()`, `.withPaths()`; `Screen(packageName, title, root)`; `NodeRef(path, viewId?, label?, cls, bounds)`; `nfkc(String)`, `clean(String)`; test helper `Fixtures.load(name): Screen`.

- [ ] **Step 1: Add the coroutines dependencies**

In `gradle/libs.versions.toml`, under `[versions]` add `coroutines = "1.10.2"`; under `[libraries]` add:

```toml
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

In `app/build.gradle.kts` `dependencies {}` add `implementation(libs.kotlinx.coroutines.android)` after `implementation(libs.androidx.core.ktx)`, and `testImplementation(libs.kotlinx.coroutines.test)` after `testImplementation(libs.junit)`.

- [ ] **Step 2: Make the fixtures from the probe captures**

The on-phone probe (2026-10-21) saved dumps in `~/Projects/newt-a11y-probe/dumps/`. Keep only the app's own window (drops the status bar), and replace the one location string. Run from the repo root:

```bash
mkdir -p app/src/test/resources/fixtures
python3 - <<'EOF'
import json, os
src = os.path.expanduser("~/Projects/newt-a11y-probe/dumps/")
pick = {
    "wikipedia": "20261021-094056-app.vanadium.browser.json",
    "verge":     "20261021-094110-app.vanadium.browser.json",
    "kagi-home": "20261021-094231-com.kagi.search.json",
    "weather":   "20261021-094135-io.github.secnewt.weather.json",
}
for name, f in pick.items():
    d = json.load(open(src + f))
    d["windows"] = [w for w in d["windows"] if w["active"]]
    d.pop("stats", None); d["label"] = name
    text = json.dumps(d, ensure_ascii=False, indent=1).replace("Garland, TX", "Springfield")
    open(f"app/src/test/resources/fixtures/{name}.json", "w").write(text)
EOF
grep -l -i -E 'garland|linuxnewt|becca|maples' app/src/test/resources/fixtures/*.json || echo "clean"
```

Expected: `clean`. (The repo is public: never add the X or Claude-app captures.)

- [ ] **Step 3: Write the failing tests**

`app/src/test/java/io/github/secnewt/reader/engine/Fixtures.kt`:

```kotlin
package io.github.secnewt.reader.engine

import org.json.JSONObject

/** Loads a probe capture from src/test/resources/fixtures (needs Robolectric for org.json). */
object Fixtures {
    fun load(name: String): Screen {
        val json = JSONObject(javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText())
        val windows = json.getJSONArray("windows")
        val window = (0 until windows.length()).map { windows.getJSONObject(it) }.first { it.getBoolean("active") }
        return Screen(json.getString("activePackage"), window.getString("title"), node(window.getJSONObject("root")).withPaths())
    }

    private fun node(j: JSONObject): ScreenNode {
        val b = j.getString("b").split(" ").map(String::toInt)
        val kids = j.optJSONArray("kids")
        return ScreenNode(
            cls = j.getString("cls"),
            text = j.optString("text").ifEmpty { null },
            desc = j.optString("desc").ifEmpty { null },
            hint = j.optString("hint").ifEmpty { null },
            viewId = j.optString("id").ifEmpty { null },
            clickable = j.optBoolean("click"),
            scrollable = j.optBoolean("scroll"),
            editable = j.optBoolean("edit"),
            password = j.optBoolean("password"),
            heading = j.optBoolean("heading"),
            visible = j.optBoolean("visible", true),
            bounds = Bounds(b[0], b[1], b[2], b[3]),
            children = if (kids == null) emptyList() else (0 until kids.length()).map { node(kids.getJSONObject(it)) },
        )
    }
}
```

`app/src/test/java/io/github/secnewt/reader/engine/TextTest.kt`:

```kotlin
package io.github.secnewt.reader.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TextTest {
    @Test fun nfkcTurnsMathLettersPlain() = assertEquals("linuxnewt", nfkc("𝚕𝚒𝚗𝚞𝚡𝚗𝚎𝚠𝚝"))

    @Test fun nfkcKeepsEdgeSpaces() = assertEquals(" or ", nfkc(" or "))

    @Test fun cleanCollapsesSpacesKeepsLines() =
        assertEquals("a b\nc", clean("  a    b \n  c  "))

    @Test fun cleanLimitsBlankLines() = assertEquals("a\n\nb", clean("a\n\n\n\nb"))

    @Test fun pathsFollowChildIndexes() {
        val tree = ScreenNode("r", children = listOf(ScreenNode("a"), ScreenNode("b", children = listOf(ScreenNode("c"))))).withPaths()
        assertEquals(listOf(1, 0), tree.children[1].children[0].path)
        assertEquals(listOf("r", "a", "b", "c"), tree.walk().map { it.cls }.toList())
    }

    @Test fun labelPrefersTextThenDescThenHint() {
        assertEquals("t", ScreenNode("x", text = "t", desc = "d").label)
        assertEquals("d", ScreenNode("x", text = " ", desc = "d").label)
        assertEquals("h", ScreenNode("x", hint = "h").label)
    }

    @Test fun fixturesLoad() {
        val wiki = Fixtures.load("wikipedia")
        assertEquals("app.vanadium.browser", wiki.packageName)
        assertTrue(wiki.root.walk().count() > 500)
    }
}
```

- [ ] **Step 4: Push and watch them fail**

```bash
git add -A && git commit -m "Engine tests: text cleaning, tree paths, probe fixtures" && git push -u origin v1-reader
gh pr create --draft --title "Newt Reader v1" --body "Implements docs/superpowers/specs/2026-10-21-newt-reader-design.md (plan: docs/superpowers/plans/2026-10-21-newt-reader-v1.md)."
gh pr checks --watch
```

Expected: the tests job FAILS to compile (`Unresolved reference: ScreenNode`, `nfkc`, `clean`).

- [ ] **Step 5: Implement**

`app/src/main/java/io/github/secnewt/reader/engine/ScreenNode.kt`:

```kotlin
package io.github.secnewt.reader.engine

/** A rectangle on screen, in pixels. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val area: Long get() = maxOf(0, right - left).toLong() * maxOf(0, bottom - top)
}

/**
 * One node of an app's accessibility tree, copied into plain Kotlin so the rest of the
 * engine can be tested without a phone. [path] is the child indexes from the root.
 */
data class ScreenNode(
    val cls: String,
    val text: String? = null,
    val desc: String? = null,
    val hint: String? = null,
    val viewId: String? = null,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false,
    val heading: Boolean = false,
    val visible: Boolean = true,
    val bounds: Bounds = Bounds(0, 0, 0, 0),
    val path: List<Int> = emptyList(),
    val children: List<ScreenNode> = emptyList(),
) {
    /** What the node says: its text, else its description, else its hint. */
    val label: String? get() = listOf(text, desc, hint).firstOrNull { !it.isNullOrBlank() }
    val isLeaf: Boolean get() = children.isEmpty()

    fun walk(): Sequence<ScreenNode> = sequence {
        yield(this@ScreenNode)
        children.forEach { yieldAll(it.walk()) }
    }

    fun ref() = NodeRef(path, viewId, label, cls, bounds)

    /** A copy with every node's [path] filled in. */
    fun withPaths(at: List<Int> = emptyList()): ScreenNode =
        copy(path = at, children = children.mapIndexed { i, c -> c.withPaths(at + i) })
}

/** A whole screen: the app's package, its window title, and the tree. */
data class Screen(val packageName: String, val title: String, val root: ScreenNode)

/** How to find a node again later, when the live tree may have changed. */
data class NodeRef(val path: List<Int>, val viewId: String?, val label: String?, val cls: String, val bounds: Bounds)
```

`app/src/main/java/io/github/secnewt/reader/engine/Text.kt`:

```kotlin
package io.github.secnewt.reader.engine

import java.text.Normalizer

/** Look-alike letters (𝚕𝚒𝚗𝚞𝚡, fullwidth, ligatures) become plain ones so they draw in Atkinson. Spacing is kept. */
fun nfkc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

/** [nfkc], then runs of spaces become one, each line is trimmed, and at most one blank line in a row. */
fun clean(s: String): String =
    nfkc(s)
        .replace(Regex("[ \\t\\u00A0]+"), " ")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
```

- [ ] **Step 6: Push and watch them pass**

```bash
git add -A && git commit -m "Engine: screen tree model and text cleaning" && git push
gh pr checks --watch
```

Expected: "Tests, screenshots, accessibility and lint" PASSES.

---

### Task 2: Blocks, Classifier and Stitcher

**Files:**
- Create: `app/src/main/java/io/github/secnewt/reader/engine/Blocks.kt`, `engine/Classifier.kt`, `engine/Stitcher.kt`
- Test: `app/src/test/java/io/github/secnewt/reader/engine/ClassifierTest.kt`, `engine/StitcherTest.kt`

**Interfaces:**
- Consumes: `ScreenNode`, `Screen`, `NodeRef`, `nfkc`, `clean` (Task 1).
- Produces: `enum TapKind { FOLLOW, CLOSE_FIRST }`; `sealed interface Span { text }` with `Span.Plain(text)`, `Span.Link(text, target: NodeRef)`; `Control(label, target, kind)`; `sealed interface Block { onScreen: Boolean; text: String; key: String }` with `Block.Heading(text, onScreen, target: NodeRef? = null)` (a target = tappable, e.g. a collapsed section's toggle; always FOLLOW), `Block.Paragraph(spans, onScreen)`, `Block.ListItem(text, target, onScreen)`, `Block.Button(control, onScreen)`, `Block.ControlRow(controls, onScreen)`, `Block.Field(label, target, password, onScreen)`, `Block.Image(description?, onScreen)`; `Classifier.forButton(label): TapKind`; `Stitcher.stitch(screen): List<Block>`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/io/github/secnewt/reader/engine/ClassifierTest.kt`:

```kotlin
package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierTest {
    @Test fun followWords() {
        listOf("Show more", "Next", "See all", "Read more", "Page 3", "Older posts", "Expand", "prev")
            .forEach { assertEquals(it, TapKind.FOLLOW, Classifier.forButton(it)) }
    }

    @Test fun everythingElseClosesFirst() {
        listOf("Send", "Call", "Play", "Post", "Delete", "Comments 73", "Moreover", "Download PDF", "")
            .forEach { assertEquals(it, TapKind.CLOSE_FIRST, Classifier.forButton(it)) }
    }
}
```

`app/src/test/java/io/github/secnewt/reader/engine/StitcherTest.kt`:

```kotlin
package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StitcherTest {
    private fun tv(t: String, visible: Boolean = true) = ScreenNode("android.widget.TextView", text = t, visible = visible)
    private fun link(t: String) = ScreenNode("android.view.View", text = t, clickable = true, children = listOf(tv(t)))
    private fun web(vararg kids: ScreenNode) =
        Screen("p", "T", ScreenNode("root", children = listOf(ScreenNode("android.webkit.WebView", children = kids.toList()))).withPaths())
    private fun app(vararg kids: ScreenNode) = Screen("p", "T", ScreenNode("root", children = kids.toList()).withPaths())

    @Test fun webParagraphKeepsLinksInline() {
        val blocks = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(
            tv("Visual"), tv(" or "), tv("vision impairment"), tv(" is the inability of "), link("visual perception"), tv(". It matters."),
        ))))
        val p = blocks.single() as Block.Paragraph
        assertEquals("Visual or vision impairment is the inability of visual perception. It matters.", p.text)
        val l = p.spans.filterIsInstance<Span.Link>().single()
        assertEquals("visual perception", l.text)
        assertEquals(listOf(0, 0, 4), l.target.path)
    }

    @Test fun nestedFootnoteStaysInTheParagraph() {
        val footnote = ScreenNode("android.view.View", children = listOf(
            ScreenNode("android.view.View", desc = "[8]", clickable = true, children = listOf(
                ScreenNode("android.view.View", children = listOf(tv("["), tv("8"), tv("]")))))))
        val p = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(tv("Treatment helps."), footnote, tv(" More text follows here."))))).single() as Block.Paragraph
        assertEquals("Treatment helps.[8] More text follows here.", p.text)
        assertEquals("[8]", p.spans.filterIsInstance<Span.Link>().single().text)
    }

    @Test fun appLeavesAreSeparateParagraphs() {
        val blocks = Stitcher.stitch(app(tv("Running"), tv("The probe connected fine.")))
        assertEquals(listOf("Running", "The probe connected fine."), blocks.map { it.text })
    }

    @Test fun clickableWithLongTextIsAListItem() {
        val row = ScreenNode("android.view.View", clickable = true, children = listOf(
            ScreenNode("android.view.View", text = "Today: sunny. High 81. Low 58."), ScreenNode("android.widget.Button")))
        val b = Stitcher.stitch(app(row)).single()
        assertTrue(b is Block.ListItem)
        assertEquals("Today: sunny. High 81. Low 58.", b.text)
    }

    @Test fun shortButtonsAndTheirKind() {
        val blocks = Stitcher.stitch(app(
            ScreenNode("android.widget.Button", text = "Send", clickable = true), tv("gap"),
            ScreenNode("android.widget.Button", text = "Show more", clickable = true)))
        assertEquals(TapKind.CLOSE_FIRST, (blocks[0] as Block.Button).control.kind)
        assertEquals(TapKind.FOLLOW, (blocks[2] as Block.Button).control.kind)
    }

    @Test fun webLinksOutsideParagraphsFollow() {
        val b = Stitcher.stitch(web(link("Blindness"))).single() as Block.Button
        assertEquals(TapKind.FOLLOW, b.control.kind)
    }

    @Test fun threeButtonsInARowFold() {
        val blocks = Stitcher.stitch(app(
            ScreenNode("android.widget.Button", text = "Like", clickable = true),
            ScreenNode("android.widget.Button", text = "Reply", clickable = true),
            ScreenNode("android.widget.Button", text = "Share", clickable = true)))
        val row = blocks.single() as Block.ControlRow
        assertEquals(listOf("Like", "Reply", "Share"), row.controls.map { it.label })
    }

    @Test fun unlabelledButtonsAreSkipped() =
        assertTrue(Stitcher.stitch(app(ScreenNode("android.widget.Button", clickable = true))).isEmpty())

    @Test fun clickableContainerWithClickableInsideIsOpened() {
        val post = ScreenNode("android.view.View", clickable = true, children = listOf(
            tv("A long post body that is well over the limit."), ScreenNode("android.widget.Button", text = "Reply", clickable = true)))
        val blocks = Stitcher.stitch(app(post))
        assertEquals(listOf("A long post body that is well over the limit.", "Reply"), blocks.map { it.text })
    }

    @Test fun passwordFieldsNeverShowContents() {
        val f = Stitcher.stitch(app(ScreenNode("android.widget.EditText", text = "hunter2", hint = "Password", editable = true, password = true))).single() as Block.Field
        assertTrue(f.password)
        assertFalse(f.label.contains("hunter2"))
        assertEquals("Password", f.label)
    }

    @Test fun headingsAndImages() {
        val blocks = Stitcher.stitch(app(
            ScreenNode("android.widget.TextView", text = "Causes", heading = true),
            ScreenNode("android.widget.Image", desc = "A Snellen chart"),
            ScreenNode("android.widget.ImageView", bounds = Bounds(0, 0, 40, 40))))
        assertEquals(listOf("Causes", "A Snellen chart"), blocks.map { it.text })
        assertTrue(blocks[0] is Block.Heading)
        assertTrue(blocks[1] is Block.Image)
    }

    @Test fun clickableHeadingIsATappableHeading() {
        val toggle = ScreenNode("android.view.View", clickable = true, children = listOf(
            ScreenNode("android.widget.TextView", text = "Classification", heading = true), ScreenNode("android.view.View")))
        val h = Stitcher.stitch(web(toggle)).single() as Block.Heading
        assertEquals("Classification", h.text)
        assertEquals(listOf(0, 0), h.target?.path)
    }

    @Test fun repeatsInARowAreDropped() =
        assertEquals(1, Stitcher.stitch(app(tv("Save"), tv("Save"))).size)

    @Test fun mathLettersArePlain() =
        assertEquals("linuxnewt", Stitcher.stitch(app(tv("𝚕𝚒𝚗𝚞𝚡𝚗𝚎𝚠𝚝"))).single().text)

    @Test fun onScreenComesFromVisibility() {
        val blocks = Stitcher.stitch(app(tv("above", visible = false), tv("here")))
        assertEquals(listOf(false, true), blocks.map { it.onScreen })
    }
}
```

- [ ] **Step 2: Push and watch them fail**

```bash
git add -A && git commit -m "Stitcher and classifier tests" && git push && gh pr checks --watch
```

Expected: compile FAILS (`Unresolved reference: Stitcher`, `TapKind`, `Block`).

- [ ] **Step 3: Implement**

`app/src/main/java/io/github/secnewt/reader/engine/Blocks.kt`:

```kotlin
package io.github.secnewt.reader.engine

/** What tapping something in Reader does: stay open and re-read, or close Reader and then tap. */
enum class TapKind { FOLLOW, CLOSE_FIRST }

/** A run of text inside a paragraph; links are tappable. */
sealed interface Span {
    val text: String
    data class Plain(override val text: String) : Span
    data class Link(override val text: String, val target: NodeRef) : Span
}

/** One tappable control: what it says, where it is, and what tapping it does. */
data class Control(val label: String, val target: NodeRef, val kind: TapKind)

/** One piece of the reading view, in reading order. */
sealed interface Block {
    /** True if it was on the app's screen when read; Reader opens at the first such block. */
    val onScreen: Boolean
    val text: String
    /** Same type and text = the same block; used to skip repeats when loading more. */
    val key: String get() = "${this::class.simpleName}:$text"

    /** [target] is set when the heading itself is tappable (mobile Wikipedia's section toggles); tapping it FOLLOWs. */
    data class Heading(override val text: String, override val onScreen: Boolean, val target: NodeRef? = null) : Block

    data class Paragraph(val spans: List<Span>, override val onScreen: Boolean) : Block {
        override val text: String get() = spans.joinToString("") { it.text }
    }

    /** A tappable row with real content (a post, a headline, a forecast day). Always FOLLOW. */
    data class ListItem(override val text: String, val target: NodeRef, override val onScreen: Boolean) : Block

    data class Button(val control: Control, override val onScreen: Boolean) : Block {
        override val text: String get() = control.label
    }

    /** Three or more small buttons in a row (toolbars, like/reply/share), shown as chips. */
    data class ControlRow(val controls: List<Control>, override val onScreen: Boolean) : Block {
        override val text: String get() = controls.joinToString(" · ") { it.label }
    }

    /** A text field. Tapping it always closes Reader so the keyboard can appear. */
    data class Field(val label: String, val target: NodeRef, val password: Boolean, override val onScreen: Boolean) : Block {
        override val text: String get() = label
    }

    data class Image(val description: String?, override val onScreen: Boolean) : Block {
        override val text: String get() = description.orEmpty()
    }
}
```

`app/src/main/java/io/github/secnewt/reader/engine/Classifier.kt`:

```kotlin
package io.github.secnewt.reader.engine

/**
 * Buttons that move through content (more, next, expand…) keep Reader open; anything else
 * (Send, Call, Play, or a label we don't know) closes Reader first, so nothing happens hidden behind it.
 */
object Classifier {
    private val followWords =
        Regex("""\b(more|next|previous|prev|show|expand|see all|read more|page \d+|older|newer)\b""", RegexOption.IGNORE_CASE)

    fun forButton(label: String): TapKind =
        if (followWords.containsMatchIn(label)) TapKind.FOLLOW else TapKind.CLOSE_FIRST
}
```

`app/src/main/java/io/github/secnewt/reader/engine/Stitcher.kt`:

```kotlin
package io.github.secnewt.reader.engine

/**
 * Turns a screen's tree into reading blocks.
 *
 * Web pages (anything under a WebView) split text at every link and nest footnotes three
 * levels deep, so a paragraph is a node with a direct text child whose other children are all
 * inline (a link, or short text); its text is every leaf below it, with links kept as spans.
 * Outside web pages each piece of text is its own paragraph.
 */
object Stitcher {
    private const val SHORT = 25
    private const val INLINE_LINK_MAX = 200

    fun stitch(screen: Screen): List<Block> {
        val out = mutableListOf<Block>()
        visit(screen.root, inWeb = false, out)
        return foldControlRows(dropRepeats(out))
    }

    private fun visit(n: ScreenNode, inWeb: Boolean, out: MutableList<Block>) {
        val web = inWeb || n.cls.endsWith("WebView")
        when {
            n.editable || n.password -> out += Block.Field(fieldLabel(n), n.ref(), n.password, n.anyVisible())
            isClickableUnit(n) -> clickable(n, web)?.let { out += it }
            n.heading && allText(n, web).isNotEmpty() -> out += Block.Heading(allText(n, web), n.anyVisible())
            web && isParagraph(n) -> out += Block.Paragraph(spans(n), n.anyVisible())
            n.isLeaf && isImage(n) -> if (worthShowing(n)) out += Block.Image(n.desc?.let(::clean)?.ifEmpty { null }, n.visible)
            n.isLeaf -> n.label?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { out += Block.Paragraph(listOf(Span.Plain(it)), n.visible) }
            else -> n.children.forEach { visit(it, web, out) }
        }
    }

    /** A clickable node with nothing clickable inside it is one control. */
    private fun isClickableUnit(n: ScreenNode) =
        n.clickable && !n.cls.endsWith("WebView") && n.walk().drop(1).none { it.clickable || it.editable }

    private fun clickable(n: ScreenNode, web: Boolean): Block? {
        val text = n.label?.let(::clean)?.ifEmpty { null } ?: allText(n, web)
        if (text.isEmpty()) return null
        // A clickable heading (a collapsed section's toggle) stays a heading, and tapping it expands in place.
        if (n.walk().any { it.heading }) return Block.Heading(text, n.anyVisible(), n.ref())
        if (text.length >= SHORT) return Block.ListItem(text, n.ref(), n.anyVisible())
        // In web content, Chromium reports <button> as a Button class; anything else clickable is a link.
        val kind = if (web && !n.cls.endsWith("Button")) TapKind.FOLLOW else Classifier.forButton(text)
        return Block.Button(Control(text, n.ref(), kind), n.anyVisible())
    }

    private fun isParagraph(n: ScreenNode): Boolean =
        n.children.isNotEmpty() &&
            n.children.any { it.isLeaf && it.cls.endsWith("TextView") && !it.label.isNullOrBlank() } &&
            n.children.all { it.isLeaf || (it.clickable && allText(it, true).length < INLINE_LINK_MAX) || allText(it, true).length < SHORT }

    private fun spans(n: ScreenNode): List<Span> {
        val raw = mutableListOf<Span>()
        fun go(x: ScreenNode) {
            when {
                x.clickable -> allText(x, true).ifEmpty { x.label?.let(::clean).orEmpty() }
                    .takeIf { it.isNotEmpty() }?.let { raw += Span.Link(it, x.ref()) }
                x.isLeaf -> x.label?.let { raw += Span.Plain(nfkc(it)) }
                else -> x.children.forEach(::go)
            }
        }
        n.children.forEach(::go)
        // Join neighbouring plain runs, collapse spaces, and trim the paragraph's two ends.
        val merged = mutableListOf<Span>()
        for (s in raw) {
            val last = merged.lastOrNull()
            if (s is Span.Plain && last is Span.Plain) merged[merged.lastIndex] = Span.Plain(last.text + s.text) else merged += s
        }
        return merged.mapIndexed { i, s ->
            if (s !is Span.Plain) return@mapIndexed s
            var t = s.text.replace(Regex("[ \\t\\u00A0]+"), " ")
            if (i == 0) t = t.trimStart()
            if (i == merged.lastIndex) t = t.trimEnd()
            Span.Plain(t)
        }.filter { it.text.isNotEmpty() }
    }

    /** Every leaf's words in order; joined with nothing on the web (runs carry their own spaces), new lines elsewhere. */
    private fun allText(n: ScreenNode, web: Boolean): String =
        clean(n.walk().filter { it.isLeaf }.mapNotNull { it.label }.map(::nfkc).joinToString(if (web) "" else "\n"))

    private fun fieldLabel(n: ScreenNode): String =
        clean(listOfNotNull(n.hint, n.desc, if (n.password) null else n.text).firstOrNull { it.isNotBlank() } ?: "text field")

    private fun isImage(n: ScreenNode) = n.cls.endsWith("Image") || n.cls.endsWith("ImageView")

    /** Skip small unlabelled icons; keep described images and big pictures. */
    private fun worthShowing(n: ScreenNode) =
        !n.desc.isNullOrBlank() || (n.bounds.right - n.bounds.left >= 300 && n.bounds.bottom - n.bounds.top >= 200)

    private fun ScreenNode.anyVisible() = walk().any { it.visible && it.isLeaf } || (isLeaf && visible)

    private fun dropRepeats(blocks: List<Block>): List<Block> =
        blocks.filterIndexed { i, b -> i == 0 || blocks[i - 1].key != b.key }

    private fun foldControlRows(blocks: List<Block>): List<Block> {
        val out = mutableListOf<Block>()
        var run = mutableListOf<Block.Button>()
        fun flush() {
            if (run.size >= 3) out += Block.ControlRow(run.map { it.control }, run.any { it.onScreen }) else out += run
            run = mutableListOf()
        }
        for (b in blocks) if (b is Block.Button) run += b else { flush(); out += b }
        flush()
        return out
    }
}
```

- [ ] **Step 4: Push and watch them pass**

```bash
git add -A && git commit -m "Engine: blocks, classifier, stitcher" && git push && gh pr checks --watch
```

Expected: tests PASS. If one fails, read the assertion message, fix the Stitcher rule (not the test) unless the test contradicts the spec.

---

### Task 3: Stitcher on real captures

**Files:**
- Test: `app/src/test/java/io/github/secnewt/reader/engine/StitcherFixtureTest.kt`
- Modify (only if a test fails for a real reason): `engine/Stitcher.kt`

**Interfaces:**
- Consumes: `Fixtures.load` (Task 1), `Stitcher.stitch`, `Block` types (Task 2).

- [ ] **Step 1: Write the tests**

These facts were checked against the captures while planning (2026-10-21).

```kotlin
package io.github.secnewt.reader.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StitcherFixtureTest {
    private fun blocks(name: String) = Stitcher.stitch(Fixtures.load(name))

    @Test fun wikipediaLeadIsOneParagraphWithItsLinks() {
        val lead = blocks("wikipedia").filterIsInstance<Block.Paragraph>()
            .first { it.text.startsWith("Visual or vision impairment") }
        assertTrue(lead.text, lead.text.startsWith("Visual or vision impairment (VI or VIP) is the partial or total inability of visual perception."))
        assertTrue(lead.spans.any { it is Span.Link && it.text == "visual perception" })
        assertTrue(lead.onScreen)
    }

    @Test fun wikipediaOffScreenPartsAreThereButMarked() {
        val b = blocks("wikipedia")
        val caption = b.first { it.text.contains("Snellen chart that is frequently used") }
        assertFalse(caption.onScreen)
        // Mobile Wikipedia's section headings are clickable expand toggles; their bodies aren't in the tree until expanded.
        val heading = b.filterIsInstance<Block.Heading>().first { it.text == "Classification" }
        assertFalse(heading.onScreen)
        assertTrue(heading.target != null)
        assertTrue(b.indexOfFirst { it.onScreen } < b.indexOf(heading))
    }

    @Test fun vergeFrontPageReads() {
        val b = blocks("verge")
        assertTrue(b.size > 50)
        assertTrue(b.any { it.text.contains("The iPhone Duo is a messy, expensive, good time") })
    }

    @Test fun kagiSearchBoxIsAField() {
        val f = blocks("kagi-home").filterIsInstance<Block.Field>().single()
        assertTrue(f.label, f.label.startsWith("Let's fetch"))
    }

    @Test fun weatherComposeScreen() {
        val b = blocks("weather")
        assertTrue(b.any { it is Block.Heading && it.text == "Next 7 days" })
        val today = b.filterIsInstance<Block.ListItem>().first { it.text.startsWith("Today: sunny. High 81.") }
        assertTrue(today.onScreen)
        // The city is a clickable heading (it opens the ZIP card): a tappable heading.
        val city = b.filterIsInstance<Block.Heading>().single { it.text == "Springfield" }
        assertTrue(city.target != null)
    }

    @Test fun noFixtureHasTheSameBlockTwiceInARow() {
        for (name in listOf("wikipedia", "verge", "kagi-home", "weather")) {
            blocks(name).zipWithNext().forEach { (a, b) -> assertTrue("$name: ${a.key}", a.key != b.key) }
        }
    }
}
```

- [ ] **Step 2: Push and check**

```bash
git add -A && git commit -m "Stitcher tests on real phone captures" && git push && gh pr checks --watch
```

Expected: PASS. If one fails, look at that part of the fixture before touching code, e.g.:

```bash
python3 -c "
import json; d=json.load(open('app/src/test/resources/fixtures/wikipedia.json'))
def show(x,dep=0):
    print('  '*dep+x['cls'].split('.')[-1], repr((x.get('text') or x.get('desc') or '')[:50]), 'click' if x.get('click') else '')
    [show(c,dep+1) for c in x.get('kids',[])]
show(d['windows'][0]['root'])" | grep -n -B3 -A12 'Snellen chart that'
```

Fix the rule in `Stitcher.kt`, re-run Task 2's tests too (same CI job), commit `"Stitcher: <what changed>"`.

---

### Task 4: Merge, RefMatcher and Settle

**Files:**
- Create: `app/src/main/java/io/github/secnewt/reader/engine/Merge.kt`, `engine/RefMatcher.kt`, `engine/Settle.kt`
- Test: `app/src/test/java/io/github/secnewt/reader/engine/MergeTest.kt`, `engine/RefMatcherTest.kt`, `engine/SettleTest.kt`

**Interfaces:**
- Consumes: `Block`, `ScreenNode`, `NodeRef`, `Bounds` (Tasks 1–2).
- Produces: `Merge.appendNew(shown: List<Block>, fresh: List<Block>): List<Block>`; `RefMatcher.find(root: ScreenNode, ref: NodeRef): ScreenNode?`, `RefMatcher.at(root, path): ScreenNode?`; `suspend Settle.await(changes: Flow<Unit>, quietMs: Long = 300, maxMs: Long = 3000): Boolean`.

- [ ] **Step 1: Write the failing tests**

`MergeTest.kt`:

```kotlin
package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    private fun p(t: String) = Block.Paragraph(listOf(Span.Plain(t)), true)

    @Test fun onlyNewBlocksInOrder() =
        assertEquals(listOf(p("c"), p("d")), Merge.appendNew(listOf(p("a"), p("b")), listOf(p("b"), p("c"), p("d"))))

    @Test fun repeatsInsideFreshCountOnce() =
        assertEquals(listOf(p("c")), Merge.appendNew(listOf(p("a")), listOf(p("c"), p("c"))))

    @Test fun nothingNew() = assertEquals(emptyList<Block>(), Merge.appendNew(listOf(p("a")), listOf(p("a"))))

    @Test fun sameTextDifferentTypeIsNew() =
        assertEquals(1, Merge.appendNew(listOf(p("Causes")), listOf(Block.Heading("Causes", true))).size)
}
```

`RefMatcherTest.kt`:

```kotlin
package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefMatcherTest {
    private fun btn(t: String?, id: String? = null, top: Int = 0) =
        ScreenNode("android.widget.Button", text = t, viewId = id, clickable = true, bounds = Bounds(0, top, 100, top + 50))

    private val old = ScreenNode("r", children = listOf(btn("Next", top = 100), btn("Send", top = 200))).withPaths()

    @Test fun samePathStillMatches() = assertEquals(listOf(1), RefMatcher.find(old, old.children[1].ref())?.path)

    @Test fun movedNodeFoundByLabel() {
        val fresh = ScreenNode("r", children = listOf(btn("Ad"), btn("Next", top = 150), btn("Send", top = 250))).withPaths()
        assertEquals(listOf(2), RefMatcher.find(fresh, old.children[1].ref())?.path)
    }

    @Test fun idWins() {
        val o = ScreenNode("r", children = listOf(btn("Go", id = "go"))).withPaths()
        val fresh = ScreenNode("r", children = listOf(btn("Go"), btn("Go", id = "go", top = 400))).withPaths()
        assertEquals(listOf(1), RefMatcher.find(fresh, o.children[0].ref())?.path)
    }

    @Test fun unlabelledFoundNearby() {
        val o = ScreenNode("r", children = listOf(btn(null, top = 300))).withPaths()
        val fresh = ScreenNode("r", children = listOf(btn("x", top = 0), btn(null, top = 310))).withPaths()
        assertEquals(listOf(1), RefMatcher.find(fresh, o.children[0].ref())?.path)
    }

    @Test fun goneMeansNull() {
        val fresh = ScreenNode("r", children = listOf(btn("Other", top = 900))).withPaths()
        assertNull(RefMatcher.find(fresh, old.children[1].ref()))
    }
}
```

`SettleTest.kt`:

```kotlin
package io.github.secnewt.reader.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleTest {
    @Test fun quietAppSettlesAfter300ms() = runTest {
        assertTrue(Settle.await(MutableSharedFlow()))
        assertEquals(300, currentTime)
    }

    @Test fun waitsForChangesToStop() = runTest {
        val changes = MutableSharedFlow<Unit>()
        backgroundScope.launch { repeat(10) { delay(100); changes.emit(Unit) } }  // last change at 1000 ms
        assertTrue(Settle.await(changes))
        assertEquals(1300, currentTime)
    }

    @Test fun givesUpAt3s() = runTest {
        val changes = MutableSharedFlow<Unit>()
        backgroundScope.launch { while (true) { delay(100); changes.emit(Unit) } }
        assertFalse(Settle.await(changes))
        assertEquals(3000, currentTime)
    }
}
```

- [ ] **Step 2: Push and watch them fail**

```bash
git add -A && git commit -m "Merge, RefMatcher, Settle tests" && git push && gh pr checks --watch
```

Expected: compile FAILS (`Unresolved reference: Merge`, `RefMatcher`, `Settle`).

- [ ] **Step 3: Implement**

`Merge.kt`:

```kotlin
package io.github.secnewt.reader.engine

object Merge {
    /** The blocks in [fresh] that [shown] doesn't already have (same type + text), in order, each once. */
    fun appendNew(shown: List<Block>, fresh: List<Block>): List<Block> {
        val seen = shown.mapTo(HashSet()) { it.key }
        return fresh.filter { seen.add(it.key) }
    }
}
```

`RefMatcher.kt`:

```kotlin
package io.github.secnewt.reader.engine

import kotlin.math.abs

/**
 * Finds a node again in a fresh tree, because apps rebuild their trees all the time:
 * the same path if it still says the same thing, else the same view id and label,
 * else the same label and class nearest the old spot, else an unlabelled node of the
 * same class within 50 px of where it was.
 */
object RefMatcher {
    private const val NEAR_PX = 50

    fun find(root: ScreenNode, ref: NodeRef): ScreenNode? {
        fun same(n: ScreenNode) = n.cls == ref.cls && n.label == ref.label
        at(root, ref.path)?.let { if (same(it)) return it }
        val all = root.walk().toList()
        if (ref.viewId != null) all.firstOrNull { it.viewId == ref.viewId && it.label == ref.label }?.let { return it }
        if (ref.label != null) return all.filter(::same).minByOrNull { distance(it.bounds, ref.bounds) }
        return all.filter { it.cls == ref.cls && it.label == null && distance(it.bounds, ref.bounds) <= NEAR_PX }
            .minByOrNull { distance(it.bounds, ref.bounds) }
    }

    fun at(root: ScreenNode, path: List<Int>): ScreenNode? =
        path.fold(root as ScreenNode?) { n, i -> n?.children?.getOrNull(i) }

    private fun distance(a: Bounds, b: Bounds) = abs(a.left - b.left) + abs(a.top - b.top)
}
```

`Settle.kt`:

```kotlin
package io.github.secnewt.reader.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

/** Waits for an app to stop changing before Reader reads it (pages load, lists animate in). */
object Settle {
    const val QUIET_MS = 300L
    const val MAX_MS = 3_000L

    /** True once [changes] has been quiet for [quietMs]; false if it is still changing after [maxMs]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun await(changes: Flow<Unit>, quietMs: Long = QUIET_MS, maxMs: Long = MAX_MS): Boolean =
        withTimeoutOrNull(maxMs) {
            changes.onStart { emit(Unit) }.transformLatest { delay(quietMs); emit(Unit) }.first()
        } != null
}
```

- [ ] **Step 4: Push and watch them pass**

```bash
git add -A && git commit -m "Engine: merge, ref matcher, settle" && git push && gh pr checks --watch
```

Expected: PASS.

---

### Task 5: ReaderController, state and prefs

**Files:**
- Create: `app/src/main/java/io/github/secnewt/reader/reader/ScreenPort.kt`, `reader/ReaderState.kt`, `reader/ReaderController.kt`, `reader/ReaderPrefs.kt`
- Test: `app/src/test/java/io/github/secnewt/reader/reader/FakePort.kt`, `reader/ReaderControllerTest.kt`, `reader/ReaderStateTest.kt`, `reader/ReaderPrefsTest.kt`

**Interfaces:**
- Consumes: `Screen`, `NodeRef`, `Block`, `Span`, `TapKind`, `Stitcher`, `Merge` (Tasks 1–4).
- Produces:
  - `interface ScreenPort { suspend fun read(): Screen?; suspend fun tap(target: NodeRef): Boolean; suspend fun scrollForward(): Boolean; suspend fun back() }`
  - `enum Footer { NONE, LOADING, LOAD_MORE, END }`; `data class ReaderState(open, title, blocks, jumpTo: Int?, busy, footer, message: String?)`; `fun ReaderState.findTarget(label: String): Pair<NodeRef, TapKind>?`
  - `class ReaderController(port: ScreenPort, scope: CoroutineScope, onClosed: () -> Unit = {})` with `state: StateFlow<ReaderState>`, `open()`, `close()`, `tap(target: NodeRef, kind: TapKind)`, `back()`, `nearEnd()`, `loadMore()`, `jumpDone()`; constants `ReaderController.NOTHING_SHARED`, `CHANGED`.
  - `class ReaderPrefs(context)` with `var textScale: Float`, `var floatingButton: Boolean`, `watch(onChange: () -> Unit)`, `unwatch()`; companion `DEFAULT_SCALE = 1.5f`, `MIN_SCALE = 1.0f`, `MAX_SCALE = 3.0f`, `stepped(current: Float, up: Boolean): Float`.

- [ ] **Step 1: Write the failing tests**

`FakePort.kt`:

```kotlin
package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Screen
import io.github.secnewt.reader.engine.ScreenNode

/** Hands out [screens] in order; the last one repeats. Records what the controller did. */
class FakePort(vararg screens: Screen?) : ScreenPort {
    private val queue = ArrayDeque(screens.toList())
    val taps = mutableListOf<NodeRef>()
    var tapWorks = true
    var scrollWorks = true
    var scrolls = 0
    var backs = 0

    override suspend fun read(): Screen? = if (queue.size > 1) queue.removeFirst() else queue.firstOrNull()
    override suspend fun tap(target: NodeRef): Boolean { taps += target; return tapWorks }
    override suspend fun scrollForward(): Boolean { scrolls++; return scrollWorks }
    override suspend fun back() { backs++ }
}

/** A non-web screen: each line is a paragraph, "[Label]" is a button. Lines before [firstOnScreen] are off-screen. */
fun page(title: String, vararg lines: String, firstOnScreen: Int = 0): Screen =
    Screen("app", title, ScreenNode("root", children = lines.mapIndexed { i, t ->
        if (t.startsWith("[")) ScreenNode("android.widget.Button", text = t.trim('[', ']'), clickable = true, visible = i >= firstOnScreen)
        else ScreenNode("android.widget.TextView", text = t, visible = i >= firstOnScreen)
    }).withPaths())
```

`ReaderControllerTest.kt`:

```kotlin
package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.TapKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControllerTest {
    private fun TestScope.controller(port: FakePort, onClosed: () -> Unit = {}) = ReaderController(port, this, onClosed)
    private fun ReaderController.texts() = state.value.blocks.map { it.text }
    private fun ReaderController.button(label: String) =
        state.value.blocks.filterIsInstance<Block.Button>().single { it.text == label }.control

    @Test fun opensAtWhatWasOnScreen() = runTest {
        val c = controller(FakePort(page("Article", "a", "b", "c", "d", firstOnScreen = 2)))
        c.open(); advanceUntilIdle()
        with(c.state.value) {
            assertTrue(open); assertFalse(busy)
            assertEquals("Article", title)
            assertEquals(listOf("a", "b", "c", "d"), blocks.map { it.text })
            assertEquals(2, jumpTo)
        }
        c.jumpDone()
        assertNull(c.state.value.jumpTo)
    }

    @Test fun nothingSharedSaysSoThenCloses() = runTest {
        var closed = false
        val port = FakePort(null)
        val c = controller(port) { closed = true }
        c.open()
        testScheduler.advanceTimeBy(100)
        assertEquals(ReaderController.NOTHING_SHARED, c.state.value.message)
        advanceUntilIdle()
        assertFalse(c.state.value.open)
        assertTrue(closed)
    }

    @Test fun followTapStaysOpenAndRereads() = runTest {
        val port = FakePort(page("One", "intro", "[Next]"), page("Two", "second page"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        val next = c.button("Next")
        assertEquals(TapKind.FOLLOW, next.kind)
        c.tap(next.target, next.kind); advanceUntilIdle()
        assertEquals(listOf(next.target), port.taps)
        assertTrue(c.state.value.open)
        assertEquals(listOf("second page"), c.texts())
        assertEquals(0, c.state.value.jumpTo)
    }

    @Test fun followThatChangesNothingStaysInPlace() = runTest {
        val port = FakePort(page("One", "intro", "[Show more]"))
        val c = controller(port)
        c.open(); advanceUntilIdle(); c.jumpDone()
        val more = c.button("Show more")
        c.tap(more.target, more.kind); advanceUntilIdle()
        assertNull(c.state.value.jumpTo)
    }

    @Test fun closeFirstTapClosesThenTaps() = runTest {
        var closed = false
        val port = FakePort(page("Chat", "hello", "[Send]"))
        val c = controller(port) { closed = true }
        c.open(); advanceUntilIdle()
        val send = c.button("Send")
        assertEquals(TapKind.CLOSE_FIRST, send.kind)
        c.tap(send.target, send.kind)
        assertFalse(c.state.value.open)
        assertTrue(closed)
        advanceUntilIdle()
        assertEquals(listOf(send.target), port.taps)
    }

    @Test fun failedTapSaysItChanged() = runTest {
        val port = FakePort(page("One", "intro", "[Next]")).apply { tapWorks = false }
        val c = controller(port)
        c.open(); advanceUntilIdle()
        val next = c.button("Next")
        c.tap(next.target, next.kind); advanceUntilIdle()
        assertEquals(ReaderController.CHANGED, c.state.value.message)
        assertTrue(c.state.value.open)
    }

    @Test fun backAfterFollowGoesBackInTheApp() = runTest {
        val port = FakePort(page("One", "intro", "[Next]"), page("Two", "second"), page("One", "intro", "[Next]"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.button("Next").let { c.tap(it.target, it.kind) }; advanceUntilIdle()
        c.back(); advanceUntilIdle()
        assertEquals(1, port.backs)
        assertEquals("intro", c.texts().first())
        c.back(); advanceUntilIdle()
        assertEquals(1, port.backs)
        assertFalse(c.state.value.open)
    }

    @Test fun backOnFirstPageCloses() = runTest {
        val port = FakePort(page("One", "intro"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.back(); advanceUntilIdle()
        assertEquals(0, port.backs)
        assertFalse(c.state.value.open)
    }

    @Test fun nearEndAppendsOnlyNewBlocks() = runTest {
        val port = FakePort(page("Feed", "a", "b"), page("Feed", "b", "c"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.nearEnd(); advanceUntilIdle()
        assertEquals(1, port.scrolls)
        assertEquals(listOf("a", "b", "c"), c.texts())
        assertEquals(Footer.NONE, c.state.value.footer)
    }

    @Test fun loadMoreFallbackThenEnd() = runTest {
        val port = FakePort(page("Feed", "a"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.nearEnd(); advanceUntilIdle()
        assertEquals(Footer.LOAD_MORE, c.state.value.footer)
        c.nearEnd(); advanceUntilIdle()
        assertEquals(1, port.scrolls)  // nearEnd does nothing while the button is showing
        c.loadMore(); advanceUntilIdle()
        assertEquals(Footer.END, c.state.value.footer)
    }

    @Test fun closeResets() = runTest {
        val c = controller(FakePort(page("One", "intro")))
        c.open(); advanceUntilIdle()
        c.close()
        assertEquals(ReaderState(), c.state.value)
    }
}
```

`ReaderStateTest.kt`:

```kotlin
package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Bounds
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderStateTest {
    private fun ref(n: Int) = NodeRef(listOf(n), null, null, "x", Bounds(0, 0, 0, 0))
    private val state = ReaderState(open = true, blocks = listOf(
        Block.Paragraph(listOf(Span.Plain("See the "), Span.Link("Next page", ref(1))), true),
        Block.Button(Control("Send", ref(2), TapKind.CLOSE_FIRST), true),
        Block.ControlRow(listOf(Control("Like", ref(3), TapKind.CLOSE_FIRST)), true),
        Block.ListItem("A forecast row that is long", ref(4), true),
        Block.Field("Search", ref(5), false, true),
        Block.Heading("Causes", true, ref(6)),
    ))

    @Test fun findsEveryKindOfTarget() {
        assertEquals(ref(1) to TapKind.FOLLOW, state.findTarget("Next page"))
        assertEquals(ref(2) to TapKind.CLOSE_FIRST, state.findTarget("Send"))
        assertEquals(ref(3) to TapKind.CLOSE_FIRST, state.findTarget("Like"))
        assertEquals(ref(4) to TapKind.FOLLOW, state.findTarget("A forecast row that is long"))
        assertEquals(ref(5) to TapKind.CLOSE_FIRST, state.findTarget("Search"))
        assertEquals(ref(6) to TapKind.FOLLOW, state.findTarget("Causes"))
        assertNull(state.findTarget("Nope"))
    }
}
```

`ReaderPrefsTest.kt`:

```kotlin
package io.github.secnewt.reader.reader

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReaderPrefsTest {
    private val prefs = ReaderPrefs(ApplicationProvider.getApplicationContext())

    @Test fun defaults() {
        assertEquals(1.5f, prefs.textScale)
        assertFalse(prefs.floatingButton)
    }

    @Test fun remembersAndClamps() {
        prefs.textScale = 9f
        assertEquals(3.0f, prefs.textScale)
        prefs.floatingButton = true
        assertTrue(ReaderPrefs(ApplicationProvider.getApplicationContext()).floatingButton)
    }

    @Test fun stepsByTenths() {
        assertEquals(1.6f, ReaderPrefs.stepped(1.5f, up = true))
        assertEquals(1.4f, ReaderPrefs.stepped(1.5f, up = false))
        assertEquals(1.0f, ReaderPrefs.stepped(1.0f, up = false))
        assertEquals(3.0f, ReaderPrefs.stepped(3.0f, up = true))
        assertEquals(1.7f, ReaderPrefs.stepped(1.63f, up = true))
    }

    @Test fun watchSeesChanges() {
        var seen = 0
        prefs.watch { seen++ }
        prefs.floatingButton = !prefs.floatingButton
        prefs.unwatch()
        assertTrue(seen >= 1)
    }
}
```

`androidx.test.core` is already on the test classpath through `androidx.test.ext:junit`.

- [ ] **Step 2: Push and watch them fail**

```bash
git add -A && git commit -m "Reader controller, state and prefs tests" && git push && gh pr checks --watch
```

Expected: compile FAILS (`Unresolved reference: ScreenPort`, `ReaderController`, `ReaderPrefs`).

- [ ] **Step 3: Implement**

`reader/ScreenPort.kt`:

```kotlin
package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Screen

/** Everything the controller needs from the phone. The real one is ServicePort; tests use a fake. */
interface ScreenPort {
    /** Waits for the app to stop changing, then reads it. Null if the app shares nothing. */
    suspend fun read(): Screen?
    /** Taps the real control. False if it can't be found any more. */
    suspend fun tap(target: NodeRef): Boolean
    /** Scrolls the app's main list forward. False if there's nothing to scroll. */
    suspend fun scrollForward(): Boolean
    /** Android's Back, delivered to the app under Reader. */
    suspend fun back()
}
```

`reader/ReaderState.kt`:

```kotlin
package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind

/** What's at the bottom of the reading view. */
enum class Footer { NONE, LOADING, LOAD_MORE, END }

data class ReaderState(
    val open: Boolean = false,
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    /** Scroll here once (then call jumpDone): the first on-screen block when opening, 0 after following a link. */
    val jumpTo: Int? = null,
    /** Shows the slim "Loading…" bar. */
    val busy: Boolean = false,
    val footer: Footer = Footer.NONE,
    val message: String? = null,
)

/** The tappable thing labelled [label], and what tapping it does. Used by the emulator test. */
fun ReaderState.findTarget(label: String): Pair<NodeRef, TapKind>? {
    for (b in blocks) {
        when (b) {
            is Block.Paragraph -> b.spans.filterIsInstance<Span.Link>().firstOrNull { it.text == label }?.let { return it.target to TapKind.FOLLOW }
            is Block.Button -> if (b.control.label == label) return b.control.target to b.control.kind
            is Block.ControlRow -> b.controls.firstOrNull { it.label == label }?.let { return it.target to it.kind }
            is Block.ListItem -> if (b.text == label) return b.target to TapKind.FOLLOW
            is Block.Field -> if (b.label == label) return b.target to TapKind.CLOSE_FIRST
            is Block.Heading -> if (b.text == label && b.target != null) return b.target to TapKind.FOLLOW
            is Block.Image -> Unit
        }
    }
    return null
}
```

`reader/ReaderController.kt`:

```kotlin
package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Merge
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Screen
import io.github.secnewt.reader.engine.Stitcher
import io.github.secnewt.reader.engine.TapKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reader's behaviour, with the phone behind [port]. One thing runs at a time: a new action
 * cancels the one before. [onClosed] runs every time Reader closes.
 */
class ReaderController(
    private val port: ScreenPort,
    private val scope: CoroutineScope,
    private val onClosed: () -> Unit = {},
) {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /** How many links deep we've followed; Back at 0 closes Reader. */
    private var depth = 0
    private var emptyLoads = 0
    private var job: Job? = null

    fun open() = run {
        depth = 0
        emptyLoads = 0
        _state.value = ReaderState(open = true, busy = true)
        var blocks = port.read()?.let(::stitched)
        if (blocks.isNullOrEmpty()) blocks = port.read()?.let(::stitched)
        val screen = lastScreen
        if (blocks.isNullOrEmpty() || screen == null) return@run nothingShared()
        show(screen, blocks, jumpTo = blocks.indexOfFirst { it.onScreen }.coerceAtLeast(0))
    }

    fun close() {
        job?.cancel()
        _state.value = ReaderState()
        onClosed()
    }

    fun tap(target: NodeRef, kind: TapKind) {
        if (kind == TapKind.CLOSE_FIRST) {
            close()
            scope.launch {
                delay(CLOSE_BEFORE_TAP_MS)  // let the overlay go so the app gets focus (keyboard, playback)
                port.tap(target)
            }
            return
        }
        run {
            _state.update { it.copy(busy = true, message = null) }
            val worked = port.tap(target)
            if (worked) depth++
            reread(message = if (worked) null else CHANGED)
        }
    }

    fun back() {
        if (depth == 0) return close()
        run {
            _state.update { it.copy(busy = true, message = null) }
            port.back()
            depth--
            reread(message = null)
        }
    }

    /** The reading view scrolled near its end: load more, unless a load or the fallback button is already showing. */
    fun nearEnd() {
        val s = _state.value
        if (s.open && !s.busy && s.footer == Footer.NONE) loadMore()
    }

    fun loadMore() = run {
        _state.update { it.copy(footer = Footer.LOADING) }
        val fresh = if (port.scrollForward()) port.read()?.let(::stitched).orEmpty() else emptyList()
        val added = Merge.appendNew(_state.value.blocks, fresh)
        if (added.isNotEmpty()) {
            emptyLoads = 0
            _state.update { it.copy(blocks = it.blocks + added, footer = Footer.NONE) }
        } else {
            emptyLoads++
            _state.update { it.copy(footer = if (emptyLoads >= 2) Footer.END else Footer.LOAD_MORE) }
        }
    }

    fun jumpDone() = _state.update { it.copy(jumpTo = null) }

    private var lastScreen: Screen? = null

    private fun stitched(screen: Screen): List<Block> {
        lastScreen = screen
        return Stitcher.stitch(screen)
    }

    private suspend fun reread(message: String?) {
        emptyLoads = 0
        val before = _state.value.blocks.firstOrNull()?.key
        val blocks = port.read()?.let(::stitched)
        val screen = lastScreen
        if (blocks.isNullOrEmpty() || screen == null) return nothingShared()
        // Same first block = the page didn't change (e.g. "Show more" expanded in place): keep the reader's place.
        show(screen, blocks, jumpTo = if (blocks.first().key == before) null else 0, message = message)
    }

    private suspend fun nothingShared() {
        _state.value = ReaderState(open = true, message = NOTHING_SHARED)
        delay(NOTHING_SHARED_MS)
        close()
    }

    private fun show(screen: Screen, blocks: List<Block>, jumpTo: Int?, message: String? = null) {
        _state.value = ReaderState(open = true, title = screen.title, blocks = blocks, jumpTo = jumpTo, message = message)
    }

    private fun run(action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { action() }
    }

    companion object {
        const val NOTHING_SHARED = "This app isn't sharing its text"
        const val CHANGED = "That changed — here's the screen now"
        private const val NOTHING_SHARED_MS = 2_000L
        private const val CLOSE_BEFORE_TAP_MS = 150L
    }
}
```

`reader/ReaderPrefs.kt`:

```kotlin
package io.github.secnewt.reader.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.roundToInt

/** Reader's only saved settings: text size and the floating button. Nothing Reader reads is stored. */
class ReaderPrefs(context: Context) {
    private val sp = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    var textScale: Float
        get() = sp.getFloat(KEY_SCALE, DEFAULT_SCALE)
        set(value) = sp.edit { putFloat(KEY_SCALE, value.coerceIn(MIN_SCALE, MAX_SCALE)) }

    var floatingButton: Boolean
        get() = sp.getBoolean(KEY_BUBBLE, false)
        set(value) = sp.edit { putBoolean(KEY_BUBBLE, value) }

    fun watch(onChange: () -> Unit) {
        unwatch()
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
            .also(sp::registerOnSharedPreferenceChangeListener)
    }

    fun unwatch() {
        listener?.let(sp::unregisterOnSharedPreferenceChangeListener)
        listener = null
    }

    companion object {
        const val DEFAULT_SCALE = 1.5f
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 3.0f
        private const val KEY_SCALE = "text_scale"
        private const val KEY_BUBBLE = "floating_button"

        /** One A−/A+ press: the nearest tenth, one step up or down, kept in range. */
        fun stepped(current: Float, up: Boolean): Float {
            val tenths = (current * 10).roundToInt() + if (up) 1 else -1
            return (tenths / 10f).coerceIn(MIN_SCALE, MAX_SCALE)
        }
    }
}
```

- [ ] **Step 4: Push and watch them pass**

```bash
git add -A && git commit -m "Reader controller, state and prefs" && git push && gh pr checks --watch
```

Expected: PASS. Note `stepped(1.63f, up = true)`: 16 + 1 = 17 → 1.7 ✓.

---

### Task 6: The Reader overlay UI

**Files:**
- Create: `app/src/main/java/io/github/secnewt/reader/ui/BlockView.kt`, `ui/ReaderScreen.kt`
- Test: `app/src/test/java/io/github/secnewt/reader/ui/ReaderScreenTest.kt`

**Interfaces:**
- Consumes: `ReaderState`, `Footer`, `ReaderController`, `ReaderPrefs` (Task 5); `Block`, `Span`, `Control`, `TapKind`, `NodeRef` (Task 2); `NewtTheme`, `NewtFonts`, `neonOutline` from newt-ui.
- Produces: `@Composable ReaderScreen(state, textScale, onTextScale, onTap: (NodeRef, TapKind) -> Unit, onClose, onNearEnd, onLoadMore, onJumpDone)`; `@Composable ReaderRoute(controller: ReaderController, prefs: ReaderPrefs)`; `@Composable BlockView(block, onTap)`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/io/github/secnewt/reader/ui/ReaderScreenTest.kt`:

```kotlin
package io.github.secnewt.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Bounds
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderState
import io.github.secnewt.test.newtScreenshot
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ReaderScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val ref = NodeRef(emptyList(), null, null, "x", Bounds(0, 0, 0, 0))
    private val sample = ReaderState(
        open = true,
        title = "Visual impairment - Wikipedia",
        blocks = listOf(
            Block.Heading("Visual impairment", true),
            Block.Paragraph(listOf(
                Span.Plain("Visual or vision impairment is the partial or total inability of "),
                Span.Link("visual perception", ref),
                Span.Plain(". Without treatment it can make everyday tasks hard."),
            ), true),
            Block.ControlRow(listOf(Control("Article", ref, TapKind.FOLLOW), Control("Talk", ref, TapKind.FOLLOW), Control("Edit", ref, TapKind.CLOSE_FIRST)), true),
            Block.ListItem("Today: sunny. High 81. Low 58.", ref, true),
            Block.Button(Control("Send", ref, TapKind.CLOSE_FIRST), true),
            Block.Field("Search", ref, false, true),
            Block.Image("A Snellen eye chart", true),
            Block.Heading("Causes", true, ref),
        ),
    )

    @Composable private fun Screen(state: ReaderState = sample, onTap: (NodeRef, TapKind) -> Unit = { _, _ -> }) =
        ReaderScreen(state, 1.5f, {}, onTap, {}, {}, {}, {})

    @Test fun dark() {
        composeRule.newtScreenshot("reader-dark", dark = true) { Screen() }
        composeRule.onNodeWithText("visual perception", substring = true).assertIsDisplayed()
    }

    @Test fun light() = composeRule.newtScreenshot("reader-light", dark = false) { Screen() }

    @Test fun largeText() = composeRule.newtScreenshot("reader-200pct", dark = true, fontScale = 2f) { Screen() }

    @Test fun busyWithMessage() = composeRule.newtScreenshot("reader-busy", dark = true) {
        Screen(sample.copy(busy = true, message = "That changed — here's the screen now"))
    }

    @Test fun loadMoreFooter() = composeRule.newtScreenshot("reader-load-more", dark = true) {
        Screen(sample.copy(blocks = sample.blocks.take(2), footer = Footer.LOAD_MORE))
    }

    @Test fun tappingSendReportsCloseFirst() {
        var tapped: TapKind? = null
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen { _, kind -> tapped = kind } } }
        composeRule.onNodeWithText("Send", substring = true).performClick()
        assertEquals(TapKind.CLOSE_FIRST, tapped)
    }
}
```


- [ ] **Step 2: Push and watch them fail**

```bash
git add -A && git commit -m "Reader overlay screenshot tests" && git push && gh pr checks --watch
```

Expected: compile FAILS (`Unresolved reference: ReaderScreen`).

- [ ] **Step 3: Implement**

`ui/BlockView.kt`:

```kotlin
package io.github.secnewt.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.neonOutline

/** Body text with generous line spacing: at Reader's default size a line is about 48 dp, so inline links are easy to hit. */
@Composable
fun readingStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 2.em)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockView(block: Block, onTap: (NodeRef, TapKind) -> Unit) {
    when (block) {
        is Block.Heading -> {
            val tap = block.target?.let { t -> Modifier.clickable(onClickLabel = "Expand") { onTap(t, TapKind.FOLLOW) }.heightIn(min = 48.dp) } ?: Modifier
            Text(
                if (block.target != null) "${block.text}  ▸" else block.text,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = NewtFonts.Display),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().then(tap).semantics { heading() },
            )
        }
        is Block.Paragraph -> Text(paragraphText(block, onTap), style = readingStyle())
        is Block.ListItem -> Surface(
            onClick = { onTap(block.target, TapKind.FOLLOW) },
            shape = RoundedCornerShape(12.dp),
            border = neonOutline(),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(block.text, style = readingStyle(), modifier = Modifier.padding(16.dp)) }
        is Block.Button -> ReaderButton(block.control.label, block.control.kind) { onTap(block.control.target, block.control.kind) }
        is Block.ControlRow -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.controls.forEach { c ->
                OutlinedButton(onClick = { onTap(c.target, c.kind) }, border = neonOutline(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (c.kind == TapKind.CLOSE_FIRST) "${c.label} ↗" else c.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        is Block.Field -> ReaderButton(
            if (block.password) "✎ Type in: ••• (hidden)" else "✎ Type in: ${block.label}",
            TapKind.CLOSE_FIRST,
        ) { onTap(block.target, TapKind.CLOSE_FIRST) }
        is Block.Image -> Text(
            block.description?.let { "🖼 Image: $it" } ?: "🖼 Image",
            style = readingStyle().copy(fontStyle = FontStyle.Italic),
        )
    }
}

/** A full-width 56 dp Newt button; ↗ marks one that leaves Reader. */
@Composable
fun ReaderButton(label: String, kind: TapKind, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, border = neonOutline(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (kind == TapKind.CLOSE_FIRST) {
            Text("↗", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "leaves Reader" })
        }
    }
}

@Composable
private fun paragraphText(p: Block.Paragraph, onTap: (NodeRef, TapKind) -> Unit): AnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.tertiary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        p.spans.forEach { s ->
            when (s) {
                is Span.Plain -> append(s.text)
                is Span.Link -> withLink(LinkAnnotation.Clickable(s.text, linkStyle) { onTap(s.target, TapKind.FOLLOW) }) { append(s.text) }
            }
        }
    }
}
```

`ui/ReaderScreen.kt`:

```kotlin
package io.github.secnewt.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.reader.ReaderState
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.NewtTheme
import io.github.secnewt.ui.neonOutline
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Connects the overlay to the controller and the saved text size. */
@Composable
fun ReaderRoute(controller: ReaderController, prefs: ReaderPrefs) {
    val state by controller.state.collectAsState()
    var scale by remember { mutableFloatStateOf(prefs.textScale) }
    NewtTheme {
        ReaderScreen(
            state = state,
            textScale = scale,
            onTextScale = { scale = it; prefs.textScale = it },
            onTap = controller::tap,
            onClose = controller::close,
            onNearEnd = controller::nearEnd,
            onLoadMore = controller::loadMore,
            onJumpDone = controller::jumpDone,
        )
    }
}

@Composable
fun ReaderScreen(
    state: ReaderState,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    onTap: (NodeRef, TapKind) -> Unit,
    onClose: () -> Unit,
    onNearEnd: () -> Unit,
    onLoadMore: () -> Unit,
    onJumpDone: () -> Unit,
) {
    val list = rememberLazyListState()
    val nearEnd by rememberUpdatedState(onNearEnd)
    val currentScale by rememberUpdatedState(textScale)

    LaunchedEffect(state.jumpTo) {
        state.jumpTo?.let { list.scrollToItem(it); onJumpDone() }
    }
    // "Near the end" = the last three items are on screen.
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect { nearEnd() }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(state.title, textScale, onTextScale, onClose)
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading" })
            }
            state.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val density = LocalDensity.current
            // Reader's own text size, on top of the phone's; the top bar above stays normal size.
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().pinchToScale({ currentScale }, onTextScale),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.blocks.size) { i -> BlockView(state.blocks[i], onTap) }
                    item { FooterView(state.footer, onLoadMore) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, scale: Float, onScale: (Float) -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title.ifBlank { "Newt Reader" },
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = NewtFonts.Display),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        BarButton("A−", "Smaller text") { onScale(ReaderPrefs.stepped(scale, up = false)) }
        BarButton("A+", "Bigger text") { onScale(ReaderPrefs.stepped(scale, up = true)) }
        BarButton("Close", null, onClose)
    }
}

@Composable
private fun BarButton(label: String, description: String?, onClick: () -> Unit) {
    val talk = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    OutlinedButton(onClick = onClick, border = neonOutline(), modifier = Modifier.heightIn(min = 56.dp).then(talk)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FooterView(footer: Footer, onLoadMore: () -> Unit) {
    when (footer) {
        Footer.NONE -> Spacer(Modifier.height(1.dp))
        Footer.LOADING -> Text("Loading more…", style = readingStyle())
        Footer.LOAD_MORE -> ReaderButton("Load more", TapKind.FOLLOW, onLoadMore)
        Footer.END -> Text("That's everything this app is showing.", style = readingStyle())
    }
}

/** Two-finger pinch changes Reader's text size; one finger still scrolls. */
private fun Modifier.pinchToScale(current: () -> Float, onScale: (Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        var scale = current()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    scale = (scale * zoom).coerceIn(ReaderPrefs.MIN_SCALE, ReaderPrefs.MAX_SCALE)
                    onScale(scale)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
```

- [ ] **Step 4: Push, watch them pass, look at the screenshots**

```bash
git add -A && git commit -m "Reader overlay UI" && git push && gh pr checks --watch
gh run download -n screenshots -D /tmp/claude-reader-shots $(gh run list -b v1-reader -w "Build APK" -L 1 --json databaseId -q '.[0].databaseId')
```

Expected: PASS. Open each `reader-*.png` (Read tool) and check: text not clipped at 200%, links cyan and underlined, ↗ on Send / Edit / Search, top bar readable. Fix anything that looks wrong before moving on.

---

### Task 7: Settings screen, launcher, icon and strings

**Files:**
- Create: `app/src/main/java/io/github/secnewt/reader/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/io/github/secnewt/reader/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml:64-66`, `app/src/main/res/drawable/ic_launcher_monochrome.xml:16`
- Delete: `app/src/main/java/io/github/secnewt/reader/ui/WelcomeScreen.kt`, `app/src/test/java/io/github/secnewt/reader/ui/WelcomeScreenTest.kt`
- Test: `app/src/test/java/io/github/secnewt/reader/ui/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `ReaderPrefs` (Task 5); `SectionHeading`, `neonOutline`, `NewtFonts` (newt-ui).
- Produces: `@Composable SettingsScreen(serviceOn, textScale, onTextScale, floatingButton, onFloatingButton, onOpenAccessibilitySettings)`; strings `service_summary`, `service_description`, `tile_label`.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.secnewt.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.test.newtScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun offDark() {
        composeRule.newtScreenshot("settings-off-dark", dark = true) { SettingsScreen(false, 1.5f, {}, false, {}, {}) }
        composeRule.onNodeWithText("Reader is off", substring = true).assertIsDisplayed()
    }

    @Test fun onLight() {
        composeRule.newtScreenshot("settings-on-light", dark = false) { SettingsScreen(true, 1.5f, {}, true, {}, {}) }
        composeRule.onNodeWithText("Reader is on", substring = true).assertIsDisplayed()
    }

    @Test fun largeText() = composeRule.newtScreenshot("settings-200pct", dark = true, fontScale = 2f) {
        SettingsScreen(false, 2.0f, {}, false, {}, {})
    }
}
```

Delete the welcome screen and its test in the same commit:

```bash
git rm app/src/main/java/io/github/secnewt/reader/ui/WelcomeScreen.kt app/src/test/java/io/github/secnewt/reader/ui/WelcomeScreenTest.kt
```

- [ ] **Step 2: Push and watch it fail**

```bash
git add -A && git commit -m "Settings screen tests; drop the template welcome screen" && git push && gh pr checks --watch
```

Expected: compile FAILS (`Unresolved reference: SettingsScreen`, and `MainActivity` can't find `WelcomeScreen`).

- [ ] **Step 3: Implement**

`ui/SettingsScreen.kt`:

```kotlin
package io.github.secnewt.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.secnewt.reader.R
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.SectionHeading
import io.github.secnewt.ui.neonOutline
import kotlin.math.roundToInt

/** The launcher screen: whether Reader is on, how to turn it on, and its two settings. */
@Composable
fun SettingsScreen(
    serviceOn: Boolean,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    floatingButton: Boolean,
    onFloatingButton: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (serviceOn) "Reader is on. Swipe up with two fingers, or tap the Read screen tile in Quick Settings, to read any screen."
                else "Reader is off. Turn it on in Accessibility settings, then choose it for the two-finger swipe up.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = onOpenAccessibilitySettings, border = neonOutline(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Open accessibility settings", style = MaterialTheme.typography.titleMedium)
            }
            if (!serviceOn) {
                Text(
                    "If Android says the setting is restricted: open App info for Newt Reader, tap ⋮, then Allow restricted settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionHeading("Text size")
            Text("How big Reader draws text. Your phone's own text size doesn't change.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onTextScale(ReaderPrefs.stepped(textScale, up = false)) },
                    border = neonOutline(),
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Smaller text" },
                ) { Text("A−", style = MaterialTheme.typography.titleMedium) }
                Text(
                    "${(textScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = NewtFonts.Mono),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onTextScale(ReaderPrefs.stepped(textScale, up = true)) },
                    border = neonOutline(),
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Bigger text" },
                ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
            }
            Text(
                "Sample: the quick brown fox reads the news.",
                style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * textScale) },
            )

            SectionHeading("Floating button")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Shows a small Newt button on the edge of the screen. Tap it to read; drag it out of the way.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = floatingButton,
                    onCheckedChange = onFloatingButton,
                    modifier = Modifier.semantics { contentDescription = "Floating button" },
                )
            }
        }
    }
}
```

Replace `MainActivity.kt`:

```kotlin
package io.github.secnewt.reader

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.ui.SettingsScreen
import io.github.secnewt.ui.NewtTheme

class MainActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ReaderPrefs(this)
        setContent {
            NewtTheme {
                var scale by remember { mutableFloatStateOf(prefs.textScale) }
                var bubble by remember { mutableStateOf(prefs.floatingButton) }
                SettingsScreen(
                    serviceOn = serviceOn,
                    textScale = scale,
                    onTextScale = { scale = it; prefs.textScale = it },
                    floatingButton = bubble,
                    onFloatingButton = { bubble = it; prefs.floatingButton = it },
                    onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Newt Reader</string>
    <string name="service_summary">Redraws any screen as large, readable text. Swipe up with two fingers to read.</string>
    <string name="service_description">Reads the screen when you ask, to redraw it larger. Taps only what you tap. Nothing it reads is saved or sent anywhere, and Newt Reader has no internet access.</string>
    <string name="tile_label">Read screen</string>
</resources>
```

Icon badge: orange (`#FF9E64`, Tokyo Night orange) with a dark open book. In `ic_launcher_foreground.xml`, replace the badge line `<path android:fillColor="#FFBB9AF7" android:pathData="M58,75 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0 Z" />` with:

```xml
        <path android:fillColor="#FFFF9E64" android:pathData="M58,75 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0 Z" />
        <!-- Reader's badge symbol: an open book. -->
        <path android:fillColor="#FF0B0C14" android:pathData="M65,67 L74,69.5 L74,83 L65,80.5 Z M76,69.5 L85,67 L85,80.5 L76,83 Z" />
```

and update the header comment's first line to `<!-- Newt Reader icon: the Tokyo Night blue-to-purple newt with an orange badge holding an open book.`

In `ic_launcher_monochrome.xml`, replace the last path (the badge) with a badge that has the book cut out:

```xml
        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M58,75 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0 Z M65,67 L74,69.5 L74,83 L65,80.5 Z M76,69.5 L85,67 L85,80.5 L76,83 Z" />
```

- [ ] **Step 4: Push and watch it pass**

```bash
git add -A && git commit -m "Settings screen, launcher and Reader icon badge" && git push && gh pr checks --watch
```

Expected: PASS. Download screenshots as in Task 6 and check `settings-*.png`.

---

### Task 8: Android glue: service, overlay, tile, floating button

No unit tests here: everything is Android framework calls. The gates are compile + lint + the permission audit in CI, then the emulator test (Task 9) and Jerry's phone.

**Files:**
- Create: `app/src/main/java/io/github/secnewt/reader/engine/AndroidSnapshot.kt`, `reader/ServicePort.kt`, `service/OverlayOwner.kt`, `service/OverlayHost.kt`, `service/FloatingButton.kt`, `service/ReaderTileService.kt`, `service/ReaderService.kt`, `app/src/release/java/io/github/secnewt/reader/service/DebugHooks.kt`, `app/src/debug/java/io/github/secnewt/reader/service/DebugHooks.kt` (no-op for now; Task 9 fills it in), `app/src/main/res/xml/reader_service.xml`, `app/src/main/res/drawable/ic_tile.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: everything from Tasks 1–7.
- Produces: `AndroidSnapshot.capture(root: AccessibilityNodeInfo, title: String): Screen`, `AndroidSnapshot.liveAt(root, path): AccessibilityNodeInfo?`; `class ServicePort(service, changes: Flow<Unit>, globalBack: suspend () -> Unit) : ScreenPort`; `class OverlayHost(service)` with `showing`, `show(onBack, content)`, `hide()`, `suspend withoutFocus(block)`; `class FloatingButton(service, onTap)` with `show()`, `hide()`; `class ReaderService` with `companion running: ReaderService?`, `toggle()`, `open()`, `openFromTile()`; `object DebugHooks { fun register(context: Context, scope: CoroutineScope, controller: ReaderController, open: () -> Unit) }`.

- [ ] **Step 1: Snapshot and port**

`engine/AndroidSnapshot.kt`:

```kotlin
package io.github.secnewt.reader.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** The one place the engine touches Android: copies a live accessibility tree into ScreenNodes. */
object AndroidSnapshot {
    /** Huge pages are cut off here so a runaway tree can't stall Reader (The Verge was 1,112). */
    private const val MAX_NODES = 5_000

    fun capture(root: AccessibilityNodeInfo, title: String): Screen {
        var count = 0
        fun copy(n: AccessibilityNodeInfo, path: List<Int>): ScreenNode {
            count++
            val r = Rect().also(n::getBoundsInScreen)
            val kids = if (count > MAX_NODES) emptyList()
            else (0 until n.childCount).mapNotNull { i -> n.getChild(i)?.let { copy(it, path + i) } }
            return ScreenNode(
                cls = n.className?.toString().orEmpty(),
                text = n.text?.toString(),
                desc = n.contentDescription?.toString(),
                hint = n.hintText?.toString(),
                viewId = n.viewIdResourceName,
                clickable = n.isClickable,
                scrollable = n.isScrollable,
                editable = n.isEditable,
                password = n.isPassword,
                heading = n.isHeading,
                visible = n.isVisibleToUser,
                bounds = Bounds(r.left, r.top, r.right, r.bottom),
                path = path,
                children = kids,
            )
        }
        return Screen(root.packageName?.toString().orEmpty(), title, copy(root, emptyList()))
    }

    /** Follows [path] (child indexes) from [root] in the live tree. */
    fun liveAt(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? =
        path.fold(root as AccessibilityNodeInfo?) { n, i -> n?.getChild(i) }
}
```

`reader/ServicePort.kt`:

```kotlin
package io.github.secnewt.reader.reader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.github.secnewt.reader.engine.AndroidSnapshot
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.RefMatcher
import io.github.secnewt.reader.engine.Screen
import io.github.secnewt.reader.engine.Settle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * ScreenPort on a real phone. Reads the top app window: Reader's own overlay is an
 * accessibility-overlay window, never an application window, so it's never read.
 */
class ServicePort(
    private val service: AccessibilityService,
    private val changes: Flow<Unit>,
    private val globalBack: suspend () -> Unit,
) : ScreenPort {
    override suspend fun read(): Screen? {
        Settle.await(changes)
        val window = appWindow() ?: return null
        val root = window.root ?: return null
        val title = window.title?.toString().orEmpty()
        return withContext(Dispatchers.Default) { AndroidSnapshot.capture(root, title) }
    }

    override suspend fun tap(target: NodeRef): Boolean {
        val root = appWindow()?.root ?: return false
        val fresh = withContext(Dispatchers.Default) { AndroidSnapshot.capture(root, "") }
        val match = RefMatcher.find(fresh.root, target) ?: return false
        return AndroidSnapshot.liveAt(root, match.path)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override suspend fun scrollForward(): Boolean {
        val root = appWindow()?.root ?: return false
        val fresh = withContext(Dispatchers.Default) { AndroidSnapshot.capture(root, "") }
        val list = fresh.root.walk().filter { it.scrollable && it.visible }.maxByOrNull { it.bounds.area } ?: return false
        return AndroidSnapshot.liveAt(root, list.path)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    override suspend fun back() = globalBack()

    private fun appWindow(): AccessibilityWindowInfo? =
        service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
}
```

- [ ] **Step 2: Overlay windows**

`service/OverlayOwner.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Compose needs a lifecycle; a service window doesn't have one, so each overlay gets its own. Use once. */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun attach(view: View) {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun detach() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
```

`service/OverlayHost.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * The full-screen Reader window. It's an accessibility overlay, so it needs no "draw over
 * other apps" permission and the app underneath keeps running. It takes focus so Back
 * comes to Reader; [withoutFocus] hands focus back to the app for one action.
 */
class OverlayHost(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var owner: OverlayOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    )

    val showing: Boolean get() = root != null

    fun show(onBack: () -> Unit, content: @Composable () -> Unit) {
        if (root != null) return
        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onBack()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.addView(ComposeView(service).apply { setContent(content) })
        val o = OverlayOwner().also { it.attach(frame) }
        wm.addView(frame, params)
        root = frame
        owner = o
    }

    fun hide() {
        root?.let(wm::removeView)
        owner?.detach()
        root = null
        owner = null
    }

    suspend fun withoutFocus(block: suspend () -> Unit) {
        val v = root ?: return block()
        setFocusable(v, false)
        try { block() } finally { if (root === v) setFocusable(v, true) }
    }

    private fun setFocusable(v: View, focusable: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.flags = if (focusable) params.flags and flag.inv() else params.flags or flag
        wm.updateViewLayout(v, params)
    }
}
```

`service/FloatingButton.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.ui.NewtTheme
import io.github.secnewt.ui.neonOutline
import kotlin.math.roundToInt

/** The optional floating Newt button (off by default). Tap to read; drag to move. */
class FloatingButton(private val service: AccessibilityService, private val onTap: () -> Unit) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var owner: OverlayOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 600
    }

    fun show() {
        if (view != null) return
        val v = ComposeView(service).apply { setContent { NewtTheme { Bubble(onTap, ::move) } } }
        val o = OverlayOwner().also { it.attach(v) }
        wm.addView(v, params)
        view = v
        owner = o
    }

    fun hide() {
        view?.let(wm::removeView)
        owner?.detach()
        view = null
        owner = null
    }

    private fun move(dx: Float, dy: Float) {
        val v = view ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        wm.updateViewLayout(v, params)
    }
}

@Composable
private fun Bubble(onTap: () -> Unit, onDrag: (Float, Float) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        border = neonOutline(2.dp),
        modifier = Modifier
            .size(64.dp)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
            .clickable(onClickLabel = "Read this screen", onClick = onTap)
            .semantics { contentDescription = "Newt Reader" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Read", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

- [ ] **Step 3: Service, tile, hooks**

`service/ReaderService.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.reader.ServicePort
import io.github.secnewt.reader.ui.ReaderRoute
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Newt Reader's accessibility service. It does nothing until asked (two-finger swipe,
 * the tile, or the floating button); only then does it listen for screen changes and read.
 */
class ReaderService : AccessibilityService() {
    private val scope = MainScope()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var listening = false
    private lateinit var prefs: ReaderPrefs
    private lateinit var overlay: OverlayHost
    private lateinit var bubble: FloatingButton
    private lateinit var controller: ReaderController

    override fun onServiceConnected() {
        prefs = ReaderPrefs(this)
        overlay = OverlayHost(this)
        bubble = FloatingButton(this, ::toggle)
        val port = ServicePort(this, changes) {
            overlay.withoutFocus { performGlobalAction(GLOBAL_ACTION_BACK); delay(100) }
        }
        controller = ReaderController(port, scope, onClosed = { listening = false })
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) = toggle()
            },
        )
        scope.launch { controller.state.collect { render(it.open) } }
        prefs.watch(::refreshBubble)
        refreshBubble()
        DebugHooks.register(this, scope, controller, ::open)
        running = this
    }

    fun toggle() = if (controller.state.value.open) controller.close() else open()

    fun open() {
        listening = true
        controller.open()
    }

    /** The tile: close the shade first; reading waits for the screen to settle anyway. */
    fun openFromTile() {
        listening = true
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        controller.open()
    }

    private fun render(open: Boolean) {
        if (open) overlay.show(onBack = controller::back) { ReaderRoute(controller, prefs) } else overlay.hide()
        refreshBubble()
    }

    private fun refreshBubble() {
        if (prefs.floatingButton && !overlay.showing) bubble.show() else bubble.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!listening) return
        // Ignore Reader's own overlay redrawing; anything else (including our own test page) counts.
        if (event.packageName == packageName && fromOverlay(event)) return
        changes.tryEmit(Unit)
    }

    private fun fromOverlay(event: AccessibilityEvent) =
        windows.firstOrNull { it.id == event.windowId }?.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = null
        if (::prefs.isInitialized) {
            prefs.unwatch()
            overlay.hide()
            bubble.hide()
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** The connected service, for the tile. Null when Reader is turned off. */
        @Volatile var running: ReaderService? = null
            private set
    }
}
```

`service/ReaderTileService.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.secnewt.reader.MainActivity

/** Quick Settings "Read screen" tile. If Reader is off, it opens the setup screen instead. */
class ReaderTileService : TileService() {
    override fun onClick() {
        val service = ReaderService.running
        if (service != null) {
            service.openFromTile()
            return
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
```

`app/src/release/java/io/github/secnewt/reader/service/DebugHooks.kt` **and** (for now, identical) `app/src/debug/java/io/github/secnewt/reader/service/DebugHooks.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.content.Context
import io.github.secnewt.reader.reader.ReaderController
import kotlinx.coroutines.CoroutineScope

/** Release builds: no test hooks. (The debug build's version drives the emulator test.) */
@Suppress("UNUSED_PARAMETER")
object DebugHooks {
    fun register(context: Context, scope: CoroutineScope, controller: ReaderController, open: () -> Unit) = Unit
}
```

`app/src/main/res/xml/reader_service.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Flags match the 2026-10-21 probe that proved reading works on the phone.
     flagRequestAccessibilityButton = the two-finger swipe up (gesture navigation). -->
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/service_description"
    android:summary="@string/service_summary"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows|flagRequestAccessibilityButton"
    android:canRetrieveWindowContent="true"
    android:isAccessibilityTool="true"
    android:notificationTimeout="50" />
```

`app/src/main/res/drawable/ic_tile.xml` (an open book; tiles are drawn white by the system):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M2,4.5 L11,6.5 L11,20 L2,18 Z M13,6.5 L22,4.5 L22,18 L13,20 Z" />
</vector>
```

In `app/src/main/AndroidManifest.xml`, inside `<application>` after the `MainActivity` block, add:

```xml
        <!-- Reader itself. Android asks the owner before turning it on; it reads only when asked. -->
        <service
            android:name=".service.ReaderService"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/reader_service" />
        </service>

        <!-- Quick Settings "Read screen" tile. -->
        <service
            android:name=".service.ReaderTileService"
            android:exported="true"
            android:icon="@drawable/ic_tile"
            android:label="@string/tile_label"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>
```

and change the comment at the top to `<!-- No permissions, and no internet access. The accessibility service and tile are bound by the system; they need no <uses-permission>. -->`.

- [ ] **Step 4: Push and check compile, lint, permission audit**

```bash
git add -A && git commit -m "Reader service, overlay, tile and floating button" && git push && gh pr checks --watch
```

Expected: all checks PASS, including **Build test APK → Permission audit passed** with no new permissions. If lint fails, read `gh run view <id> --log-failed`, fix the cause (don't add `lint.xml` suppressions without a comment saying why).

---

### Task 9: Emulator test: read, follow, back, close-first

**Files:**
- Modify: `app/src/debug/java/io/github/secnewt/reader/service/DebugHooks.kt` (real hooks), `.github/workflows/build.yml`
- Create: `app/src/debug/java/io/github/secnewt/reader/TestPageActivity.kt`, `app/src/debug/AndroidManifest.xml`, `app/src/debug/assets/page1.html`, `app/src/debug/assets/page2.html`, `scripts/emulator-reader-test.sh`

**Interfaces:**
- Consumes: `ReaderController`, `ReaderState.findTarget`, `Block.Heading` (Tasks 2, 5); `DebugHooks.register` signature (Task 8).
- Produces: broadcasts `io.github.secnewt.reader.debug.OPEN`, `…TAP` (extra `label`), `…BACK`; log tag `NewtReaderTest` with lines `busy`, `closed`, `shows: <first heading>`.

- [ ] **Step 1: The test page (debug build only)**

`app/src/debug/assets/page1.html`:

```html
<!doctype html>
<html><head><meta name="viewport" content="width=device-width"><title>Test page one</title></head>
<body>
<h1>Test page one</h1>
<p>This is the first test page. It has a <a href="page2.html">Next page</a> link in the middle of a sentence, so Reader must keep it inline.</p>
<button onclick="document.title='Sent'">Send</button>
</body></html>
```

`app/src/debug/assets/page2.html`:

```html
<!doctype html>
<html><head><meta name="viewport" content="width=device-width"><title>Test page two</title></head>
<body>
<h1>Test page two</h1>
<p>You followed the link. Reader should still be open and reading this page.</p>
</body></html>
```

`app/src/debug/java/io/github/secnewt/reader/TestPageActivity.kt`:

```kotlin
package io.github.secnewt.reader

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback

/** Debug build only: a two-page local web page for the emulator test. No JavaScript, no internet. */
class TestPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this).apply {
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/page1.html")
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else finish() }
    }
}
```

`app/src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- Emulator test page; exported so adb can start it. Debug build only. -->
        <activity android:name=".TestPageActivity" android:exported="true" />
    </application>
</manifest>
```

- [ ] **Step 2: Real debug hooks**

Replace `app/src/debug/java/io/github/secnewt/reader/service/DebugHooks.kt`:

```kotlin
package io.github.secnewt.reader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.findTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug build only: lets the emulator test drive Reader with adb broadcasts and see what
 * it shows in logcat (tag NewtReaderTest). Release builds have a no-op version.
 */
object DebugHooks {
    private const val TAG = "NewtReaderTest"
    private const val OPEN = "io.github.secnewt.reader.debug.OPEN"
    private const val TAP = "io.github.secnewt.reader.debug.TAP"
    private const val BACK = "io.github.secnewt.reader.debug.BACK"

    fun register(context: Context, scope: CoroutineScope, controller: ReaderController, open: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    OPEN -> open()
                    BACK -> controller.back()
                    TAP -> {
                        val label = intent.getStringExtra("label").orEmpty()
                        val target = controller.state.value.findTarget(label)
                        if (target == null) Log.i(TAG, "no target: $label") else controller.tap(target.first, target.second)
                    }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(OPEN); addAction(TAP); addAction(BACK) }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        scope.launch {
            controller.state.collect { s ->
                Log.i(TAG, when {
                    !s.open -> "closed"
                    s.busy -> "busy"
                    else -> "shows: " + (s.blocks.firstOrNull { it is Block.Heading }?.text ?: "(no heading)")
                })
            }
        }
    }
}
```

- [ ] **Step 3: The emulator script**

`scripts/emulator-reader-test.sh`:

```bash
#!/usr/bin/env bash
# Emulator test: turns Reader on, opens a local two-page web page, and checks Reader
# reads it, follows an inline link without closing, goes Back, and closes before "Send".
set -euo pipefail

pkg="io.github.secnewt.reader.debug"
adb install -r app-debug.apk
adb shell settings put secure enabled_accessibility_services "$pkg/io.github.secnewt.reader.service.ReaderService"
adb shell settings put secure accessibility_enabled 1
sleep 5

adb shell am start -n "$pkg/io.github.secnewt.reader.TestPageActivity"
sleep 10

# Sends one broadcast, then waits up to 30 s for a matching NewtReaderTest log line.
step() {
  local expect="$1"; shift
  adb logcat -c
  adb shell am broadcast -p "$pkg" "$@" > /dev/null
  for _ in $(seq 1 30); do
    if adb logcat -d -s NewtReaderTest | grep -q -- "$expect"; then echo "OK: $expect"; return 0; fi
    sleep 1
  done
  echo "::error::Never saw '$expect'. Reader's log:"
  adb logcat -d -s NewtReaderTest | tail -20
  exit 1
}

step "shows: Test page one$" -a io.github.secnewt.reader.debug.OPEN
step "shows: Test page two$" -a io.github.secnewt.reader.debug.TAP --es label "Next page"
step "shows: Test page one$" -a io.github.secnewt.reader.debug.BACK
step "closed"                -a io.github.secnewt.reader.debug.TAP --es label "Send"
echo "Reader read, followed, went back, and closed before Send."
```

```bash
chmod +x scripts/emulator-reader-test.sh
```

- [ ] **Step 4: Run it in CI**

Append to `.github/workflows/build.yml` under `jobs:` (same pinned actions as Newt Phone's emulator job):

```yaml

  # Newt Reader only: read, follow, Back and close-first on a real emulator.
  emulator:
    name: Emulator reader test
    # Slower (about 10 minutes), so it runs on pull requests and on demand.
    if: github.event_name == 'pull_request' || github.event_name == 'workflow_dispatch'
    needs: newt
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7
      - uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8
        with:
          name: newt-reader-test-apk
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Read a web page with Reader on an emulator
        uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d # v2
        with:
          api-level: 34
          target: default
          arch: x86_64
          disable-animations: true
          script: bash scripts/emulator-reader-test.sh
```

```bash
git add -A && git commit -m "Emulator test: Reader reads, follows, goes back, closes first" && git push && gh pr checks --watch
```

Expected: **Emulator reader test** PASSES with four `OK:` lines. If it fails:
- `Never saw 'shows: Test page one$'` with `(no heading)` → Chromium didn't mark the `<h1>` as a heading; check the Stitcher heading rule against what the log shows.
- Fails at BACK → the overlay kept focus; this is the one risky mechanism (`OverlayHost.withoutFocus`). Record exactly what the log shows before changing anything.

---

### Task 10: README, PR ready, phone acceptance, notes

**Files:**
- Modify: `README.md`
- Modify (vault, separate repo): `~/Obsidian/personal/AI/newt-android.md`, `~/Obsidian/personal/AI/newt-computer-idea.md`

- [ ] **Step 1: README**

Replace `README.md` with:

```markdown
# Newt Reader

Swipe up with two fingers on any screen and Newt Reader redraws it as large, readable
text in the Newt look, with the screen's links and buttons still working. Part of the
Newt family (built from [newt-android-template](https://github.com/sec-newt/newt-android-template));
follows the [house style](NEWT_STYLE.md).

> **A personal project, in early development.**

## What it does

- Reads the app you're looking at through Android's accessibility service and shows it
  reflowed, at its own text size (A− / A+ or pinch). Your phone's font size stays normal.
- Links and "more / next" buttons work inside Reader. Anything else (Send, Call, Play,
  text fields) closes Reader first, then taps, so nothing happens hidden behind it.
- Scroll to the end and Reader loads more from the app by itself.
- Open it with the two-finger swipe, the **Read screen** Quick Settings tile, or an
  optional floating button.

## Privacy

No internet permission. Nothing Reader reads is saved or logged. It reads only when you ask.

## Install

Releases are built and signed by GitHub Actions. Install and update with Obtainium from
this repository's Releases page. Then: Settings → Accessibility → Newt Reader → on, and
choose it for the accessibility gesture. If Android says the setting is restricted:
App info → ⋮ → Allow restricted settings.

## Design

[docs/superpowers/specs/2026-10-21-newt-reader-design.md](docs/superpowers/specs/2026-10-21-newt-reader-design.md)
```

```bash
git add README.md && git commit -m "README: what Newt Reader does" && git push
gh pr ready && gh pr checks --watch
```

Expected: every check green.

- [ ] **Step 2: Jerry's phone: acceptance (with him, not alone)**

Install the PR's test APK from the `newt-reader-test-apk` artifact over adb:

```bash
gh run download -n newt-reader-test-apk -D /tmp/claude-reader-apk $(gh run list -b v1-reader -w "Build APK" -L 1 --json databaseId -q '.[0].databaseId')
~/.cache/newt-a11y-test/sdk/platform-tools/adb install -r /tmp/claude-reader-apk/app-debug.apk
```

Jerry turns it on himself (Settings → Accessibility → Newt Reader (test)) and assigns the gesture. Walk the spec's acceptance list and record each result in the PR description:
1. Vanadium long article: opens at the spot he was reading; links follow; Back works.
2. Brave → X: posts readable; scrolling to the end loads more.
3. Claude app: auto-load reaches older messages.
4. Kagi search results: readable; the search box closes Reader and brings the keyboard.
5. A Newt app (Weather).
6. Both the two-finger swipe and the tile open Reader; the floating button works when switched on.
7. Ask him: does it beat Reading Mode? What's the default text size he wants?

Anything that fails becomes a fix on this branch (with a test where the engine is at fault) before "merge it".

- [ ] **Step 3: Vault notes**

Add a "Newt Reader" section to `~/Obsidian/personal/AI/newt-android.md` (repo, what shipped, what the phone test showed, open items) and update the status line of `~/Obsidian/personal/AI/newt-computer-idea.md` to point at it. Commit in the vault repo with the attribution lines.
