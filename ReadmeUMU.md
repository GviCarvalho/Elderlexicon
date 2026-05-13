© GviCarvalho – This documentation is part of Elderlexicon and is licensed under CC BY-NC-ND 4.0. See LICENSE-DOCS.txt for details.

📜 UMU — Universal Magical Unit
Elder Lexicon Energy Consumption System

UMU (Universal Magical Unit) is the standard unit used to measure, calculate, and execute the cost of any spell within the Elder Lexicon system.

Every rune, action, transformation, or effect uses UMU as fundamental energy.

⚡ 1. Consumption Order

Whenever a spell requires UMU, the player pays in this fixed order:

XP (Experience)

Saturation

HP (Health)

If all are depleted, the spell fails.

🧪 2. Resource → UMU Conversions

These conversions follow your official logic:

🎓 XP → UMU

XP is the primary conjuration resource.

10 XP points = 1 UMU

This maintains coherence with vanilla game flow and facilitates balancing.

🍗 Saturation → UMU

Saturation is bodily energy converted into magic.

1 saturation point = 1 UMU

Saturation is less valuable than XP, but functions as a secondary reserve.
Allows casting simple spells even without XP.

❤️ HP → UMU

Health is raw energy.
It is the last resource and the most expensive.

Vita is worth 5 UMU per HP point.

Thus:

1 HP point = 5 UMU

1 heart = 2 HP = 10 UMU

Default player: 20 HP = 100 UMU total (if willing to die casting)

HP is only used when XP and Saturation are depleted.

🔥 3. Base Rune Costs (non-fused)

Pulled directly from your technical documentation, with nothing invented.

🜄 Sources

Sources have no fixed cost of their own — they cost the amount of UMU equivalent to what they are moving/converting.

Rune       | Meaning | Cost     | Notes
-----------|---------|----------|-------
aqua       | water   | variable | According to water quantity in Liters, where 0.5L = 1 UMU. A water bottle contains 0.5L, a cauldron contains 1.5L (3 UMU), same as a bucket and a water source block.
aura       | air     | variable | Same principle, default is 1/psi
igni       | fire    | variable | According to the light level of blocks/entities/particles/items that emit light through fire, where 1 UMU = 12 light emission levels
firmo      | earth   | variable | According to block hardness, i.e., blast resistance 10 = 1 UMU. For reference, a dirt block has 0.5 BR (Blast resistance), while a stone block has 30 BR
vis        | mana    | 1 UMU per mana point | Mana is the starting point itself. Important to remember that mana is composed of energy from all 4 primordial elements in perfect balance (25% igni, 25% aura, 25% aqua, 25% firmo).
vita       | life    | 5 UMU per HP point | Important to remember that Vita is also the energy coming from the junction of the 4 primordial elements, but in specific quantities (56% aqua, 38% aura, 2% igni, and 4% firmo).

Sources are "raw material". The real cost comes from how much the function demands to manipulate.

🜂 Functions

These do have defined costs from the document, now organized here as base UMU:

Function   | Meaning            | Base Cost         | How to interpret
-----------|--------------------|-------------------|-------------------
exsugat    | absorb             | 0 UMU             | Cost occurs in transfer, not activation
ligabis    | connect            | 0.1 UMU × time    | Continuous maintenance
vertere    | convert            | UMU converted     | Transfers 1:1
iactare    | evoke/project      | 1 UMU/s           | Cost flows during cast
vocant     | invoke             | 1 UMU (lump sum)  | Instantaneous cost
reframe    | resignify          | 5 UMU             | Fixed cost
surgit     | conjure            | 1 UMU             | Fixed cost
impediunt  | repel/contain      | UMU of the impulse| Thrust value defines cost

🜁 Filters

Filters have no cost of their own — they cost exactly the value they modify.

Filter     | Meaning       | Cost
-----------|---------------|--------------------------
quantum    | quantity      | modified value (in UMU)
chronos    | time          | modified value (in seconds = UMU)
ubis       | range         | modified value (in meters = UMU)

📘 4. Cost Calculation Example

Linear spell:

igni vertere aura iactare


Calculation:

igni → cost depends on flow (not fixed)

vertere → 1:1 (converts the igni flow)

aura → same

iactare → 1 UMU per second of projection

Assuming a flow of 3 UMU and iactare duration of 2s:

igni: 3 UMU

vertere: 3 UMU

aura: 0 (source has no fixed cost)

iactare: 2 UMU

Total: 8 UMU

If the player has:

2 XP → spends 2 UMU

4 saturation → spends 4 UMU

remaining 2 UMU → pays with HP → loses 0.4 hearts

📌 Final Summary
Conversion

1 XP = 1 UMU

1 saturation = 1 UMU

1 HP = 5 UMU

Order

XP → Saturation → HP

Sources

Cost the amount they are manipulating.

Functions

Cost defined by documentation.

Filters

Cost = the modified value.
