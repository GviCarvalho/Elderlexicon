"""A pena de ponta larga: o instrumento das bastardas medievais.

A pena é um segmento de largura fixa, segurado num ângulo fixo. Arrastada pelo caminho do traço, ela deixa um rastro
grosso onde o movimento é perpendicular a ela e fino onde é paralelo: é daí que sai o contraste das letras escritas à
mão. Nas pontas, a pena se levanta e o traço afina, o que faz as caudas afiadas.

Cada traço vira vários quadriláteros sobrepostos (o rastro da pena entre dois pontos do caminho), mais um traço fino
que garante que nenhuma parte suma quando o movimento é paralelo à pena.
"""
import math

NIB = 0.95                 # largura da pena, em casas
ANGLE = math.radians(38)   # o ângulo da pena (subindo para a direita)
HAIR = 0.10                # a espessura mínima, a do fio da pena
SMOOTH = 3                 # cortes das quinas (a pena faz curvas, mas guarda um pouco do ângulo)
LIFT = 0.22                # a fração final do traço em que a pena se levanta (cauda afiada)
SLANT = math.radians(5)


def _chaikin(points, n):
    for _ in range(n):
        out = [points[0]]
        for (ax, ay), (bx, by) in zip(points, points[1:]):
            out += [(ax * 0.75 + bx * 0.25, ay * 0.75 + by * 0.25), (ax * 0.25 + bx * 0.75, ay * 0.25 + by * 0.75)]
        out.append(points[-1])
        points = out
    return points


def nib_polygons(points, lift=True, entry=True, nib=NIB, smooth=SMOOTH):
    path = _chaikin(points, smooth) if len(points) > 2 else [
        points[0], ((points[0][0] + points[1][0]) / 2, (points[0][1] + points[1][1]) / 2), points[1]]
    path = _chaikin(path, 2) if len(points) == 2 else path
    s = [0.0]
    for (ax, ay), (bx, by) in zip(path, path[1:]):
        s.append(s[-1] + math.hypot(bx - ax, by - ay))
    total = s[-1] or 1
    hx, hy = math.cos(ANGLE) / 2, -math.sin(ANGLE) / 2

    def half(t):
        k = 1.0
        if entry and t < 0.06:
            k = 0.55 + 0.45 * t / 0.06
        if lift and t > 1 - LIFT:
            k *= 1 - 0.85 * ((t - (1 - LIFT)) / LIFT) ** 1.5
        return nib * k

    polys = []
    for i in range(len(path) - 1):
        (ax, ay), (bx, by) = path[i], path[i + 1]
        wa, wb = half(s[i] / total), half(s[i + 1] / total)
        polys.append([(ax + hx * wa, ay + hy * wa), (bx + hx * wb, by + hy * wb),
                      (bx - hx * wb, by - hy * wb), (ax - hx * wa, ay - hy * wa)])
        L = math.hypot(bx - ax, by - ay) or 1
        nx, ny = -(by - ay) / L * HAIR / 2, (bx - ax) / L * HAIR / 2
        polys.append([(ax + nx, ay + ny), (bx + nx, by + ny), (bx - nx, by - ny), (ax - nx, ay - ny)])
    return polys


def nib_dot(c, nib=NIB):
    """Um ponto feito a pena: um toque curto, que deixa um losango."""
    x, y = c
    return nib_polygons([(x - 0.12, y - 0.18), (x + 0.12, y + 0.18)], lift=False, entry=False, nib=nib * 0.9)


def slant(poly, base=6):
    k = math.tan(SLANT)
    return [(x + (base - y) * k, y) for x, y in poly]


FINE = 0.26   # a largura de um traço fino de floreio, em fração da pena


def glyph(strokes, dots, nib=NIB, lift_all=True, smooth=SMOOTH):
    """Os polígonos de um glifo. Um traço escrito como ("fino", pontos) é um fio de floreio, feito com o canto da pena."""
    polys = []
    for st in strokes:
        if isinstance(st, tuple) and st[0] == "fino":
            polys += nib_polygons(st[1], lift=True, entry=False, nib=nib * FINE, smooth=smooth)
        else:
            polys += nib_polygons(st, lift=lift_all, nib=nib, smooth=smooth)
    for d in dots:
        polys += nib_dot(d, nib)
    return [slant(p) for p in polys]
