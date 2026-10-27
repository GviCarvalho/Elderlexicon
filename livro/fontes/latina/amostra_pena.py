"""Amostra da bastarda e das runas a pena, em duas larguras de pena."""
import sys
sys.path.insert(0, ".."); sys.path.insert(0, "../sga")
from PIL import Image, ImageDraw
import pena
from bastarda import compose
from glifos import GLYPHS

INK = (30, 22, 18)


def bbox(polys):
    xs = [x for p in polys for x, _ in p]
    return min(xs), max(xs)


def write(d, text, ox, oy, scale, nib, runes=False, gap=0.32, space=1.5, smooth=1, squeeze=0.88):
    x = ox
    for ch in text:
        if ch == " ":
            x += space * scale
            continue
        if runes:
            _, _, strokes, dots = GLYPHS[ch]
            polys = pena.glyph(strokes, dots, nib=nib, smooth=3)
        else:
            strokes, dots = compose(ch)
            polys = pena.glyph(strokes, dots, nib=nib, smooth=smooth)
            polys = [[(x0_ * squeeze, y_) for x0_, y_ in p] for p in polys]
        x0, x1 = bbox(polys)
        for p in polys:
            d.polygon([(x + (px - x0) * scale, oy + py * scale) for px, py in p], fill=INK)
        x += (x1 - x0 + gap) * scale
    return x


def sheet(nib, name):
    im = Image.new("RGB", (2200, 1500), (238, 228, 205))
    d = ImageDraw.Draw(im)
    d.text((40, 10), f"pena {nib}", fill=(150, 30, 25))
    write(d, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", 40, 50, 15, nib)
    write(d, "abcdefghijklmnopqrstuvwxyz", 40, 230, 18, nib)
    write(d, "áàâãéêíóôõúç ÁÉÍÓÚÇ 0123456789 .,;:!?()«»", 40, 430, 15, nib)
    write(d, "Capítulo 6: As Fontes", 40, 640, 26, nib)
    write(d, "Fundamentos de Siglística", 40, 930, 22, nib)
    write(d, "CABDE QIJ", 40, 1200, 20, nib, runes=True, gap=1.0, space=2.0)
    im.save(name)


sheet(0.80, "amostra_pena.png")
print("ok")
