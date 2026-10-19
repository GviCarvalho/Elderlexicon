"""
Draws the casters' skins (docs/mago-design.md, 5i): the Wizard's robes, one for each place it lives and each rank of a
university; the hats some of them wear; the stole a Magister wears in its chair's colour; and the beards that tell a
caster's degree (an apprentice has none, a master a black one, an elder a long white one).

Everything is painted pixel by pixel from the villager model's UV layout, with a fixed seed: running it again gives the
same files. Run from the repository root:

    python tools/caster_skins.py [preview_dir]

With a preview directory it also writes an enlarged sheet of every robe, for looking at them side by side.
"""

import os
import random
import sys

from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "elderlexicon", "textures", "entity")
WIZARD_DIR = os.path.join(ROOT, "wizard")
CASTER_DIR = os.path.join(ROOT, "caster")

CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------------------------------------------------- painting

def faces(u, v, w, h, d):
    """The six faces of a box's UV area, as Minecraft lays them out: (x0, y0, x1, y1), end exclusive."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# The villager model's boxes (VillagerModel.createBodyModel, WizardModel): texture offset and size.
HEAD = faces(0, 0, 8, 10, 8)
HOOD = faces(32, 0, 8, 10, 8)  # the "hat" overlay, a little larger than the head
NOSE = faces(24, 0, 2, 4, 2)
BODY = faces(16, 20, 8, 12, 6)
ROBE = faces(0, 38, 8, 20, 6)  # the "jacket", down to the feet
ARM = faces(44, 22, 4, 8, 4)  # the crossed arms' sleeves
LONG_ARM = faces(32, 48, 4, 12, 4)  # the whole arms, raised in a trance or holding the grimoire (where a hat brim was)
HANDS = faces(40, 38, 8, 4, 4)  # the bar where the crossed arms meet: the hands
LEG = faces(0, 22, 4, 12, 4)


class Painter:
    def __init__(self, size=(64, 64), seed=0):
        self.img = Image.new("RGBA", size, CLEAR)
        self.rnd = random.Random(seed)

    def vary(self, color, amount=6):
        """A colour a little lighter or darker, so cloth is not one flat tone."""
        if len(color) == 4 and color[3] == 0:
            return color
        k = self.rnd.randint(-amount, amount)
        r, g, b = color[:3]
        return (clamp(r + k), clamp(g + k), clamp(b + k), 255)

    def px(self, x, y, color, amount=0):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.img.putpixel((x, y), self.vary(color, amount) if amount else rgba(color))

    def fill(self, rect, color, amount=6):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, color, amount)

    def row(self, rect, dy, color, amount=4):
        """Paints row {@code dy} of a face (negative counts from the bottom)."""
        x0, y0, x1, y1 = rect
        y = (y1 + dy) if dy < 0 else (y0 + dy)
        for x in range(x0, x1):
            self.px(x, y, color, amount)

    def col(self, rect, dx, color, y_from=0, y_to=None, amount=4):
        x0, y0, x1, y1 = rect
        x = (x1 + dx) if dx < 0 else (x0 + dx)
        for y in range(y0 + y_from, y1 if y_to is None else y0 + y_to):
            self.px(x, y, color, amount)

    def at(self, rect, dx, dy, color, amount=0):
        x0, y0, x1, y1 = rect
        self.px((x1 + dx) if dx < 0 else (x0 + dx), (y1 + dy) if dy < 0 else (y0 + dy), color, amount)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


def clamp(c):
    return max(0, min(255, c))


def rgba(color):
    return color if len(color) == 4 else (*color, 255)


def darker(color, by=24):
    return tuple(clamp(c - by) for c in color[:3])


def lighter(color, by=24):
    return tuple(clamp(c + by) for c in color[:3])


# ---------------------------------------------------------------------------------------------------------- the body

SKIN = (178, 128, 100)
SKIN_SHADE = (156, 110, 86)
NOSE_FRONT = (136, 88, 62)
NOSE_SIDE = (110, 66, 44)
EYE_WHITE = (232, 232, 226)
EYE = (52, 120, 64)
BROW = (64, 46, 36)


def head(p, hair, hair_rows=2):
    """A villager's head, its long nose and green eyes; hair on top, behind and at the sides."""
    for name in ("front", "right", "left", "back", "bottom"):
        p.fill(HEAD[name], SKIN, 3)
    p.fill(HEAD["top"], hair)
    for name in ("right", "left", "back"):
        for r in range(hair_rows + (2 if name == "back" else 1)):
            p.row(HEAD[name], r, hair)
    p.fill(HEAD["back"][:3] + (HEAD["back"][1] + 6,), hair)
    for r in range(hair_rows):
        p.row(HEAD["front"], r, hair)
    # As a villager's face: the brow over the eyes, the eyes on the row the nose hangs from, a mouth under it.
    for dx in range(1, 7):
        p.at(HEAD["front"], dx, 5, BROW)
    for dx, color in ((1, EYE_WHITE), (2, EYE), (5, EYE), (6, EYE_WHITE)):
        p.at(HEAD["front"], dx, 6, color)
    for dx in range(2, 6):
        p.at(HEAD["front"], dx, 8, (119, 66, 53))
    # Cheeks a shade darker at the edges.
    for dy in range(7, 10):
        p.at(HEAD["front"], 0, dy, SKIN_SHADE)
        p.at(HEAD["front"], -1, dy, SKIN_SHADE)
    # The nose darker than the face, as a villager's: its front and underside a shade down, its sides and top two.
    for name in NOSE:
        p.fill(NOSE[name], NOSE_SIDE if name in ("right", "left", "top") else NOSE_FRONT, 3)


