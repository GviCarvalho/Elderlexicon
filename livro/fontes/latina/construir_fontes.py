"""Gera as duas fontes latinas do livro, escritas com a mesma pena de ../pena.py:

- Siglística Bastarda (títulos, capitulares, epígrafes): a bastarda de bastarda.py, a pena larga, pouca inclinação.
- Siglística Corrente (corpo do texto): a mão rápida do Imhexis (newton.py), pena fina e bem inclinada. Cada letra
  tem três desenhos, que se revezam sozinhos (calt), e uma forma ligada, com o fio que vem da letra anterior quando
  ela termina embaixo (calt). Ligaduras antigas sempre ligadas (liga: ff, fi, fl, st, ct), a abreviação de "que" como
  q com til (dlig), e as palavras das runas viram o glifo da runa quando o recurso ss01 está ligado (só nas palavras
  marcadas como runa; "vis" é palavra comum na prosa). As runas também estão no U+E100 em diante.

Os contornos de cada glifo são a união (skia-pathops) dos rastros da pena.
"""
import math, random, sys, zlib
sys.path.insert(0, ".."); sys.path.insert(0, "../sga")
import pathops
from fontTools.fontBuilder import FontBuilder
from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.reverseContourPen import ReverseContourPen

import pena
import bastarda
import newton
from glifos import GLYPHS as RUNES
from construir_fonte import WORDS as RUNE_WORDS

UPM = 1000
F = bastarda.F

# Sinais que o livro usa e que as mãos ainda não tinham (na grade: maiúsculas de 0 a 6, olho de 3 a 6)
EXTRA = {
    "—": ([[(0.0, 4.6), (4.2, 4.4)]], []),
    "…": ([], [(0.3, 5.7), (1.2, 5.7), (2.1, 5.7)]),
    '"': ([[(0.4, 0.4), (0.2, 1.8)], [(1.2, 0.4), (1.0, 1.8)]], []),
    "*": ([[(1.0, 0.4), (1.0, 2.6)], [(0.0, 1.0), (2.0, 2.0)], [(2.0, 1.0), (0.0, 2.0)]], []),
    "+": ([[(1.2, 3.2), (1.2, 5.8)], [(0.0, 4.5), (2.4, 4.5)]], []),
    "=": ([[(0.0, 3.9), (2.4, 3.8)], [(0.0, 5.1), (2.4, 5.0)]], []),
    "<": ([[(2.2, 3.4), (0.0, 4.5), (2.2, 5.6)]], []),
    ">": ([[(0.0, 3.4), (2.2, 4.5), (0.0, 5.6)]], []),
    "[": ([[(1.2, 0.0), (0.2, 0.0), (0.2, 8.0), (1.2, 8.0)]], []),
    "]": ([[(0.0, 0.0), (1.0, 0.0), (1.0, 8.0), (0.0, 8.0)]], []),
    "%": ([[(2.6, 0.6), (0.0, 6.0)]], [(0.3, 1.2), (2.3, 5.4)]),
    "#": ([[(1.0, 2.0), (0.6, 6.0)], [(2.2, 2.0), (1.8, 6.0)], [(0.0, 3.4), (2.8, 3.4)], [(0.0, 4.8), (2.8, 4.8)]], []),
    "_": ([[(0.0, 6.6), (3.0, 6.6)]], []),
    "|": ([[(0.4, 0.0), (0.4, 8.4)]], []),
    "~": ([[(0.0, 4.6), (0.6, 4.1), (1.4, 4.6), (2.0, 4.1)]], []),
    "·": ([], [(0.3, 4.4)]),
    "×": ([[(0.0, 3.4), (2.0, 5.6)], [(2.0, 3.4), (0.0, 5.6)]], []),
    "÷": ([[(0.0, 4.5), (2.4, 4.5)]], [(1.2, 3.5), (1.2, 5.5)]),
    "→": ([[(0.0, 4.5), (3.4, 4.4)], [(2.4, 3.6), (3.4, 4.4), (2.4, 5.2)]], []),
    "≈": ([[(0.0, 4.0), (0.6, 3.6), (1.4, 4.0), (2.0, 3.6)], [(0.0, 5.2), (0.6, 4.8), (1.4, 5.2), (2.0, 4.8)]], []),
    "≥": ([[(0.0, 3.0), (2.2, 3.9), (0.0, 4.8)], [(0.0, 5.8), (2.2, 5.8)]], []),
    "√": ([[(0.0, 4.4), (0.5, 4.2), (1.0, 6.0), (2.2, 1.2), (4.0, 1.2)]], []),
    "✦": ([[(1.4, 2.2), (1.6, 4.2), (1.4, 6.2)], [(0.0, 4.2), (1.5, 4.0), (3.0, 4.2)]], []),
}


