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
