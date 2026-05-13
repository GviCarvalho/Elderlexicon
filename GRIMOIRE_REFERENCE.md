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
2. **Functions (Funções):** What to do with the energy (`iactare`, `vertere`, `exsugat`, `ligabis`, `reframe`, etc.).
3. **Forms (Formas):** How to shape the manifestation (`hasta`, `murus`, `orbis`, `vortex`, `sigillum`, etc.).
4. **Filters (Filtros):** Adjustments to time, quantity, or range (`chronos`, `quantum`, `ubis`).

### 5.2 Canonical Syntax

The Dreaming Mind expects short, well-formed orders:

Expr ::= source → function
| source → form → function
| source → form → value → filter → function


Examples:

- `igni iactare` — Evoke fire.
- `igni hasta iactare` — Evoke a lance of fire.
- `igni hasta 5 chronos iactare` — Evoke a lance of fire that persists for 5 seconds.

Filters placed before a rune modify its value; filters placed after extract the pattern:

- `igni 5 quantum iactare` — Evoke five measures of fire.
- `igni iactare chronos exsugat` — Absorb fire for the default casting window (2 seconds).

### 5.3 Conversion with `vertere`

`vertere` converts one source into another. The syntax is:

`[source-origin] vertere [source-target]`

The final source does not leak; its purpose is fulfilled. A function can be appended:

- `igni vertere aqua iactare` — Convert heat into water and evoke it immediately.

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
- **Removal:** `[mark] ligabis exsugat` — The link is drained.

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
| `orbis`    | A closed sphere or compact projectile.           |
| `sigillum` | A glyph that fixes the order to a point.         |
| `vortex`   | A whirlpool that mixes sources actively.         |

### 7.2 Core Functions

| Function     | Action                              |
|--------------|-------------------------------------|
| `iactare`    | Evoke, project, cast outward.      |
| `vertere`    | Convert one source into another.   |
| `impediunt`  | Repel what came before it in the order. |
| `ligabis`    | Create a persistent link or bond.  |
| `exsugat`    | Drain, absorb, sequester energy.   |
| `vocant`     | Summon, attract from afar.         |
| `reframe`    | Compress a spell into a new rune.  |
| `surgit`     | Raise senses; make the Dreaming Mind read what the caster sees. |

### 7.3 Compound Functions

These are fusions of two core functions, forming more specific actions:

| Compound        | Fusion                | Action                                    |
|-----------------|-----------------------|-------------------------------------------|
| `transiectio`   | `vertere`+`iactare`   | Convert and hurl (teleport).              |
| `aversio`       | `vertere`+`impediunt` | Convert and repel.                        |
| `cohaesio`      | `vertere`+`ligabis`   | Fuse targets (alchemy).                   |
| `exhaustio`     | `vertere`+`exsugat`   | Exhaust energy reserves.                  |
| `transvocatio`  | `vertere`+`vocant`    | Convert and invoke elsewhere (transference). |
| `deflectio`     | `iactare`+`impediunt` | Deflect flows and projectiles.            |
| `vinculatio`    | `iactare`+`ligabis`   | Link targets via launching.               |
| `exsuctio`      | `iactare`+`exsugat`   | Channeled draining (active siphon).       |
| `evocatio`      | `iactare`+`vocant`    | Conjure stably.                           |
| `compeditio`    | `impediunt`+`ligabis` | Shackle and immobilize.                   |
| `exinanitio`    | `impediunt`+`exsugat` | Bleed slowly.                             |
| `coniuratio`    | `ligabis`+`vocant`    | Conspire, gather targets.                 |
| `extractio`     | `exsugat`+`vocant`    | Converge content to a focus.              |

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