def hands(p, sleeve, cuff):
    """The bar where the crossed arms meet: the hands, with the sleeves' cuffs at both ends."""
    for name in HANDS:
        p.fill(HANDS[name], SKIN, 3)
    for name in ("right", "left"):
        p.fill(HANDS[name], sleeve)
    for name in ("front", "top", "bottom", "back"):
        p.col(HANDS[name], 0, cuff)
        p.col(HANDS[name], -1, cuff)


def sleeves(p, cloth, cuff, cuff_rows=1):
    """The crossed arms' sleeves, and the whole arms: the same cloth, the cuff at the end, a hand out of it."""
    for arm in (ARM, LONG_ARM):
        for name in ("right", "front", "left", "back", "top"):
            p.fill(arm[name], cloth)
        for name in ("right", "front", "left", "back"):
            for r in range(1, cuff_rows + 1):
                p.row(arm[name], -r, cuff)
        p.fill(arm["bottom"], SKIN, 3)
    # The whole arm's hand shows past the cuff.
    for name in ("right", "front", "left", "back"):
        p.row(LONG_ARM[name], -1, SKIN, 3)
        p.row(LONG_ARM[name], -2, SKIN, 3)
        for r in range(cuff_rows):
            p.row(LONG_ARM[name], -3 - r, cuff)


def robe(p, cloth, legs=None):
    """The body under the robe, the legs, and the robe itself, all of one cloth."""
    for name in BODY:
        p.fill(BODY[name], cloth)
    for name in LEG:
        p.fill(LEG[name], legs or darker(cloth, 30))
    for name in ROBE:
        p.fill(ROBE[name], cloth)
    p.fill(LEG["bottom"], (58, 42, 30))  # boots


def hem(p, color, rows=1):
    for name in ("front", "right", "left", "back"):
        for r in range(1, rows + 1):
            p.row(ROBE[name], -r, color)


def belt(p, color, row=10, buckle=None):
    for name in ("front", "right", "left", "back"):
        p.row(ROBE[name], row, color)
    if buckle:
        p.at(ROBE["front"], 3, row, buckle)
        p.at(ROBE["front"], 4, row, buckle)


