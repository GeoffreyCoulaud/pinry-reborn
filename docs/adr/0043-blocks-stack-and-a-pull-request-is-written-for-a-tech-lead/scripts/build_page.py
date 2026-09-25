"""Inject one round's blinded descriptions, shuffled per pull request, into the reading page.

Usage: build_page.py <round>
"""
import json, pathlib, random, sys

ROUND = int(sys.argv[1]) if len(sys.argv) > 1 else 1
SUFFIX = "" if ROUND == 1 else str(ROUND)
ROOT = pathlib.Path(__file__).resolve().parent.parent
key = json.loads((ROOT / "eval" / f"key{SUFFIX}.json").read_text())
ABOUT = {
    "219": ("#219 · API and contract", "The contract declares the export and import states and the handshake publishes the import bounds."),
    "224": ("#224 · The upload", "The account screen gains the file picker and the chunked upload view."),
    "229": ("#229 · Closing block", "The whole-lot review's findings fixed, with the handoff, backlog and specification corrections."),
}
rng = random.Random(7 + ROUND)
prs = []
for pr, (label, about) in ABOUT.items():
    ids = sorted(b for b, stem in key.items() if stem.split("-")[1] == pr)
    rng.shuffle(ids)
    items = [{"id": b, "text": (ROOT / f"blind{SUFFIX}" / f"{b}.full.md").read_text()} for b in ids]
    prs.append({"pr": pr, "label": label, "about": about, "items": items})
count = len(prs[0]["items"])
data = {
    "prs": prs,
    "collection": "rankings" if ROUND == 1 else f"rankings-round-{ROUND}",
    "lede": f"Round {ROUND}. {count} descriptions per pull request, written from the same diff and the same facts. "
            f"Their order and labels are random; nothing says which instructions produced which. For each pull request, "
            f"give every description a rank from 1 (best for reviewing) to {count}, add a note if you like, then save.",
}
page = (ROOT / "eval" / "page-template.html").read_text().replace("/*DATA*/", json.dumps(data).replace("</", "<\\/"))
(ROOT / "eval" / f"blind-reading{SUFFIX}.html").write_text(page)
print(len(page), [[i["id"] for i in p["items"]] for p in prs])
