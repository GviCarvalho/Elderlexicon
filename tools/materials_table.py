"""Generates the material table: every block of the game with its own code (docs/particulas-design.md).

Every block is matter, and its code (how many of its 4096 particles are terra, água, ar and fogo) is what makes it what
it is: no two blocks share one. A block is a substance of its own, and the shapes made of it (its slab, stairs, wall,
fence, door...) are forms of that substance holding fewer particles. The codes come from a few rules, so they mean
something and can be argued with:

  - a base for each natural material (granite has more fire than stone, diorite more air, tuff is porous ash);
  - small shifts for how a material was worked (polished closes the pores, bricks carry mortar, cracked lets air in,
    mossy brings water and air), each a handful of particles;
  - a shift for each dye, after what the dye is made of (red, fire; blue, lapis; black, ink; white, bone);
  - a mixture of the ingredients of the recipe for what is crafted (a furnace is its cobblestone, a bed its wool and
    planks), with a shift of its own so it is never the same as what it was made of.

The table keeps the entries the game already relied on (the primordials, stone, iron, wood...) with their ids. Blocks
that are no matter (air is the air, but a barrier, a portal or a command block are not) are left out on purpose.

Run from the repository root: python tools/materials_table.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BLOCKS = ROOT / "tools" / "minecraft_blocks_1_20_1.txt"
OUT = ROOT / "src" / "main" / "resources" / "data" / "elderlexicon" / "lexicon" / "materials.json"

TOTAL = 4096
ASPECTS = ("firmo", "aqua", "aura", "igni")
F, A, R, I = range(4)

ALL_BLOCKS = [line.strip() for line in BLOCKS.read_text(encoding="utf-8").splitlines() if line.strip()]
BLOCK_SET = set(ALL_BLOCKS)

# Blocks that are no matter of the table: technical blocks, portals, things only creative mode or commands have.
LEFT_OUT = {
    "barrier", "structure_block", "structure_void", "jigsaw", "command_block", "chain_command_block",
    "repeating_command_block", "light", "moving_piston", "end_gateway", "end_portal", "nether_portal", "item_frame",
    "glow_item_frame",
}

# Blocks that show more than one substance: every rock melts into lava, and lava read is the first of them, stone.
SHOWN_BY_MANY = {"lava"}

# How many particles a shape of a block holds, as if it were as loose as earth: a full block holds 4096. The game packs
# every form of a substance by its block's hardness when it loads the table (a stone slab: 2048 x 3).
SHAPES = {
    "slab": 2048, "stairs": 3072, "wall": 2048, "fence": 1024, "fence_gate": 1024, "door": 768, "trapdoor": 768,
    "pressure_plate": 256, "button": 128, "sign": 512, "wall_sign": 512, "hanging_sign": 512,
    "wall_hanging_sign": 512, "carpet": 256, "pane": 512,
}


# ---------------------------------------------------------------------------------------------------------- vectors

def pct(f, a, r, i):
    """A composition given as shares (any measure), as particles of a block."""
    total = float(f + a + r + i)
    return [f * TOTAL / total, a * TOTAL / total, r * TOTAL / total, i * TOTAL / total]


def mix(*parts):
    """The mixture of compositions, each with its weight (how much of the recipe it is)."""
    weight = float(sum(w for _, w in parts))
    return [sum(v[k] * w for v, w in parts) / weight for k in range(4)]


def shift(v, f=0, a=0, r=0, i=0):
    """A composition moved by so many particles of each; what would go below nothing is taken from the most there is."""
    out = [v[F] + f, v[A] + a, v[R] + r, v[I] + i]
    for k in range(4):
        if out[k] < 0:
            short = -out[k]
            out[k] = 0
            richest = max(range(4), key=lambda j: out[j])
            out[richest] -= short
    return out


def code(v):
    """Whole particles adding up to exactly 4096: the whole part of each, and what is left to the largest fractions."""
    total = sum(v)
    exact = [x * TOTAL / total for x in v]
    counts = [int(x) for x in exact]
    left = TOTAL - sum(counts)
    order = sorted(range(4), key=lambda k: (-(exact[k] - counts[k]), k))
    for k in order[:left]:
        counts[k] += 1
    return counts


# ---------------------------------------------------------------------------------------------------------- shifts

def polished(v): return shift(v, f=12, r=-12)
def smooth(v): return shift(v, f=8, r=-8)
def cut(v): return shift(v, f=6, r=-6)
def chiseled(v): return shift(v, f=3, r=-4, i=1)
def bricks(v): return shift(v, f=-24, a=24)
def cracked(v): return shift(v, f=-16, r=16)
def mossy(v): return shift(v, f=-48, a=40, r=8)
def cobbled(v): return shift(v, f=-40, r=40)
def tiles(v): return shift(v, f=16, a=-16)
def pillar(v): return shift(v, f=10, a=-10)
def infested(v): return shift(v, f=-90, a=60, r=30)        # a silverfish inside
def stripped(v): return shift(v, f=-24, a=24)              # the bark taken off
def all_bark(v): return shift(v, f=6, a=-6)                # bark on every face
def dried_planks(v): return shift(v, f=20, a=-60, r=40)    # sawn and dried
def waxed(v): return shift(v, f=-12, a=6, i=6)             # honeycomb's wax
def exposed(v): return shift(v, f=-60, a=40, r=20)         # copper weathering, step by step
def worked(v): return shift(v, f=-6, i=6)                  # what a crafting adds: the work, a little heat


# What each dye shifts, after what it is made of.
DYES = {
    "white": dict(f=8, a=-8),          # bone meal
    "orange": dict(i=6, a=-6),
    "magenta": dict(i=4, r=4, f=-8),
    "light_blue": dict(a=6, r=2, f=-8),
    "yellow": dict(i=8, f=-8),
    "lime": dict(a=4, r=4, f=-8),
    "pink": dict(i=3, r=5, f=-8),
    "gray": dict(f=4, r=-4),
    "light_gray": dict(f=2, r=-2),
    "cyan": dict(a=6, f=2, r=-8),
    "purple": dict(f=2, a=2, i=5, r=-9),
    "blue": dict(f=4, a=4, r=-8),      # lapis
    "brown": dict(f=6, i=2, r=-8),     # cocoa
    "green": dict(a=8, f=-8),          # cactus
    "red": dict(i=10, f=-10),
    "black": dict(f=4, a=6, r=-10),    # ink
}
COLORS = list(DYES)
COLOR_NAMES = {
    "white": "branco", "orange": "laranja", "magenta": "magenta", "light_blue": "azul-claro", "yellow": "amarelo",
    "lime": "verde-limão", "pink": "rosa", "gray": "cinza", "light_gray": "cinza-claro", "cyan": "ciano",
    "purple": "roxo", "blue": "azul", "brown": "marrom", "green": "verde", "red": "vermelho", "black": "preto",
}


def dyed(v, color, times=1):
    d = {k: x * times for k, x in DYES[color].items()}
    return shift(v, **d)


# ---------------------------------------------------------------------------------------------------------- the table

SUBSTANCES = {}   # id -> dict(name, vec, state, forms)
CRAFTED = set()   # substances made by a crafting recipe

# What nature makes, and what only hands make (user, 17/10/2026): matter near no code but a made thing's is not that
# thing. Matter whose code is exactly a made thing's still is it (a bench's iron sword stays iron). Natural is whatever
# the world generates by itself, outside the buildings of its structures: rocks, ores, soils, plants, ice, coral...
MADE_WORDS = ("polished", "smooth_stone", "smooth_sandstone", "smooth_red_sandstone", "smooth_quartz", "cut_", "chiseled",
              "bricks", "tiles", "pillar", "planks", "stripped", "waxed", "glazed", "concrete", "glass", "_bed",
              "banner", "candle", "cake", "potted", "shulker", "carved_pumpkin", "jack_o_lantern", "dirt_path",
              "farmland", "hay_block", "dried_kelp_block", "honeycomb_block", "slime_block", "sea_lantern", "purpur",
              "quartz_block", "packed_mud", "mosaic", "petrified", "calibrated", "_head", "_skull", "tripwire",
              "exposed_", "weathered_", "oxidized_", "raw_", "wool", "reinforced")
MADE_IDS = {"coal", "iron", "gold", "copper", "diamond", "emerald", "lapis", "redstone", "netherite", "tinted_glass",
            "cobblestone", "cobbled_deepslate", "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood",
            "dark_oak_wood", "mangrove_wood", "cherry_wood", "mossy_cobblestone", "infested_cobblestone", "prismarine",
            "dark_prismarine", "honey_block"} | {color + "_terracotta" for color in (
                "magenta", "light_blue", "lime", "pink", "gray", "cyan", "purple", "blue", "green", "black")}


def made(sid):
    return sid in CRAFTED or sid in MADE_IDS or any(word in sid for word in MADE_WORDS)


# What nature makes inside something else, not of matter pressed alone: a vein of ore in rock, a creature in stone, a
# thing buried in sand. Earth pressed hard is rock, never ore; like a made thing, it is only found by its very code.
VEIN_WORDS = ("_ore", "infested_", "suspicious_", "gilded_", "budding_", "ancient_debris")


def origin(sid):
    if made(sid):
        return "hands"
    if any(word in sid for word in VEIN_WORDS):
        return "vein"
    return None
OWNER = {}        # block or item id -> substance id


def form_entry(kind, game_id, particles):
    entry = {kind: "minecraft:" + game_id if ":" not in game_id else game_id}
    if particles is not None:
        entry["particles"] = particles
    return entry


def sub(sid, name, v, state="solid", solid=(), liquid=(), gas=(), plasma=(), items=(), extra=None):
    """A substance and how it shows: blocks (an id, or (id, particles)) in each state, and items (id, particles)."""
    if sid in SUBSTANCES:
        raise SystemExit("substance twice: " + sid)
    forms = {}
    for st, blocks in (("solid", solid), ("liquid", liquid), ("gas", gas), ("plasma", plasma)):
        for block in blocks:
            game_id, particles = (block, None) if isinstance(block, str) else block
            if not game_id.startswith("particle:") and game_id not in BLOCK_SET:
                raise SystemExit("no such block: " + game_id)
            if game_id.startswith("particle:"):
                forms.setdefault(st, []).append({"particle": game_id[len("particle:"):]})
                continue
            if game_id in OWNER and OWNER[game_id] != sid and game_id not in SHOWN_BY_MANY:
                raise SystemExit(game_id + " is both " + OWNER[game_id] + " and " + sid)
            OWNER.setdefault(game_id, sid)
            forms.setdefault(st, []).append(form_entry("block", game_id, particles))
    for item, particles in items:
        forms.setdefault("solid", []).append(form_entry("item", item, particles))
    if extra:
        for st, entries in extra.items():
            forms.setdefault(st, []).extend(entries)
    SUBSTANCES[sid] = {"name": name, "vec": v, "state": state, "forms": forms}
    return v


def shapes(prefix, kinds):
    """The shapes of a material that the game has, as (block, particles)."""
    found = []
    for kind in kinds:
        block = prefix + "_" + kind
        if block in BLOCK_SET:
            found.append((block, SHAPES[kind]))
    return found


STONE_SHAPES = ("slab", "stairs", "wall", "button", "pressure_plate")
WOOD_SHAPES = ("slab", "stairs", "fence", "fence_gate", "door", "trapdoor", "pressure_plate", "button", "sign",
               "wall_sign", "hanging_sign", "wall_hanging_sign")


def rock(sid, name, v, full, prefix=None, extra_solid=(), liquid=()):
    return sub(sid, name, v, solid=[full] + shapes(prefix or full, STONE_SHAPES) + list(extra_solid), liquid=liquid)


LAVA = ["lava"]

# ------------------------------------------------------------------ the primordials and the old ones (ids kept)

EARTH = sub("earth", "terra", pct(1, 0, 0, 0), solid=["dirt"])
WATER = sub("water", "água", pct(0, 1, 0, 0), state="liquid", solid=["ice"], liquid=["water", "bubble_column"],
            gas=["particle:minecraft:cloud"])
AIR = sub("air", "ar", pct(0, 0, 1, 0), state="gas", gas=["air", "cave_air", "void_air"])
FIRE = sub("fire", "fogo", pct(0, 0, 0, 1), state="plasma", plasma=["fire"])
sub("mud", "lama", pct(1, 1, 0, 0), solid=["mud"])
sub("dust", "pó", pct(1, 0, 1, 0), state="gas", gas=["particle:minecraft:white_ash"])
sub("mist", "névoa", pct(0, 1, 1, 0), state="gas", gas=["particle:minecraft:cloud"])
sub("steam", "vapor", pct(0, 9, 0, 1), state="gas", gas=["particle:minecraft:campfire_cosy_smoke"])
MAGMA = sub("magma", "magma", pct(1, 0, 0, 1), solid=["magma_block"])
SUBSTANCES["lightning"] = {"name": "raio", "vec": pct(0, 0, 1, 1), "state": "plasma",
                           "forms": {"plasma": [{"entity": "minecraft:lightning_bolt"},
                                                {"particle": "minecraft:electric_spark"}]}}

# Pressed water: a block of packed ice holds twice the water, blue ice four times (docs/condensacao-design.md).
sub("packed_ice", "gelo compactado", shift(WATER, f=40, a=-40), solid=["packed_ice"])
sub("blue_ice", "gelo azul", shift(WATER, f=80, a=-80), solid=["blue_ice"])
sub("frosted_ice", "gelo fino", shift(WATER, r=30, a=-30), solid=["frosted_ice"])

# ------------------------------------------------------------------ stone and its kin

STONE = rock("stone", "pedra", pct(80, 5, 5, 10), "stone", liquid=LAVA)
rock("cobblestone", "pedregulho", cobbled(STONE), "cobblestone")
rock("mossy_cobblestone", "pedregulho musgoso", mossy(cobbled(STONE)), "mossy_cobblestone")
sub("smooth_stone", "pedra lisa", smooth(STONE), solid=["smooth_stone", ("smooth_stone_slab", 2048)])
STONE_BRICKS = rock("stone_bricks", "tijolos de pedra", bricks(STONE), "stone_bricks", prefix="stone_brick")
rock("mossy_stone_bricks", "tijolos de pedra musgosos", mossy(bricks(STONE)), "mossy_stone_bricks",
     prefix="mossy_stone_brick")
sub("cracked_stone_bricks", "tijolos de pedra rachados", cracked(bricks(STONE)), solid=["cracked_stone_bricks"])
sub("chiseled_stone_bricks", "tijolos de pedra talhados", chiseled(bricks(STONE)), solid=["chiseled_stone_bricks"])
for base, v in (("stone", STONE), ("cobblestone", cobbled(STONE)), ("stone_bricks", bricks(STONE)),
                ("mossy_stone_bricks", mossy(bricks(STONE))), ("cracked_stone_bricks", cracked(bricks(STONE))),
                ("chiseled_stone_bricks", chiseled(bricks(STONE)))):
    sub("infested_" + base, base.replace("_", " ") + " infestado", infested(v), solid=["infested_" + base])

GRANITE = rock("granite", "granito", pct(74, 4, 6, 16), "granite", liquid=LAVA)
rock("polished_granite", "granito polido", polished(GRANITE), "polished_granite")
DIORITE = rock("diorite", "diorito", pct(76, 4, 12, 8), "diorite", liquid=LAVA)
rock("polished_diorite", "diorito polido", polished(DIORITE), "polished_diorite")
ANDESITE = rock("andesite", "andesito", pct(79, 7, 5, 9), "andesite", liquid=LAVA)
rock("polished_andesite", "andesito polido", polished(ANDESITE), "polished_andesite")
sub("tuff", "tufo", pct(66, 6, 18, 10), solid=["tuff"], liquid=LAVA)
sub("calcite", "calcita", pct(72, 14, 8, 6), solid=["calcite"])
sub("dripstone", "espeleotema", pct(74, 20, 2, 4), solid=["dripstone_block", ("pointed_dripstone", 1024)])

DEEPSLATE = sub("deepslate", "ardósia", pct(88, 9, 2, 1), solid=["deepslate"], liquid=LAVA)
rock("cobbled_deepslate", "ardósia em pedregulho", cobbled(DEEPSLATE), "cobbled_deepslate")
rock("polished_deepslate", "ardósia polida", polished(DEEPSLATE), "polished_deepslate")
rock("deepslate_bricks", "tijolos de ardósia", bricks(DEEPSLATE), "deepslate_bricks", prefix="deepslate_brick")
sub("cracked_deepslate_bricks", "tijolos de ardósia rachados", cracked(bricks(DEEPSLATE)),
    solid=["cracked_deepslate_bricks"])
rock("deepslate_tiles", "ladrilhos de ardósia", tiles(DEEPSLATE), "deepslate_tiles", prefix="deepslate_tile")
sub("cracked_deepslate_tiles", "ladrilhos de ardósia rachados", cracked(tiles(DEEPSLATE)),
    solid=["cracked_deepslate_tiles"])
sub("chiseled_deepslate", "ardósia talhada", chiseled(DEEPSLATE), solid=["chiseled_deepslate"])
sub("infested_deepslate", "ardósia infestada", infested(DEEPSLATE), solid=["infested_deepslate"])
sub("reinforced_deepslate", "ardósia reforçada", pct(86, 9, 3, 2), solid=["reinforced_deepslate"])
sub("bedrock", "rocha-mãe", pct(96, 2, 1, 1), solid=["bedrock"])

BASALT = sub("basalt", "basalto", pct(70, 2, 6, 22), solid=["basalt"], liquid=LAVA)
sub("polished_basalt", "basalto polido", polished(BASALT), solid=["polished_basalt"])
sub("smooth_basalt", "basalto liso", smooth(BASALT), solid=["smooth_basalt"])
BLACKSTONE = rock("blackstone", "pedra-negra", pct(74, 1, 5, 20), "blackstone", liquid=LAVA)
rock("polished_blackstone", "pedra-negra polida", polished(BLACKSTONE), "polished_blackstone")
rock("polished_blackstone_bricks", "tijolos de pedra-negra", bricks(polished(BLACKSTONE)),
     "polished_blackstone_bricks", prefix="polished_blackstone_brick")
sub("cracked_polished_blackstone_bricks", "tijolos de pedra-negra rachados", cracked(bricks(polished(BLACKSTONE))),
    solid=["cracked_polished_blackstone_bricks"])
sub("chiseled_polished_blackstone", "pedra-negra talhada", chiseled(polished(BLACKSTONE)),
    solid=["chiseled_polished_blackstone"])

OBSIDIAN = sub("obsidian", "obsidiana", pct(70, 10, 0, 20), solid=["obsidian"])
sub("crying_obsidian", "obsidiana chorona", pct(66, 14, 0, 20), solid=["crying_obsidian"])
NETHERRACK = sub("netherrack", "pedra infernal", pct(60, 0, 10, 30), solid=["netherrack"])
NETHER_BRICK = rock("nether_bricks", "tijolos do Nether", pct(66, 2, 6, 26), "nether_bricks", prefix="nether_brick",
                    extra_solid=[("nether_brick_fence", 1024)])
sub("cracked_nether_bricks", "tijolos do Nether rachados", cracked(NETHER_BRICK), solid=["cracked_nether_bricks"])
sub("chiseled_nether_bricks", "tijolos do Nether talhados", chiseled(NETHER_BRICK), solid=["chiseled_nether_bricks"])
rock("red_nether_bricks", "tijolos do Nether vermelhos", pct(60, 6, 8, 26), "red_nether_bricks",
     prefix="red_nether_brick")

SAND = sub("sand", "areia", pct(70, 0, 30, 0), solid=["sand"])
sub("suspicious_sand", "areia suspeita", shift(SAND, f=10, r=-10), solid=["suspicious_sand"])
RED_SAND = sub("red_sand", "areia vermelha", pct(68, 0, 30, 2), solid=["red_sand"])
GRAVEL = sub("gravel", "cascalho", pct(80, 0, 20, 0), solid=["gravel"])
sub("suspicious_gravel", "cascalho suspeito", shift(GRAVEL, f=-10, a=10), solid=["suspicious_gravel"])
SANDSTONE = rock("sandstone", "arenito", pct(76, 6, 18, 0), "sandstone")
sub("cut_sandstone", "arenito cortado", cut(SANDSTONE), solid=["cut_sandstone", ("cut_sandstone_slab", 2048)])
sub("chiseled_sandstone", "arenito talhado", chiseled(SANDSTONE), solid=["chiseled_sandstone"])
rock("smooth_sandstone", "arenito liso", smooth(SANDSTONE), "smooth_sandstone")
RED_SANDSTONE = rock("red_sandstone", "arenito vermelho", pct(74, 6, 18, 2), "red_sandstone")
sub("cut_red_sandstone", "arenito vermelho cortado", cut(RED_SANDSTONE),
    solid=["cut_red_sandstone", ("cut_red_sandstone_slab", 2048)])
sub("chiseled_red_sandstone", "arenito vermelho talhado", chiseled(RED_SANDSTONE), solid=["chiseled_red_sandstone"])
rock("smooth_red_sandstone", "arenito vermelho liso", smooth(RED_SANDSTONE), "smooth_red_sandstone")

END_STONE = sub("end_stone", "pedra do Fim", pct(70, 0, 22, 8), solid=["end_stone"])
rock("end_stone_bricks", "tijolos de pedra do Fim", bricks(END_STONE), "end_stone_bricks", prefix="end_stone_brick")
PURPUR = sub("purpur_block", "púrpura", pct(55, 5, 25, 15),
             solid=["purpur_block", ("purpur_slab", 2048), ("purpur_stairs", 3072)])
sub("purpur_pillar", "pilar de púrpura", pillar(PURPUR), solid=["purpur_pillar"])
PRISMARINE = rock("prismarine", "prismarinho", pct(55, 35, 5, 5), "prismarine")
rock("prismarine_bricks", "tijolos de prismarinho", bricks(PRISMARINE), "prismarine_bricks", prefix="prismarine_brick")
rock("dark_prismarine", "prismarinho escuro", dyed(PRISMARINE, "black", 3), "dark_prismarine")
QUARTZ = sub("quartz_block", "quartzo", pct(70, 2, 20, 8),
             solid=["quartz_block", ("quartz_slab", 2048), ("quartz_stairs", 3072)])
sub("chiseled_quartz_block", "quartzo talhado", chiseled(QUARTZ), solid=["chiseled_quartz_block"])
sub("quartz_bricks", "tijolos de quartzo", bricks(QUARTZ), solid=["quartz_bricks"])
sub("quartz_pillar", "pilar de quartzo", pillar(QUARTZ), solid=["quartz_pillar"])
rock("smooth_quartz", "quartzo liso", smooth(QUARTZ), "smooth_quartz")
AMETHYST = sub("amethyst", "ametista", pct(62, 8, 22, 8),
               solid=["amethyst_block", ("amethyst_cluster", 1024), ("large_amethyst_bud", 768),
                      ("medium_amethyst_bud", 512), ("small_amethyst_bud", 256)])
sub("budding_amethyst", "ametista em broto", shift(AMETHYST, f=-20, a=20), solid=["budding_amethyst"])
CLAY = sub("clay", "argila", pct(60, 40, 0, 0), solid=["clay"], items=[("clay_ball", 1024)])
BRICK = rock("bricks", "tijolos", pct(75, 10, 5, 10), "bricks", prefix="brick")
PACKED_MUD = sub("packed_mud", "lama compactada", pct(58, 36, 4, 2), solid=["packed_mud"])
rock("mud_bricks", "tijolos de lama", bricks(PACKED_MUD), "mud_bricks", prefix="mud_brick")

TERRACOTTA = sub("terracotta", "terracota", pct(72, 12, 6, 10), solid=["terracotta"])
for color in COLORS:
    v = sub(color + "_terracotta", "terracota " + COLOR_NAMES[color], dyed(TERRACOTTA, color),
            solid=[color + "_terracotta"])
    sub(color + "_glazed_terracotta", "terracota vitrificada " + COLOR_NAMES[color], shift(v, i=8, a=-8),
        solid=[color + "_glazed_terracotta"])
CONCRETE_POWDER = mix((SAND, 4), (GRAVEL, 4))
for color in COLORS:
    powder = sub(color + "_concrete_powder", "concreto em pó " + COLOR_NAMES[color],
                 dyed(CONCRETE_POWDER, color), solid=[color + "_concrete_powder"])
    sub(color + "_concrete", "concreto " + COLOR_NAMES[color], shift(powder, a=80, r=-80),
        solid=[color + "_concrete"])

GLASS = sub("glass", "vidro", pct(68, 0, 28, 4), solid=["glass", ("glass_pane", 512)])
for color in COLORS:
    sub(color + "_stained_glass", "vidro " + COLOR_NAMES[color], dyed(GLASS, color),
        solid=[color + "_stained_glass", (color + "_stained_glass_pane", 512)])
sub("tinted_glass", "vidro fumê", mix((GLASS, 4), (AMETHYST, 1)), solid=["tinted_glass"])

# Glowing things.
sub("glowstone", "pedra luminosa", pct(35, 0, 25, 40), solid=["glowstone"])
sub("sea_lantern", "lanterna do mar", pct(45, 30, 10, 15), solid=["sea_lantern"])
sub("shroomlight", "cogulume", pct(30, 25, 15, 30), solid=["shroomlight"])
sub("ochre_froglight", "luz-de-sapo ocre", pct(40, 20, 10, 30), solid=["ochre_froglight"])
sub("verdant_froglight", "luz-de-sapo verdejante", pct(38, 24, 10, 28), solid=["verdant_froglight"])
sub("pearlescent_froglight", "luz-de-sapo perolada", pct(36, 22, 14, 28), solid=["pearlescent_froglight"])
sub("sponge", "esponja", pct(20, 5, 70, 5), solid=["sponge"])
sub("wet_sponge", "esponja molhada", pct(18, 50, 27, 5), solid=["wet_sponge"])

# ------------------------------------------------------------------ metals, minerals and their ores

COAL = sub("coal", "carvão", pct(60, 0, 0, 40), solid=["coal_block"], items=[("coal", 455), ("charcoal", 455)])
IRON = sub("iron", "ferro", pct(90, 0, 0, 10),
           solid=["iron_block", ("iron_bars", 512), ("chain", 256), ("iron_door", 1536), ("iron_trapdoor", 1820),
                  ("heavy_weighted_pressure_plate", 910)],
           items=[("iron_ingot", 455), ("iron_nugget", 50)])
RAW_IRON = sub("raw_iron", "ferro bruto", pct(86, 2, 2, 10), solid=["raw_iron_block"], items=[("raw_iron", 455)])
GOLD = sub("gold", "ouro", pct(70, 0, 0, 30), solid=["gold_block", ("light_weighted_pressure_plate", 910)],
           items=[("gold_ingot", 455), ("gold_nugget", 50)])
RAW_GOLD = sub("raw_gold", "ouro bruto", pct(68, 2, 2, 28), solid=["raw_gold_block"], items=[("raw_gold", 455)])
COPPER = sub("copper", "cobre", pct(70, 20, 0, 10), solid=["copper_block", ("lightning_rod", 1365)],
             items=[("copper_ingot", 455)])
RAW_COPPER = sub("raw_copper", "cobre bruto", pct(68, 22, 2, 8), solid=["raw_copper_block"],
                 items=[("raw_copper", 455)])
stage_v = COPPER
for stage, name in (("", ""), ("exposed_", " exposto"), ("weathered_", " desgastado"), ("oxidized_", " oxidado")):
    if stage:
        stage_v = exposed(stage_v)
        sub(stage + "copper", "cobre" + name, stage_v, solid=[stage + "copper"])
    cut_v = cut(stage_v)
    sub(stage + "cut_copper", "cobre cortado" + name, cut_v,
        solid=[stage + "cut_copper", (stage + "cut_copper_slab", 2048), (stage + "cut_copper_stairs", 3072)])
    sub("waxed_" + stage + "copper", "cobre encerado" + name, waxed(stage_v),
        solid=["waxed_" + stage + "copper" + ("_block" if not stage else "")])
    sub("waxed_" + stage + "cut_copper", "cobre cortado encerado" + name, waxed(cut_v),
        solid=["waxed_" + stage + "cut_copper", ("waxed_" + stage + "cut_copper_slab", 2048),
               ("waxed_" + stage + "cut_copper_stairs", 3072)])
DIAMOND = sub("diamond", "diamante", pct(92, 0, 2, 6), solid=["diamond_block"], items=[("diamond", 455)])
EMERALD = sub("emerald", "esmeralda", pct(70, 20, 5, 5), solid=["emerald_block"], items=[("emerald", 455)])
LAPIS = sub("lapis", "lápis-lazúli", pct(60, 30, 5, 5), solid=["lapis_block"], items=[("lapis_lazuli", 455)])
REDSTONE = sub("redstone", "pó de redstone", pct(30, 0, 20, 50), solid=["redstone_block", ("redstone_wire", 455)],
               items=[("redstone", 455)])
NETHER_QUARTZ = pct(70, 2, 20, 8)
DEBRIS = sub("ancient_debris", "detrito antigo", pct(80, 0, 0, 20), solid=["ancient_debris"])
sub("netherite", "netherita", pct(82, 0, 0, 18), solid=["netherite_block"], items=[("netherite_ingot", 455)])

ORE = 0.2   # how much of an ore block is the mineral
for mineral, name, v in (("coal", "carvão", COAL), ("iron", "ferro", RAW_IRON), ("copper", "cobre", RAW_COPPER),
                         ("gold", "ouro", RAW_GOLD), ("redstone", "redstone", REDSTONE), ("emerald", "esmeralda", EMERALD),
                         ("lapis", "lápis-lazúli", LAPIS), ("diamond", "diamante", DIAMOND)):
    sub(mineral + "_ore", "minério de " + name, mix((STONE, 1 - ORE), (v, ORE)), solid=[mineral + "_ore"])
    sub("deepslate_" + mineral + "_ore", "minério de " + name + " em ardósia", mix((DEEPSLATE, 1 - ORE), (v, ORE)),
        solid=["deepslate_" + mineral + "_ore"])
sub("nether_gold_ore", "minério de ouro do Nether", mix((NETHERRACK, 1 - ORE), (GOLD, ORE)), solid=["nether_gold_ore"])
sub("nether_quartz_ore", "minério de quartzo do Nether", mix((NETHERRACK, 1 - ORE), (NETHER_QUARTZ, ORE)),
    solid=["nether_quartz_ore"])
sub("gilded_blackstone", "pedra-negra dourada", mix((BLACKSTONE, 0.85), (GOLD, 0.15)), solid=["gilded_blackstone"])

# ------------------------------------------------------------------ soils, snow and the living ground

sub("coarse_dirt", "terra grossa", pct(92, 0, 8, 0), solid=["coarse_dirt"])
sub("rooted_dirt", "terra enraizada", pct(84, 6, 6, 4), solid=["rooted_dirt", ("hanging_roots", 512)])
sub("grass_block", "terra com grama", pct(82, 8, 6, 4), solid=["grass_block"])
sub("podzol", "podzol", pct(80, 6, 8, 6), solid=["podzol"])
sub("mycelium", "micélio", pct(80, 8, 8, 4), solid=["mycelium"])
sub("dirt_path", "caminho de terra", pct(90, 2, 4, 4), solid=["dirt_path"])
sub("farmland", "terra arada", pct(80, 14, 4, 2), solid=["farmland"])
sub("soul_sand", "areia das almas", pct(60, 0, 25, 15), solid=["soul_sand"])
sub("soul_soil", "terra das almas", pct(66, 0, 20, 14), solid=["soul_soil"])
sub("crimson_nylium", "nylium carmesim", pct(58, 2, 10, 30), solid=["crimson_nylium"])
sub("warped_nylium", "nylium distorcido", pct(58, 10, 12, 20), solid=["warped_nylium"])
MOSS = sub("moss", "musgo", pct(30, 45, 15, 10), solid=["moss_block", ("moss_carpet", 256)])
SNOW = sub("snow", "neve", pct(0, 70, 30, 0), solid=["snow_block", ("snow", 512)], items=[("snowball", 1024)])
sub("powder_snow", "neve em pó", shift(SNOW, a=-60, r=60), solid=["powder_snow"])
SCULK = sub("sculk", "sculk", pct(30, 30, 20, 20), solid=["sculk", ("sculk_vein", 128)])
sub("sculk_sensor", "sensor de sculk", shift(SCULK, r=20, f=-20), solid=[("sculk_sensor", 2048)])
sub("calibrated_sculk_sensor", "sensor de sculk calibrado", mix((shift(SCULK, r=20, f=-20), 1), (AMETHYST, 0.3)),
    solid=[("calibrated_sculk_sensor", 2048)])
sub("sculk_catalyst", "catalisador de sculk", shift(SCULK, f=40, i=10, a=-50), solid=["sculk_catalyst"])
sub("sculk_shrieker", "guinchador de sculk", shift(SCULK, r=40, a=-40), solid=[("sculk_shrieker", 2048)])

# ------------------------------------------------------------------ wood

WOODS = {
    # species: (log, name, the word the game uses for its log, wood, planks, leaves and sapling)
    "oak": (pct(40, 30, 10, 20), "carvalho"),
    "spruce": (pct(41, 28, 9, 22), "abeto"),
    "birch": (pct(38, 30, 14, 18), "bétula"),
    "jungle": (pct(40, 34, 8, 18), "selva"),
    "acacia": (pct(42, 26, 10, 22), "acácia"),
    "dark_oak": (pct(44, 28, 8, 20), "carvalho-escuro"),
    "mangrove": (pct(38, 36, 8, 18), "mangue"),
    "cherry": (pct(39, 30, 12, 19), "cerejeira"),
    "crimson": (pct(40, 20, 10, 30), "carmesim"),
    "warped": (pct(38, 32, 14, 16), "distorcido"),
}
LEAVES = pct(20, 50, 20, 10)
PLANKS = {}
for species, (log, name) in WOODS.items():
    fungus = species in ("crimson", "warped")
    stem, wood = ("stem", "hyphae") if fungus else ("log", "wood")
    sid_log = "wood" if species == "oak" else species + "_" + stem
    sub(sid_log, ("haste " if fungus else "tora de ") + name, log, solid=[species + "_" + stem],
        items=[("stick", 512)] if species == "oak" else ())
    sub(species + "_" + wood, ("hifas " if fungus else "madeira de ") + name, all_bark(log), solid=[species + "_" + wood])
    sub("stripped_" + species + "_" + stem, ("haste descascada " if fungus else "tora descascada de ") + name,
        stripped(log), solid=["stripped_" + species + "_" + stem])
    sub("stripped_" + species + "_" + wood, ("hifas descascadas " if fungus else "madeira descascada de ") + name,
        stripped(all_bark(log)), solid=["stripped_" + species + "_" + wood])
    planks = sub(species + "_planks", "tábuas de " + name, dried_planks(log),
                 solid=[species + "_planks"] + shapes(species, WOOD_SHAPES))
    PLANKS[species] = planks
    if not fungus:
        # Oak's are the leaves the table always had; the others lean to their tree.
        leaves = LEAVES if species == "oak" else mix((LEAVES, 4), (log, 1))
        sub(species + "_leaves", "folhas de " + name, leaves, solid=[species + "_leaves"])
    sapling = species + ("_propagule" if species == "mangrove" else "_sapling")
    if sapling in BLOCK_SET:
        sub(sapling, "muda de " + name, mix((LEAVES, 1), (log, 1)), solid=[(sapling, 256)])
# The leaves of the old table keep its id for oak's.
SUBSTANCES["leaves"] = SUBSTANCES.pop("oak_leaves")
SUBSTANCES["leaves"]["name"] = "folhas de carvalho"
OWNER["oak_leaves"] = "leaves"
sub("azalea_leaves", "folhas de azaleia", shift(LEAVES, a=10, r=-10), solid=["azalea_leaves"])
sub("flowering_azalea_leaves", "folhas de azaleia florida", shift(LEAVES, a=10, r=-6, i=-4),
    solid=["flowering_azalea_leaves"])
BAMBOO = sub("bamboo", "bambu", pct(36, 28, 20, 16), solid=["bamboo_block", ("bamboo", 512), ("bamboo_sapling", 128)])
sub("stripped_bamboo_block", "bambu descascado", stripped(BAMBOO), solid=["stripped_bamboo_block"])
BAMBOO_PLANKS = sub("bamboo_planks", "tábuas de bambu", dried_planks(BAMBOO),
                    solid=["bamboo_planks"] + shapes("bamboo", WOOD_SHAPES))
PLANKS["bamboo"] = BAMBOO_PLANKS
rock("bamboo_mosaic", "mosaico de bambu", cut(BAMBOO_PLANKS), "bamboo_mosaic")
sub("petrified_oak", "carvalho petrificado", mix((PLANKS["oak"], 1), (STONE, 1)), solid=[("petrified_oak_slab", 2048)])
sub("mangrove_roots", "raízes de mangue", shift(WOODS["mangrove"][0], r=60, f=-60), solid=["mangrove_roots"])
sub("muddy_mangrove_roots", "raízes de mangue enlameadas", mix((WOODS["mangrove"][0], 1), (pct(1, 1, 0, 0), 1)),
    solid=["muddy_mangrove_roots"])

# ------------------------------------------------------------------ plants

PLANT = pct(15, 55, 20, 10)
FLOWERS = {
    # flower: the dye it gives
    "dandelion": "yellow", "poppy": "red", "blue_orchid": "light_blue", "allium": "magenta",
    "azure_bluet": "light_gray", "red_tulip": "red", "orange_tulip": "orange", "white_tulip": "light_gray",
    "pink_tulip": "pink", "oxeye_daisy": "light_gray", "cornflower": "blue", "lily_of_the_valley": "white",
    "wither_rose": "black", "torchflower": "orange", "sunflower": "yellow", "lilac": "magenta", "rose_bush": "red",
    "peony": "pink", "pink_petals": "pink", "pitcher_plant": "cyan",
}
# Flowers that give the same dye differ by which aspect their life leans to.
LEANS = [dict(), dict(r=4, a=-4), dict(i=4, a=-4), dict(f=4, a=-4), dict(r=8, a=-8), dict(i=8, a=-8)]
seen = {}
for flower, color in FLOWERS.items():
    lean = LEANS[seen.get(color, 0)]
    seen[color] = seen.get(color, 0) + 1
    tall = flower in ("sunflower", "lilac", "rose_bush", "peony", "pitcher_plant")
    sub(flower, flower.replace("_", " "), shift(dyed(PLANT, color, 3), **lean), solid=[(flower, 512 if tall else 256)])

GREENS = {
    # plant: (composition, particles)
    "grass": (pct(15, 55, 22, 8), 128), "tall_grass": (pct(15, 54, 23, 8), 256), "fern": (pct(16, 56, 20, 8), 128),
    "large_fern": (pct(16, 55, 21, 8), 256), "dead_bush": (pct(40, 5, 40, 15), 128),
    "seagrass": (pct(12, 78, 6, 4), 128), "tall_seagrass": (pct(12, 77, 7, 4), 256),
    "kelp": (pct(15, 75, 5, 5), 256), "kelp_plant": (pct(15, 74, 6, 5), 256), "lily_pad": (pct(14, 70, 10, 6), 128),
    "vine": (pct(22, 48, 20, 10), 128), "glow_lichen": (pct(20, 40, 20, 20), 64),
    "cave_vines": (pct(20, 44, 16, 20), 256), "cave_vines_plant": (pct(21, 44, 16, 19), 256),
    "weeping_vines": (pct(24, 24, 22, 30), 256), "weeping_vines_plant": (pct(25, 24, 22, 29), 256),
    "twisting_vines": (pct(24, 34, 26, 16), 256), "twisting_vines_plant": (pct(25, 34, 26, 15), 256),
    "spore_blossom": (pct(16, 50, 26, 8), 256), "big_dripleaf": (pct(16, 60, 16, 8), 512),
    "big_dripleaf_stem": (pct(20, 58, 14, 8), 256), "small_dripleaf": (pct(16, 59, 17, 8), 256),
    "azalea": (pct(24, 50, 16, 10), 1024), "flowering_azalea": (pct(24, 49, 16, 11), 1024),
    "sweet_berry_bush": (pct(22, 50, 14, 14), 512), "sugar_cane": (pct(30, 50, 12, 8), 512),
    "cactus": (pct(25, 60, 5, 10), 3584), "cocoa": (pct(30, 20, 10, 40), 256),
    "wheat": (pct(30, 30, 25, 15), 256), "carrots": (pct(20, 60, 10, 10), 256),
    "potatoes": (pct(30, 55, 8, 7), 256), "beetroots": (pct(22, 58, 10, 10), 256),
    "torchflower_crop": (pct(18, 54, 14, 14), 256), "pitcher_crop": (pct(17, 55, 18, 10), 256),
    "melon_stem": (pct(18, 62, 12, 8), 128), "attached_melon_stem": (pct(18, 61, 13, 8), 128),
    "pumpkin_stem": (pct(19, 60, 12, 9), 128), "attached_pumpkin_stem": (pct(19, 59, 13, 9), 128),
    "nether_wart": (pct(25, 35, 10, 30), 256), "crimson_roots": (pct(30, 20, 18, 32), 128),
    "warped_roots": (pct(28, 32, 22, 18), 128), "nether_sprouts": (pct(28, 30, 24, 18), 64),
    "crimson_fungus": (pct(30, 30, 10, 30), 256), "warped_fungus": (pct(30, 40, 15, 15), 256),
    "brown_mushroom": (pct(30, 45, 15, 10), 128), "red_mushroom": (pct(28, 45, 15, 12), 128),
    "chorus_plant": (pct(30, 10, 40, 20), 2048), "chorus_flower": (pct(28, 12, 40, 20), 4096),
    "sea_pickle": (pct(15, 60, 10, 15), 512), "frogspawn": (pct(10, 80, 8, 2), 64),
}
for plant, (v, particles) in GREENS.items():
    sub(plant, plant.replace("_", " "), v, solid=[(plant, particles)])
sub("melon", "melancia", pct(10, 80, 5, 5), solid=["melon"])
PUMPKIN = sub("pumpkin", "abóbora", pct(18, 62, 10, 10), solid=["pumpkin"])
sub("carved_pumpkin", "abóbora entalhada", shift(PUMPKIN, a=-60, r=60), solid=["carved_pumpkin"])
sub("jack_o_lantern", "abóbora iluminada", shift(PUMPKIN, a=-60, r=40, i=20), solid=["jack_o_lantern"])
sub("brown_mushroom_block", "bloco de cogumelo marrom", pct(31, 44, 15, 10), solid=["brown_mushroom_block"])
sub("red_mushroom_block", "bloco de cogumelo vermelho", pct(29, 44, 15, 12), solid=["red_mushroom_block"])
sub("mushroom_stem", "caule de cogumelo", pct(34, 42, 14, 10), solid=["mushroom_stem"])
sub("nether_wart_block", "bloco de verruga do Nether", pct(26, 34, 10, 30), solid=["nether_wart_block"])
sub("warped_wart_block", "bloco de verruga distorcida", pct(26, 40, 14, 20), solid=["warped_wart_block"])
sub("hay_block", "fardo de feno", pct(30, 25, 30, 15), solid=["hay_block"])
sub("dried_kelp_block", "bloco de alga seca", pct(40, 15, 20, 25), solid=["dried_kelp_block"])

CORALS = {"tube": "blue", "brain": "pink", "bubble": "magenta", "fire": "red", "horn": "yellow"}
CORAL = pct(40, 45, 5, 10)
for coral, color in CORALS.items():
    alive = dyed(CORAL, color, 3)
    dead = shift(alive, f=200, a=-200)
    for state_v, prefix in ((alive, ""), (dead, "dead_")):
        sub(prefix + coral + "_coral_block", prefix + coral + " coral block", state_v,
            solid=[prefix + coral + "_coral_block"])
        sub(prefix + coral + "_coral", prefix + coral + " coral", shift(state_v, r=20, f=-20),
            solid=[(prefix + coral + "_coral", 512), (prefix + coral + "_coral_fan", 256),
                   (prefix + coral + "_coral_wall_fan", 256)])

# ------------------------------------------------------------------ from animals

BONE = sub("bone", "osso", pct(60, 20, 0, 20), solid=["bone_block"], items=[("bone", 1365)])
sub("flesh", "carne", pct(20, 60, 10, 10), items=[("rotten_flesh", 256)])
WOOL = pct(40, 10, 40, 10)
for color in COLORS:
    sid = "wool" if color == "white" else color + "_wool"
    v = sub(sid, "lã " + COLOR_NAMES[color], WOOL if color == "white" else dyed(WOOL, color),
            solid=[color + "_wool", (color + "_carpet", 256)])
    sub(color + "_bed", "cama " + COLOR_NAMES[color], mix((v, 3), (PLANKS["oak"], 3)),
        solid=[(color + "_bed", 2304)])
    sub(color + "_banner", "estandarte " + COLOR_NAMES[color], mix((v, 6), (WOODS["oak"][0], 0.5)),
        solid=[(color + "_banner", 1024), (color + "_wall_banner", 1024)])
STRING = pct(30, 10, 55, 5)
sub("cobweb", "teia", STRING, solid=[("cobweb", 256)])
sub("tripwire", "linha", shift(STRING, r=10, a=-10), solid=[("tripwire", 64)])
WAX = pct(30, 30, 10, 30)
sub("honeycomb_block", "bloco de favo de mel", WAX, solid=["honeycomb_block"])
sub("honey_block", "bloco de mel", pct(20, 60, 5, 15), solid=["honey_block"])
sub("slime_block", "bloco de geleia", pct(24, 69, 5, 2), solid=["slime_block"])
CANDLE = sub("candle", "vela", mix((WAX, 1), (STRING, 0.2)), solid=[("candle", 256)])
for color in COLORS:
    sub(color + "_candle", "vela " + COLOR_NAMES[color], dyed(CANDLE, color), solid=[(color + "_candle", 256)])
CAKE = sub("cake", "bolo", pct(20, 40, 20, 20), solid=[("cake", 2048)])
sub("candle_cake", "bolo com vela", mix((CAKE, 8), (CANDLE, 1)), solid=[("candle_cake", 2304)])
for color in COLORS:
    sub(color + "_candle_cake", "bolo com vela " + COLOR_NAMES[color], mix((CAKE, 8), (dyed(CANDLE, color), 1)),
        solid=[(color + "_candle_cake", 2304)])
sub("turtle_egg", "ovo de tartaruga", pct(40, 40, 10, 10), solid=[("turtle_egg", 512)])
sub("sniffer_egg", "ovo de farejador", pct(42, 38, 10, 10), solid=[("sniffer_egg", 2048)])
sub("dragon_egg", "ovo de dragão", pct(60, 0, 20, 20), solid=[("dragon_egg", 3072)])
SKULL = BONE
for head, lean in (("skeleton_skull", dict()), ("wither_skeleton_skull", dict(i=40, a=-40)),
                   ("zombie_head", dict(a=200, f=-200)), ("creeper_head", dict(a=120, i=80, f=-200)),
                   ("piglin_head", dict(a=160, i=60, f=-220)), ("player_head", dict(a=180, r=40, f=-220)),
                   ("dragon_head", dict(r=60, i=60, f=-120))):
    wall = head.replace("_skull", "_wall_skull").replace("_head", "_wall_head")
    sub(head, head.replace("_", " "), shift(SKULL, **lean), solid=[(head, 1024), (wall, 1024)])

# ------------------------------------------------------------------ crafted things: their ingredients, worked

COBBLE = cobbled(STONE)
OAK = PLANKS["oak"]
GUNPOWDER = pct(20, 0, 30, 50)
PAPER = pct(30, 30, 30, 10)
BLAZE = pct(10, 0, 30, 60)
TORCH = mix((WOODS["oak"][0], 1), (COAL, 1))


def crafted(sid, name, parts, blocks, lean=None):
    CRAFTED.add(sid)
    return sub(sid, name, worked(shift(mix(*parts), **(lean or {}))), solid=blocks)


crafted("crafting_table", "mesa de trabalho", [(OAK, 4)], ["crafting_table"])
CHEST = crafted("chest", "baú", [(OAK, 8)], [("chest", 3584)], dict(r=10, f=-10))
crafted("trapped_chest", "baú com armadilha", [(OAK, 8), (IRON, 1), (STRING, 1)], [("trapped_chest", 3584)])
crafted("barrel", "barril", [(OAK, 7)], ["barrel"], dict(r=20, f=-20))
FURNACE = crafted("furnace", "fornalha", [(COBBLE, 8)], ["furnace"], dict(i=20, r=-20))
crafted("blast_furnace", "alto-forno", [(IRON, 5), (FURNACE, 1), (smooth(STONE), 3)], ["blast_furnace"])
crafted("smoker", "defumador", [(FURNACE, 1), (WOODS["oak"][0], 4)], ["smoker"])
BOOK = mix((PAPER, 3), (pct(30, 40, 20, 10), 1))
crafted("bookshelf", "estante", [(OAK, 6), (BOOK, 3)], ["bookshelf"])
crafted("chiseled_bookshelf", "estante talhada", [(OAK, 6), (OAK, 3)], ["chiseled_bookshelf"], dict(r=30, f=-30))
crafted("lectern", "atril", [(OAK, 4), (BOOK, 3)], [("lectern", 2048)])
crafted("loom", "tear", [(OAK, 2), (STRING, 2)], ["loom"])
crafted("cartography_table", "mesa de cartografia", [(OAK, 4), (PAPER, 2)], ["cartography_table"])
crafted("fletching_table", "mesa de flechas", [(OAK, 4), (GRAVEL, 2)], ["fletching_table"])
crafted("smithing_table", "mesa de ferraria", [(OAK, 4), (IRON, 2)], ["smithing_table"])
crafted("stonecutter", "cortador de pedra", [(STONE, 3), (IRON, 1)], [("stonecutter", 2304)])
crafted("grindstone", "rebolo", [(OAK, 2), (STONE, 1)], [("grindstone", 2048)])
crafted("composter", "composteira", [(OAK, 7)], [("composter", 2048)], dict(a=40, f=-40))
crafted("note_block", "bloco musical", [(OAK, 8), (REDSTONE, 1)], ["note_block"])
crafted("jukebox", "toca-discos", [(OAK, 8), (DIAMOND, 1)], ["jukebox"])
crafted("enchanting_table", "mesa de encantamento", [(OBSIDIAN, 4), (DIAMOND, 2), (BOOK, 1)], [("enchanting_table", 3072)])
crafted("beacon", "sinalizador", [(GLASS, 5), (OBSIDIAN, 3), (pct(20, 0, 40, 40), 1)], ["beacon"])
crafted("conduit", "conduíte", [(pct(30, 60, 5, 5), 1), (PRISMARINE, 1)], [("conduit", 1024)])
crafted("brewing_stand", "suporte de poções", [(BLAZE, 1), (COBBLE, 3)], [("brewing_stand", 1024)])
CAULDRON = crafted("cauldron", "caldeirão", [(IRON, 7)], [("cauldron", 3185)])
crafted("water_cauldron", "caldeirão com água", [(CAULDRON, 1), (WATER, 1)], [("water_cauldron", 7281)])
crafted("lava_cauldron", "caldeirão com lava", [(CAULDRON, 1), (STONE, 1)], [("lava_cauldron", 7281)])
crafted("powder_snow_cauldron", "caldeirão com neve em pó", [(CAULDRON, 1), (shift(SNOW, a=-60, r=60), 1)],
        [("powder_snow_cauldron", 7281)])
crafted("hopper", "funil", [(IRON, 5), (CHEST, 1)], [("hopper", 2048)])
DISPENSER = crafted("dispenser", "ejetor", [(COBBLE, 7), (REDSTONE, 1), (OAK, 1)], ["dispenser"])
crafted("dropper", "liberador", [(COBBLE, 7), (REDSTONE, 1)], ["dropper"])
crafted("observer", "observador", [(COBBLE, 6), (REDSTONE, 2), (NETHER_QUARTZ, 1)], ["observer"])
PISTON = crafted("piston", "pistão", [(OAK, 3), (COBBLE, 4), (IRON, 1), (REDSTONE, 1)],
                 ["piston", ("piston_head", 1024)])
crafted("sticky_piston", "pistão grudento", [(PISTON, 1), (pct(24, 70, 5, 1), 0.2)], ["sticky_piston"])
crafted("repeater", "repetidor", [(STONE, 3), (REDSTONE, 3)], [("repeater", 512)])
crafted("comparator", "comparador", [(STONE, 3), (REDSTONE, 3), (NETHER_QUARTZ, 1)], [("comparator", 512)])
crafted("redstone_torch", "tocha de redstone", [(WOODS["oak"][0], 1), (REDSTONE, 1)],
        [("redstone_torch", 128), ("redstone_wall_torch", 128)])
crafted("redstone_lamp", "lâmpada de redstone", [(pct(35, 0, 25, 40), 4), (REDSTONE, 4)], ["redstone_lamp"])
crafted("daylight_detector", "sensor de luz do dia", [(GLASS, 3), (NETHER_QUARTZ, 3), (OAK, 3)],
        [("daylight_detector", 1536)])
crafted("target", "alvo", [(pct(30, 25, 30, 15), 1), (REDSTONE, 4)], ["target"])
crafted("tnt", "dinamite", [(SAND, 4), (GUNPOWDER, 5)], ["tnt"])
crafted("lever", "alavanca", [(WOODS["oak"][0], 1), (COBBLE, 1)], [("lever", 256)])
crafted("tripwire_hook", "gancho de armadilha", [(IRON, 1), (WOODS["oak"][0], 1), (OAK, 1)], [("tripwire_hook", 256)])
crafted("rail", "trilho", [(IRON, 6), (WOODS["oak"][0], 1)], [("rail", 256)])
crafted("powered_rail", "trilho energizado", [(GOLD, 6), (WOODS["oak"][0], 1), (REDSTONE, 1)], [("powered_rail", 256)])
crafted("detector_rail", "trilho detector", [(IRON, 6), (pct(30, 0, 20, 50), 1), (STONE, 1)], [("detector_rail", 256)])
crafted("activator_rail", "trilho ativador", [(IRON, 6), (WOODS["oak"][0], 2), (REDSTONE, 1)],
        [("activator_rail", 256)])
crafted("ladder", "escada de mão", [(WOODS["oak"][0], 7)], [("ladder", 256)], dict(r=30, f=-30))
crafted("scaffolding", "andaime", [(BAMBOO, 6), (STRING, 1)], [("scaffolding", 1024)])
crafted("torch", "tocha", [(TORCH, 1)], [("torch", 128), ("wall_torch", 128)])
SOUL = pct(66, 0, 20, 14)
crafted("soul_torch", "tocha das almas", [(TORCH, 1), (SOUL, 1)], [("soul_torch", 128), ("soul_wall_torch", 128)])
crafted("lantern", "lanterna", [(IRON, 8), (TORCH, 1)], [("lantern", 512)])
crafted("soul_lantern", "lanterna das almas", [(IRON, 8), (TORCH, 1), (SOUL, 1)], [("soul_lantern", 512)])
crafted("campfire", "fogueira", [(WOODS["oak"][0], 3), (COAL, 1)], [("campfire", 1792)])
crafted("soul_campfire", "fogueira das almas", [(WOODS["oak"][0], 3), (SOUL, 1)], [("soul_campfire", 1792)])
sub("soul_fire", "fogo das almas", pct(5, 0, 10, 85), state="plasma", plasma=["soul_fire"])
crafted("end_rod", "bastão do Fim", [(BLAZE, 1), (pct(30, 10, 40, 20), 1)], [("end_rod", 256)])
POT = crafted("flower_pot", "vaso de flor", [(BRICK, 3)], [("flower_pot", 512)])
for block in ALL_BLOCKS:
    if block.startswith("potted_"):
        plant = block[len("potted_"):]
        plant_v = SUBSTANCES[OWNER[plant]]["vec"] if plant in OWNER else (
            WOODS[plant.replace("_bush", "")][0] if plant.replace("_bush", "") in WOODS else PLANT)
        if plant in ("azalea_bush", "flowering_azalea_bush"):
            plant_v = SUBSTANCES[plant.replace("_bush", "")]["vec"]
        crafted(block, "vaso com " + plant.replace("_", " "), [(POT, 2), (plant_v, 1)], [(block, 768)])
crafted("decorated_pot", "vaso decorado", [(BRICK, 4)], [("decorated_pot", 2048)])
crafted("bell", "sino", [(GOLD, 1)], [("bell", 1024)], dict(r=40, f=-40))
crafted("ender_chest", "baú do Fim", [(OBSIDIAN, 8), (pct(20, 10, 40, 30), 1)], [("ender_chest", 3584)])
SHELL = pct(40, 10, 40, 10)
crafted("shulker_box", "caixa de shulker", [(SHELL, 2), (CHEST, 1)], ["shulker_box"])
for color in COLORS:
    crafted(color + "_shulker_box", "caixa de shulker " + COLOR_NAMES[color],
            [(dyed(mix((SHELL, 2), (CHEST, 1)), color), 1)], [color + "_shulker_box"])
crafted("respawn_anchor", "âncora de renascimento", [(pct(66, 14, 0, 20), 6), (pct(35, 0, 25, 40), 3)],
        ["respawn_anchor"])
crafted("spawner", "gerador de monstros", [(IRON, 2), (FIRE, 1)], ["spawner"], dict(r=40, f=-40))
crafted("end_portal_frame", "moldura do portal do Fim", [(END_STONE, 4), (pct(20, 10, 40, 30), 1)],
        [("end_portal_frame", 3328)])
crafted("lodestone", "magnetita", [(STONE_BRICKS, 8), (pct(82, 0, 0, 18), 1)], ["lodestone"])
ANVIL_IRON = 31 * 455
crafted("anvil", "bigorna", [(IRON, 1)], [("anvil", ANVIL_IRON)], dict(f=6, r=-6))
crafted("chipped_anvil", "bigorna lascada", [(IRON, 1)], [("chipped_anvil", ANVIL_IRON * 2 // 3)], dict(f=4, r=-4))
crafted("damaged_anvil", "bigorna danificada", [(IRON, 1)], [("damaged_anvil", ANVIL_IRON // 3)], dict(f=2, r=-2))
crafted("bee_nest", "colmeia silvestre", [(WOODS["oak"][0], 3), (WAX, 1)], ["bee_nest"])
crafted("beehive", "colmeia", [(OAK, 6), (WAX, 3)], ["beehive"])


# ---------------------------------------------------------------------------------------------------------- writing

def recipe(counts):
    return {ASPECTS[k]: counts[k] for k in range(4) if counts[k] > 0}


def main():
    missing = [b for b in ALL_BLOCKS if b not in OWNER and b not in LEFT_OUT]
    if missing:
        raise SystemExit("blocks with no code: " + " ".join(missing))
    codes = {}
    for sid, entry in SUBSTANCES.items():
        counts = code(entry["vec"])
        # Two substances may not share a code: the later one moves a particle from earth to air until it is its own.
        while tuple(counts) in codes:
            richest = max(range(4), key=lambda k: counts[k])
            to = R if richest != R else A
            counts[richest] -= 1
            counts[to] += 1
        codes[tuple(counts)] = sid
        entry["code"] = counts

    old = json.loads(OUT.read_text(encoding="utf-8"))
    substances = {}
    for sid, entry in SUBSTANCES.items():
        out = {"name": entry["name"], "recipe": recipe(entry["code"]), "state": entry["state"]}
        if origin(sid):
            out["origin"] = origin(sid)
        if entry["forms"]:
            out["forms"] = entry["forms"]
        substances[sid] = out
    table = {
        "_license": old.get("_license"),
        "_about": old.get("_about"),
        "_generated": "Written by tools/materials_table.py: change the rules there, not here. Each recipe is the "
                      "substance's code, its particles in a block as loose as earth (4096). The game packs each "
                      "substance by its block's hardness over earth's (stone 3: 12288 in a block) and multiplies "
                      "every form's particles by that (docs/particulas-design.md, Densidade).",
        "substances": substances,
        "beings": old.get("beings", {}),
    }
    lines = ["{"]
    keys = ["_license", "_about", "_generated"]
    for key in keys:
        lines.append("  " + json.dumps(key) + ": " + json.dumps(table[key], ensure_ascii=False) + ",")
    lines.append('  "substances": {')
    items = list(substances.items())
    for n, (sid, out) in enumerate(items):
        lines.append("    " + json.dumps(sid) + ": " + json.dumps(out, ensure_ascii=False)
                     + ("," if n < len(items) - 1 else ""))
    lines.append("  },")
    lines.append('  "beings": {')
    beings = list(table["beings"].items())
    for n, (bid, out) in enumerate(beings):
        lines.append("    " + json.dumps(bid) + ": " + json.dumps(out, ensure_ascii=False)
                     + ("," if n < len(beings) - 1 else ""))
    lines.append("  }")
    lines.append("}")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(substances)} substances, {len(OWNER)} blocks and items with a code, "
          f"{len(LEFT_OUT & BLOCK_SET)} blocks left out")


if __name__ == "__main__":
    main()
