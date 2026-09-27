# Interações entre elementos: física emergente

**Status:** desenho aprovado (27/09/2026). Implementados: os quatro aspectos da energia como fundamento (custo do
`vertere` e a vis solta), calor, fases da água e da lava, vapor e explosão de vapor, corpos com gravidade e contenção,
gotículas, agitação e carga (relâmpago), e a página lida por colunas.

## A ideia

Não existe tabela "A + B = C". Os elementos escrevem num **estado físico do mundo**, e um punhado de **leis** age sobre
esse estado. Nenhuma lei cita dois elementos: cada uma fala de temperatura, pressão, fase ou carga. O que acontece
quando dois elementos se encontram é consequência, não regra.

O livro dá os dois exemplos, e os dois saem das leis sem serem escritos:

- **aura + aqua no mesmo instante → relâmpago** (10.1): umidade turbulenta separa carga, e carga demais descarrega.
- **aqua + igni → explosão de vapor** (10.1.1): a água ferve, o vapor se expande e, sem ter por onde sair, explode.

Isso também mantém a modularidade das runas: as leis não sabem que feitiço trouxe a energia, só onde e quando ela
está. Qualquer combinação escrita pelo jogador interage com qualquer outra, sem pré-programação.

## Os quatro aspectos da energia (o fundamento)

Decisão de 27/09/2026. Os elementos não são substâncias: são **aspectos da energia**. Toda energia tem os quatro em
alguma medida, e o elemento é o aspecto que ela mostra. A UMU é a moeda única entre eles.

| Elemento | Aspecto | O que guarda | No livro |
|---|---|---|---|
| igni | **calor** | energia térmica | energia e transformação |
| aqua | **coesão** (umidade) | o que a água segura e solta ao mudar de fase (o calor latente) | fluidez e coesão |
| aura | **expansão** (pressão) | o trabalho de expandir | movimento e expansão |
| firmo | **massa** (densidade) | a energia da massa, da estrutura; no extremo, o buraco negro | estabilidade e estrutura |
| vis | **o centro**: os quatro em equilíbrio, 0,25 de cada (livro 3.1) | | a partícula perfeita |

Umidade e densidade são, a rigor, quantidades de matéria. O aspecto é a energia que essa matéria guarda: a coesão da
água, a massa da terra. Assim os quatro são energia de verdade, e somam.

### A geometria: dois eixos de opostos

- **Eixo térmico: calor ↔ coesão.** O calor evapora a água; a água engole o calor e apaga o fogo.
- **Eixo mecânico: expansão ↔ massa.** A pressão espalha; a densidade junta.

Aspectos de **eixos diferentes são vizinhos** e passam energia um ao outro diretamente:

| Vizinhos | Na física |
|---|---|
| calor + expansão | gás aquecido expande; ar comprimido esquenta, e expandindo esfria |
| calor + massa | derreter, dilatar; matéria que se contrai esquenta (estrelas) |
| coesão + expansão | pressão de vapor, ebulição, tempestade |
| coesão + massa | lama, erosão, gelo que racha a rocha |

**Opostos** só se encontram anulando um ao outro. É o mapa antigo das qualidades visto como geometria: vizinhos
compartilham uma qualidade (quente, frio, úmido, seco), opostos nenhuma.

### O que o fundamento decide

- **O custo do `vertere` é distância nessa geometria**: vizinho = 1 passo (5%), oposto = 2 (10%). Sair da vis é
  gratuito (ela é o centro, energia ainda sem aspecto); voltar a ela exige equilibrar os quatro, tão caro quanto cruzar
  um eixo. Implementado em `spell/Aspect.java`, e o `Conversion` usa essa distância (dá os mesmos números da tabela de
  qualidades que havia antes).
- **As leis são trocas entre aspectos**, sempre conservando a UMU. O estado físico guarda, em cada bloco, calor,
  coesão (água e gotículas), expansão (pressão) e massa (o próprio bloco).
- **Um feitiço só escreve aspectos num lugar.** Nenhum elemento precisa de efeito próprio: o que acontece é a lei.
- **A vis solta mostra os quatro de uma vez**: um quarto de calor, um de massa, um de água, um de ar comprimido, no
  mesmo ponto e instante. O resto é emergente: o calor ferve a água, a pressão a espalha, a massa fica. Em energia
  grande, é uma pequena erupção (gás quente sob pressão, rocha, vapor, e, se o ar gelar, raios). Implementado em
  `VisSpots.release`, junto com o clarão de luz que já existia.
- **Direção para as próximas leis**: num gás, a expansão não é independente, é massa × calor (a lei dos gases). Hoje o
  estado já acopla calor → pressão (o ar aquecido expande) e o ar comprimido solto esfria ao expandir. O passo seguinte
  é o inverso, a compressão aquecendo, e tratar a pressão do ar como consequência de massa e calor, não um número solto.

## A página: cada linha é um feitiço, todas ao mesmo tempo

