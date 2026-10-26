---
name: petla
description: Run the Bahasa Kotak learning loop end to end - pull the learner's export from the phone over adb, analyze it per docs/ANALYSIS_PROMPT.md, write the next lesson pack (with feedback), validate it and drop it into the app's inbox for a one-tap import. Use when the user types /petla, says "pętla", "nowa paczka", "przeanalizuj postępy", or asks to send the phone a new pack.
---

# /petla – export → analysis → new pack → phone

The user should only have to type `/petla` here and tap **Importuj** on the phone. Do every step yourself;
talk to the user in Polish. Scripts live in `tools/loop/` and run from the repo root.

## 1. Pull the export

```bash
tools/loop/pull-export.sh
```

- Prints the saved path (`postepy/export-YYYYMMDD-HHMM.json`) and a one-line summary.
- Exit 2 = no phone. Tell the user to plug in USB, or turn on **Opcje programisty → Debugowanie bezprzewodowe**
  on the same Wi-Fi (pairing once: `adb pair IP:PORT CODE` from the "Sparuj urządzenie za pomocą kodu" screen;
  afterwards adb finds the phone by itself). Then retry; don't continue without fresh data.
- Exit 3 = the app hasn't written an export yet; ask the user to open the app once.
- If the export's `exportedAt` is older than the newest session the user mentions, ask them to open the app
  (it re-exports on start and after every session).

## 2. Analyze

```bash
tools/loop/summarize-export.py postepy/export-….json
```

Read `docs/ANALYSIS_PROMPT.md` in full; it is the brief for this step (learner profile, what to analyze, pack
rules, the **≥40% `office` register** priority on Jakarta workplace speech). Work from the summary; open the
raw export (`content`) only to check specific items, e.g. their accepted variants, before calling an error false.

Decide, as the prompt describes:
- a **new pack** `loop-NNN` (the summary prints the next ID), or
- a **new version of an installed pack** (copy it from the export's `content` in full, bump `packVersion`) when
  existing items need fixing, typically **false errors**: a correct answer graded wrong because a variant is
  missing (e.g. `person in charge` for `PIC`). If both are needed, fixes go first; mention the rest in the report.
- **Exception: `starter` is bundled in the APK.** Fix it in `packs/starter.json` (bump `packVersion`), run
  `./gradlew testDebugUnitTest installDebug`, and commit that change; the app imports it on start. Don't push a
  starter version through the inbox, or the bundled copy and the installed one diverge.

## 3. Write the pack and report

- `postepy/<packId>-v<packVersion>.json`: the pack, with a `feedback` field (5–8 `- ` bullets, Polish,
  simple Markdown) that the app shows in the import dialog. Set `basedOnExport` to the export's `exportedAt`.
- `postepy/<packId>-v<packVersion>.md`: the full report (≤15 points) for the record.

Write Indonesian the way people in Jakarta actually say it, and keep Polish natural. Be generous with
`acceptedPl`/`acceptedId`.

## 4. Validate

```bash
tools/loop/check-pack.py postepy/<pack>.json postepy/export-….json
```

Fix every ERROR and re-run until it prints OK. Treat warnings as defects unless there is a reason (state it).

## 5. Send to the phone

```bash
tools/loop/push-pack.sh postepy/<pack>.json
```

It writes the pack into the app's inbox (`files/inbox/`, checksum-verified) and brings the app forward; Home
shows the import preview with the feedback. If the user is mid-session, the dialog appears when they return
to Home.

## 6. Tell the user

In Polish, briefly: the 3–5 key findings, what the pack adds or fixes, and "stuknij **Importuj** na telefonie".
Don't paste the JSON. Don't commit anything from `postepy/` (it is gitignored personal data).