def opening(p, color, from_row=0):
    """The robe open down the front, its edges trimmed."""
    p.col(ROBE["front"], 3, color, from_row)
    p.col(ROBE["front"], 4, color, from_row)


def collar(p, color, rows=1):
    for r in range(rows):
        for name in ("front", "right", "left", "back"):
            p.row(ROBE[name], r, color)
    p.fill(ROBE["top"], color)


def scatter(p, rect, color, count, seed):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = rect
    for _ in range(count):
        p.px(rnd.randrange(x0, x1), rnd.randrange(y0, y1), color)


# ---------------------------------------------------------------------------------------------------------- the robes

def village():
    """The village's wizard: a homely blue robe with gold at its edges, a soft cap."""
    blue, gold, leather = (59, 74, 140), (201, 178, 107), (92, 58, 36)
    p = Painter(seed=1)
    head(p, (112, 98, 86))
    robe(p, blue)
    collar(p, gold)
    opening(p, gold, 11)
    belt(p, leather, 10, gold)
    hem(p, gold)
    # Its mark on the back: a rune in gold.
    back = ROBE["back"]
    for dx, dy in ((3, 4), (4, 4), (3, 5), (3, 6), (4, 6), (4, 7), (3, 8), (4, 8), (2, 5), (5, 7)):
        p.at(back, dx, dy, gold)
    sleeves(p, blue, gold)
    hands(p, blue, gold)
    # A soft cap: the top of the hood overlay and a band of gold around it.
    p.fill(HOOD["top"], blue)
    for name in ("front", "right", "left", "back"):
        p.row(HOOD[name], 0, blue)
        p.row(HOOD[name], 1, gold)
    return p


def tower():
    """The tower's wizard, the oldest look of all: deep indigo sown with stars, silver edges; the pointed hat."""
    indigo, silver, star = (46, 36, 94), (188, 192, 208), (236, 214, 128)
    p = Painter(seed=2)
    head(p, (138, 132, 126), 3)
    robe(p, indigo)
    collar(p, silver)
    opening(p, silver, 9)
    belt(p, silver, 9)
    hem(p, silver, 2)
    for i, name in enumerate(("front", "right", "left", "back")):
        scatter(p, ROBE[name][:3] + (ROBE[name][3] - 2,), star, 7 if name in ("front", "back") else 4, 20 + i)
    # A crescent moon on the back.
    back = ROBE["back"]
    for dx, dy in ((4, 5), (3, 6), (3, 7), (3, 8), (4, 9), (5, 9), (5, 5)):
        p.at(back, dx, dy, star)
    sleeves(p, indigo, silver, 2)
    for name in ("front", "right", "left", "back"):
        scatter(p, ARM[name][:3] + (ARM[name][3] - 2,), star, 2, 40 + len(name))
        scatter(p, LONG_ARM[name][:3] + (LONG_ARM[name][3] - 4,), star, 3, 60 + len(name))
    hands(p, indigo, silver)
    return p