Decisão de 27/09/2026. Numa página, **cada linha é um feitiço independente**, e todas são lidas **juntas**. O relógio é a
**coluna**: runas na mesma coluna acontecem no mesmo instante, e uma linha é solta na coluna da sua última runa. A linha
de baixo não é lida depois da de cima (linhas em branco não atrasam mais nada).

A **fusão** do livro passa a ser só lore. Não há regra de fusão no leitor: quando os processos de duas linhas se cruzam
no mesmo lugar e no mesmo instante (o alinhamento das colunas), o que acontece entre eles é a física. O exemplo do livro,
aura e aqua no mesmo instante dando relâmpago, sai das leis abaixo.

## Corpos e contenção

Um condensado lançado é um **corpo**: tem velocidade, cai com a gravidade (como uma flecha) e o ar o freia. A mira só
dá o impulso. O que ele carrega fica **contido pelo espírito** por um **timer curto (2 s)**: se bater em algo antes
(chão, criatura ou a superfície da água, que num golpe rápido é dura como chão), é solto ali; se não, é solto **onde
estiver**. Mirando no céu, o gelo VII sobe, freia e estoura no alto; o que ele carrega chove.

Soltar não tem efeito programado por elemento: o conteúdo vai para o estado físico no ponto.

- **Ar comprimido solto**: o ar em volta é **agitado** e, expandindo de uma pressão tão grande, **esfria** (metade da
  energia da pressão sai como frio).
- **Água comprimida solta** (gelo VII ou água condensada): volta ao seu volume e é jogada como **gotículas** num raio
  de metade do alcance da explosão, e a explosão **agita** o ar. As gotículas caem e empoçam; não há mais fontes de
  água postas à mão.

## O que existe e o que muda

| Peça | Hoje | Com as interações |
|---|---|---|
| Runas, `Heat`/`Density`/`Pressure`/`AirPressure`, executor | decidem quanta energia e com que intensidade | **não mudam** |
| `HeatSpots`, `WaterSpots`, `AirSpots`, `EarthSpots` | cada um age sozinho no mundo | continuam agindo, e **também escrevem no estado físico** |
| `scene/SceneLaws` (fricção e térmica entre feixes e pontos) | só vê feitiços que se cruzam no ar | continua; no passo 6 as emissões passam a escrever no estado físico e ele se aposenta |
| Estado físico (`spell/nature`) | não existe | novo: temperatura, pressão e carga por bloco, só onde há energia ativa |

**A matéria é o próprio mundo.** Água, gelo, lava e pedra já são blocos, e a fase deles é o bloco. O estado físico
guarda só o que o Minecraft não tem: **temperatura**, **pressão** e **carga**.

## O estado físico

- Esparso: uma célula por bloco, **só onde há energia ativa**. Cada célula volta ao ambiente com o tempo e some.
- Temperatura na escala do `Heat`: **0 = ambiente**, 1 = fogo comum, 10 = pedra derrete, 30 = plasma.
- Pressão em UMU de ar a mais no bloco, na escala do `AirPressure`: **0 = atmosfera**, `−0,5` = vácuo (um bloco de ar
  vale 0,5 UMU).
- O estado guarda só **o que aconteceu**, os desvios do mundo como ele sempre é. A matéria natural entra quando a
  energia chega nela (uma água natural esquentada ferve), mas o calor natural em repouso é a linha de base: um lago de
  lava parado não esquenta o ar para sempre nem é acordado inteiro por um toque. Só entra a lava que um feitiço acordou
  (a que o `HeatSpots` derrete). Sem energia mágica por perto, nada é calculado.
- Teto de células por mundo; passando dele, o que já está ativo continua e nada novo acorda.

## Os materiais

Cada bloco é lido como um material com propriedades, e as leis só olham para elas:

O estado também guarda, no ar: **gotículas** (UMU de água suspensa), **poça** (água caída que ainda não é bloco),
**agitação** (energia do movimento do ar) e **carga**.

| Material | Capacidade (UMU por grau) | Condução | Transições |
|---|---|---|---|
| ar | 0,1 | baixa (mais convecção para cima) | carrega pressão e deixa escapar para a atmosfera |
| água | 1 | média | ferve acima de 0,1 (muda com a pressão) → vapor; congela abaixo de −0,02 → gelo |
| gelo | 0,9 | alta | derrete acima de 0 → água |
| neve | 0,3 | baixa | derrete acima de 0 |
| lava | 3 | média | nasce a 15; abaixo de 10 solidifica: **obsidiana** se esfriou de repente (≥0,1 por tick), **basalto** se devagar |
| pedra, terra, areia, outros sólidos | 0,6–0,8 | média | guardam calor (pedra quente + água = vapor); derreter pedra e vitrificar areia continua no `HeatSpots` por enquanto |

Mudar de fase **custa calor latente**: derreter um gelo consome 1 UMU, ferver um bloco de água consome 5. Por isso
água apaga fogo: ela come o calor ao virar vapor. Cada UMU fervida vira 1 de pressão de vapor (um bloco inteiro de
água = 5), e o calor que sobra num bloco que virou vapor é gás quente, que se expande (0,4 de pressão por UMU).

