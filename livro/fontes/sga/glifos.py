"""O SGA manuscrito: os glifos da siglística desenhados à mão, sem mudar a forma de nenhum.

O Standard Galactic Alphabet é canônico neste sistema: as letras de A a Z são as runas (A a P) e os Fatos (Q a Z),
e o leitor as aprendeu jogando. Esta fonte só muda o traço: cada glifo é desenhado como traços de estilete, largos
no começo e finos no fim, por cima da forma em pixels que o jogo e o mod usam.

Cada glifo guarda:
- a forma de referência em pixels (5 colunas por 7 linhas, de cima para baixo), que é a canônica;
- os traços manuscritos (listas de pontos na mesma grade) e os pontos soltos.
A conferência compara os traços com os pixels, para que nenhum glifo se afaste da forma que o leitor conhece.
"""

# nome do glifo: (caractere, runa, pixels canônicos, traços, pontos)
GLYPHS = {}


def g(char, rune, pixels, strokes, dots=()):
    GLYPHS[char] = (rune, [row.ljust(5, ".")[:5] for row in pixels.split()], strokes, list(dots))


# As letras do SGA: as runas
g("A", "aqua", "..##. .#..# .#... .#... .#... .#... ##...",
  [[(4, 1.1), (3.4, 0.1), (2.2, 0.1), (1.25, 0.8), (1, 1.7), (1, 6), (0, 6)]])
g("B", "aura", "..#.. ..#.. ..#.. ..#.. ...#. ....# #####",
  [[(2, 0), (2, 3.1), (4, 5.0), (4, 6), (0, 6)]])
g("C", "igni", "#.... ..... #.... #.... ##... .#... .#...",
  [[(0, 2), (0, 4.1), (1, 4.7), (1, 6)]], [(0, 0)])
g("D", "firmo", "##### ..... ##... ..#.. ...## ..... .....",
  [[(0, 0), (4, 0)], [(0, 2), (1.1, 2.15), (2.6, 3.5), (4, 4)]])
g("E", "vis", "#...# #.... #.... #.... #.... #.... #####",
  [[(0, 0), (0, 6), (4, 6)]], [(4, 0)])
g("F", "tenet", "##### ..... #.#.# ..... ..... ..... .....",
  [[(0, 0), (4, 0)]], [(0, 2), (2, 2), (4, 2)])
g("G", "ligabis", "..#.. ..#.. ..#.. ###.. ..#.. ..#.. ..#..",
  [[(2, 0), (2, 6)], [(0, 3), (2, 3)]])
g("H", "vertere", "##### ..... ##### ..#.. ..#.. ..#.. ..#..",
  [[(0, 0), (4, 0)], [(0, 2), (4, 2)], [(2, 2), (2, 6)]])
g("I", "iactare", "#.... #.... #.... ..... #.... #.... #....",
  [[(0, 0), (0, 2)], [(0, 4), (0, 6)]])
g("J", "vocant", "#.... #.... ..... #.... ..... #.... #....",
  [[(0, 0), (0, 1)], [(0, 5), (0, 6)]], [(0, 3)])
g("K", "reframe", "..#.. ..#.. ..#.. #.#.# ..#.. ..#.. ..#..",
  [[(2, 0), (2, 6)]], [(0, 3), (4, 3)])
g("L", "surgit", "#.... #.#.. #.... #.... #.... #.#.. #....",
  [[(0, 0), (0, 6)]], [(2, 1), (2, 5)])
g("M", "impediunt", "#...# ....# ....# ....# ....# ....# #####",
  [[(4, 0), (4, 6), (0, 6)]], [(0, 0)])
g("N", "quantum", "#..#. #..#. ...#. ..#.. ..#.. .#... #....",
  [[(0, 0), (0, 1.2)], [(3, 0), (3, 2.1), (2.2, 3.5), (1.3, 5.1), (0, 6)]])
