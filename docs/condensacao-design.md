# Condensação: a intensidade dos elementos

**Status:** **os quatro elementos estão implementados** (26/09/2026). As interações entre eles ainda são só desenho. A seção "O que já existe"
lista as peças do mod que este desenho aproveita.

## A lei

A lei vem do livro, cap. 3: *"a energia nunca pode ser criada e nunca pode ser destruída; ela apenas muda de forma"*.
Toda UMU manifestada precisa vir de algum lugar.

Toda energia de um elemento é **intensidade × extensão**, como na física, onde se multiplica uma grandeza intensiva
por uma extensiva:

| Elemento | Intensidade (a qualidade de cada unidade) | Extensão (quantas unidades) | Na física |
|---|---|---|---|
| firmo | densidade: quantos kg cabem num bloco | blocos | massa = densidade × volume |
| igni | calor: quão quente é a chama | chamas ou golpes | calor = temperatura × massa |
| aura | pressão: quão forte é o vento | volume de ar | energia = pressão × volume |
| aqua | pressão: quantos litros estão num ponto | pontos ou blocos | energia = pressão × volume |

**UMU = intensidade × extensão**, mais o custo das runas.

## A regra: a intensidade é conquistada, não escrita

- **O `quantum` é sempre extensão.** Ele diz *quantas unidades*, nunca *quão intensas*. **Não existe `quantum` antes
  da fonte para dar intensidade.** Se bastasse escrever para ter um fogo fortíssimo, a técnica de juntar o elemento não
  teria propósito.
  - O `quantum` antes da fonte continua existindo **na revelação** (`quantum 2 firmo surgit`), mas lá ele *mede* o que
    se procura e não cria nada.
- **Sem captura, a intensidade é 1, o elemento comum:** chama comum, terra solta, água parada, brisa. A energia vem do
  corpo. `igni quantum 10 iactare` = 10 unidades × 1 = 10 UMU, como no livro (4.3.2).
- **Com captura, o que foi puxado é entregue com a intensidade que conquistou:**
  - `igni exsugat quantum iactare`: tudo o que foi puxado, espalhado pelas próprias chamas. O fogo guarda o calor
    **médio** do que foi capturado: o anel de 44 fogos comuns continua comum (calor 1), e uma poça de lava continua
    quente (calor 10).
  - `igni exsugat quantum chronos 0 iactare`: tudo o que foi puxado, **solto num único instante**. O `chronos` muda "o
    instante de sua manifestação" (4.3.2), e com zero toda a energia vai num golpe só: **calor = toda a energia
    capturada**. O anel de 44 UMU vira um golpe de calor 44.
  - O número depois do exsugat continua sendo **quanto tirar**, como no livro (9.2: `igni exsugat quantum 3 iactare`
    absorve "uma pequena quantidade de calor").
  - O `chronos 0` só concentra quando há captura. Com a energia do corpo, ele solta tudo de uma vez, mas em fogo comum.

### O `quantum` sem número: toda a energia disponível da fonte

O `quantum` sem número quer dizer **"toda a energia disponível daquela fonte"**. O que muda é de onde ela vem:

| Escrita | De onde vem |
|---|---|
| `fonte exsugat quantum …` | todo aquele elemento **ao alcance, no mundo** |
| `fonte quantum …` (sem exsugat) | todo aquele elemento **no corpo do mago**: o Vita dele (água ~55, ar ~38, terra ~5, fogo ~2, equilibrado) |
| `vis quantum …` | toda a **mana** do mago, que no mod é a experiência: 10 XP = 1 UMU, **sem limite** (a Âncora dos Cem do livro não existe no mod) |

- O espírito é literal: tira tudo, e o corpo vive com o que sobrou. O desequilíbrio do Vita segue o curso dele até o corpo
  voltar ao equilíbrio sozinho.
- Com `chronos 0`, isso também é condensado: `firmo quantum chronos 0 iactare` lança os ossos do mago condensados numa
  pedra; `vis quantum chronos 0 iactare` lança toda a mana dele numa esfera.
- **A vis condensada é energia pura.** Na física real, a junção equilibrada dos quatro estados da matéria (sólido,
  líquido, gás, plasma) é a energia antes de ter forma, cujo análogo mais próximo é a **luz**. Por enquanto ela é só
  luz: onde cai, há um clarão, e o lugar fica iluminado por 2 s + ln(1 + UMU) s, no máximo 30. A esfera é violeta e
  embranquece conforme a mana se junta. O efeito coerente com a física fica para depois.

### Exemplo: o anel condensado

```
página 1:  igni eu ubis quantum 40 chronos 30 vocant
           igni eu ubis quantum 40 chronos 30 impediunt
página 2:  igni exsugat quantum chronos 0 iactare
```

