# Newt Reader

A private reading app for low vision, part of the Newt family, in early development. The owner has low vision; readability beats everything else. What it does is in
[README.md](README.md), the shared rules in the
[Newt house style](NEWT_STYLE.md).

## Stack

Kotlin, Jetpack Compose, Material 3. The theme and shared pieces come from `newt-ui`, and
the screenshot and accessibility test helpers (`newtScreenshot()`) from `newt-test`. Both
live in [newt-android-lib](https://github.com/sec-newt/newt-android-lib). Code is in
`app/src/main/java/io/github/secnewt/reader/`, tests in `app/src/test/`.

## Checks: run them and show the output

CI runs the same checks on every PR (see `.github/workflows/build.yml`).

```sh
./gradlew recordRoborazziDebug   # logic, screenshot and accessibility tests
./gradlew lintDebug              # lint, including Android security checks
./gradlew assembleDebug && scripts/check-permissions.sh app/build/outputs/apk/debug/app-debug.apk
```

Paste the result. Don't say "tests pass" without the output. If Gradle can't download
the Newt packages (a 401), the GitHub login needs `read:packages`: ask the owner to run
`gh auth refresh -s read:packages`, and lean on CI meanwhile, saying so in the PR.

## Rules that are easy to break

- **No internet permission, ever.** New permissions go in `config/allowed-permissions.txt`
  with a plain sentence saying why; the build fails otherwise.
- **7:1 contrast** for every text color, **no text under 16sp**, buttons at least 56dp
  and rows at least 48dp tall, words next to icons. Must work at 200% text size and with
  TalkBack, and no two things on a screen read out the same.
- **Animations stop when Android's animations are off.**
- **README.md is the user's manual.** Update it when behavior changes, in plain words.

## Before opening a PR, and after every push to one

1. Run the checks above.
2. **Review it.** Run `/code-review` on the branch with [REVIEW.md](REVIEW.md) as the rules,
   plus `/security-review`. Fix what they find, or say why not. Check every item under
   "What was asked" against what the code really does.
3. Open the PR with every section of the template filled in (What was asked, What
   changes, Testing, Risk, How to revert), then **post the review as a PR comment**:
   start with the count ("Nothing to fix." or "1 important, fixed in abc123."), then each
   finding with its file and line, its tag, and what was done about it. The owner reads
   the review there.
4. **Every later push to the PR gets the same review** of what changed since the last
   one, however small, posted as a new comment that names the commits it covers and the
   reviews that ran. If a review was skipped, the comment says so.

Merge only after the owner says "merge it". Merges to `main` publish a signed release.