def name_of(unit):
    if unit in RUNE_WORDS:
        return "rune." + unit
    if len(unit) > 1:
        return "_".join(name_of(c) for c in unit)
    return unit if unit.isascii() and unit.isalpha() else f"uni{ord(unit):04X}"


def base_letter(ch):
    return bastarda.COMPOSED[ch][0] if ch in bastarda.COMPOSED else ch


# ------------------------------------------------------------------- contornos

def to_glyph(polys, K, x0):
    """Une os rastros da pena num contorno só e passa para a grade da fonte (y para cima, linha de base em 0)."""
    path = pathops.Path()
    pen = path.getPen()
    for poly in polys:
        pts = [(round((x - x0) * K, 1), round((6 - y) * K, 1)) for x, y in poly]
        a = sum(xa * yb - xb * ya for (xa, ya), (xb, yb) in zip(pts, pts[1:] + pts[:1]))
        if abs(a) < 1e-6:
            continue
        if a < 0:          # todos no mesmo sentido, para a união somar e não furar
            pts.reverse()
        pen.moveTo(pts[0])
        for p in pts[1:]:
            pen.lineTo(p)
        pen.closePath()
    path.simplify(fix_winding=True)
    tt = TTGlyphPen(None)
    target = tt
    contours = list(path.contours)
    if contours:
        # o TrueType quer o contorno de fora no sentido horário
        biggest = max(contours, key=lambda c: abs(c.area))
        if biggest.clockwise is False:
            target = ReverseContourPen(tt)
    path.draw(_RoundPen(target))
    return tt.glyph()


class _RoundPen:
    """Arredonda os pontos para inteiros e descarta os repetidos."""
    def __init__(self, other):
        self.o, self.cur = other, []

    def moveTo(self, p):
        self.cur = [(round(p[0]), round(p[1]))]

    def lineTo(self, p):
        q = (round(p[0]), round(p[1]))
        if q != self.cur[-1]:
            self.cur.append(q)

    def qCurveTo(self, *pts):
        self.lineTo(pts[-1])

    def curveTo(self, *pts):
        self.lineTo(pts[-1])

    def closePath(self):
        pts = self.cur
        if len(pts) > 1 and pts[0] == pts[-1]:
            pts = pts[:-1]
        if len(pts) >= 3:
            self.o.moveTo(pts[0])
            for p in pts[1:]:
                self.o.lineTo(p)
            self.o.closePath()
        self.cur = []

    endPath = closePath


# ------------------------------------------------------------------- a Corrente

