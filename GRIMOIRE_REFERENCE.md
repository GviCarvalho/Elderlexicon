© GviCarvalho – This document is part of Elderlexicon and is licensed under CC BY-NC-ND 4.0. See LICENSE-DOCS.txt.

# Grimoire of the Ancient Language — System Reference

*"Every order is a whisper through the dream. Learn to speak with respect, and the world will listen."*

This document is the canonical foundation of the Elderlexicon magic system. It describes the pure logic, grammar, and metaphysics of the Ancient Language as compiled by Archmage Edris Val Lorian in the year 300 D.Z.

It is intentionally **platform-agnostic**. No game mechanics, conversion rates, or implementation details are included here. Those live in other documents. This is the law behind the mod.

---

## 1. The Two Minds and the Pulse

Every act of magic depends on two aspects of the caster:

- **The Waking Mind (Mente Desperta):** Consciousness, intention, linear thought. It formulates the runic order.
- **The Dreaming Mind (Mente Adormecida):** The subconscious, which dwells in the Submerged Realm where ideas are as malleable as clay. It interprets the runes and manifests the result.

When a runic order is spoken, written, or gestured, the two minds intertwine for a **2-second pulse**. During this window:

1. The Waking Mind projects the order through the chosen channel (voice, gesture, or writing).
2. The Dreaming Mind reads and interprets the runic sequence.
3. The result is executed in the waking dimension.

The window is absolute. If the order is too long to be transmitted in 2 seconds, only the understood portion is executed; the rest leaks. However, written orders read after the pulse (via `surgit`) bypass this limit.

---

## 2. The Primordial Elements

All magical energy originates from four primordial elements:

| Rune    | Element | Essence                                  |
|---------|---------|------------------------------------------|
| `igni`  | Fire    | Heat, combustion, clarity                |
| `firmo` | Earth   | Mass, minerals, solidity                 |
| `aqua`  | Water   | Flow, dissolution, humidity              |
| `aura`  | Air     | Pressure, breath, sound waves            |

These elements make up every other thing: what something is comes from how much of each it holds (§5.3.1).

---

## 3. Mana (Vis) and Life

- **`vis` (Mana):** The perfect equilibrium of all four elements — 25% `igni`, 25% `aura`, 25% `aqua`, 25% `firmo`. It is neutral energy, ideal for realignments.
- **Life:** The specific elemental blend that composes living beings. It has no rune of its own (the `vita` rune left
  the language with the fused runes): a being is the four in its proportion, held by an anchor of 100 UMU of Vis.

| Element | Proportion |
|---------|------------|
| `aqua`  | 55% (later refined to 56%) |
| `aura`  | 38% |
| `firmo` | 5% (later refined to 4%)  |
| `igni`  | 2%  |

Manipulating life allows precise healing or harm. The elemental balance is maintained by a cycle:

- **+**`igni` ⇒ **–**`aura`
- **+**`aura` ⇒ **–**`firmo`
- **+**`firmo` ⇒ **–**`aqua`
- **+**`aqua` ⇒ **–**`igni`

Disturbing one element without compensating the others causes nausea, fever, internal burns, or collapse.

---

## 4. The Universal Magical Unit (UMU)

Every spell consumes energy. The **UMU** is an abstract, universal measure of that cost — independent of the manifestation (heat, mass, vapor, etc.). A simple `igni iactare` costs few UMU; a long chain of conversions may cost hundreds.

The UMU is the accounting system. How a given platform translates it into tangible resources (XP, health, saturation, etc.) is an implementation decision.

---

## 5. The Runic Language

### 5.1 Rune Categories

1. **Sources (Fontes):** The energy to mobilize (`igni`, `aqua`, `aura`, `firmo`, `vis`).
2. **Functions (Funções):** What to do with the energy (`iactare`, `impediunt`, `vocant`, `vertere`, `ligabis`, `surgit`, `reframe`).
3. **Filters (Filtros):** Adjustments to time, quantity, place, or origin (`chronos`, `quantum`, `ubis`, `tenet`).