g("O", "chronos", "####. ...#. ...#. ..#.. ..#.. .#... #....",
  [[(0, 0), (3, 0), (3, 2.1), (2.2, 3.5), (1.3, 5.1), (0, 6)]])
g("P", "ubis", "#.#.. ..#.. #.#.. #.#.. #.#.. #.... #.#..",
  [[(0, 2), (0, 6)], [(2, 0), (2, 4)]], [(0, 0), (2, 6)])

# As letras do SGA: os Fatos
g("Q", "ferit", "..#.. ..... ##### ....# ....# ....# #####",
  [[(0, 2), (4, 2), (4, 6), (0, 6)]], [(2, 0)])
g("R", "patitur", "#..#. ..... ..... ..... ..... ..... #..#.",
  [], [(0, 0), (3, 0), (0, 6), (3, 6)])
g("S", "necat", "#.... #.... #.... ##... .#... .#... .#...",
  [[(0, 0), (0, 3.1), (1, 3.7), (1, 6)]])
g("T", "salit", "##### ....# ....# ....# ....# ..... ....#",
  [[(0, 0), (4, 0), (4, 4)]], [(4, 6)])
g("U", "cadit", "..... ..... .#.#. ..... ##### ..... .....",
  [[(0, 4), (4, 4)]], [(1, 2), (3, 2)])
g("V", "frangit", "..#.. ..#.. ..#.. ..#.. ##### ..... #####",
  [[(2, 0), (2, 4)], [(0, 4), (4, 4)], [(0, 6), (4, 6)]])
g("W", "utitur", "..... ..... ..#.. ..... ..... #...# .....",
  [], [(2, 2), (0, 5), (4, 5)])
g("X", "latet", "#...# ...#. ...#. ..#.. .#... .#... #....",
  [[(4, 0), (3, 1.3), (2.1, 3.3), (1, 5.2), (0, 6)]], [(0, 0)])
g("Y", "currit", "#.#.. #.#.. #.#.. #.#.. #.#.. #.#.. #.#..",
  [[(0, 0), (0, 6)], [(2, 0), (2, 6)]])
g("Z", "ardet", "..#.. .#.#. #...# #...# #...# #...# #...#",
  [[(0, 6), (0, 2.2), (0.9, 1.0), (2, 0.1), (3.1, 1.0), (4, 2.2), (4, 6)]])

# Os glifos do mod: os algarismos (U+E000 a U+E009)
g("0", "0", ".##.. #.... #.... ..... ....# ....# ..##.",
  [[(2.2, 0), (1, 0), (0, 1), (0, 2.1)], [(4, 4), (4, 5.1), (3, 6), (1.8, 6)]])
g("1", "1", "##... ..... ..... ..#.. ..#.. ..#.. ...##",
  [[(0, 0), (1.2, 0)], [(2, 3), (2, 5.1), (2.7, 6), (4, 6)]])
g("2", "2", "....# ....# ....# ..... ..... ..... #####",
  [[(4, 0), (4, 2.1)], [(0, 6), (4, 6)]])
g("3", "3", "##### ..... .###. ....# ....# ....# .....",
  [[(0, 0), (4, 0)], [(1, 2), (3, 2), (4, 3), (4, 5.1)]])
g("4", "4", "#...# ...#. ..... ..... ....# ....# ....#",
  [[(4, 0), (3, 1.1)], [(4, 4), (4, 6)]], [(0, 0)])
g("5", "5", "..### ..... ####. ....# ....# ....# ..###",
  [[(2, 0), (4, 0)], [(0, 2), (3, 2), (4, 3), (4, 5), (3, 6), (2, 6)]])
g("6", "6", "##### ..... ..##. ....# #.... #.... .##..",
  [[(0, 0), (4, 0)], [(2, 2), (3, 2), (4, 3)], [(0, 4), (0, 5), (1, 6), (2.2, 6)]])
g("7", "7", "###.. ..... ..... ..... ....# ....# ....#",
  [[(0, 0), (2.2, 0)], [(4, 4), (4, 6)]])
