"""
Draws the loose page's paper (a page torn out of the grimoire, as it is seen on a block and in the hand): the grimoire's
own page, cut from its book texture, with the edge it was bound by torn. Run from the repository root:

    python tools/loose_page.py

The runes and the ruled lines are drawn on it by the game (ScrollPage), as the grimoire draws them.
"""

import os
import random

from PIL import Image

ASSETS = os.path.join("src", "main", "resources", "assets", "elderlexicon", "textures")
BOOK = os.path.join(ASSETS, "gui", "grimoire_book.png")
OUT = os.path.join(ASSETS, "entity", "loose_page.png")

# The left page's paper, inside its printed border and above the corner that turns it.
CUT = (20, 14, 194, 202)
# Torn: up to this many pixels bitten out of the bound edge (the right one, by the spine), a shade darker before it.
TORN = 3


def main():
    rnd = random.Random(7)
    page = Image.open(BOOK).convert("RGBA").crop(CUT)
    w, h = page.size
    for y in range(h):
        bite = rnd.choice((0, 1, 1, 2, 2, 3))
        for x in range(w - bite, w):
            page.putpixel((x, y), (0, 0, 0, 0))
        edge = w - bite - 1
        r, g, b, a = page.getpixel((edge, y))
        page.putpixel((edge, y), (max(0, r - 28), max(0, g - 30), max(0, b - 34), a))
    # The other three edges, a hair darker with handling.
    for x in range(w):
        for y in (0, h - 1):
            r, g, b, a = page.getpixel((x, y))
            if a:
                page.putpixel((x, y), (max(0, r - 18), max(0, g - 20), max(0, b - 24), a))
    for y in range(h):
        r, g, b, a = page.getpixel((0, y))
        page.putpixel((0, y), (max(0, r - 18), max(0, g - 20), max(0, b - 24), a))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    page.save(OUT)
    print(OUT, page.size)


if __name__ == "__main__":
    main()
