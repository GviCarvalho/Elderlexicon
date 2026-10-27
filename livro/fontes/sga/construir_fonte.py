"""Gera a fonte do SGA manuscrito (TTF) a partir de glifos.py, escrita com a pena de ../pena.py.

As letras A a Z (maiúsculas e minúsculas) dão os glifos do SGA; os algarismos 0 a 9 e U+E000 a U+E009, os
algarismos do mod; U+E010 a U+E015, as runas novas do mod (languet, mergitur, aut, non, ego, ille).
"""
from fontTools.fontBuilder import FontBuilder
from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.pens.ttGlyphPen import TTGlyphPen

from glifos import GLYPHS, CODEPOINTS, width
import sys
sys.path.insert(0, "..")
import pena

NIB = 0.80  # a mesma pena da bastarda (../latina): a mesma mão, o mesmo instrumento

FAMILY = "Siglistica Manuscrita"
UPM = 1000
CELL = 100          # uma casa da grade
SIDE = 50           # margem de cada lado do glifo
ASCENT, DESCENT = 800, -200
OUT = "SiglisticaManuscrita.ttf"


def to_font(x, y):
    # A casa (0, 0) é o canto de cima à esquerda; o centro da linha 6 fica sobre a linha de base.
    return round(SIDE + x * CELL + CELL / 2), round((6 - y) * CELL + CELL / 2)


def area(pts):
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])) / 2


def draw(char):
    _, _, strokes, dots = GLYPHS[char]
    pen = TTGlyphPen(None)
    for poly in pena.glyph(strokes, dots, nib=NIB, smooth=3):
        pts = [to_font(x, y) for x, y in poly]
        pts = [p for i, p in enumerate(pts) if p != pts[i - 1]]
        if len(pts) < 3:
            continue
        if area(pts) > 0:          # todos no mesmo sentido (horário), para a sobreposição somar
            pts.reverse()
        pen.moveTo(pts[0])
        for p in pts[1:]:
            pen.lineTo(p)
        pen.closePath()
    glyph = pen.glyph()
    if glyph.numberOfContours > 0:
        glyph.flags[0] |= 0x40     # OVERLAP_SIMPLE: os contornos se sobrepõem de propósito
    return glyph


# As palavras da Língua Antiga que viram o glifo da runa, quando escritas sozinhas (ligadura)
WORDS = {
    "aqua": "A", "aura": "B", "igni": "C", "firmo": "D", "vis": "E", "tenet": "F", "ligabis": "G",
    "vertere": "H", "iactare": "I", "vocant": "J", "reframe": "K", "surgit": "L", "impediunt": "M",
    "quantum": "N", "chronos": "O", "ubis": "P",
    "ferit": "Q", "patitur": "R", "necat": "S", "salit": "T", "cadit": "U", "frangit": "V", "utitur": "W",
    "latet": "X", "currit": "Y", "ardet": "Z",
    "languet": "languet", "mergitur": "mergitur", "aut": "aut", "non": "non", "ego": "ego", "ille": "ille",
}


def features():
    """A ligadura: uma palavra inteira da língua vira o glifo; dentro de outra palavra (uma marca), não."""
    letters = " ".join("ABCDEFGHIJKLMNOPQRSTUVWXYZ") + " languet mergitur aut non ego ille"
    out = ["languagesystem DFLT dflt;", "languagesystem latn dflt;", f"@L = [{letters}];"]
    rules = []
    for word, glyph in sorted(WORDS.items(), key=lambda kv: -len(kv[0])):
        seq = [c.upper() for c in word]
        name = f"w_{word}"
        out.append(f"lookup {name} {{ sub {' '.join(seq)} by {glyph}; }} {name};")
        marked = " ".join(f"{c}'" for c in seq)
        rules += [f"  ignore sub @L {marked};", f"  ignore sub {marked} @L;",
                  f"  sub {seq[0]}' lookup {name} {' '.join(c + chr(39) for c in seq[1:])};"]
    out.append("feature liga {")
    out += rules
    out.append("} liga;")
    return "\n".join(out)


def glyph_name(char):
    if char.isdigit():
        return ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"][int(char)]
    return char


def main():
    order = [".notdef", "space"] + [glyph_name(c) for c in GLYPHS]
    glyphs, metrics, cmap = {}, {}, {}

    empty = TTGlyphPen(None).glyph()
    glyphs[".notdef"], metrics[".notdef"] = empty, (500, 0)
    glyphs["space"], metrics["space"] = empty, (400, 0)
    cmap[0x20] = "space"

    for char in GLYPHS:
        name = glyph_name(char)
        glyphs[name] = draw(char)
        metrics[name] = (width(char) * CELL + 2 * SIDE + CELL // 2, SIDE)
        for cp in CODEPOINTS[char]:
            cmap[cp] = name

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    # a margem esquerda do hmtx tem de ser o xMin do glifo: é por ela que o TrueType posiciona a letra
    metrics = {n: (adv, glyphs[n].xMin if glyphs[n].numberOfContours > 0 else 0) for n, (adv, _) in metrics.items()}
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=ASCENT, descent=DESCENT)
    fb.setupNameTable({
        "familyName": FAMILY,
        "styleName": "Regular",
        "uniqueFontIdentifier": FAMILY + " Regular 0.4",
        "fullName": FAMILY + " Regular",
        "psName": FAMILY.replace(" ", "") + "-Regular",
        "version": "Version 0.4",
        "designer": "Elderlexicon",
        "description": "O Standard Galactic Alphabet escrito a pena, com os glifos da siglística.",
    })
    fb.setupOS2(sTypoAscender=ASCENT, sTypoDescender=DESCENT, sTypoLineGap=200,
                usWinAscent=ASCENT + 100, usWinDescent=-DESCENT + 100, sCapHeight=700, sxHeight=700)
    fb.setupPost()
    addOpenTypeFeaturesFromString(fb.font, features())
    fb.save(OUT)
    print(f"{OUT}: {len(order)} glifos, {len(cmap)} códigos")


if __name__ == "__main__":
    main()
