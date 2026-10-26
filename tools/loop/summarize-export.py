#!/usr/bin/env python3
"""Condenses an export into what docs/ANALYSIS_PROMPT.md asks to analyze, as compact Markdown.

The raw export grows with every answer; this keeps the analysis input small. Full details stay in the file
(e.g. look up an item's accepted variants in `content` before deciding it was a false error).

Usage: tools/loop/summarize-export.py EXPORT.json
"""
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from statistics import median

e = json.load(open(sys.argv[1], encoding="utf-8"))
items, lessons_by_topic, topics = {}, defaultdict(list), {}
for pack in e["content"]:
    for t in pack["topics"]:
        topics.setdefault(t["id"], {"title": t["title"], "order": t["order"], "pack": pack["packId"]})
        for l in t["lessons"]:
            lessons_by_topic[t["id"]].append((l["order"], l["id"], l["title"], l["difficulty"]))
            for i in l["items"]:
                items[i["id"]] = {**i, "lesson": l["id"], "pack": pack["packId"]}

answers = e["answers"]
first = [a for a in answers if not a["retry"] and a["exerciseType"] != "placement"]
exported = datetime.fromisoformat(e["exportedAt"].replace("Z", "+00:00"))
week = [a for a in first if datetime.fromisoformat(a["answeredAt"].replace("Z", "+00:00")) > exported - timedelta(days=7)]


def acc(rows):
    if not rows:
        return "–"
    c = Counter(a["grade"] for a in rows)
    return f"{c['correct'] / len(rows):.0%} correct, {c['typo']} typo, {c['wrong']} wrong (n={len(rows)})"


print(f"# Export {e['exportedAt']} ({e['timeZone']})\n")
print("Packs: " + ", ".join(f"{p['packId']} v{p['packVersion']}" for p in e["installedPacks"]))
loops = [int(m.group(1)) for p in e["installedPacks"] if (m := re.fullmatch(r"loop-(\d+)", p["packId"]))]
print(f"Next new pack ID: loop-{max(loops, default=0) + 1:03d}")
sessions = [s for s in e["sessions"] if not s.get("placement")]
days = sorted({s["startedAt"][:10] for s in sessions})
print(f"Sessions: {len(sessions)} on {len(days)} days ({days[0] if days else '–'} … {days[-1] if days else '–'})\n")

print("## Accuracy (first attempts, placement excluded)\n")
print(f"- all: {acc(first)}")
print(f"- last 7 days: {acc(week)}")
for key, label in (("direction", "direction"), ("exerciseType", "exercise")):
    for value in sorted({a[key] for a in first}):
        print(f"- {label} {value}: {acc([a for a in first if a[key] == value])}")
for reg in ("formal", "office", "colloquial", "neutral"):
    rows = [a for a in first if items.get(a["itemId"], {}).get("register") == reg]
    if rows:
        print(f"- register {reg}: {acc(rows)}")
durations = [a["durationMs"] for a in first if a.get("durationMs")]
if durations:
    print(f"- median answer time: {median(durations) / 1000:.1f}s")

print("\n## Every non-correct answer, by card (incl. retries; '' = \"Nie wiem\")\n")
by_card = defaultdict(list)
for a in answers:
    if a["grade"] != "correct":
        by_card[(a["itemId"], a["direction"])].append(a)
attempts = Counter((a["itemId"], a["direction"]) for a in first)
for (item_id, direction), rows in sorted(by_card.items(), key=lambda kv: -len(kv[1])):
    i = items.get(item_id, {})
    given = "; ".join(f"'{a['userAnswer']}'→{a['grade']}{' (retry)' if a['retry'] else ''}" for a in rows)
    print(f"- `{item_id}` {direction} [{i.get('register', '?')}, {i.get('kind', '?')}] "
          f"prompt '{rows[0]['prompt']}', expected '{rows[0]['expected']}', "
          f"{attempts[(item_id, direction)]} first attempts: {given}")

print("\n## Slowest cards (median of correct first attempts, ≥2)\n")
slow = defaultdict(list)
for a in first:
    if a["grade"] == "correct" and a.get("durationMs"):
        slow[(a["itemId"], a["direction"])].append(a["durationMs"])
for (item_id, direction), ds in sorted(((k, v) for k, v in slow.items() if len(v) >= 2), key=lambda kv: -median(kv[1]))[:8]:
    print(f"- `{item_id}` {direction}: {median(ds) / 1000:.1f}s")

print("\n## Topics and lessons (continue a topic with its exact title/order; next lesson order = max + 1)\n")
status = {l["lessonId"]: l["status"] for l in e["lessons"]}
for tid, t in sorted(topics.items(), key=lambda kv: (kv[1]["order"], kv[0])):
    ls = sorted(lessons_by_topic[tid])
    print(f"- `{tid}` \"{t['title']}\" order {t['order']} (pack {t['pack']}), max lesson order {ls[-1][0]}: "
          + ", ".join(f"{lid} d{d} {status.get(lid, '?')}" for _, lid, _, d in ls))

boxes = Counter(c["box"] for c in e["cards"] if not c["retired"])
print("\n## Cards\n")
print("Boxes: " + ", ".join(f"{'mastered' if b == 6 else f'box {b}'}: {n}" for b, n in sorted(boxes.items())))
learnable = [i for i in items.values() if not i.get("placementOnly")]
introduced = {c["itemId"] for c in e["cards"]}
print(f"Introduced {sum(1 for i in learnable if i['id'] in introduced)}/{len(learnable)} learnable items; "
      f"not yet introduced: {sum(1 for i in learnable if i['id'] not in introduced)}")
print(f"Item registers in content: {dict(Counter(i['register'] for i in learnable))}")