def wilds():
    """The wizard of the wilds: a weathered cloak of moss and earth, patched, frayed at the hem; the hood up."""
    moss, earth, rope = (86, 94, 52), (101, 78, 52), (170, 142, 92)
    p = Painter(seed=3)
    head(p, (90, 74, 60))
    robe(p, moss, earth)
    # Patches of other cloth.
    for name, (dx, dy) in (("front", (1, 13)), ("back", (5, 3)), ("right", (2, 8)), ("left", (1, 14))):
        x0, y0, _, _ = ROBE[name]
        p.fill((x0 + dx, y0 + dy, x0 + dx + 2, y0 + dy + 3), earth)
        p.px(x0 + dx, y0 + dy, darker(earth, 20))
    belt(p, rope, 10)
    # A pouch at its side.
    x0, y0, _, _ = ROBE["left"]
    p.fill((x0 + 1, y0 + 11, x0 + 4, y0 + 14), (110, 72, 44))
    p.px(x0 + 2, y0 + 11, rope)
    # Frayed: the hem is torn here and there, and darker with mud.
    hem(p, darker(moss, 25))
    rnd = random.Random(31)
    for name in ("front", "right", "left", "back"):
        x0, _, x1, y1 = ROBE[name]
        for x in range(x0, x1):
            if rnd.random() < 0.45:
                p.px(x, y1 - 1, CLEAR)
    # A leaf or two caught in it.
    p.at(ROBE["back"], 2, 15, (120, 150, 60))
    p.at(ROBE["front"], 6, 4, (120, 150, 60))
    sleeves(p, moss, earth)
    hands(p, moss, earth)
    # The hood, up: all of the overlay but an opening for the face.
    for name in HOOD:
        p.fill(HOOD[name], moss)
    x0, y0, x1, y1 = HOOD["front"]
    p.fill((x0 + 1, y0 + 3, x1 - 1, y1), CLEAR, 0)
    p.fill(HOOD["bottom"], CLEAR, 0)
    p.col(HOOD["front"], 0, darker(moss, 20), 3)
    p.col(HOOD["front"], -1, darker(moss, 20), 3)
    p.row(HOOD["front"], 2, darker(moss, 20))
    return p


def gown(p, cloth, bands=(236, 236, 230)):
    """A university's gown: to the feet, wide sleeves, the white bands at the throat."""
    robe(p, cloth)
    collar(p, darker(cloth, 12))
    p.at(ROBE["front"], 3, 0, bands)
    p.at(ROBE["front"], 4, 0, bands)
    p.at(ROBE["front"], 3, 1, bands)
    p.at(ROBE["front"], 4, 1, bands)


def auditor():
    """The one who listens: a plain grey gown, nothing on its head."""
    grey = (122, 122, 128)
    p = Painter(seed=4)
    head(p, (96, 70, 48))
    gown(p, grey)
    belt(p, darker(grey, 30), 10)
    sleeves(p, grey, darker(grey, 20))
    hands(p, grey, darker(grey, 20))
    return p


def scriba():
    """The one who writes: a gown the blue of ink, ink on its cuffs, a satchel for its papers; a beret and quill."""
    ink, strap, stain = (36, 44, 72), (110, 74, 44), (16, 16, 24)
    p = Painter(seed=5)
    head(p, (78, 58, 42))
    gown(p, ink)
    # The satchel's strap, from the right shoulder down to the left hip.
    front, back = ROBE["front"], ROBE["back"]
    for i in range(8):
        p.at(front, 7 - i, 2 + i, strap)
        p.at(back, i, 2 + i, strap)
    left = ROBE["left"]
    p.fill((left[0] + 1, left[1] + 9, left[0] + 5, left[1] + 14), strap)
    p.row((left[0] + 1, left[1] + 9, left[0] + 5, left[1] + 14), 0, darker(strap, 25))
    scatter(p, (front[0], front[1] + 11, front[2], front[3] - 1), stain, 4, 51)
    sleeves(p, ink, ink)
    for name in ("front", "right", "left", "back"):
        p.row(ARM[name], -1, stain)
        p.row(ARM[name], -2, stain, 10)
        p.row(LONG_ARM[name], -3, stain)
    hands(p, ink, stain)
    # Ink on the fingers too.
    p.at(HANDS["front"], 2, 2, stain)
    p.at(HANDS["front"], 5, 1, stain)
    return p


