"""Unblind quiz scores and fact coverage, per arm and per pull request."""
import json, pathlib, sys
from collections import defaultdict

ROUND = int(sys.argv[1]) if len(sys.argv) > 1 else 1
S = "" if ROUND == 1 else str(ROUND)
E = pathlib.Path(__file__).resolve().parent
key = json.loads((E / f"key{S}.json").read_text())
stats = json.loads((E / f"stats{S}.json").read_text())
rows = []
for pr in ("219", "224", "229"):
    scores = json.loads((E / f"scores{S}-{pr}.json").read_text())
    cover = json.loads((E / f"coverage{S}-{pr}.json").read_text())
    for b, s in scores.items():
        stem = key[b]
        c = cover[b]
        facts = c["yes"] + c["partial"] + c["no"]
        rows.append({"stem": stem, "arm": stem.split("-")[0], "pr": pr, "blind": b,
                     "quiz": s["context"] + s["why"] + s["how"], "q": (s["context"], s["why"], s["how"]),
                     "facts_yes": c["yes"], "facts_partial": c["partial"], "facts_no": c["no"], "facts": facts,
                     "text_lines": stats[stem]["visible_text_lines"]})
for r in sorted(rows, key=lambda r: (r["pr"], r["arm"], r["stem"])):
    print(f'{r["stem"]:15} {r["blind"]} quiz={r["quiz"]}/6 {r["q"]} facts yes={r["facts_yes"]} partial={r["facts_partial"]} no={r["facts_no"]} /{r["facts"]} lines={r["text_lines"]}')
agg = defaultdict(list)
for r in rows:
    agg[r["arm"]].append(r)
for arm, rs in sorted(agg.items()):
    n = len(rs)
    print(f'{arm:8} quiz mean={sum(r["quiz"] for r in rs)/n:.2f}/6  facts yes={sum(r["facts_yes"] for r in rs)/sum(r["facts"] for r in rs):.0%} '
          f'no={sum(r["facts_no"] for r in rs)/sum(r["facts"] for r in rs):.0%}  lines mean={sum(r["text_lines"] for r in rs)/n:.1f}')
(E / f"results{S}.json").write_text(json.dumps(rows, indent=1))