g("8", "8", "..#.. ...#. #.... #.... ....# ...#. ..#..",
  [[(2, 0), (3, 1)], [(0, 2), (0, 3)], [(4, 4), (3, 5), (2, 6)]])
g("9", "9", ".##.. #.... .###. ..... ....# ....# .###.",
  [[(2.2, 0), (1, 0), (0, 1)], [(1, 2), (3, 2)], [(4, 4), (4, 5), (3, 6), (1, 6)]])

# Os glifos do mod: as runas novas (U+E010 a U+E015). Trazem a marca de família do mod num canto.
g("languet", "languet", "###.. ...#. ..... ....# ..... ..#.. #....",
  [[(0, 0), (2, 0), (3, 1)]], [(4, 3), (2, 5), (0, 6)])
g("mergitur", "mergitur", "##### ..... ..#.. .#.#. ..#.. ..... #....",
  [[(0, 0), (4, 0)], [(2, 2), (3, 3), (2, 4), (1, 3), (2, 2)]], [(0, 6)])
g("aut", "aut", "#...# .#.#. ..#.. ..... ..#.. ..#.. #....",
  [[(0, 0), (2, 2)], [(4, 0), (2, 2)], [(2, 4), (2, 5.1)]], [(0, 6)])
g("non", "non", "....# ...#. ##### .#... ..... ..... #....",
  [[(4, 0), (1, 3.1)], [(0, 2), (4, 2)]], [(0, 6)])
g("ego", "ego", ".###. #...# ..... #...# .###. ..... ..#..",
  [[(0, 1), (1, 0), (3, 0), (4, 1)], [(0, 3), (1, 4), (3, 4), (4, 3)]], [(2, 6)])
g("ille", "ille", "..#.. ..#.. ##.## ..#.. ..#.. ..... ..#..",
  [[(2, 0), (2, 1.1)], [(0, 2), (1.1, 2)], [(4, 2), (2.9, 2)], [(2, 3), (2, 4.1)]], [(2, 6)])

# Os códigos de cada glifo na fonte
CODEPOINTS = {c: [ord(c), ord(c.lower())] for c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ"}
for i in range(10):
    CODEPOINTS[str(i)] = [ord(str(i)), 0xE000 + i]
for i, n in enumerate(["languet", "mergitur", "aut", "non", "ego", "ille"]):
    CODEPOINTS[n] = [0xE010 + i]


def width(char):
    """A largura do glifo em casas, como no jogo: até a última coluna com pixel."""
    rows = GLYPHS[char][1]
    return max(x for row in rows for x, c in enumerate(row) if c == "#") + 1


def pixels(char):
    rows = GLYPHS[char][1]
    return {(x, y) for y, row in enumerate(rows) for x, c in enumerate(row) if c == "#"}


def traced(char):
    """As casas que os traços manuscritos cobrem, para comparar com os pixels."""
    _, _, strokes, dots = GLYPHS[char]
    out = set()
    for stroke in strokes:
        for (x0, y0), (x1, y1) in zip(stroke, stroke[1:]):
            for i in range(61):
                t = i / 60
                out.add((round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)))
    out.update((round(x), round(y)) for x, y in dots)
    return out


def check(tolerance=4):
    """Os glifos cujos traços se afastam dos pixels canônicos em mais casas do que a tolerância.

    Um traço manuscrito corta na diagonal o que os pixels fazem em degraus, então algumas casas de diferença numa
    diagonal são esperadas; o que passa da tolerância é forma errada."""
    bad = {}
    for c in GLYPHS:
        diff = pixels(c) ^ traced(c)
        if len(diff) > tolerance:
            bad[c] = sorted(diff)
    return bad


if __name__ == "__main__":
    bad = check()
    print(f"{len(GLYPHS)} glifos;", "todos seguem a forma canônica" if not bad else f"diferenças: {bad}")