## As leis

1. **O calor flui do quente para o frio** entre blocos vizinhos, freado pela condução e pela capacidade de cada um. Ar
   quente sobe (convecção). Toda célula perde um pouco para o ambiente.
2. **A pressão flui da alta para a baixa** entre blocos de gás (e água fervendo). No ar aberto ela escapa para a
   atmosfera. Pressão acima de **30** num ponto (a bomba do `AirPressure`) explode.
3. **Fase por temperatura e pressão.** Acima do ponto de fusão/ebulição o calor que sobra vira calor latente; completo o
   latente, o bloco muda. Água fervendo solta vapor, e **vapor é pressão**. O ponto de ebulição cai com a pressão
   (no vácuo a água ferve fria) e sobe com ela.
4. **Combustão = combustível + oxigênio.** Sem ar não há fogo; vento sobre o fogo o alimenta; vento forte sobre chama
   pequena a apaga.
5. **Chuva e carga.** Gotículas caem um bloco por vez (metade por tick); no chão empoçam, e cada 3 UMU vira um bloco
   de água, ou de **neve** se o ar estiver congelando. Caindo na água, se juntam a ela. No ar **congelando e agitado**,
   gotículas e o gelo em que elas congelam se chocam e separam carga, tirando energia da agitação (é a fricção de uma
   nuvem de tempestade). Água morna não carrega: precisa de gelo e água juntos. Carga vizinha que soma **4** descarrega
   como relâmpago no chão abaixo (dano 5 + 5·log10(carga/4)); sem chão em 64 blocos, é um clarão no ar. Fluxo de
   pressão também agita o ar.
6. **Gravidade**: a densidade atrai (já existe, é o poço e o buraco negro).

## Como os spots escrevem no estado

- **`HeatSpots`**: a energia do golpe entra no estado **sem ser criada de novo**: metade no contato, nos blocos a
  até 1,5 do ponto, no instante do golpe; a outra metade é irradiada, a cada varrer, conforme o spot esfria. Cada bloco
  de matéria no alcance recebe sua parte e não passa de `calor × (1 − distância/alcance)`. As regras fixas de gelo,
  neve e água saíram do `HeatSpots`; quem decide agora são as leis. A lava que ele derrete acorda no estado físico e
  solidifica quando o spot some. Areia → vidro e pedra → lava continuam no `HeatSpots`.
- **`AirSpots`** (passo 3): o vácuo segura a pressão em −0,5 enquanto dura; a rajada e a bomba empurram pressão.
- **`WaterSpots`**, **`EarthSpots`**: a água e a terra que eles põem já são blocos; entram nas leis como matéria.

## O que sai sozinho

| Encontro | O que as leis fazem |
|---|---|
| igni condensado numa água | calor → latente → vapor → pressão. Fervura lenta vira vapor que escapa; calor rápido sem saída **explode** (um golpe de calor 80 explode; um de 20 só solta vapor) |
| pouco igni em muita água | o calor some no latente, a água nem ferve |
| lava + água | a água ferve, a lava perde calor rápido → **obsidiana** |
| lava sozinha | esfria devagar → **basalto** |
| pedra aquecida + água depois | a pedra devolve o calor: vapor |
| vácuo + água (passo 3) | o ponto de ebulição cai abaixo do ambiente: ferve, esfria e **congela** |
| vácuo + fogo (passo 3/4) | sem oxigênio, apaga |
| gelo VII sozinho | borrifo morno + agitação: chove, sem carga |
| ar comprimido sozinho | ar gelado e agitado, sem gotículas: sem carga |
| gelo VII + ar comprimido no mesmo ponto e instante (40 + 40) | as gotículas congelam no ar gelado e agitado: **relâmpago**, e neve cai |
| gelo VII + ar comprimido fracos (10 + 10) | só uma nevasca |

## Ordem

1. **Estado físico e calor** (lei 1), com os spots escrevendo. *(feito)*
2. **Fases da água e da lava + vapor como pressão + explosão** (leis 2 e 3). As regras de água/gelo/neve saíram do
   `HeatSpots`. *(feito)*
3. **Corpos, contenção, gotículas, agitação e carga** (lei 5), e a página lida por colunas. *(feito)*
4. **Vácuo** no estado (ebulição no vácuo, congelamento).
5. **Combustão.**
6. **As emissões da cena escrevem no estado**, e o `SceneLaws` se aposenta.
7. **A onda de pressão como lei**: hoje o estouro ainda chama a explosão do Minecraft (que já é física: raios que
   perdem força em cada bloco conforme a resistência; por isso explosão dentro d'água não quebra nada).

Cada passo é testado com os feitiços que já funcionam (plasma com anel, meteoro, gelo VII, vácuo, buraco negro,
cadeias de vertere).

## Decisões em aberto

- Os números de capacidade, condução, latente e vapor são rascunho: ajustar jogando.
- Os círculos rúnicos e o grimório próprio do mod ficam para a reformulação da escrita.
