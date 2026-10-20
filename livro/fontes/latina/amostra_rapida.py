"""Amostra da escrita rápida: letras ligadas por fios, linha que ondula, inclinação e tinta que variam."""
import sys, random, re, math
sys.path.insert(0, ".."); sys.path.insert(0, "../sga")
from PIL import Image, ImageDraw
import pena
import bastarda
import newton
from glifos import GLYPHS

PAPER = (241, 232, 210)
RUNE_OF = {"aqua": "A", "aura": "B", "igni": "C", "firmo": "D", "vis": "E", "tenet": "F", "ligabis": "G",
           "vertere": "H", "iactare": "I", "vocant": "J", "reframe": "K", "surgit": "L", "impediunt": "M",
           "quantum": "N", "chronos": "O", "ubis": "P"}
NIB = 0.30
SQUEEZE = 0.90


def ink(load):
    """A tinta: escura com a pena cheia, mais clara quando ela vai secando."""
    dark, light = (30, 20, 14), (92, 68, 48)
    return tuple(int(dark[i] + (light[i] - dark[i]) * load) for i in range(3))


def jitter(strokes, dots, rnd, amt=0.11):
    """A pressa: cada letra se deforma um pouco (largura, altura, um leve cisalhamento) e cada ponto treme;
    os pingos e os cortes curtos fogem para a direita, como quem já está na letra seguinte."""
    sx, sy, sh = rnd.uniform(0.88, 1.1), rnd.uniform(0.9, 1.08), rnd.uniform(-0.08, 0.08)
    def j(p):
        x, y = p
        return (x * sx + (6 - y) * sh + rnd.uniform(-amt, amt), 6 - (6 - y) * sy + rnd.uniform(-amt, amt))
    out = []
    for st in strokes:
        pts = st[1] if isinstance(st, tuple) else st
        moved = [j(p) for p in pts]
        if len(pts) == 2 and abs(pts[0][1] - pts[1][1]) < 0.3:   # um corte (o do t, o do f): sai deslocado e mais longo
            dx = rnd.uniform(0.1, 0.5)
            moved = [(moved[0][0] + dx - 0.2, moved[0][1]), (moved[1][0] + dx + 0.3, moved[1][1] - 0.1)]
        out.append((st[0], moved) if isinstance(st, tuple) else moved)
    dots_out = [(j(d)[0] + rnd.uniform(0.2, 0.6), j(d)[1] + rnd.uniform(-0.2, 0.2)) for d in dots]
    return out, dots_out


def first_last(strokes):
    body = [s for s in strokes if not isinstance(s, tuple)]
    return body[0][0], body[-1][-1]


def units(token):
    m = re.fullmatch(r"`(\w+)`([.,;:]?)", token)
    if m:
        return [(RUNE_OF[m.group(1)], "rune")] + [(c, "punct") for c in m.group(2)]
    lead = re.match(r"^([\w\u00C0-\u017F-]+)(.*)$", token)
    word, tail = (lead.group(1), lead.group(2)) if lead else (token, "")
    return [(u, "body") for u in newton.shape(word)] + [(c, "punct") for c in tail]


