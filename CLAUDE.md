# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Greenfield. `bahasa-kotak-prompt.md` (Polish) is the original product brief for **Bahasa Kotak**, a sideloaded Android app for learning Indonesian. `docs/SPEC.md` (Polish) records the agreed design decisions on top of it (stack: Kotlin + Compose + Room; Leitner intervals; session planning; import idempotency) and wins where the two differ. Data formats live in `schemas/pack.schema.json` and `schemas/export.schema.json`; keep SPEC, schemas, and code in sync when changing any of them. Remote: `github.com/dyzydery/bahasa-kotak`. Commit and push after each milestone.

## Commands

Toolchain: JDK 21 (Temurin) at `~/.local/share/jdks/jdk-21`, pinned for Gradle via `org.gradle.java.home` in `gradle.properties`. Fedora 44 only ships JDK 25+, which AGP doesn't support as the Gradle JDK, so don't remove the pin. Android SDK at `~/Android/Sdk` (`local.properties`). There is no system `gradle`; always use `./gradlew`.

```fish
./gradlew assembleDebug                      # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                               # JVM unit tests (JUnit 5)
./gradlew test --tests '*AnswerCheckerTest'  # single test class; append .methodName for one test
./gradlew installDebug                       # install on the phone over adb (USB)
~/Android/Sdk/platform-tools/adb devices
```

Dependency versions live in `gradle/libs.versions.toml`. Bytecode targets Java 17 (`compileOptions` + Kotlin `jvmTarget`); don't add `jvmToolchain(...)`, because no matching JDK is installed and toolchain auto-download is off.

## Code layout and data flow

- `domain/` is pure Kotlin with no `android.*` imports, so all required logic tests run as JVM tests.
  - `AnswerChecker`: normalization plus Damerau-Levenshtein typo grading. Variants also accept their text without `(hints)`, and each side of `A / B`.
  - `Leitner`: due dates are local midnights, so they count in calendar days.
  - `SessionPlanner` decides *what* a session contains: due reviews balanced by direction, then new items from each topic's first unfinished lesson, plus grammar notes and intro cards.
  - `SessionEngine` runs the queue: the first answer moves a card, and wrong answers are re-queued as `retry` tasks that are logged but never move the card. It only reports outcomes; `SessionViewModel` persists them through `StudyRepository.saveAnswer`, which writes the card and the log entry in one transaction.
- `io/pack/`: `PackReader` validates against `schemas/pack.schema.json`, then checks ID uniqueness across the pack. `ImportPlanner` is a pure function that decides new/updated/retired and rejects same or older versions. `PackImportService` is the single entry point for every import, whatever transport delivers the file.
- `data/`: Room. Content tables (`packs`, `topics`, `lessons`, `items`) are written only by import, using `@Upsert`, never `REPLACE`. Removed content is marked `retired` and never deleted. Progress tables (`cards`, `answers`, `sessions`, `lesson_progress`) have no foreign keys to content on purpose, so a re-import can't touch learning history. The exported schema history lives in `app/roomSchemas/`; changing an entity needs a DB version bump plus a migration.
- `schemas/` and `packs/` at the repo root are added as APK assets (see `sourceSets` in `app/build.gradle.kts`). On launch the app auto-imports `packs/starter.json` if its `packVersion` is newer than the installed one, so any edit to the starter pack must bump `packVersion`. Tests read these files through the `repoRoot` system property (`RepoFiles`).
- Wiring is manual: `AppContainer`, owned by `BahasaKotakApp`. There is no DI framework.

Driving the phone UI with `adb shell input` needs MIUI's "USB debugging (Security settings)" plus an unlocked screen. Without that, `input` fails with an INJECT_EVENTS SecurityException.

On-device debugging: a debug build allows `adb exec-out run-as io.github.dyzydery.bahasakotak cat databases/bahasa-kotak.db` (also pull `-wal`). There's no `sqlite3` CLI on the laptop, so query the copy with `python3 -I -c 'import sqlite3 …'`.

