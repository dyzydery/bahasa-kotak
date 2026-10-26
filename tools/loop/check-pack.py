#!/usr/bin/env python3
"""Validates a pack before it goes to the phone: schema, ID rules against the export's content, word blocks.

Usage: tools/loop/check-pack.py PACK.json EXPORT.json
Exit 0 = OK (warnings may be printed), 1 = errors to fix.
"""
import json
import re
import sys
from pathlib import Path

import jsonschema

ROOT = Path(__file__).resolve().parents[2]


def items_of(pack):
    return [(t, l, i) for t in pack["topics"] for l in t["lessons"] for i in l["items"]]


def main(pack_path, export_path):
    pack = json.loads(Path(pack_path).read_text(encoding="utf-8"))
    export = json.loads(Path(export_path).read_text(encoding="utf-8"))
    errors, warnings = [], []

    schema = json.loads((ROOT / "schemas/pack.schema.json").read_text(encoding="utf-8"))
    errors += [f"schema: {e.message} at /{'/'.join(map(str, e.path))}"
               for e in jsonschema.Draft202012Validator(schema).iter_errors(pack)]
    if errors:
        return errors, warnings

    entries = items_of(pack)
    for kind, ids in {
        "topic": [t["id"] for t in pack["topics"]],
        "lesson": [l["id"] for t in pack["topics"] for l in t["lessons"]],
        "item": [i["id"] for _, _, i in entries],
    }.items():
        dupes = {x for x in ids if ids.count(x) > 1}
        if dupes:
            errors.append(f"duplicate {kind} IDs in pack: {sorted(dupes)}")

    installed = {p["packId"]: p["packVersion"] for p in export["installedPacks"]}
    same_pack = pack["packId"] in installed
    if same_pack and pack["packVersion"] <= installed[pack["packId"]]:
        errors.append(f"{pack['packId']} v{installed[pack['packId']]} is installed; packVersion must be higher")

    # IDs must not collide with other packs' content (a new version of the same pack may reuse its own).
    others = [p for p in export["content"] if p["packId"] != pack["packId"]]
    other_items = {i["id"]: i for p in others for _, _, i in items_of(p)}
    other_lessons = {l["id"] for p in others for t in p["topics"] for l in t["lessons"]}
    other_topics = {t["id"]: t for p in others for t in p["topics"]}
    for _, _, i in entries:
        if i["id"] in other_items:
            errors.append(f"item ID {i['id']} already exists in another pack")
    for t in pack["topics"]:
        for l in t["lessons"]:
            if l["id"] in other_lessons:
                errors.append(f"lesson ID {l['id']} already exists in another pack")
        if t["id"] in other_topics:
            old = other_topics[t["id"]]
            for key in ("title", "order", "description"):
                if t.get(key) != old.get(key):
                    errors.append(f"topic {t['id']} is reused but its {key} differs from the installed one")
            used = {l["order"] for l in old["lessons"]}
            clash = [l["id"] for l in t["lessons"] if l["order"] in used]
            if clash:
                warnings.append(f"lessons {clash} reuse lesson order values of topic {t['id']}")

    # Mirror of the app's word-block tokenization: an answer of 3+ words must survive it intact.
    for _, _, i in entries:
        for text in (i["id_text"], i["pl_text"]):
            stripped = re.sub(r"\([^)]*\)", "", text).strip()
            if stripped.count("(") or stripped.count(")"):
                errors.append(f"{i['id']}: unbalanced parentheses in '{text}'")
            if not stripped:
                errors.append(f"{i['id']}: text is empty once hints are removed: '{text}'")

    office = sum(1 for _, _, i in entries if i["register"] == "office")
    share = office / len(entries)
    if share < 0.4:
        warnings.append(f"only {office}/{len(entries)} items ({share:.0%}) are in the office register; the prompt asks for 40%+")
    if not pack.get("feedback"):
        warnings.append("no 'feedback' field; the app shows it at import")

    kinds = {k: sum(1 for _, _, i in entries if i["kind"] == k) for k in ("word", "phrase", "sentence")}
    print(f"{pack['packId']} v{pack['packVersion']}: {len(entries)} items {kinds}, office {share:.0%}, "
          f"{'new version of an installed pack' if same_pack else 'new pack'}")
    return errors, warnings


if __name__ == "__main__":
    errs, warns = main(*sys.argv[1:3])
    for w in warns:
        print("WARNING:", w)
    for e in errs:
        print("ERROR:", e)
    print("OK" if not errs else f"{len(errs)} error(s)")
    sys.exit(1 if errs else 0)