def render_word(d, token, x, y, scale, rnd, measure=False):
    pena.SLANT = math.radians(rnd.uniform(16, 22)) if not measure else math.radians(19)
    s = scale * (rnd.uniform(0.95, 1.05) if not measure else 1)
    us = units(token)
    prev_exit = None
    n = len(us)
    for k, (unit, kind) in enumerate(us):
        if kind == "rune":
            _, _, strokes, dots = GLYPHS[unit]
            strokes, dots = [list(st) for st in strokes], list(dots)
            polys = pena.glyph(strokes, dots, nib=0.55, smooth=3)
            entry = exit_ = None
        else:
            strokes, dots = newton.compose(unit)
            if not measure:
                strokes, dots = jitter(strokes, dots, rnd)
            # a última letra da palavra estica a saída
            if k == n - 1 or (k < n - 1 and us[k + 1][1] != "body"):
                body = [st for st in strokes if not isinstance(st, tuple)]
                if body and body[-1][-1][1] > 5.0:
                    last = body[-1]
                    ex, ey = last[-1]
                    strokes = [st for st in strokes if st is not last] + [last + [(ex + 0.6, ey - 0.35)]]
            polys = pena.glyph(strokes, dots, nib=NIB, smooth=3)
            if kind == "body" and unit[0].islower():
                (ex0, ey0), (ex1, ey1) = first_last(strokes)
                entry = pena.slant([(ex0, ey0)])[0]
                exit_ = pena.slant([(ex1, ey1)])[0]
            else:
                entry = exit_ = None
        # o avanço vem do esqueleto na linha de base (como numa fonte), não da caixa da letra inclinada
        skel = [px for st in strokes for px, _ in (st[1] if isinstance(st, tuple) else st)] + [px for px, _ in dots]
        x0, x1 = min(skel) - 0.15, max(skel) + 0.15
        X = lambda px: x + (px - x0) * SQUEEZE * s
        if not measure:
            col = ink(min(1.0, k / max(n, 1) * 0.9 + rnd.uniform(0, 0.15)))
            for p in polys:
                d.polygon([(X(px), y + py * s) for px, py in p], fill=col)
            # o fio que liga a letra anterior a esta, sem tirar a pena do papel
            if prev_exit and entry and prev_exit[1] > 4.6 and us[k - 1][0] not in ("c",) and unit[0] not in "acdgoqç":   # o fio não corta o bojo
                (pxe, pye), (nxe, nye) = prev_exit, (X(entry[0]), y + entry[1] * s)
                pxe_s, pye_s = pxe, y + pye * s
                path = [(pxe_s / s, pye_s / s), ((pxe_s + nxe) / 2 / s, (pye_s + nye) / 2 / s - 0.2), (nxe / s, nye / s)]
                saved = pena.SLANT
                pena.SLANT = 0
                for p in pena.nib_polygons(path, lift=False, entry=False, nib=NIB * 0.35):
                    d.polygon([(px * s, py * s) for px, py in p], fill=col)
                pena.SLANT = saved
        prev_exit = (X(exit_[0]), exit_[1]) if exit_ else None
        x = X(x1) + (rnd.uniform(0.0, 0.3) if kind == "body" and not measure else (0.15 if kind == "body" else 0.8)) * s
    return x


def paragraph(d, text, x, y, width, scale, leading, seed):
    rnd = random.Random(seed)
    cx = x
    line_phase = rnd.random() * 6
    for token in text.split():
        w = render_word(None, token, 0, 0, scale, random.Random(0), measure=True)
        if cx + w > x + width:
            cx, y = x, y + leading
            line_phase = rnd.random() * 6
        drift = math.sin(cx / 230 + line_phase) * scale * 0.45 + rnd.uniform(-0.12, 0.12) * scale   # a linha ondula
        cx = render_word(d, token, cx, y + drift, scale, rnd) + rnd.uniform(0.9, 1.6) * scale


im = Image.new("RGB", (1700, 1230), PAPER)
d = ImageDraw.Draw(im)
pena.SLANT = math.radians(5)
x = 80
for word in "Carta ao aluno".split():
    for c in word:
        st, dt = bastarda.compose(c)
        polys = pena.glyph(st, dt, nib=0.8, smooth=1)
        xs = [px for p in polys for px, _ in p]
        for p in polys:
            d.polygon([(x + (px - min(xs)) * 0.88 * 14, 40 + py * 14) for px, py in p], fill=(140, 34, 24))
        x += ((max(xs) - min(xs)) * 0.88 + 0.32) * 14
    x += 1.6 * 14
text = ("Seja bem-vindo à Edubba. Se este livro está nas suas mãos, você atravessou o deserto, passou pelos portões "
        "e foi aceito na casa das tabuletas mais antiga do mundo. Nesta sala, todos começam do mesmo lugar, e eu quero "
        "que você entenda isso antes de qualquer outra coisa: todo ser vivo carrega a mesma quantidade de vis, cem. "
        "A magia não dá mais poder a quem tem mais força. Dá a quem entende melhor. Escreva com afinco, estude o efeito "
        "de cada palavra, e lembre: `igni` `vocant` `iactare` é a lança de fogo, e o espírito é literal.")
paragraph(d, text, 80, 200, 1540, 10.5, 108, 5)
im.save("amostra_rapida.png")
print("ok")
