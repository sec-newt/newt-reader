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