class Corrente:
    FAMILY = "Siglistica Corrente"
    K = 150               # unidades da fonte por casa da grade
    NIB = 0.30
    SQUEEZE = 0.90
    GAP = 0.12            # o respiro entre letras (em casas)
    SLANT = 19            # graus
    SPACE = 1.7           # o espaço entre palavras (em casas)
    ALTS = 3
    RUNE_NIB = 0.62

    def __init__(self):
        self.glyphs, self.metrics, self.cmap = {}, {}, {}
        self.order = [".notdef", "space"]
        self.entry_names, self.exit_names, self.alt_bases = [], [], []

    # o desenho de uma unidade (letra, ligadura, abreviação) num dos seus desenhos, ligada ou não
    def draw(self, unit, alt, joined):
        strokes, dots = newton.compose(unit) if unit not in EXTRA else EXTRA[unit]
        strokes = [st if isinstance(st, tuple) else list(st) for st in strokes]
        rnd = random.Random(zlib.crc32(f"{unit}|{alt}".encode()))
        strokes, dots = jitter(strokes, dots, rnd)
        body = [st for st in strokes if not isinstance(st, tuple)]
        lower = unit[0].islower() or unit[0] == "ı"
        skel = [x for st in strokes for x, _ in (st[1] if isinstance(st, tuple) else st)] + [x for x, _ in dots]
        has_exit = False
        untailed = strokes
        if lower and body and base_letter(unit[-1]) != "c":
            # a saída: a ponta de traço mais à direita que termina embaixo; ela se estica para a letra seguinte
            ends = [(st[-1], i) for i, st in enumerate(strokes) if not isinstance(st, tuple) and st[-1][1] > 5.0]
            if ends:
                (ex, ey), i = max(ends, key=lambda e: e[0][0])
                if ex >= max(skel) - 0.35:
                    untailed = list(strokes)
                    strokes[i] = strokes[i] + [(ex + 0.4, ey - 0.25)]
                    skel.append(ex + 0.4)
                    has_exit = True
        pena.SLANT = math.radians(self.SLANT)
        polys = [[(x * self.SQUEEZE, y) for x, y in p] for p in pena.glyph(strokes, dots, nib=self.NIB, smooth=3)]
        # o espaçamento se mede na letra já inclinada, na faixa do olho (onde o olho julga o espaço entre letras);
        # hastes e caudas podem passar por cima da vizinha, como na escrita
        # a cauda de saída é um fio que some; ela passa por baixo da letra seguinte e não conta na largura
        measured = polys if untailed is strokes else [
            [(x * self.SQUEEZE, y) for x, y in p] for p in pena.glyph(untailed, dots, nib=self.NIB, smooth=3)]
        band = [x for p in measured for x, y in p if (3.2 if lower else 2.6) <= y <= 6.3] if unit[0].isalpha() or unit[0] == "ı" else []
        xs = band or [x for p in measured for x, _ in p]
        m = 0.13
        left, right = min(xs) - m, max(xs) + m
        if joined:
            # o fio que vem da saída da letra anterior (logo à esquerda, perto da linha de base) até a entrada
            ex, ey = pena.slant([body[0][0]])[0]
            enx, eny = ex * self.SQUEEZE, ey
            sx, sy = left - self.GAP - m + 0.3, 5.72      # onde termina a cauda da letra anterior
            path = [(sx, sy), ((sx + enx) / 2, (sy + eny) / 2 - 0.2), (enx, eny)]
            polys += pena.nib_polygons(path, lift=False, entry=False, nib=self.NIB * 0.35)
        adv = round((right - left + self.GAP) * self.K)
        return to_glyph(polys, self.K, left), adv, has_exit

    def add(self, name, glyph, adv, char=None):
        self.order.append(name)
        self.glyphs[name], self.metrics[name] = glyph, (adv, 0)
        if char:
            self.cmap[ord(char)] = name

    def build(self):
        empty = TTGlyphPen(None).glyph()
        self.glyphs[".notdef"], self.metrics[".notdef"] = empty, (500, 0)
        self.glyphs["space"], self.metrics["space"] = empty, (round(self.SPACE * self.K), 0)
        self.cmap[0x20], self.cmap[0xA0] = "space", "space"

        letters = [c for c in newton.LETTERS] + [c for c in bastarda.COMPOSED if c not in newton.LETTERS]
        units = letters + list(newton.LIGATURES) + list(newton.ABBREVIATIONS)
        for unit in units:
            base = name_of(unit)
            is_letter = unit[0].isalpha() or unit[0] == "ı"
            alts = self.ALTS if is_letter else 1
            lower = unit[0].islower() or unit[0] == "ı"
            joinable = lower and base_letter(unit[0]) not in "acdgoqç"
            for a in range(alts):
                name = base if a == 0 else f"{base}.a{a}"
                g, adv, ex = self.draw(unit, a, False)
                self.add(name, g, adv, unit if a == 0 and len(unit) == 1 else None)
                if ex:
                    self.exit_names.append(name)
                if joinable:
                    gj, advj, exj = self.draw(unit, a, True)
                    self.add(name + ".j", gj, advj)
                    self.entry_names.append(name)
                    if exj:
                        self.exit_names.append(name + ".j")
            if alts > 1:
                self.alt_bases.append(base)
        for ch in EXTRA:
            if ord(ch) not in self.cmap:
                g, adv, _ = self.draw(ch, 0, False)
                self.add(name_of(ch), g, adv, ch)
        # as runas, na mesma pena, um pouco mais larga, para não sumirem no meio da prosa
        pena.SLANT = math.radians(self.SLANT)
        for i, (word, key) in enumerate(RUNE_WORDS.items()):
            _, _, strokes, dots = RUNES[key]
            polys = pena.glyph(strokes, dots, nib=self.RUNE_NIB, smooth=3)
            xs = [x for p in polys for x, _ in p]
            x0 = min(xs) - 0.2
            self.add(name_of(word), to_glyph(polys, self.K, x0), round((max(xs) - x0 + 0.6) * self.K))
            self.cmap[0xE100 + i] = name_of(word)
        return self

    def features(self):
        letters = [name_of(c) for c in newton.LETTERS if c[0].isalpha()] + [name_of(c) for c in bastarda.COMPOSED]
        out = ["languagesystem DFLT dflt;", "languagesystem latn dflt;", f"@L = [{' '.join(sorted(set(letters)))}];"]
        # as palavras das runas (ss01)
        ss = []
        for word in sorted(RUNE_WORDS, key=len, reverse=True):
            seq = [name_of(c) for c in word]
            out.append(f"lookup r_{word} {{ sub {' '.join(seq)} by {name_of(word)}; }} r_{word};")
            m = " ".join(c + "'" for c in seq)
            ss += [f"  ignore sub @L {m};", f"  ignore sub {m} @L;", f"  sub {seq[0]}' lookup r_{word} {' '.join(c + chr(39) for c in seq[1:])};"]
        out += ["feature ss01 {", '  featureNames { name "Palavras das runas"; };'] + ss + ["} ss01;"]
        # as ligaduras antigas
        out.append("feature liga {")
        for lig in sorted(newton.LIGATURES):
            out.append(f"  sub {' '.join(name_of(c) for c in lig)} by {name_of(lig)};")
        out.append("} liga;")
        # a abreviação de "que", só como palavra inteira
        q = [name_of(c) for c in "que"]
        m = " ".join(c + "'" for c in q)
        out.append(f"lookup abbr_que {{ sub {' '.join(q)} by {name_of('que')}; }} abbr_que;")
        out += ["feature dlig {", f"  ignore sub @L {m};", f"  ignore sub {m} @L;",
                f"  sub {q[0]}' lookup abbr_que {q[1]}' {q[2]}';", "} dlig;"]
        # o revezamento dos desenhos e os fios de ligação
        a0 = self.alt_bases
        c = {k: " ".join(b if k == 0 else f"{b}.a{k}" for b in a0) for k in range(self.ALTS)}
        sep = "[space hyphen comma period]".replace("hyphen", name_of("-")).replace("comma", name_of(",")).replace("period", name_of("."))
        out += [f"@C{k} = [{c[k]}];" for k in range(self.ALTS)]
        out += [f"@IN = [{' '.join(self.entry_names)}];", f"@IN_J = [{' '.join(n + '.j' for n in self.entry_names)}];",
                f"@OUT = [{' '.join(self.exit_names)}];"]
        out += ["lookup cycle {"]
        for k in range(self.ALTS - 1):
            out += [f"  sub @C{k} @C0' by @C{k + 1};", f"  sub @C{k} {sep} @C0' by @C{k + 1};"]
        out += ["} cycle;", "lookup join {", "  sub @OUT @IN' by @IN_J;", "} join;",
                "feature calt {", "  lookup cycle;", "  lookup join;", "} calt;"]
        return "\n".join(out)

    def save(self, path):
        save(self, path, italic=-self.SLANT, ascent=1080, descent=-430, x_height=round(2.4 * self.K),
             cap=round(6 * self.K), desc="A mão rápida do Ummia Imhexis, à pena fina, para o corpo do texto.")


