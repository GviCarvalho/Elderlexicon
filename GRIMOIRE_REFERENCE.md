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

These elements combine to form all other sources.

---

## 3. Mana (Vis) and Life (Vita)

- **`vis` (Mana):** The perfect equilibrium of all four elements — 25% `igni`, 25% `aura`, 25% `aqua`, 25% `firmo`. It is neutral energy, ideal for fusions and realignments.
- **`vita` (Life):** The specific elemental blend that composes living beings:

| Element | Proportion |
|---------|------------|
| `aqua`  | 55% (later refined to 56%) |
| `aura`  | 38% |
| `firmo` | 5% (later refined to 4%)  |
| `igni`  | 2%  |

Manipulating `vita` allows precise healing or harm. The elemental balance is maintained by a cycle:

- **+**`igni` ⇒ **–**`aura`
- **+**`aura` ⇒ **–**`firmo`
- **+**`firmo` ⇒ **–**`aqua`
- **+**`aqua` ⇒ **–**`igni`

Disturbing one element without compensating the others causes nausea, fever, internal burns, or collapse.

---

## 4. The Universal Magical Unit (UMU)

Every spell consumes energy. The **UMU** is an abstract, universal measure of that cost — independent of the manifestation (heat, mass, vapor, etc.). A simple `igni iactare` costs few UMU; a long fusion chain may cost hundreds.

The UMU is the accounting system. How a given platform translates it into tangible resources (XP, health, saturation, etc.) is an implementation decision.

---

## 5. The Runic Language

### 5.1 Rune Categories

1. **Sources (Fontes):** The energy to mobilize (`igni`, `aqua`, `vis`, `vita`, `fusus`, `nebula`, etc.).
2. **Functions (Funções):** What to do with the energy (`iactare`, `impediunt`, `vocant`, `vertere`, `ligabis`, `surgit`, `reframe`).
3. **Forms (Formas):** How to shape the manifestation (`hasta`, `murus`, `vortex`, `sigillum`, `catena`).
4. **Filters (Filtros):** Adjustments to time, quantity, place, or origin (`chronos`, `quantum`, `ubis`, `tenet`).

Some words the language once had are gone: `exsugat` (its work is now done by `tenet` and by a turned-around verb),
`transvocatio` (move each thing with `vocant`), and the forms and fusions built on them (`orbis`, `exhaustio`,
`exsuctio`, `extractio`, `exinanitio`). The Dreaming Mind still recognizes them, and says what to write instead.

### 5.2 Canonical Syntax

The Dreaming Mind expects short, well-formed orders:

Expr ::= source → function
| source → form → function
| source → form → value → filter → function


Examples:

- `igni iactare` — Push a wave of heat toward where the caster aims.
- `igni vocant iactare` — Bring fire out at the hand and hurl it: a lance of fire.
- `igni hasta 5 chronos iactare` — Evoke a lance of fire that persists for 5 seconds.

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

The final source does not leak; its purpose is fulfilled. A function can be appended:

- `igni vertere aqua iactare` — Convert heat into water and evoke it immediately.

### 5.3.1 Matter and Its Codes

All matter is a proportion of the four elements, and any proportion is matter. There is no recipe to match: what a
mixture does comes from what it holds.

- **Codes.** Every natural thing has a code, the proportion it is made of: stone is mostly earth, with a little water,
  air and fire. Earth, water, air and fire are the four primordials; mud, dust, mist, vapor, magma and lightning are the
  codes the book teaches. Every other code — stone, iron, wood, bone — is found by trying.
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

### 5.4 Custom Runes with `reframe`

Any complete spell can be compressed into a single new rune:

1. Write the complete spell.
2. Append `reframe` as the penultimate rune.
3. Name the new rune with the final word.

Example:  
`igni hasta iactare reframe FLAMMA`  
Now `FLAMMA` means "lance of fire."

The new rune must be physically recorded, or the Dreaming Mind will forget it after the next sleep.

### 5.5 Binding with `ligabis`

