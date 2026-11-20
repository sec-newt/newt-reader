# Reader: load in place — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Text that arrives when Reader loads more is placed where it sits in the app, not at the end.

**Architecture:** A pure `Merge.placeNew` decides positions; `ReaderController.loadMore` uses it; the
reading list gets stable keys so inserts above the reader's place don't move what's on screen.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, kotlinx-coroutines-test, Robolectric (existing).

**Spec:** `docs/superpowers/specs/2026-11-14-reader-load-in-place-design.md`

## Global Constraints

- Repo is public: test text is made up ("Weekly garden news", "Sam Example"); nothing from Jerry's phone.
- No new dependencies, no new permissions.
- Block identity is `Block.key` (type + text), as today.

## Review Focus

- A fresh read that shares nothing with what's shown (a feed scrolled a full page) → new blocks at the end, as before.
- New blocks before the first known block (content that appeared above) → inserted before that known block.
- A block whose key occurs twice in `shown` (two "Share" buttons) → list keys stay unique; no crash in LazyColumn.
- Fresh read with repeats of a new block → it appears once.
- Nothing new → list unchanged and the load counts as empty (footer logic as today).

---

### Task 1: `Merge.placeNew`

**Files:**
- Modify: `app/src/main/java/io/github/secnewt/reader/engine/Merge.kt`
- Test: `app/src/test/java/io/github/secnewt/reader/engine/MergeTest.kt`

**Interfaces:**
- Produces: `Merge.placeNew(shown: List<Block>, fresh: List<Block>): List<Block>` — the whole new list.
  Replaces `appendNew` (remove it and its tests once nothing calls it).

- [ ] **Step 1: Failing tests** (with `p(t)` as in the existing test)
  - `bodyLoadedLaterGoesUnderTheHeader`: shown `[p("From Sam Example"), p("Trash"), p("1 message")]`, fresh `[p("From Sam Example"), p("Weekly garden news"), p("Planting starts Saturday"), p("Trash"), p("1 message")]` → `[From…, Weekly…, Planting…, Trash, 1 message]`.
  - `feedWithNoOverlapGoesBeforeTheBottomBar`: shown `[p("Top"), p("post 1"), p("Nav")]`, fresh `[p("Top"), p("post 2"), p("Nav")]` → `[Top, post 1, post 2, Nav]`.
  - `nothingKnownAppendsAtTheEnd`: shown `[p("a")]`, fresh `[p("b"), p("c")]` → `[a, b, c]`.
  - `newBeforeTheFirstKnownGoesBeforeIt`: shown `[p("a"), p("b")]`, fresh `[p("x"), p("b")]` → `[a, x, b]`.
  - `repeatsInsideFreshCountOnce`, `nothingNew` (returns `shown` equal), `sameTextDifferentTypeIsNew` — ported from the `appendNew` tests.
- [ ] **Step 2:** Run MergeTest; expect failures (`placeNew` unresolved).
- [ ] **Step 3: Implement.** Walk `fresh`, collecting unseen blocks into a pending run; at each block already in the result, insert the run just before that block's position in the result; at the end, append any remaining run.
- [ ] **Step 4:** Run MergeTest; expect all pass.
- [ ] **Step 5:** Commit `Merge: new blocks go before the next block already shown, not at the end`.

### Task 2: Controller and list keys

**Files:**
- Modify: `app/src/main/java/io/github/secnewt/reader/reader/ReaderController.kt` (`loadMore`)
- Modify: `app/src/main/java/io/github/secnewt/reader/ui/ReaderScreen.kt:126` (`items`)
- Test: `app/src/test/java/io/github/secnewt/reader/reader/ReaderControllerTest.kt`

**Interfaces:**
- Consumes: `Merge.placeNew` (Task 1).

- [ ] **Step 1: Failing test** `loadedTextLandsMidPage`: fake screen first reads `[header, "Trash"]`, after `scrollForward` reads `[header, body, "Trash"]`; after `loadMore()` the state's blocks are `[header, body, Trash]` and the footer is `NONE`.
- [ ] **Step 2:** Run it; expect failure (body last).
- [ ] **Step 3:** `loadMore` sets `blocks = Merge.placeNew(old, fresh)`; "added" = it grew. `ReaderScreen`'s `items` gets `key = { i -> "${blocks[i].key}#${occurrence of that key before i}" }` (computed once per block list).
- [ ] **Step 4:** Run all tests in CI (controller and screen tests need Gradle): expect green, existing screenshots unchanged.
- [ ] **Step 5:** Commit, push, PR; then Jerry checks the Proton Mail email on the phone.