def jitter(strokes, dots, rnd, amt=0.11):
    """A pressa de amostra_rapida.py: cada letra se deforma um pouco e cada ponto treme; pingos e cortes fogem à direita."""
    sx, sy, sh = rnd.uniform(0.88, 1.1), rnd.uniform(0.9, 1.08), rnd.uniform(-0.08, 0.08)

    def j(p):
        x, y = p
        return (x * sx + (6 - y) * sh + rnd.uniform(-amt, amt), 6 - (6 - y) * sy + rnd.uniform(-amt, amt))
    out = []
    for st in strokes:
        pts = st[1] if isinstance(st, tuple) else st
        moved = [j(p) for p in pts]
        if len(pts) == 2 and abs(pts[0][1] - pts[1][1]) < 0.3:
            dx = rnd.uniform(0.1, 0.5)
            moved = [(moved[0][0] + dx - 0.2, moved[0][1]), (moved[1][0] + dx + 0.3, moved[1][1] - 0.1)]
        out.append((st[0], moved) if isinstance(st, tuple) else moved)
    return out, [(j(d)[0] + rnd.uniform(0.2, 0.6), j(d)[1] + rnd.uniform(-0.2, 0.2)) for d in dots]


# ------------------------------------------------------------------- a Bastarda

class Bastarda:
    FAMILY = "Siglistica Bastarda"
    K = 125
    NIB = 0.80
    SQUEEZE = 0.88
    GAP = 0.32
    SPACE = 1.6

    def __init__(self):
        self.glyphs, self.metrics, self.cmap = {}, {}, {}
        self.order = [".notdef", "space"]

    def build(self):
        empty = TTGlyphPen(None).glyph()
        self.glyphs[".notdef"], self.metrics[".notdef"] = empty, (500, 0)
        self.glyphs["space"], self.metrics["space"] = empty, (round(self.SPACE * self.K), 0)
        self.cmap[0x20], self.cmap[0xA0] = "space", "space"
        pena.SLANT = math.radians(5)
        for ch in list(bastarda.ALL) + [c for c in EXTRA if c not in bastarda.ALL]:
            strokes, dots = EXTRA[ch] if ch in EXTRA and ch not in bastarda.ALL else bastarda.compose(ch)
            polys = pena.glyph(strokes, dots, nib=self.NIB, smooth=1)
            xs = [x for p in polys for x, _ in p]
            x0 = min(xs)
            squeezed = [[(x0 + (x - x0) * self.SQUEEZE, y) for x, y in p] for p in polys]
            name = name_of(ch)
            self.order.append(name)
            self.glyphs[name] = to_glyph(squeezed, self.K, x0)
            self.metrics[name] = (round(((max(xs) - x0) * self.SQUEEZE + self.GAP) * self.K), 0)
            self.cmap[ord(ch)] = name
        return self

    def features(self):
        return ""

    def save(self, path):
        save(self, path, italic=-5, ascent=1000, descent=-400, x_height=round(3 * self.K), cap=round(6 * self.K),
             desc="A bastarda do Ummia Imhexis, à pena larga, para títulos, capitulares e epígrafes.")