def lector():
    """The one who reads: a black gown, a blue stole down the front, its hood lined blue on its back."""
    black, blue, lining = (32, 30, 36), (52, 96, 170), (220, 226, 236)
    p = Painter(seed=6)
    head(p, (70, 52, 40))
    gown(p, black)
    front, back = ROBE["front"], ROBE["back"]
    for dx in (1, 2, 5, 6):
        p.col(front, dx, blue, 0, 20)
    p.fill((ROBE["top"][0], ROBE["top"][1], ROBE["top"][2], ROBE["top"][1] + 2), blue)
    # The hood hanging on the back: a shield of blue, lined white.
    for dy, (a, b) in enumerate(((0, 8), (0, 8), (1, 7), (1, 7), (2, 6), (2, 6), (3, 5))):
        for dx in range(a, b):
            p.at(back, dx, dy, blue)
        p.at(back, a, dy, lining)
        p.at(back, b - 1, dy, lining)
    sleeves(p, black, blue)
    hands(p, black, blue)
    return p


def magister():
    """The master who holds a chair: black with velvet down the front, three bars on each sleeve; its stole is its
    chair's colour (drawn apart, magister_stole.png); a square cap."""
    black, velvet, gold = (26, 24, 28), (104, 28, 40), (206, 176, 92)
    p = Painter(seed=7)
    head(p, (120, 112, 106))
    gown(p, black)
    front = ROBE["front"]
    p.col(front, 0, velvet, 0, 20)
    p.col(front, -1, velvet, 0, 20)
    p.fill((front[0] + 3, front[1] + 9, front[0] + 5, front[3]), velvet)
    hem(p, velvet)
    sleeves(p, black, velvet)
    # A doctor's bars across each sleeve.
    for name in ("front", "right", "left", "top"):
        for r in (1, 3, 5) if name != "top" else (1,):
            p.row(ARM[name], r, gold)
            p.row(LONG_ARM[name], r + 2 if name != "top" else r, gold)
    hands(p, black, velvet)
    return p


