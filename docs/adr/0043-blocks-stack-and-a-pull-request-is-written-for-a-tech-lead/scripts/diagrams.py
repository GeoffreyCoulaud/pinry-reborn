"""Kind and size (non-blank lines) of each visible mermaid diagram, per output of one round."""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
for folder in sys.argv[1:]:
    for f in sorted((ROOT / folder).glob("*.md")):
        visible = re.sub(r"<details>.*?</details>", "", f.read_text(), flags=re.S)
        diagrams = re.findall(r"```mermaid\n(.*?)```", visible, flags=re.S)
        print(folder, f.stem, [(d.split()[0], sum(1 for l in d.splitlines() if l.strip())) for d in diagrams])