O mago acende o fogo à sua volta e o empurra para formar o anel. Depois puxa o anel inteiro, condensa tudo num ponto e
lança. É um fogo muito mais quente que qualquer iactare escrito direto, e ele só existe porque o mago juntou esse fogo antes.

## Condensar vai contra a natureza

Na realidade, o calor quer se espalhar e a pressão quer escapar. Então:

1. **Condensar exige trabalho do espírito, que sai da própria energia condensada** (o corpo não paga): 10% da energia × (1 − 1/calor). Não custa nada quando
   nada é condensado e chega perto de 10% nos calores mais altos. Condensar o anel de 44 UMU custa ~4,3 UMU.
2. **O condensado esfria aos poucos.** Onde o golpe cai, ou onde o vocant invoca o fogo condensado, fica um **ponto
   quente**. O calor dele volta ao comum com uma constante de 3 s (calor = 1 + (calor₀ − 1)·e^(−t/3 s)). Enquanto
   esfria, ele faz o que o calor daquele momento consegue, cada vez num raio menor. Um golpe de calor 44 fica quente
   por uns 13 s.

## Juntar leva tempo (a carga)

Condensar é juntar, e juntar leva tempo. Uma condensação (`chronos 0` com captura) **carrega** antes de soltar o
resultado:

- **Duração:** 0,5 s + 0,04 s por UMU condensado, no máximo 5 s. 20 UMU levam ~1,3 s, o anel de 44 UMU ~2,3 s, e 100 UMU
  ou mais 5 s.
- **Durante a carga:** o que foi capturado continua fluindo das fontes até o ponto, e ali cresce uma esfera do
  elemento. Um som sobe de tom conforme ela se enche.
- **O ponto:** no iactare, logo à frente da mão do mago, acompanhando a mão. O disparo sai para onde o mago mira no fim
  da carga, então dá para mirar enquanto carrega. No vocant, o ponto é fixo, onde a coisa vai aparecer.
- **As fontes** são puxadas do mundo no começo da carga. Só o resultado espera por ela.
- **Fora da condensação**, as partículas voam direto ao destino (o ponto do vocant, a mão no iactare, o peito ao
  absorver) e o resultado é imediato.

**A esfera mostra cada etapa da conversão.** Com vários `vertere` (`vis quantum vertere igni vertere aura vertere
aqua vertere firmo chronos 0 iactare`), cada conversão da cadeia ocupa a sua fatia do tempo, proporcional às
qualidades que muda (vis → elemento leva só o mínimo). Em cada uma, a primeira metade treme no elemento atual e a
segunda refaz o próximo, que cresce até o total. Cada troca é marcada com um estalo.

## Cada elemento condensado

As faixas abaixo são **rascunho**, com números para discutir. Em cada faixa: o que aconteceria de verdade, e uma ideia
de como aparece no Minecraft.

### Fogo: juntar o calor de uma área num ponto (implementado)

| Calor | O que acontece | No jogo |
|---|---|---|
| 1 | chama comum | o fogo de hoje |
| 3 a 10 | chama branca e azulada | chamas de alma; derrete neve e gelo; cozinha os itens no chão como numa fornalha; areia vira vidro |
| 10 a 30 | **derrete a pedra** | perto do centro, pedra, terra e cascalho viram lava (a lava é o chão derretido, não o fogo); a água em volta evapora de uma vez |
| 30 ou mais | **plasma**, a matéria do raio e das estrelas; o livro fala do "calor do plasma" nos círculos (10.1.1) | onde golpeia, estoura com o estalo de um raio (explosão com fogo, força = calor/12, no máximo 5), faíscas elétricas |

- **Alcance:** 0,5·√calor blocos, entre 1 e 5.
- **Criaturas no alcance:** queimam por calor/2 segundos (no máximo 20) e levam 0,5 + 0,15·calor de dano (no máximo 8)
  a cada ¼ s. O mago é poupado, como em todo fogo que ele faz.
- **O centro** mantém uma chama acesa enquanto está quente.

### Terra: juntar massa num volume (implementado)

Aqui a intensidade **é o material**, porque densidade é matéria. A escala é a mesma que a revelação já usa: UMU por
bloco = dureza. Condensada (`chronos 0`), toda a terra capturada vira **um bloco só** com a densidade de tudo junto.
As fronteiras entre os materiais ficam na média geométrica das densidades vizinhas, já que a escala é logarítmica:

| Densidade | Material |
|---|---|
| até ~0,87 | terra |
| ~0,87 a ~2,1 | pedra (1,5) |
| ~2,1 a ~12 | ardósia (3) |
| ~12 a 50 | obsidiana (50) |
| 1000 ou mais | **buraco negro** (ver abaixo) |
| acima de 50 | **poço de gravidade**: obsidiana chorona que puxa criaturas e itens em volta; alcance 2 + √(densidade − 50), no máximo 12; a força cresce com a massa e cai com o quadrado da distância; relaxa para obsidiana comum com uma constante de 10 s, e para de puxar se for quebrado |

