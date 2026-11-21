# Newt Reader — read aloud

Date: 2026-11-20. Status: approved by the owner ("go and do"), building.

## Why

Reader already turns any screen into clean paragraphs in reading order. The owner has a
text-to-speech engine installed and wants Reader to read that text out loud, so long pages
can be listened to instead of read.

## What it does

1. A **Listen** button sits at the bottom right of the reading view.
2. Tapping it reads from the **first paragraph on screen** to the end, one block at a time.
3. The block being read gets a **highlight outline**, and the view scrolls to keep it on screen.
4. While reading, a **player bar** replaces the button: **Back**, **Pause / Resume**,
   **Next**, **Stop**. Each button is at least 56 dp tall, with words next to icons.
5. When it reaches the last block, it waits for more to load if loading is under way, keeps
   going if more came in, and stops otherwise.
6. It stops when Reader closes, or when the page changes (the block being read is gone).
   A tap that changes the page in place (a like) keeps it reading.
7. The screen stays on while it reads.

### What gets read

| Block | Spoken |
|---|---|
| Heading, Paragraph, list row | its text |
| Picture with a description | "Picture: description" |
| Buttons, toolbars, text fields, pictures without a description | skipped |

Long blocks are split at sentence ends so no piece is longer than the engine accepts.
Pause stops the engine (Android's TTS has no real pause); Resume restarts the sentence it was on.

### Settings

- Voice, speed and pitch come from the phone's own text-to-speech settings. The launcher
  screen gets a **Speech settings** button that opens them, with one sentence saying so.
- No new saved settings.

## Privacy

- No internet, no new permissions. The engine is the one the owner chose in Android settings.
- The engine connects only when Listen is first tapped, and disconnects when Reader closes.
- Only voices that work offline are used. If the chosen voice needs the internet, Reader switches to an
  offline voice in the same language; if there is none, it doesn't speak and says why.
  If the engine won't say which voice it uses, Reader treats it as unsafe and doesn't speak (fails closed).
- Nothing spoken is stored.

## Shape

- `speech/Speakable.kt` — pure: what a block says, and splitting into sentence pieces.
- `speech/SpeechPort.kt` — the engine behind an interface (`speak`, `stop`, done/error callbacks).
- `speech/AndroidSpeech.kt` — `SpeechPort` on Android's `TextToSpeech`, audio as media/speech
  so the media volume controls it.
- `speech/ReadAloud.kt` — the player: state (`IDLE`, `STARTING`, `PLAYING`, `PAUSED`), current
  block, start/pause/resume/next/back/stop. Watches Reader's state and stops on close or page change.
  Fully unit-tested with a fake port.
- `ReaderScreen` gets the player state and actions; `BlockView` items get the highlight.
- Manifest: a `<queries>` entry for `android.intent.action.TTS_SERVICE`, needed on Android 11+
  for an app to see installed speech engines.

## Errors

- No engine, or it fails to start: the player says "No text-to-speech engine is ready. Pick one in Speech
  settings." and goes back to the Listen button.
- An engine error mid-read: skip to the next block once; a second error in a row stops.

## 🚫 Not in this version

- Reading in the background with Reader closed, lock-screen controls.
- Speed buttons inside Reader (the phone's TTS settings cover it).
- Word-by-word highlighting.
- Tap a paragraph to read from there.
- Audio focus (pausing music while it speaks).
