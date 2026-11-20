# Reader: text that loads later goes where it belongs — short design

## Why

Jerry, on the phone (2026-11-14): opening Reader on an email in Proton Mail showed the sender and
subject but not the message. After a small scroll, the message read fine.

What the phone showed: Proton Mail doesn't share the message body with accessibility at all until
the email has scrolled. Waiting 10 seconds doesn't help. Reader's own "Loading more" scrolls the
app and the body does arrive, but Reader adds anything new to the **end**, so the body lands after
the bottom bar's Trash / Move to / Spam buttons and the top bar.

## Goal

New text from a load is placed where it sits in the app: under the email's header, above the
bottom bar. Because Reader already loads more on its own when the page is short, the body fills
in under the header about a second after Reader opens.

Non-goals: making the body present on the very first read (approach B below), anything specific to
Proton Mail.

## Approaches

- **A. Place new text where it belongs (picked by Jerry).** Each run of new blocks goes just
  before the next block in the fresh read that Reader already shows; with nothing known after it,
  at the end (a feed scrolled past everything shown, as today). Helps every app that fills in as
  it scrolls; changes nothing for apps that don't.
- **B. Nudge first.** Scroll the app forward and back before the first read. Faster for email,
  but done to every app; on feeds it can move the reader's place or trigger reloads, against
  "opens where you were reading". Kept for later, per app, only if A feels slow.

## Design

- `Merge.placeNew(shown, fresh): List<Block>` returns the whole new list. Blocks already shown (same
  type and text) are skipped; each run of new blocks is inserted before the next known block that
  follows it in `fresh`, or at the end when none follows. Repeats inside `fresh` count once.
- `ReaderController.loadMore` uses it; "something new arrived" = the list grew.
- The reading list gets stable item keys (a block's key plus how many times it occurred before),
  so text inserted above where Jerry is reading doesn't shift what's on his screen.

## Tests

- Merge: the email case (header, bottom bar, top bar shown; fresh read has the body between the
  header and the bottom bar → body lands after the header), a feed with no overlap (new posts
  before the bottom nav), a feed with overlap, repeats, nothing new. Made-up text only.
- Controller: a load whose new blocks fall mid-page puts them mid-page.
