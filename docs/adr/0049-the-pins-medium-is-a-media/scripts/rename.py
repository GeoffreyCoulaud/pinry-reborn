"""Replays block 10 of lot 0.45.0: wherever "image" names the pin's medium, it becomes "media" (ADR 0049).

Usage: python3 docs/adr/0049-the-pins-medium-is-a-media/scripts/rename.py, from the repository root
on a clean tree with the clients installed. It moves files with `git mv`, rewrites them in place,
lets Biome sort the imports again, and prints every identifier it renamed with its count.

A name is a run of letters, digits and underscores. A compound name (`ImageStore`, `image_download`)
is renamed wherever it appears. A bare word (`image`, `Images`) is renamed in code, and in a comment
or a string only where it is part of a name: touching a slash, a dot before a name, a quote, a
backtick, a bracket, a brace, a dollar, an underscore or a hyphen before a word. A bare word between
spaces is prose and stays, so the sentences the tests read keep their wording.
"""
import re, subprocess, sys
from collections import Counter
from pathlib import Path

# Where "image" still names an image (specification 2026-10-02, section 4).
KEPT = {
    "ImageProbe", "ImageProbeException", "UnsupportedImageFormatException", "UndecodableImageException",
    "ImageTooManyPixelsException", "VipsImageProbe", "VipsImageProbeTest", "imageProbe",
    "ImageTransformer", "VipsImageTransformer", "VipsImageTransformerTest", "imageTransformer",
    "VImage", "createImageBitmap", "ImageBitmap", "HTMLImageElement", "ImageHash",
}
# Where the rule's output would not read.
OVERRIDES = {"anImage": "aMedia"}
# A variable named `images` would become the `media` its elements already are.
PLURAL_VARIABLES = {
    "AccountDeletionCleanerTest.kt": "mediaRepository", "DeletePinImageTest.kt": "mediaRepository",
    "DownloadPinImageTest.kt": "mediaRepository", "GetPinImageTest.kt": "mediaRepository",
    "ResolvePinImageStateTest.kt": "mediaRepository", "SetPinImageTest.kt": "mediaRepository",
    "EbeanTransactionRunnerTest.kt": "mediaRepository", "PinRecycleBin.kt": "deletedMedia",
    "ResolvePinImageState.kt": "mediaByPin",
}

# Spans never renamed: a MIME type, a message key of the catalogue, the DOM's `as`, a fixture's name.
PROTECTED = re.compile(r"image(?=/(?!status\b))|\bm\.\w+|\bas(: |=)\"image\"|not-an-image")

CODE = re.compile(r"^(api/[^/]+/src/|api/config/detekt/|clients/apps/webapp/src/)")
UNTOUCHED = re.compile(r"/dbmigration/|\.(png|jpe?g|gif|webp|tiff|avif)$")
# The libvips adapter's `image` is a libvips image: only compound names and the package change there.
NAMES_ONLY = re.compile(r"^api/api-imaging-vips/")
# Living documents whose names follow, their prose staying.
DOCUMENTS = {"api/Dockerfile", "compose.yml", "docs/backlog.md"}

NAME = re.compile(r"[A-Za-z0-9_]+")
STEM = re.compile(r"(?<![A-Za-z])images?(?![a-z])|Images?(?![a-z])|(?<![A-Za-z])IMAGES?(?![A-Z])")
CASES = {"image": "media", "images": "media", "Image": "Media", "Images": "Media", "IMAGE": "MEDIA", "IMAGES": "MEDIA"}

renamed = Counter()


def rename_name(name):
    if name in KEPT:
        return name
    if name in OVERRIDES:
        return OVERRIDES[name]
    return STEM.sub(lambda match: CASES[match.group()], name)


def attached(text, start, end):
    before = text[start - 1] if start > 0 else " "
    after = text[end:end + 3] + "   "
    if before in "/.$[{`_-":
        return True
    if after[0] in "/_":
        return True
    # A closing quote ends a name (`"image"`) or a sentence (`"touches no image"`).
    if after[0] in "`\"']}" and not before.isspace() and after[:2] != "'s":
        return True
    # `images.data_dir` and `images.*` are keys; `image.` and Markdown's `image.**` end a sentence.
    if after[0] == "." and (after[1].isalnum() or (after[1] == "*" and after[2] != "*")):
        return True
    return after[0] == "-" and after[1].isalpha()


