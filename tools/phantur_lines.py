"""Generates Phantur's lines from docs/phantur-falas.md (the source).

Writes the lang entries (pt_br.json, en_us.json: phantur.elderlexicon.<occasion>.<speech>.<line>) and the index the game
reads (data/elderlexicon/lexicon/phantur.json: for each occasion, each speech's line lengths, which pace it).

Run from the repository root: python tools/phantur_lines.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs" / "phantur-falas.md"
LANG = ROOT / "src" / "main" / "resources" / "assets" / "elderlexicon" / "lang"
INDEX = ROOT / "src" / "main" / "resources" / "data" / "elderlexicon" / "lexicon" / "phantur.json"
PREFIX = "phantur.elderlexicon."


def parse():
    occasions = {}
    current = None
    speech = None
    for raw in SOURCE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        heading = re.match(r"^##\s+`([a-z_]+)`", line)
        if heading:
            current = heading.group(1)
            occasions[current] = []
            speech = None
            continue
        quote = re.match(r"^>\s*(pt|en):\s?(.*)$", line)
        if quote and current:
            if speech is None:
                speech = {"pt": [], "en": []}
                occasions[current].append(speech)
            speech[quote.group(1)].append(quote.group(2).replace("{name}", "%1$s"))
        elif not line:
            speech = None
    for name, speeches in occasions.items():
        for i, s in enumerate(speeches):
            if len(s["pt"]) != len(s["en"]) or not s["pt"]:
                raise SystemExit(f"{name} #{i + 1}: {len(s['pt'])} pt lines, {len(s['en'])} en lines")
    return occasions


def write_lang(file, language, occasions):
    data = json.loads(file.read_text(encoding="utf-8"))
    data = {k: v for k, v in data.items() if not k.startswith(PREFIX)}
    for name, speeches in occasions.items():
        for i, s in enumerate(speeches):
            for j, text in enumerate(s[language]):
                data[f"{PREFIX}{name}.{i}.{j}"] = text
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    occasions = parse()
    write_lang(LANG / "pt_br.json", "pt", occasions)
    write_lang(LANG / "en_us.json", "en", occasions)
    index = {name: [[len(t) for t in s["pt"]] for s in speeches] for name, speeches in occasions.items()}
    INDEX.write_text(json.dumps(index, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    total = sum(len(s) for s in occasions.values())
    print(f"{total} speeches in {len(occasions)} occasions")


if __name__ == "__main__":
    main()