def magister_stole():
    """The stole alone, pale, to be tinted with the chair's colour over the Magister's gown."""
    pale = (232, 232, 232)
    p = Painter(seed=8)
    front, back = ROBE["front"], ROBE["back"]
    for dx in (1, 2, 5, 6):
        p.col(front, dx, pale, 0, 20, 10)
    p.fill((ROBE["top"][0], ROBE["top"][1], ROBE["top"][2], ROBE["top"][1] + 2), pale, 10)
    for dy in range(6):
        p.at(back, 1 + dy // 2, dy, pale, 10)
        p.at(back, 6 - dy // 2, dy, pale, 10)
    # Its fringe at the ends.
    for dx in (1, 2, 5, 6):
        p.at(front, dx, -1, (200, 200, 200))
    return p


def rector():
    """The one who presides: scarlet, an ermine cape over its shoulders, a chain of gold; a tall velvet cap."""
    scarlet, ermine, spot, gold = (152, 24, 32), (238, 236, 228), (24, 22, 24), (220, 184, 70)
    p = Painter(seed=9)
    head(p, (150, 146, 140), 3)
    gown(p, scarlet)
    # The cape: white, flecked with black tails, the top three rows all round.
    p.fill(ROBE["top"], ermine, 3)
    for r in range(3):
        for name in ("front", "right", "left", "back"):
            p.row(ROBE[name], r, ermine, 3)
    rnd = random.Random(91)
    for name in ("front", "right", "left", "back", "top"):
        x0, y0, x1, y1 = ROBE[name]
        for _ in range(3 if name != "top" else 4):
            p.px(rnd.randrange(x0, x1), rnd.randrange(y0, min(y1, y0 + (3 if name != "top" else 6))), spot)
    # The chain over the cape, and its medallion below the hands.
    front = ROBE["front"]
    p.at(front, 0, 3, gold)
    p.at(front, 7, 3, gold)
    p.at(front, 1, 4, gold)
    p.at(front, 6, 4, gold)
    p.fill((front[0] + 3, front[1] + 9, front[0] + 5, front[1] + 11), gold, 4)
    p.col(front, 3, gold, 11)
    p.col(front, 4, gold, 11)
    hem(p, gold)
    sleeves(p, scarlet, ermine, 2)
    hands(p, scarlet, ermine)
    return p


def emissary():
    """A university's Emissary on the roads: a slate traveller's cloak with a short cape over the shoulders, the house's
    gold seal on the breast and the back, a satchel for its letters at the side, dust on the hem; a wide hat."""
    slate, cape, gold, leather = (60, 72, 88), (44, 52, 64), (214, 182, 84), (104, 70, 42)
    p = Painter(seed=13)
    head(p, (88, 70, 54))
    robe(p, slate, (52, 40, 30))
    # The cape over the shoulders: the top of the robe and its first rows all round.
    p.fill(ROBE["top"], cape)
    for r in range(3):
        for name in ("front", "right", "left", "back"):
            p.row(ROBE[name], r, cape)
    for name in ("front", "right", "left", "back"):
        p.row(ROBE[name], 3, darker(cape, 12))
    # The seal of the house: a small gold disc, on the breast (above the folded arms) and on the back.
    front, back = ROBE["front"], ROBE["back"]
    for dx, dy in ((5, 1), (6, 1), (5, 2), (6, 2)):
        p.at(front, dx, dy, gold)
    for dx, dy in ((3, 5), (4, 5), (2, 6), (5, 6), (2, 7), (5, 7), (3, 8), (4, 8), (3, 6), (4, 7)):
        p.at(back, dx, dy, gold)
    # The satchel's strap from the right shoulder down to the left hip, and the satchel there.
    for i in range(8):
        p.at(front, 7 - i, 3 + i, leather)
        p.at(back, i, 3 + i, leather)
    left = ROBE["left"]
    p.fill((left[0] + 1, left[1] + 10, left[0] + 5, left[1] + 15), leather)
    p.row((left[0] + 1, left[1] + 10, left[0] + 5, left[1] + 15), 0, darker(leather, 25))
    p.at(left, 3, 12, gold)  # its clasp
    belt(p, leather, 11)
    hem(p, (120, 104, 84), 2)  # the dust of the roads
    sleeves(p, slate, leather)
    hands(p, slate, leather)
    return p


ROBES = {
    "village": village, "tower": tower, "wilds": wilds,
    "auditor": auditor, "scriba": scriba, "lector": lector, "magister": magister, "rector": rector,
    "emissary": emissary,
}


# ---------------------------------------------------------------------------------------------------------- the hats

def hats():
    """All the hats, on one 128x64 sheet (CasterHatModel): the tower's pointed hat, the Magister's square cap and its
    tassel, the Rector's velvet cap, the Scriba's beret and quill, the Emissary's wide hat."""
    p = Painter((128, 64), seed=10)
    indigo, gold, star = (46, 36, 94), (201, 178, 107), (236, 214, 128)

    def box(u, v, w, h, d, color, amount=6):
        f = faces(u, v, w, h, d)
        for name in f:
            p.fill(f[name], color, amount)
        return f

    # The pointed hat: a brim and four tiers bending back.
    brim = box(0, 0, 12, 1, 12, indigo)
    scatter(p, brim["top"], star, 6, 101)
    tier1 = box(0, 13, 8, 4, 8, indigo)
    for name in ("front", "right", "left", "back"):
        p.row(tier1[name], -1, gold)
        p.row(tier1[name], -2, gold)
    tier2 = box(32, 13, 6, 4, 6, indigo)
    for name in ("front", "left", "back"):
        scatter(p, tier2[name], star, 2, 102 + len(name))
    box(0, 25, 4, 3, 4, indigo)
    tip = box(16, 25, 2, 3, 2, indigo)
    p.at(tip["front"], 0, 0, star)
    # The square cap: the cap, the board, its button; the tassel apart, pale, to be tinted.
    black = (24, 22, 26)
    box(0, 32, 8, 2, 8, black, 3)
    board = box(0, 42, 14, 1, 14, black, 3)
    box(56, 42, 1, 1, 1, (230, 230, 230), 4)
    box(60, 42, 1, 4, 1, (230, 230, 230), 8)
    # The Rector's tall cap: scarlet velvet with a band of gold, a fuller crown.
    scarlet = (152, 24, 32)
    base = box(64, 0, 9, 3, 9, scarlet)
    for name in ("front", "right", "left", "back"):
        p.row(base[name], -1, (220, 184, 70))
    box(64, 12, 10, 2, 10, lighter(scarlet, 10))
    # The Scriba's beret, and the quill in it.
    box(64, 24, 10, 2, 10, (30, 28, 34), 4)
    quill = box(104, 24, 1, 6, 1, (236, 234, 226), 4)
    for name in ("front", "right", "left", "back"):
        p.row(quill[name], -1, (40, 40, 48))
    # The Emissary's traveller's hat: a wide brim of brown felt, a low crown with a leather band.
    felt = (92, 68, 46)
    box(64, 36, 14, 1, 14, felt, 5)
    crown = box(64, 51, 8, 3, 8, darker(felt, 8), 5)
    for name in ("front", "right", "left", "back"):
        p.row(crown[name], -1, (54, 36, 24))
    return p


# ---------------------------------------------------------------------------------------------------------- beards

def beard(color, streak, seed):
    """A beard's hair: the colour in long strands, for every box of the beard to take its part of."""
    p = Painter((32, 32), seed=seed)
    rnd = random.Random(seed)
    for x in range(32):
        tone = color if rnd.random() < 0.7 else streak
        for y in range(32):
            p.px(x, y, tone if rnd.random() < 0.85 else (streak if tone == color else color), 5)
    return p


# ---------------------------------------------------------------------------------------------------------- preview

def front_view(img, scale=8):
    """A rough front view of a robe: head, hood, robe, the crossed hands before it."""
    view = Image.new("RGBA", (10 * scale, 32 * scale), (200, 210, 220, 255))

    def blit(rect, at):
        part = img.crop(rect).resize(((rect[2] - rect[0]) * scale, (rect[3] - rect[1]) * scale), Image.NEAREST)
        view.alpha_composite(part, (at[0] * scale, at[1] * scale))

    blit(HEAD["front"], (1, 1))
    blit(HOOD["front"], (1, 1))
    blit(ROBE["front"], (1, 11))
    blit(ARM["front"], (0, 13))
    blit(ARM["front"], (5, 13))
    blit(HANDS["front"], (1, 17))
    blit(NOSE["front"], (4, 6))
    return view


def main():
    for name, draw in ROBES.items():
        draw().save(os.path.join(WIZARD_DIR, name + ".png"))
    magister_stole().save(os.path.join(WIZARD_DIR, "magister_stole.png"))
    hats().save(os.path.join(WIZARD_DIR, "hats.png"))
    beard((40, 34, 30), (64, 54, 46), 11).save(os.path.join(CASTER_DIR, "beard_black.png"))
    beard((234, 232, 226), (196, 194, 190), 12).save(os.path.join(CASTER_DIR, "beard_white.png"))
    if len(sys.argv) > 1:
        out = sys.argv[1]
        os.makedirs(out, exist_ok=True)
        sheet = Image.new("RGBA", (len(ROBES) * 88, 264), (255, 255, 255, 255))
        for i, name in enumerate(ROBES):
            img = Image.open(os.path.join(WIZARD_DIR, name + ".png"))
            sheet.alpha_composite(front_view(img), (i * 88, 0))
        sheet.save(os.path.join(out, "robes_front.png"))
        for name in list(ROBES) + ["hats"]:
            img = Image.open(os.path.join(WIZARD_DIR, name + ".png"))
            img.resize((img.width * 6, img.height * 6), Image.NEAREST).save(os.path.join(out, name + "_x6.png"))


if __name__ == "__main__":
    main()