## Hard constraints from the brief

- **Single user, offline-first**: no accounts, backend, or Play Store. Data lives in a local DB.
- **Target device**: Xiaomi on MIUI 14 (Android 12/13), so `minSdk` ≤ 31. Installed via `adb` sideload. MIUI needs "Install via USB" turned on in Developer options.
- **No Android Studio**: the build must run from the CLI (JDK, Android command-line tools, SDK) on Fedora. Any shell commands for the user must be written for **fish**, not bash.
- **Language split**: code, identifiers, and commit messages in English. All UI strings and translations in **Polish**.
- **Text only**: no audio or TTS. Minimal gamification: no XP, hearts, maps, or notifications.
- **Content comes only from packs** (JSON). Users cannot add words in the app.

## Core domain rules (easy to get wrong)

- **Answer checking**: ignore case, punctuation, extra whitespace, and Polish diacritics (`zolw` == `żółw`). Compare against every accepted variant in the pack. A small edit distance, with the threshold scaled by word length, counts as "prawie dobrze" (almost correct): show the correct spelling. A wrong answer shows the correct one and the task comes back later in the same session.
- **Leitner, 5 boxes**: new item goes to box 1. Correct moves it up one box. Wrong sends it **back to box 1**. A typo keeps it **in the same box**. Correct in box 5 marks it mastered. Review intervals are our choice and must be justified in SPEC.md.
- **Session**: about 5 minutes. Due reviews come first, then new material to fill the remaining time. It must start instantly.
- **Directions**: PL→ID and ID→PL split evenly. Two exercise types: word-block sentence building (more distractor words at higher difficulty) and type-in flashcards.
- **Registers**: every item is tagged formal (baku) or colloquial (gaul/Jakarta).
- **Placement test** on first launch pushes known items straight into higher boxes. Trivial basics are left out of lessons but included in the test.
- Lessons can carry a short **grammar note**, shown before the lesson starts.

## Architecture: the Claude content loop

This loop is the central design driver:

1. The app exports JSON containing a **full per-answer log** (item, direction, exercise type, user answer, grade, timestamp, box before/after) plus current box and progress state.
2. The user passes it to Claude along with `docs/ANALYSIS_PROMPT.md`.
3. Claude returns a new lesson pack as JSON.
4. The app imports it. **Import must be idempotent**: stable item IDs, and updates to existing items keep their progress.

Required artifacts:
- `schemas/export.schema.json` and `schemas/pack.schema.json`, versioned. Import validates against the pack schema.
- `docs/ANALYSIS_PROMPT.md`: rules for analysis (error patterns, registers, IT/telco focus, difficulty tuning). It must require a schema-valid pack as output.
- **Swappable transport layer**: an interface plus a "file" implementation (Android share sheet and file picker). A future "Frog API" implementation will POST the export to a small HTTP service on a 256 MB Alpine VPS behind Cloudflare, which calls the Claude API. Don't build it now, but don't design it out.
- **Backup/restore** of the whole DB to a file, separate from the Claude export.

## Content

The starter pack is about 100 words plus example sentences, enough for 2–3 weeks, organized as topics with lessons of increasing difficulty. It leans on **IT/telco vocabulary** (meetings, email, projects, deployments, networks, operators, CPE devices) alongside everyday language. The user works in pre-sales and deployment of CPE device-management systems for operators.

## Testing

Unit tests are required at minimum for answer checking (variants, typos, Polish diacritics), the Leitner logic, and import/export.

## Milestones

1. CLI toolchain and an app skeleton that builds and installs on the phone.
2. Data model, JSON schemas, starter pack import.
3. Type-in flashcards, answer checking, Leitner, 5-minute session.
4. Word blocks, grammar notes, placement test.
5. Export, ANALYSIS_PROMPT.md, backup.
6. Statistics: activity and progress over time, box distribution, hardest words, per-topic progress.
