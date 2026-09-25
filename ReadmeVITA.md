© GviCarvalho – This documentation is part of Elderlexicon and is licensed under CC BY-NC-ND 4.0. See LICENSE-DOCS.txt for details.

📘 README – Health Points (HP) System as Elemental Energy (UMU)
Elder Lexicon – Core Magical Physiology

This document explains how Health Points (HP) work within the Elder Lexicon system, how they convert into UMU, and how Life is internally composed of the four primary elements.

This file serves to guide the implementation of the mod's internal mechanics, such as:

elemental draining,

physiological imbalances,

side effects,

internal energy storage,

advanced manipulation of Vita.

🧬 1. Life as Elemental Energy

In the Elder Lexicon system, "Life" (Vita) is not an element in itself, but an unbalanced mixture of the four primary elements:

Aqua (Water)

Aura (Air)

Igni (Fire)

Firmo (Earth)

The composition of Life determines both the total energy the body possesses and the natural consequences of imbalances.

📊 2. Elemental Composition of Life

Life is composed of:

56% Aqua

38% Aura

2% Igni

4% Firmo

These values were rounded from the original system, maintaining the same physiological behavior.

Total sum = 100%

❤️ 3. Total Amount of UMU in Life (20 HP)

The default Minecraft player has:

20 HP (10 hearts)

In Elder Lexicon:

20 HP equals exactly 100 UMU of "Elemental Life".

Thus, each HP point represents:

1 HP = 5 UMU

1 heart (2 HP) = 10 UMU

20 HP = 100 UMU

🔥 4. Distribution of the 100 UMU of Life

Given the total of 100 UMU:

Element | Percentage | Resulting UMU
--------|------------|---------------
Aqua    | 56%        | 56 UMU
Aura    | 38%        | 38 UMU
Igni    | 2%         | 2 UMU
Firmo   | 4%         | 4 UMU

These values represent the internal elemental energy of the living body.
They can be drained, increased, unbalanced, or manipulated by spells.

⚠️ 5. Consequences of Excess or Scarcity

Each element has natural effects when exceeding or dropping below the normal level.

These effects are not automatically implemented by the basic system, but the following table guides future implementation:

Aqua

Excess: Nausea

Scarcity: Slowness

Aura

Excess: Blindness

Scarcity: Drowning damage (suffocation sensation)

Igni

Excess: Combustion (player catches fire)

Scarcity: Cold damage

Firmo

Excess: Slowness + Nausea

Scarcity: Rapid hunger

This elemental physiology allows building spells that:

drain specific elements,

reinforce elements,

rebalance the body,

cause collapses or mutations,

or allow "transmutation of Life".

🧠 6. Relation to Mana (Vis)

Mana is defined as the perfect balance of the four primary elements:

25% Aqua

25% Aura

25% Igni

25% Firmo

Thus:

Life ≠ Mana

Life is an unbalanced state (rich in Aqua and Aura, poor in Igni and Firmo)

This explains why:

draining Life does not generate pure Mana,

converting Life into Mana requires magical processing,

spells that manipulate Vita carry risk.

🎯 7. Technical Goal

This document allows the Codex to implement:

internal UMU storage in the player,

access to the elemental values of Life,

spells that drain or reinforce specific elements,

negative states linked to imbalances,

conversion mechanics between Life and magic,

future "arcane physiology" system.

⚖️ 8. Balance over time (implemented 2026-09-25)

- Losing or regaining health takes or gives each element in its share of life, so damage and healing alone never unbalance the body.
- A change to one element (a spell, absorbed energy, a conversion) stays in that element. The old cyclic cascade (a change pushing the next elements of the cycle the opposite way) was removed: it kept the body unbalanced and made it very hard to recover.
- The body drifts back to balance by itself, as the grimoire says (3.2: it "absorbs the energy around it slowly ... until the scale returns to balance"): every second each element moves 10% of the way to its share of the current life (`VitaSystem.RELAX_PER_SECOND`), about 13 seconds from a severe imbalance to balanced.
- `/vita reset` balances the body for the health the player has now (it used to assume a full 100 UMU, so a hurt player was unbalanced again on the next tick).
- Note: the code uses 55/38/2/5 while section 2 above says 56/38/2/4.