# ------------------------------------------------------------------- gravar

def save(f, path, italic, ascent, descent, x_height, cap, desc):
    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(f.order)
    fb.setupCharacterMap(f.cmap)
    fb.setupGlyf(f.glyphs)
    for name, g in f.glyphs.items():
        if g.numberOfContours > 0:
            g.flags[0] |= 0x40
    # a margem esquerda do hmtx tem de ser o xMin do glifo: é por ela que o TrueType posiciona a letra
    glyf = fb.font["glyf"]
    fb.setupHorizontalMetrics({n: (adv, glyf[n].xMin if glyf[n].numberOfContours > 0 else 0)
                               for n, (adv, _) in f.metrics.items()})
    fb.setupHorizontalHeader(ascent=ascent, descent=descent)
    fb.setupNameTable({
        "familyName": f.FAMILY, "styleName": "Regular",
        "uniqueFontIdentifier": f.FAMILY + " Regular 0.1", "fullName": f.FAMILY + " Regular",
        "psName": f.FAMILY.replace(" ", "") + "-Regular", "version": "Version 0.1",
        "designer": "Elderlexicon", "description": desc,
    })
    fb.setupOS2(sTypoAscender=ascent, sTypoDescender=descent, sTypoLineGap=0, usWinAscent=ascent + 60,
                usWinDescent=-descent + 60, sCapHeight=cap, sxHeight=x_height)
    fb.setupPost(italicAngle=italic)
    fea = f.features()
    if fea:
        addOpenTypeFeaturesFromString(fb.font, fea)
    fb.save(path)
    print(f"{path}: {len(f.order)} glifos, {len(f.cmap)} códigos")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    Bastarda().build().save("SiglisticaBastarda.ttf")
    Corrente().build().save("SiglisticaCorrente.ttf")