`ligabis` creates links between targets and marks.

- **Bidirectional:** `[source] ligabis [mark]` — Both feel the link.
- **Unidirectional (master-slave):** `[source] ligabis vertere [mark]` — The target becomes the master.
- **Removal:** a link breaks when the marks of its members are removed, or when its cost can no longer be paid.

Marks can be custom runes (e.g., `SIGMA`) or sigillic symbols. Active links consume UMU continuously.

---

## 6. Compound Sources (Fusions)

Elemental pairs fuse into compound sources:

| Fusion   | Components    | Essence                              |
|----------|---------------|--------------------------------------|
| `fusus`  | `igni`+`firmo` | Magma — molten earth                |
| `caligo` | `aqua`+`igni`  | Vapor — hot mist, latent heat       |
| `lutum`  | `aqua`+`firmo` | Mud — binding, entrapping           |
| `pulvis` | `aura`+`firmo` | Dust — choking, visibility reduction|
| `nebula` | `aura`+`aqua`  | Nebula — cold mist, obscuring       |
| `fulmen` | `aura`+`igni`  | Lightning — shared fury, dangerous  |

These compound sources behave exactly like primordial sources in syntax.

---

## 7. Forms and Functions — Narrative Essence

Every rune carries ancestral memory. The Dreaming Mind feels its meaning, not just its translation.

### 7.1 Forms

| Form       | Narrative                                          |
|------------|----------------------------------------------------|
| `hasta`    | A sudden column, lance, or linear jet.           |
| `murus`    | A wall rising from the ground, holding the source in its surface. |
| `catena`   | A chain or curved trajectory, linking or dragging. |
| `sigillum` | A glyph that fixes the order to a point.         |
| `vortex`   | A whirlpool that mixes sources actively.         |

### 7.2 Core Functions

| Function     | Action                              |
|--------------|-------------------------------------|
| `iactare`    | Evoke, project, cast outward.      |
| `vertere`    | Convert one source into another.   |
| `impediunt`  | Push away from a centre, over an area. |
| `ligabis`    | Create a persistent link or bond.  |
| `vocant`     | Carry to a point; turned around, bring to the caster. |
| `reframe`    | Compress a spell into a new rune.  |
| `surgit`     | Raise senses; make the Dreaming Mind read what the caster sees. |

### 7.3 Compound Functions

These are fusions of two core functions, forming more specific actions:

| Compound        | Fusion                | Action                                    |
|-----------------|-----------------------|-------------------------------------------|
| `transiectio`   | `vertere`+`iactare`   | Convert and hurl (teleport).              |
| `aversio`       | `vertere`+`impediunt` | Convert and repel.                        |
| `cohaesio`      | `vertere`+`ligabis`   | Fuse targets (alchemy).                   |
| `deflectio`     | `iactare`+`impediunt` | Deflect flows and projectiles.            |
| `vinculatio`    | `iactare`+`ligabis`   | Link targets via launching.               |
| `evocatio`      | `iactare`+`vocant`    | Conjure stably.                           |
| `compeditio`    | `impediunt`+`ligabis` | Shackle and immobilize.                   |
| `coniuratio`    | `ligabis`+`vocant`    | Conspire, gather targets.                 |

---

## 8. Advanced Composition

### 8.1 Column Realignment

When runes are written in superimposed lines, they share an index `P = column + line`. Runes with identical `P` fuse.

### 8.2 Circle Magic

Concentric circles are resolved from the innermost ring outward. Each ring applies its rune to the result of the previous ring. Rectangles inside rings preserve standard linear order.

Example: four concentric rings — inner ring fuses `igni` and `aqua`; second ring applies `vertere`; third applies `firmo`; fourth applies `vocant`. The Dreaming Mind rewrites this as `caligo vertere firmo vocant` before execution.

### 8.3 Compound Fusions via Intersection

Overlapping circles create shared regions. Each intersection fuses the runes of the overlapping areas before the outer ring applies the final function.

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
