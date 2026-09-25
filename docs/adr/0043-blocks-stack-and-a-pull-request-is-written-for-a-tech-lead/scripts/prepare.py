"""Blind one round's outputs: visible title+body per random id, a key file, and line counts.

Usage: prepare.py <round>, reading out<suffix>/ and writing blind<suffix>/, eval/key<suffix>.json, eval/stats<suffix>.json
(round 1 has no suffix).
"""
import json, pathlib, random, re, sys

ROUND = int(sys.argv[1]) if len(sys.argv) > 1 else 1
SUFFIX = "" if ROUND == 1 else str(ROUND)
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT, BLIND = ROOT / f"out{SUFFIX}", ROOT / f"blind{SUFFIX}"
BLIND.mkdir(exist_ok=True)

files = sorted(OUT.glob("*.md"))
rng = random.Random(20260925 + ROUND)
ids = rng.sample(range(100, 1000), len(files))
key, stats = {}, {}
for f, n in zip(files, ids):
    text = f.read_text()
    visible = re.sub(r"<details>.*?</details>", "", text, flags=re.S).rstrip() + "\n"
    blind_id = f"b{n}"
    (BLIND / f"{blind_id}.md").write_text(visible)
    (BLIND / f"{blind_id}.full.md").write_text(text)
    key[blind_id] = f.stem
    # Text lines of the visible body: mermaid code excluded, other code counted, blank lines excluded.
    body = visible.split("\n", 2)[2] if visible.count("\n") > 1 else ""
    body = re.sub(r"```mermaid.*?```", "", body, flags=re.S)
    stats[f.stem] = {
        "visible_text_lines": sum(1 for line in body.splitlines() if line.strip()),
        "total_lines": len(text.splitlines()),
        "tables": bool(re.search(r"^\|.*\|\s*$", visible, flags=re.M)),
        "mermaid": visible.count("```mermaid"),
        "details": "<details>" in text,
    }
(ROOT / "eval" / f"key{SUFFIX}.json").write_text(json.dumps(key, indent=1))
(ROOT / "eval" / f"stats{SUFFIX}.json").write_text(json.dumps(stats, indent=1))
for stem, s in sorted(stats.items()):
    print(stem, s)
