# Repertório de runas: a árvore de habilidades

**Status:** primeira versão implementada (06/10/2026): runas desbloqueáveis, a janela nova e o comando de teste. As
circunstâncias que ensinam cada runa ainda não foram definidas. Não testado dentro do jogo.

## 1. Famílias

O livro divide as runas "em vários f: Fonte, Função, Filtro e Forma" (cap. 4). No mod:

| Família | *English* | Runas |
|---|---|---|
| **Fontes** | *Fonts* | `igni`, `aqua`, `firmo`, `aura`, `vis` |
| **Funções** | *Functions* | os verbos: `vertere`, `iactare`, `vocant`, `impediunt`, `ligabis`, `reframe`, `surgit` |
| **Filtros** | *Filters* | `quantum`, `chronos`, `ubis`, `tenet` |
| **Fatos** | *Facts* | as condições (`ferit`, `latet`, `aut`, `non`…) e as runas de cena (`ego`, `ille`) |

A **Forma** fica reservada: o livro a cita e não a ensina. Toda família nova começa com F, em português e em inglês.

## 2. Desbloqueio

- **Sem nível e sem hierarquia:** cada runa se desbloqueia sozinha, sem precisar de outra antes.
- **Um mago novo não conhece nenhuma runa** (`repertoire` vazio em `runes.json`).
- **Cada runa** se aprende entendendo-a e escapando do seu sonho em Nahvá (docs/aprendizado-runas-design.md), que chama
  `RuneUnlocks.unlock(jogador, runa)`: ela avisa o jogador ("Você aprendeu a runa X.") e atualiza o cliente.
- **No criativo, tudo está desbloqueado**, sem precisar aprender.
- **Uma runa bloqueada** não entra nos 9 slots do repertório e não pode ser dita no transe.
- **Saves antigos:** quem já jogava conhece as runas que tinha nos slots.
- **Teste:** `/runas desbloquear <runa|todas> [jogadores]`, `/runas bloquear ...`, `/runas lista [jogador]` (operador).

## 3. A janela

- **Uma página só**, com as famílias agrupadas como constelações do Skyrim: cada família é um anel das suas runas, com
  o nome no meio e um contorno suave, e os anéis ficam lado a lado com espaço entre eles. Não há linhas ligando
  runas, porque não há hierarquia.
- **Runa bloqueada:** um bloquinho escuro, sem o glifo, sem o nome e sem a descrição; não se arrasta.
- **Runa desbloqueada:** o glifo, o nome embaixo e, com o mouse em cima, a tradução e o que ela faz (o lore).
- **Arrastar** uma runa para um dos 9 slots a põe no repertório; clique direito esvazia o slot.
- **Arrastar o fundo** percorre as famílias; **a roda do mouse dá zoom** (de 0,4× a 2,5×), mantendo no lugar o que está
  sob o ponteiro.
- **Tela cheia:** o botão no canto abre a página na janela inteira, sem moldura: o céu ocupa tudo, e o título, o botão
  e os 9 slots ficam por cima dele. A escolha fica enquanto o jogo estiver aberto.
- Entre uma família e outra há 90 de céu vazio.

## 4. O que existe no código

- `spelling/data/SpellingRepertoire`: os slots e as runas desbloqueadas (`unlocked` no NBT).
- `spelling/data/RuneUnlocks`: desbloquear e bloquear.
- `ServerRepertoireSyncPacket`: leva os slots e as runas desbloqueadas ao cliente.
- `ClientRepertoireUpdatePacket` e `ServerSpellingController.playerHasRunes`: recusam runas bloqueadas (fora do criativo).
- `command/RuneCommand`: o `/runas`.
- `spelling/client/SpellingRepertoireScreen`: a janela.

## 5. Perguntas abertas

1. **As circunstâncias de cada runa:** o que ensina cada uma?
2. **Os 9 slots continuam sendo o limite do transe?** Com filtros e fatos, 9 fica apertado.

## 6. Decisões tomadas

1. Família das condições e das runas de cena: **Fatos** (*Facts*). (06/10/2026)
2. As runas se desbloqueiam sob certas circunstâncias, sem nível nem hierarquia; no criativo, tudo desbloqueado.
   (06/10/2026)
3. Uma página só, famílias agrupadas com espaço entre elas, como a árvore do Skyrim; botão de tela cheia; runa
   bloqueada é um bloquinho escuro que não revela nada. (06/10/2026)
4. Tela cheia de verdade (a janela inteira, sem moldura), mais espaço entre as famílias e zoom na roda do mouse.
   (06/10/2026)
5. O mago começa sem saber nenhuma runa. (06/10/2026)