Some words the language once had are gone: `exsugat` (its work is now done by `tenet` and by a turned-around verb),
`transvocatio` (move each thing with `vocant`), and every fused rune: the sources `vita`, `fusus`, `caligo`, `lutum`,
`pulvis`, `nebula` and `fulmen`; the forms (`hasta`, `murus`, `vortex`, `sigillum`, `catena` and their kin, `orbis`
among them); and the fused functions (`transiectio`, `aversio`, `cohaesio`, `evocatio` and the rest, `exhaustio`,
`exsuctio`, `extractio` and `exinanitio` among them). What two runes do together comes from their order and from the
instant and place they meet in, never from a rune that stands for both. The Dreaming Mind still recognizes the old
words, and says what to write instead.

### 5.2 Canonical Syntax

The Dreaming Mind expects short, well-formed orders:

Expr ::= source → function
| source → value → filter → function


Examples:

- `igni iactare` — Push a wave of heat toward where the caster aims.
- `igni vocant iactare` — Bring fire out at the hand and hurl it: a lance of fire.
- `igni chronos 5 iactare` — Push the heat for 5 seconds.

A filter governs the function written right after it. A value may be written before or after its filter:

- `igni 5 quantum iactare` / `igni quantum 5 iactare` — Five measures of fire.
- `igni quantum -10 vocant` — Turned around: ten measures of fire are drawn from the world into the body.

### 5.2.1 The Grammar of Force and Matter

- **Subject.** A function acts on the last thing named before it: a source, a mark, or what the function before it
  produced or moved. In `igni 10 40 50 ubis vocant iactare` the fire appears at the place and is hurled from there.
- **Where.** `ubis` before a function says where it acts; without it, where the caster aims. For `iactare` it is where
  the force pushes to, for `impediunt` the centre it pushes away from, for `vocant` where things arrive.
- **Sign.** A negative quantity turns a function around: `iactare` pulls, `impediunt` draws in, `vocant` brings to the
  caster. Other functions refuse it.
- **Origin.** Without `tenet` the source comes from the caster's body. `firmo tenet` takes the earth of the world within
  reach; marks and numbers written before `tenet` say where from (`firmo m1 tenet`).
- **Force on a raw source.** With nothing materialized, a force releases the source as a flow of its state: fire as a
  heat wave, air as a gust, water as a jet, earth as a shock through the ground, vis as a pure push.
