"""Builds the mod's own glyphs from tools/glyphs/glyphs.txt (docs/glifos-design.md).

    python tools/glyphs/build.py                  writes the sheet, the font and the preview
    python tools/glyphs/build.py --drafts F.txt   only a preview of the glyphs in F.txt beside the alphabet

Writes:
    src/main/resources/assets/elderlexicon/textures/font/sga_glyphs.png   16x16 cells of 8x8, U+E000 to U+E0FF
    src/main/resources/assets/elderlexicon/font/sga.json                  the game's letters and that sheet
    tools/glyphs/preview.png                                              every glyph, large, beside the letters

The game's own letters (minecraft:alt) are read from the Minecraft client jar in the Gradle cache, for the preview
and the distance checks only; they are never copied into the mod.
"""
import argparse
import glob
import io
import json
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, 'tools', 'glyphs', 'glyphs.txt')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'elderlexicon')
SHEET = os.path.join(ASSETS, 'textures', 'font', 'sga_glyphs.png')
FONT = os.path.join(ASSETS, 'font', 'sga.json')
PREVIEW = os.path.join(ROOT, 'tools', 'glyphs', 'preview.png')

FIRST, LAST = 0xE000, 0xE0FF
FIRST_RUNE = 0xE010
WIDTH, HEIGHT = 5, 7
MIN_DISTANCE = 4


def read(path):
    """The glyphs of a file: {code point: (name, rows)}."""
    glyphs = {}
    lines = [line.rstrip() for line in open(path, encoding='utf-8')]
    lines = [line for line in lines if line and not line.startswith('#') or line.startswith('#') and set(line) <= set('#.')]
    i = 0
    while i < len(lines):
        head = lines[i].split()
        code, name = int(head[0], 16), (head[1] if len(head) > 1 else '?')
        rows = lines[i + 1:i + 1 + HEIGHT]
        problems = []
        if not FIRST <= code <= LAST:
            problems.append('outside U+E000-E0FF')
        if code in glyphs:
            problems.append('given twice')
        if len(rows) != HEIGHT or any(len(r) > WIDTH or set(r) - set('#.') for r in rows):
            problems.append('must be 7 rows of up to 5 of # and .')
        if not any('#' in r for r in rows):
            problems.append('has no ink')
        if problems:
            sys.exit('U+%04X %s: %s' % (code, name, '; '.join(problems)))
        glyphs[code] = (name, [r.ljust(WIDTH, '.') for r in rows])
        i += 1 + HEIGHT
    return glyphs


def letters():
    """The game's SGA letters A-Z as rows, or {} when no Minecraft client jar is found."""
    home = os.path.expanduser('~')
    for jar in glob.glob(os.path.join(home, '.gradle', 'caches', '**', 'client-extra.jar'), recursive=True):
        with zipfile.ZipFile(jar) as z:
            try:
                image = Image.open(io.BytesIO(z.read('assets/minecraft/textures/font/ascii_sga.png'))).convert('RGBA')
            except KeyError:
                continue
        found = {}
        for i, ch in enumerate('ABCDEFGHIJKLMNOPQRSTUVWXYZ'):
            r, c = 4 + (i + 1) // 16, (i + 1) % 16
            found[ch] = [''.join('#' if image.getpixel((c * 8 + x, r * 8 + y))[3] else '.' for x in range(WIDTH))
                         for y in range(HEIGHT)]
        return found
    return {}


def distance(a, b):
    return sum(1 for ra, rb in zip(a, b) for pa, pb in zip(ra, rb) if pa != pb)


def mirror(rows):
    return [r[::-1] for r in rows]