- **Carvão vira diamante.** Se ~90% do que foi capturado era carvão (minério = 1 carvão, bloco = 9) e a densidade passou
  de 3, o resultado são **diamantes**: um a cada 9 carvões, e o que sobra continua carvão.
- **Lançada (iactare), a terra condensada cai como um meteoro.** Abre uma cratera com força 1 + ln(densidade),
  no máximo 6, e o bloco fica lá.
- **Invocada (vocant)**, o bloco aparece onde o mago mira.
- **`quantum N chronos 0`** condensa só N UMU (o livro, 9.2: o número depois do exsugat é quanto tirar).
  **`quantum chronos 0`**, sem número, condensa **toda** a terra ao alcance. Na terra isso escava uma esfera de uns
  5 blocos em volta do ponto mirado, e o trabalho de condensar centenas de blocos pesa muito no corpo.

**O buraco negro.** Na física real, um bloco de 1 m só vira buraco negro com uma massa maior que a da Terra. A
experiência absurda do mundo de teste chega lá. A partir de densidade 1000 (20× a obsidiana, rascunho), a terra
condensada cai sobre si mesma:

- **Não é mais um bloco.** É uma esfera preta, que nenhuma luz deixa, com um disco de matéria incandescente girando em
  volta.
- **É um vórtice.** Tudo em volta espirala para dentro. A força cai com o quadrado da distância: quase nada na borda,
  muito forte perto do centro.
- **Criaturas e itens** são puxados de todo o alcance: 2 + 3·log₁₀(densidade/50) blocos, ~6 com 10³, ~14 com 10⁶,
  ~47 com 10¹⁷.
- **O chão** só dentro de 2/3 desse alcance, **de dentro para fora, camada por camada**. Cada bloco resiste pela
  dureza, pelo efeito de maré: só se solta se dureza × distância² ≤ 5% da densidade. Por isso a terra solta sai de
  longe e a obsidiana só colada nele. Os blocos soltos atravessam o que estiver no caminho, girando até o centro.
- **Ele se alimenta.** O que chega ao centro é engolido e soma a sua UMU à massa dele:
  - um bloco soma a dureza dele;
  - um item soma 0,1 por unidade;
  - a experiência soma o seu valor/10;
  - quem está vivo é despedaçado (4 de dano a cada meio segundo, que ignora armadura) e soma 5 UMU por ponto de vida
    arrancado.
- **Tamanho:** 0,35 + 0,15·log₁₀(densidade/1000) blocos. **Blocos arrancados por vez:** 2 + log₁₀(densidade/1000).
  Nada disso tem teto.
- **Evapora o tempo todo**, rápido como os buracos negros pequenos na radiação de Hawking (constante de 3 s). Só cresce
  se comer mais do que evapora. Quando cai abaixo de
  1000, vira o poço de gravidade (obsidiana chorona), que depois relaxa em obsidiana. Com a XP do mundo de teste
  (~10¹⁷), ele dura cerca de 1,5 min.
- **Lançado (iactare)**, voa como a esfera preta e se forma onde bate. **Invocado (vocant)**, forma-se no ponto.

### Água: litros num ponto são pressão (implementado)

A pressão é quantos UMU de água cabem num ponto. Uma fonte parada guarda 3. Condensada (`chronos 0`), toda a água
capturada vai para um ponto só.

| Pressão | O que acontece | Iactare | Vocant |
|---|---|---|---|
| até 10 | jato | um jato em linha reta até 20 blocos: empurra criaturas e itens, apaga o fogo delas e o fogo no caminho | estoura na hora |
| 10 a 30 | jato que **corta** | o jato também leva embora a terra mole do caminho (terra, areia, cascalho, argila, lama, neve, folhas, plantas), 1 bloco a cada 3 UMU, até 64 | estoura na hora |
| 30 ou mais | **gelo VII** | lança uma pedra de gelo de pressão (gelo azul) que voa como o meteoro e **estoura** onde bate | cria o gelo de pressão, que se mantém enquanto dura o `chronos` (2 s sem ele) e então **estoura** |

- **O gelo VII faz o que faria na realidade.** Ele só existe sob pressão. Quando a pressão acaba (o tempo termina ou o
  gelo é quebrado), ele se descomprime de uma vez.
- **O gelo VII despedaça o ambiente ao estourar:** a água volta ao próprio volume de uma vez, como uma caldeira que
  explode. A força é 1 + ln(pressão/10), no máximo 6, e a água enche a cratera que abriu. A água que não chegou a virar
  gelo (abaixo de 30) só empurra e inunda.