def rewrite(text, regions, protected, sources, plural):
    """Renames the names of [regions], a list of (start, end, is_code) spans covering [text]."""
    out, cursor = [], 0
    for start, end, is_code in regions:
        for match in NAME.finditer(text, start, end):
            name = match.group()
            new = rename_name(name)
            if plural and is_code is True and name == "images" and text[match.start() - 1] != ".":
                new = plural
            if new == name or any(s < match.end() and match.start() < e for s, e in protected):
                continue
            if name in CASES and is_code is None and text[match.start() - 1] != ".":
                continue
            if name in CASES and is_code is False and not attached(text, *match.span()):
                continue
            out.append(text[cursor:match.start()])
            out.append(new)
            cursor = match.end()
            renamed[name] += 1
            if is_code is True and text[match.start() - 1] != ".":
                sources.setdefault(new, set()).add(name)
    out.append(text[cursor:])
    return "".join(out)


def collisions(path, text, regions, sources):
    """Two names that become one in a file's code, or a new name its code already held."""
    present = {name for start, end, is_code in regions if is_code is True for name in NAME.findall(text, start, end)}
    for new, olds in sources.items():
        if len(olds) > 1 or (new in present and new not in olds):
            print(f"collision in {path}: {sorted(olds)} -> {new}", file=sys.stderr)


def lex(text, kotlin):
    """Splits source into code and prose (comments, strings, Kotlin's backticked names)."""
    regions, i, start, n = [], 0, 0, len(text)

    def close(kind_end, is_code):
        nonlocal start
        if kind_end > start:
            regions.append((start, kind_end, is_code))
        start = kind_end

    def skip_template(j):
        depth = 1
        while j < n and depth:
            depth += {"{": 1, "}": -1}.get(text[j], 0)
            j += 1
        return j

    while i < n:
        two = text[i:i + 2]
        if two == "//":
            close(i, True)
            i = text.find("\n", i)
            i = n if i < 0 else i
            close(i, False)
        elif two == "/*":
            close(i, True)
            i = text.find("*/", i + 2)
            i = n if i < 0 else i + 2
            close(i, False)
        elif kotlin and text.startswith('"""', i):
            close(i, True)
            i = text.find('"""', i + 3)
            i = n if i < 0 else i + 3
            while i < n and text[i] == '"':
                i += 1
            close(i, False)
        elif text[i] in "\"'`":
            quote = text[i]
            close(i, True)
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\":
                    i += 1
                elif text.startswith("${", i) and quote != "'":
                    i = skip_template(i + 2) - 1
                elif text[i] == "\n" and quote != "`":
                    break
                i += 1
            i += 1
            close(min(i, n), False)
        else:
            i += 1
    close(n, True)
    return regions


def regions_of(path, text):
    if NAMES_ONLY.match(path):
        return [(0, len(text), None)]
    if path in DOCUMENTS:
        return [(0, len(text), False)]
    suffix = Path(path).suffix
    if suffix in (".kt", ".kts"):
        return lex(text, kotlin=True)
    if suffix in (".ts", ".tsx"):
        return lex(text, kotlin=False)
    if suffix == ".properties":
        return [(m.start(), m.end(), not m.group().lstrip().startswith("#")) for m in re.finditer(r".*\n?", text)]
    if suffix == ".xml":
        return [(0, len(text), True)]
    return [(0, len(text), False)]


def protected_spans(path, text):
    spans = [m.span() for m in PROTECTED.finditer(text)]
    if not path.endswith((".ts", ".tsx")):
        spans = [(s, e) for s, e in spans if not text.startswith("m.", s)]
    return spans


def rename_compound(match):
    return match.group() if match.group() in CASES else rename_name(match.group())


def new_path(path):
    """A directory or file named by a bare word is renamed (`images.ts`), otherwise its compound names."""
    parts = []
    for part in path.split("/"):
        stem, dot, rest = part.partition(".")
        stem = rename_name(stem) if stem in CASES else NAME.sub(rename_compound, stem)
        parts.append(stem + dot + rest)
    return "/".join(parts)


def main():
    files = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout.split()
    files = [f for f in files if (CODE.match(f) or f in DOCUMENTS) and not UNTOUCHED.search(f)]
    for path in files:
        text, sources = Path(path).read_text(), {}
        regions = regions_of(path, text)
        result = rewrite(text, regions, protected_spans(path, text), sources, PLURAL_VARIABLES.get(Path(path).name))
        collisions(path, text, regions, sources)
        target = new_path(path) if CODE.match(path) else path
        if target != path:
            Path(target).parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["git", "mv", path, target], check=True)
        if result != text:
            Path(target).write_text(result)
    # A renamed module sorts elsewhere among the imports.
    subprocess.run(["pnpm", "exec", "biome", "check", "--write", "apps/webapp/src"], cwd="clients", check=True)
    for name, count in sorted(renamed.items()):
        print(f"{count:5} {name} -> {rename_name(name)}")


if __name__ == "__main__":
    sys.exit(main())
