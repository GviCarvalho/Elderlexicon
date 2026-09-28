# Magias de círculo

**Status:** implementado (27/09/2026), falta testar no jogo.

## A ideia

Um círculo mágico é feito de **peças no chão** (ou numa parede, ou no teto): pergaminhos destacados colocados sobre
blocos, e runas **escritas direto nos blocos**. Cada peça é uma página, com suas linhas e colunas como no grimório.
O espírito lê o círculo **de dentro para fora**, do anel mais interno ao mais externo, como no livro (10.1.1). Magia
de círculo serve para **feitiços em cadeia**.

Não há fusão escrita: tudo que está no mesmo anel é solto no mesmo instante, e o que acontece entre esses feitiços é a
física (`interacoes-design.md`).

## Qualquer forma é um círculo

O sistema não conhece formatos. Ele:

1. **Junta as peças** do mesmo plano e viradas para o mesmo lado (todas no chão, ou todas na mesma parede) que estão
   a no máximo 3 blocos de outra do grupo. Qualquer tamanho e quantidade.
2. **Descasca como uma cebola** (`spell/circle/CircleShape`): as peças no contorno que embrulha todas (um elástico
   esticado em volta) são o anel mais externo; o mesmo de novo com o que sobrou é o anel seguinte; o que sobra no fim
   é o núcleo. Uma peça a até meio bloco do contorno está nele (círculos de blocos nunca são redondos de verdade).
3. **Lê do núcleo para fora.**

| Forma | O que o espírito vê |
|---|---|
| cruz (centro + 4) | núcleo: o centro → anel: os 4 |
| 3×3 | núcleo: o centro → anel: os 8 |
| 4×3 | núcleo: os 2 do meio → anel: os 10 |
| 4×4 | núcleo: os 4 do meio → anel: os 12 |
| contorno vazio | um anel só |
| contorno + centro | núcleo: o centro → anel: o contorno |
| anéis dentro de anéis | um anel por camada, de dentro para fora |
| elipse, forma torta | o mesmo: o contorno é um anel |

Círculos separados (peças a mais de 3 blocos uma da outra) são lidos cada um por si.

## O tempo

- **Os anéis vêm um depois do outro**: um anel começa quando o de dentro é solto.
- **As peças de um anel se ajustam para serem soltas juntas**: cada página tem seu comprimento (a coluna da última
  runa + 1); uma página mais curta começa mais tarde, para terminar junto com a mais longa do anel
  (`spell/circle/CircleTiming`).
- Dentro de cada página, o relógio continua sendo a coluna.

Exemplo: núcleo com uma página de 3 passos; anel com páginas de 2 e 4 passos. O núcleo vai do passo 0 ao 2; o anel
começa no passo 3: a página de 4 passos do 3 ao 6, a de 2 passos do 5 ao 6. As duas são soltas no passo 6.

## Como se ativa

- **Mirando numa peça**: `surgit` olhando para um pergaminho ou para runas escritas num bloco que fazem parte de um
  círculo (duas peças ou mais) lê o círculo inteiro. Uma peça sozinha é lida sozinha, como antes.
- **Ao alcance do toque**: sem mirar em nada, `surgit` lê cada círculo que tenha uma peça ao alcance da mão (o
  alcance de quebrar e usar blocos). Assim a mira fica livre para os feitiços do círculo (um `vocant` faz surgir onde
  o mago olha).
- **Por marca**: `marca surgit` lê, **de qualquer lugar da dimensão**, cada círculo do qual uma peça marcada faz parte
  (um pergaminho com a marca, ou um bloco marcado com runas escritas). O círculo precisa estar carregado. O custo
  cresce com a distância: **× (1 + distância / 64)** (a 64 blocos, o dobro; a 640, onze vezes). Os pergaminhos
  marcados que não estão em círculo continuam sendo lidos como nos rituais.
- **Os feitiços saem do conjurador**, e ele paga. (Escolher de onde os feitiços saem é uma mecânica para depois.)

## O círculo aparece enquanto é montado

O cliente lê as peças em volta do jogador do mesmo jeito que o espírito (`CircleShape`) e desenha o círculo mágico no
plano, como numa ilustração de grimório:

- **cada anel é uma faixa entre duas linhas de luz lavanda**, redonda, ou oval quando o círculo é alongado (passa
  pelas peças mais afastadas do anel), pulsando de leve, **desenhada em pixels** na grade dos texels dos blocos (1/16
  de bloco), para ter a cara do jogo;
- **as runas de cada peça são escritas ao longo da faixa**, centradas onde a peça está, com o topo dos glifos para
  fora; se o anel tiver mais a dizer do que cabe, as runas encolhem;
- **o núcleo de uma peça só** tem as runas escritas no meio, linha sob linha, sem faixa.
- **enquanto o círculo aparece, as runas das próprias peças somem**: as inscrições não são desenhadas nas faces e os
  pergaminhos aparecem em branco, para que só as runas do círculo sejam lidas.

Aparece quando o jogador segura um pergaminho, uma pena ou o grimório, ou está a até 6 blocos de uma peça. Assim dá
para ver, antes de ler, quantos anéis o círculo tem e o que cada um diz.

## Escrever direto no bloco

Com uma **pena** na mão e um **pigmento** no inventário (qualquer corante, bolsa de tinta ou bolsa de tinta
brilhante), usar a pena numa face de um bloco abre a escrita: **quatro linhas curtas**, em SGA enquanto se digita.
Cada linha é um feitiço, e a coluna é o tempo, como numa página.

- Escrever gasta **um pigmento** (nada no modo criativo); a tinta tem a **cor** do corante. A **tinta brilhante** faz
  as runas brilharem no escuro.
- Reabrir a face com a pena permite editar; apagar tudo limpa a face (sem gastar pigmento).
- **Quebrar o bloco** apaga o que estava escrito nele.
- As runas escritas são peças de círculo como os pergaminhos, e podem ser lidas sozinhas com `surgit` mirando nelas.
- Guardadas com o mundo (`Inscriptions`), e enviadas aos jogadores daquele mundo.

## Próximo

- Escolher de onde saem os feitiços de cada peça (do pergaminho, para fora do círculo…).