- **Impact.** What a force throws strikes with the energy of its motion: half its mass times its speed squared. Earth
  weighs, water half as much, fire and air almost nothing. The blow hurts and pushes what it hits, breaks the blocks it
  can pay for by their hardness, and is heard louder and deeper the harder it is; strong enough, it goes off like an
  explosion. Hot things burn where they strike, wet things put fire out. Servers choose whether magic breaks blocks
  (`magic.breaksBlocks`: `ALWAYS`, `NEVER`, or `MOB_GRIEFING` to follow the world's rule).
- **A condensation handed on.** `firmo tenet quantum chronos 0 vocant iactare` gathers all the earth in reach into an
  orb before the hand; the `iactare` hurls that orb, and it is released where it strikes as one block as dense as all
  of it.

The three functions of force and matter:

| Function    | Action                                   | Turned around                        |
|-------------|------------------------------------------|--------------------------------------|
| `vocant`    | Moves the subject to a point; nothing is created, only carried | Brings it to the caster, or absorbs it |
| `iactare`   | Pushes the subject toward a point        | Pulls it                             |
| `impediunt` | Pushes everything in an area away from a centre | Draws it in toward the centre   |

### 5.3 Conversion with `vertere`

`vertere` converts one source into another. The syntax is:

`[source-origin] vertere [source-target]`

The four elements are the rungs of one ladder of states: `firmo` is solid, `aqua` liquid, `aura` gas, `igni` plasma.
`vis` stands off the ladder. Converting climbs or descends it, and the farther it goes, the more of the energy the
Dreaming Mind spends on the work; returning anything to `vis` costs the whole ladder.

- In the body, `vertere` turns one aspect of the caster's energy into another: `igni vertere aqua` cools fever into
  water.
- On matter of the world (`firmo tenet vertere aqua`, or a marked thing), it changes only the state: stone melts and
  is still stone, molten; water freezes and is still water. What a thing *is* does not change.
- On a thing's core (a source before it, a mark after it: `aqua quantum 16 vertere m1`), it changes what the thing
  *is*.
  - Everything has a core of a hundred parts shared among the four elements, and the quantum says exactly how many
    of them the source written takes. Written with no number, the elements share the hundred evenly:
    `aqua vertere m1` makes m1 all water, and `aqua` and `igni` together make it half of each.
  - What the thing holds is converted, as much in all as before: nothing is brought and nothing is lost. The parts not
    written keep their mix.
  - The lines of one column are weighed together, so the lines of a code make that code. A core is always a hundred:
    parts past it, or all four written short of it, change nothing.
  - What it becomes is in its own natural state: stone or lava made all water is water; lava that keeps its fire, in
    steam's proportion, is vapour.
  - What makes no whole block or item leaves as light. Each line pays, when it is cast, the Dreaming Mind's work of what
    it converts, as any conversion does: by the rungs of the ladder each particle crosses.
  - A being's body is its life, 5 UMU for each point, held by its anchor of 100 Vis (a person is 200 UMU). It becomes
    the kind its core is nearest, with the life it had: a chicken given a cow's core is a cow with a chicken's life.
  - A player changes the same way: their body becomes that kind's, its size and its look, and they keep what they
    carry, their life and their experience. Their own core, written back, brings their own body back.

The final source does not leak; its purpose is fulfilled. A function can be appended:

- `igni vertere aqua iactare` — Convert heat into water and evoke it immediately.

### 5.3.1 Matter and Its Codes

All matter is a proportion of the four elements, and any proportion is matter. There is no recipe to match: what a
mixture does comes from what it holds.

- **Codes.** Every natural thing has a code, the proportion it is made of: stone is mostly earth, with a little water,
  air and fire. Earth, water, air and fire are the four primordials, and §6 says what two of them make together. Every
  other code — stone, iron, wood, bone — is found by trying.
- **Qualities.** Each element gives matter a quality in the proportion it is in it: earth weight, water cohesion, air
  lightness, fire heat. Hot matter burns what touches it and kindles what is around; wet matter puts fire out; heavy
  liquid is thick to wade through. A quality acts only once it is a real part of the matter: a little fire in stone does
  not burn.
- **Mixing.** Fluid matter (anything but a solid) poured where fluid matter is mixes with it. A mixture near a natural
  thing's code *becomes* that thing; any other mixture is formless matter, which holds and acts by its qualities. Solids
  do not mix; they are joined by `ligabis`.
- **Opposites.** In a fluid, fire and water, and earth and air, react when each is a real part of it: as much of one as of
  the other separates out as a gas, and the side that won stays. Fire poured into a pool boils it away; earth stirred
  with air scatters as dust. A solid holds its parts still and does not react.
- **The body keeps energy, not matter.** What the body absorbs loses what it was and enters it as the element of the
  state it was in; what leaves it with no code known is the primordial of its state (`firmo vocant` brings earth).
- **Particles.** Matter is counted in particles, all worth the same: 256 make one UMU. A block of anything holds 4096
  of them, 16 UMU, in any state; an item holds 256 unless the game joins several into a block (nine raw irons, four
  clay balls, three bones). A `vocant` brings one block, 16 UMU, when no quantity is written, and makes whole blocks
  only: what makes no whole block goes back to the body (`firmo quantum 40 vocant` makes two blocks and gives eight
  back).
- **Beings.** Matter held by an anchor of 100 UMU of Vis is a being: the kind whose proportion is nearest. The
  ingredients of a body can be brought from the world in the same instant: flesh lying on the ground, taken with
  `firmo quantum 25 … tenet … ubis vocant`, with air, water and the anchor released at the same place, is a homunculus.

### 5.4 Custom Runes with `reframe`

Any complete spell can be compressed into a single new rune:

1. Write the complete spell.
2. Append `reframe` as the penultimate rune.
3. Name the new rune with the final word.

Example:  
`igni vocant iactare reframe FLAMMA`  
Now `FLAMMA` means "lance of fire."

The new rune must be physically recorded, or the Dreaming Mind will forget it after the next sleep.

### 5.5 Binding with `ligabis`

`ligabis` creates links between targets and marks.

- **Bidirectional:** `[source] ligabis [mark]` — Both feel the link.
- **Unidirectional (master-slave):** `[source] ligabis vertere [mark]` — The target becomes the master.
- **Removal:** a link breaks when the marks of its members are removed, or when its cost can no longer be paid.

Marks can be custom runes (e.g., `SIGMA`) or sigillic symbols. Active links consume UMU continuously.

---

## 6. Compounds Are Matter, Not Runes

Two elements together make things, but no rune stands for them: magma is earth and fire, vapor water and fire, mud
earth and water, dust earth and air, mist water and air, lightning air and fire. Each is a natural thing with its code
(§5.3.1), made by bringing its elements together in one place: fire poured into water boils it into vapor, water poured
into molten earth makes mud. The fused runes that once named them (`fusus`, `caligo`, `lutum`, `pulvis`, `nebula`,
`fulmen`) left the language.

---

## 7. Functions — Narrative Essence

Every rune carries ancestral memory. The Dreaming Mind feels its meaning, not just its translation.

### 7.1 Core Functions

| Function     | Action                              |
|--------------|-------------------------------------|
| `iactare`    | Evoke, project, cast outward.      |
| `vertere`    | Convert one source into another.   |
| `impediunt`  | Push away from a centre, over an area. |
| `ligabis`    | Create a persistent link or bond.  |
| `vocant`     | Carry to a point; turned around, bring to the caster. |
| `reframe`    | Compress a spell into a new rune.  |
| `surgit`     | Raise senses; make the Dreaming Mind read what the caster sees. |

Two functions never fuse into one: to convert and then hurl, write `vertere` and then `iactare`
(`igni vertere aqua iactare`). The fused functions (`transiectio`, `aversio`, `cohaesio` and their kin) left the language.

---

## 8. Advanced Composition

### 8.1 The Page and Its Columns

On a page, each line is a spell of its own, and all are read together. The column is the clock: runes written in the
same column act in the same instant. Runes never fuse: when the processes of two lines meet in one place and one
instant, what happens between them is the physics of the world (water and fire boil; a body and an anchor bind).

### 8.2 Circle Magic

Concentric circles are resolved from the innermost ring outward, each ring acting on what the ring inside it left.
What sits together in one ring acts in the same instant, as a column of a page does, and nothing in it is rewritten
as a single rune: `igni` and `aqua` in one ring are fire and water released together, which boil.

---

## 9. Leaks, Body Impacts, and Foci

### 9.1 Leaks

A source without a function leaks into the caster. `igni aqua iactare` leaves `igni` unspent — it burns at the caster's feet.

### 9.2 Body Impacts

Every source passes through the caster's body before manifesting:

- `igni` raises body temperature (fever, internal burns).
- `aqua` makes limbs heavy and cold (hypothermia risk).
- `firmo` increases weight (knees may fail).
- `aura` raises blood pressure and breathing rate.

### 9.3 Foci

Foci (staves, wands, etched conduits) channel magical flow outside the nerves. They are tuned to the caster's dominant element:
- Long staves dissipate `aqua`.
- Copper wands favor `igni`.
- Fossilized bone or living silver is preferred for `vis`.

Personalize the focus. The body itself is the final living focus.

---

## 10. The Role of `surgit` and Persistent Writing

`surgit` forces the Dreaming Mind to use the caster's current perception as the reading lens. This allows written runes to be executed long after the 2-second pulse, making traps and delayed spells possible.

---

*This document is the pure logic. It is the whisper. How you translate it into code, mechanics, and experience is the art.*