- **O estouro:** tudo em volta é arremessado para longe (alcance 1 + √pressão/2, entre 2 e 8 blocos) e **toda a água
  comprimida volta**: uma fonte a cada 3 UMU, espalhada a partir do ponto, primeiro para baixo e depois para os lados.
  É a conservação: os litros que entraram são os que saem.

### Ar: vácuo e pressão (implementado)

**Capturar ar cria vácuo.** O `aura exsugat` puxa os blocos de ar mais próximos da mira, cada um valendo 0,5 UMU
(rascunho). Onde o ar foi puxado fica um **vácuo** por 2 s:

- **Sem ar para respirar:** criaturas com a cabeça no vácuo perdem o fôlego depressa e depois sufocam, como embaixo
  d'água. O mago é poupado nos blocos que o próprio corpo ocupa.
- **Nada queima:** o fogo apaga nos blocos e nas criaturas.
- **O ar volta de uma vez:** puxa tudo em volta para onde o vácuo estava e fecha com um estrondo de trovão, que é o
  mesmo que o raio faz com o ar que ele rasga.
- **As consequências mais devastadoras** (vácuos enormes com o `quantum` sem número, a pressão nos corpos) ficam para
  depois, como o usuário pediu.

**Ar condensado é pressão.** Condensado (`chronos 0`), todo o ar capturado vai para um ponto só:

| Pressão | Iactare | Vocant |
|---|---|---|
| até 3 | brisa: empurra de leve | lufada em volta do ponto |
| 3 a 10 | rajada: empurra criaturas e itens na linha da mira | rajada em volta do ponto |
| 10 a 30 | ventania: também arranca folhas, flores, plantas, vidro e tochas, e apaga o fogo | igual, em volta do ponto |
| 30 ou mais | **bomba de pressão**: detona onde a mira acerta | **bomba de pressão** no ponto |

- **A bomba:** explosão sem fogo, força 1 + ln(pressão/10), no máximo 6, mais uma onda de choque que arremessa tudo.
- **As faces térmicas** (o ar comprimido esquenta, o ar expandindo gela) ficam para a etapa das interações.

## O que já existe no mod

- **A captura** (`docs/exsugat-vertere-design.md`): o exsugat puxa fontes inteiras, com o valor de cada uma, e as
  funções seguintes gastam esse valor.
- **O `quantum` sem número depois do exsugat** puxa tudo ao alcance e entrega à função seguinte.
- **O anel** (`docs/impediunt-design.md`): o vocant alimentando e o impediunt empurrando formam o anel na hora, e o
  exsugat alcança o anel inteiro.
- **A escala da terra** (UMU por bloco = dureza) já é usada pela revelação e pelo exsugat.

O fogo já tem intensidade: `Heat` (as faixas, o alcance, o trabalho e o resfriamento) e `HeatSpots` (os pontos
quentes no mundo). Terra, água e ar ainda voltam ao comum quando são capturados.

## Tudo escala

Nenhum efeito tem teto. Até um ponto, cresce como antes; dali em diante cresce **devagar, pelo logaritmo**: dez vezes
mais UMU somam um pouco mais.
- **Alcance do calor:** 5 + 2·log₁₀(calor/100) acima de 100.
- **Explosão do plasma:** 5 + 2·log₁₀(calor/60) acima de 60.
- **Dano do fogo:** sem teto.
- **Meteoro:** 6 + 2·log₁₀(densidade/148).
- **Gelo VII e bomba de ar:** 6 + 2·log₁₀(pressão/1484).
- **Estouro da água e alcance do ar:** crescem igual.
- **Jato:** alcança 20 + 5·log₁₀(pressão/10) e corta sem limite.

Ficam dois limites, que são técnicos:
- **O empurrão de entidades** segue limitado a ~3,5 blocos/tick, o máximo que a rede transmite.
- **Uma varredura com mais de 20 mil blocos** (o calor num raio enorme, por exemplo) passa a ser feita por amostragem,
  com pontos diferentes a cada vez, para não travar o servidor.

## Decisões tomadas

- A condensação se escreve com `chronos 0`, não com o número do `quantum`.
- Condensar tem custo próprio (o trabalho do espírito).
- O condensado esfria aos poucos.
- Começou pelo fogo; depois terra, água e ar, nessa ordem.
- O trabalho de condensar é 10% da energia × (1 − 1/n), com n = quantas fontes foram fundidas numa só.

## Decisões em aberto

1. **Os limites das faixas:** os números acima (3, 10, 30) ainda são rascunho, a calibrar jogando.
2. **Custo das funções:** hoje um iactare de 10 UMU custa ~3 ao corpo, com custos base fixos por runa. Pela lei do
   cap. 3 seria 1:1 (10 UMU de efeito = 10 UMU pagos). Isso fica para uma etapa separada.
3. **As consequências do vácuo** em grande escala.
4. **As interações entre elementos condensados:** o ar comprimido com fogo, e o ar expandindo com água.
