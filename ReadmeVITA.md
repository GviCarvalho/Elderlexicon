📘 README – Sistema de Pontos de Vida (HP) como Energia Elemental (UMU)
Elder Lexicon – Core Magical Physiology

Este documento explica como Pontos de Vida (HP) funcionam dentro do sistema Elder Lexicon, como eles se convertem em UMU, e como a Vida é composta internamente pelos quatro elementos primários.

Este arquivo serve para orientar a implementação de mecânicas internas do mod, como:

drenagem elemental,

desequilíbrios fisiológicos,

efeitos colaterais,

armazenamento interno de energia,

manipulação avançada de Vita.

🧬 1. Vida como Energia Elemental

No sistema Elder Lexicon, “Vida” (Vita) não é um elemento próprio, mas sim uma mistura desequilibrada dos quatro elementos primários:

Aqua (Água)

Aura (Ar)

Igni (Fogo)

Firmo (Terra)

A composição da Vida determina tanto a energia total que o corpo possui quanto as consequências naturais de desequilíbrios.

📊 2. Composição Elemental da Vida

A Vida é composta por:

56% Aqua

38% Aura

2% Igni

4% Firmo

Esses valores foram arredondados a partir do sistema original, mantendo o mesmo comportamento fisiológico.

Soma total = 100%

❤️ 3. Quantidade Total de UMU em Vida (20 HP)

O jogador padrão do Minecraft possui:

20 HP (10 corações)

No Elder Lexicon:

20 HP equivalem exatamente a 100 UMU de “Vida Elemental”.

Assim, cada ponto de HP representa:

1 HP = 5 UMU

1 coração (2 HP) = 10 UMU

20 HP = 100 UMU

🔥 4. Distribuição dos 100 UMU de Vida

Dado o total de 100 UMU:

Elemento	Percentual	UMU resultante
Aqua	56%	56 UMU
Aura	38%	38 UMU
Igni	2%	2 UMU
Firmo	4%	4 UMU

Esses valores representam a energia elemental interna do corpo vivo.
Eles podem ser drenados, aumentados, desequilibrados ou manipulados por feitiços.

⚠️ 5. Consequências de Excesso ou Escassez

Cada elemento possui efeitos naturais quando excede ou abaixa do nível normal.

Esses efeitos não são implementados automaticamente pelo sistema básico, mas a tabela a seguir orienta a implementação futura:

Aqua

Excesso: Náusea

Escassez: Lentidão

Aura

Excesso: Cegueira

Escassez: Dano de Afogamento (sensação de sufocamento)

Igni

Excesso: Combustão (jogador pega fogo)

Escassez: Dano por frio

Firmo

Excesso: Lentidão + Náusea

Escassez: Fome rápida

Essa fisiologia elemental permite construir feitiços que:

drenam elementos específicos,

reforçam elementos,

reequilibram o corpo,

causam colapsos ou mutações,

ou permitem “transmutação de Vida”.

🧠 6. Relação com Mana (Vis)

Mana é definida como equilíbrio perfeito dos quatro elementos primários:

25% Aqua

25% Aura

25% Igni

25% Firmo

Assim:

Vida ≠ Mana

Vida é um estado desequilibrado (rica em Aqua e Aura, pobre em Igni e Firmo)

Isso explica por que:

drenar Vida não gera Mana pura,

converter Vida em Mana exige processamento mágico,

feitiços que manipulam Vita carregam risco.

🎯 7. Objetivo Técnico

Este documento permite que o Codex implemente:

armazenamento de UMU interno no jogador,

acesso aos valores elementais da Vida,

feitiços que drenam ou reforçam elementos específicos,

estados negativos ligados a desequilíbrios,

mecânicas de conversão entre Vida e magia,

sistema futuro de “fisiologia arcana”.