def check(glyphs, alphabet):
    """Warns of a rune glyph too close to another glyph, or the mirror of one."""
    others = {('U+%04X %s' % (code, name)): rows for code, (name, rows) in glyphs.items()}
    others.update({('letter ' + ch): rows for ch, rows in alphabet.items()})
    warnings = []
    for code, (name, rows) in glyphs.items():
        if code < FIRST_RUNE:
            continue
        me = 'U+%04X %s' % (code, name)
        for other, theirs in others.items():
            if other == me:
                continue
            d = distance(rows, theirs)
            if d < MIN_DISTANCE:
                warnings.append('%s differs from %s in only %d pixels' % (me, other, d))
            if mirror(rows) == theirs:
                warnings.append('%s is the mirror of %s' % (me, other))
    return warnings


def draw_preview(glyphs, alphabet, path, title):
    scale, cell = 8, 64
    entries = [(ch, rows, (90, 80, 70)) for ch, rows in alphabet.items()]
    entries += [('%04X' % code + ' ' + name, rows, (40, 30, 20)) for code, (name, rows) in sorted(glyphs.items())]
    cols = 13
    lines = (len(entries) + cols - 1) // cols
    image = Image.new('RGB', (cols * cell * 2, 40 + lines * (cell + 40)), (245, 240, 225))
    draw = ImageDraw.Draw(image)
    try:
        label = ImageFont.truetype('arial.ttf', 13)
    except OSError:
        label = ImageFont.load_default()
    draw.text((10, 10), title, font=label, fill=(120, 0, 0))
    for n, (name, rows, ink) in enumerate(entries):
        x, y = (n % cols) * cell * 2 + 20, 40 + (n // cols) * (cell + 40)
        for yy, row in enumerate(rows):
            for xx, p in enumerate(row):
                colour = ink if p == '#' else (228, 220, 202)
                draw.rectangle([x + xx * scale, y + yy * scale, x + xx * scale + scale - 2, y + yy * scale + scale - 2],
                               fill=colour)
        draw.text((x, y + HEIGHT * scale + 4), name, font=label, fill=(120, 0, 0))
    image.save(path)


def build(glyphs):
    sheet = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    for code, (_, rows) in glyphs.items():
        index = code - FIRST
        for y, row in enumerate(rows):
            for x, p in enumerate(row):
                if p == '#':
                    sheet.putpixel(((index % 16) * 8 + x, (index // 16) * 8 + y), (255, 255, 255, 255))
    sheet.save(SHEET)
    chars = [''.join(chr(FIRST + r * 16 + c) if FIRST + r * 16 + c in glyphs else '\u0000' for c in range(16))
             for r in range(16)]
    font = {"providers": [
        {"type": "reference", "id": "minecraft:alt"},
        {"type": "bitmap", "file": "elderlexicon:font/sga_glyphs.png", "ascent": 7, "chars": chars}]}
    open(FONT, 'w', encoding='utf-8', newline='\n').write(json.dumps(font, indent=2, ensure_ascii=True) + '\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--drafts', help='a file of draft glyphs to preview beside the alphabet; nothing is written')
    parser.add_argument('--out', default=PREVIEW, help='where the preview goes')
    args = parser.parse_args()
    glyphs = read(SOURCE)
    alphabet = letters()
    if not alphabet:
        print('warning: no Minecraft client jar found; letters left out of the checks and the preview')
    if args.drafts:
        drafts = read(args.drafts)
        clash = set(drafts) & set(glyphs)
        if clash:
            sys.exit('drafts take places already given: ' + ', '.join('U+%04X' % c for c in sorted(clash)))
        everything = dict(glyphs)
        everything.update(drafts)
        warnings = check(everything, alphabet)
        draw_preview(everything, alphabet, args.out, 'Drafts: ' + os.path.basename(args.drafts))
    else:
        warnings = check(glyphs, alphabet)
        build(glyphs)
        draw_preview(glyphs, alphabet, args.out, 'The alphabet of the mod')
        print('wrote', os.path.relpath(SHEET, ROOT), 'and', os.path.relpath(FONT, ROOT))
    for warning in warnings:
        print('warning:', warning)
    print('preview:', os.path.relpath(args.out, ROOT) if args.out.startswith(ROOT) else args.out)


if __name__ == '__main__':
    main()
