# Plano: força, transferência e matéria

Plano aprovado para a segunda fase do rework da magia. Ele continua o `magia-modular-design.md`.

**Pilares:**
- **Modular:** tudo o que é conhecimento (runas, substâncias, receitas, blocos) fica em dados.
- **Emergente:** o código só tem regras gerais. Nenhum feitiço, substância ou criatura é programado.

## 1. O modelo da matéria

Toda porção de matéria tem três coisas:
- uma **substância**, que é o que ela é;
- um **estado**, que é como ela está: um degrau da escada;
- uma quantidade de **UMU**.

**A escada de estados**

firmo (sólido) → aqua (líquido) → aura (gasoso) → igni (plasma)

A vis fica fora da escada: é energia sem forma, a do corpo e da XP.

**Substâncias primordiais.** São uma por estado, e são os próprios quatro elementos:

| Substância | Estado natural |
|---|---|
| terra | sólida |
| água | líquida |
| ar | gasoso |
| fogo | plasma |

**Substâncias compostas.** Cada coisa natural (bloco, item ou entidade que não se crafta) tem uma **receita**: a
proporção de primordiais que a forma.
- As receitas ficam em dados e **ficam escondidas**. O jogador descobre por tentativa e erro; o `surgit` mostra estado e
  UMU, nunca a receita.
- As coisas craftáveis ficam fora da tabela: saem do crafting a partir dos materiais.
- As fusões de fonte que já existem viram as **primeiras receitas conhecidas**, que o livro ensina:

  | Fusão | Receita | O que é |
  |---|---|---|
  | `lutum` | terra + água | lama |
  | `pulvis` | terra + ar | pó |
  | `nebula` | água + ar | névoa |
  | `caligo` | água + fogo | vapor |
  | `fusus` | terra + fogo | magma |
  | `fulmen` | ar + fogo | raio |

**Tabela de materiais** (`materials.json`). Diz qual bloco, item, fluido ou partícula do Minecraft representa cada
substância em cada estado:
- pedra: sólida = `stone`, líquida = `lava`;
- água: sólida = `ice`, líquida = `water`, gasosa = partículas de vapor.

Se falta uma entrada, a tabela usa o estado vizinho mais próximo. Blocos de addons entram adicionando linhas.

**Leis da matéria**, que ficam no código e valem para qualquer substância:

| Lei | O que diz |
|---|---|
| **L1 · Conservação** | Nenhum verbo cria ou destrói UMU, só move, empurra ou muda de estado. |
| **L2 · Estado** | O `vertere` sobe ou desce a escada e mantém substância e UMU. O custo é proporcional aos degraus. Sair de vis é grátis; voltar para vis custa. |
| **L3 · Corpo** | O corpo guarda energia, não matéria. Absorver desfaz a substância e o UMU entra no vita, no estado em que estava. O que sai do corpo sem receita vira a substância primordial do estado (`firmo vocant` dá terra). |
| **L4 · Mistura** | Porções de matéria **fluida** (qualquer estado menos sólido) no mesmo lugar se misturam. Se a proporção bate com uma receita, dentro de ±5%, a mistura vira aquela substância. Sólidos não se misturam; se juntam por `ligabis`. |
| **L5 · Amálgama** | Uma mistura que não bate com nenhuma receita vira um amálgama instável, que com o tempo se desfaz de volta nas primordiais, sem perder UMU. |

## 2. Vocabulário

**Verbos**

| Runa | Ação primitiva | Negativo |
|---|---|---|
| `vocant` | transfere o sujeito para um ponto | do ponto para o conjurador (absorver ou trazer) |
| `iactare` | força sobre o sujeito, em direção a um ponto | a força ao contrário |
| `impediunt` | força sobre a área, para longe de um centro | atrai para o centro |
| `vertere` | muda o estado do sujeito (L2) | — |
| `ligabis` | liga uma propriedade entre coisas marcadas | — |
| `surgit` | percebe estado e UMU, e faz ver | — |
| `reframe` | dá nome a um feitiço; uma receita descoberta vira uma runa assim | — |

**Fontes:** `igni`, `aqua`, `aura`, `firmo` (cada uma nomeia um estado) e `vis`.

**Filtros:** `quantum` (agora com sinal), `chronos`, `ubis` e o novo `tenet` (glifo `F`, que era do `exsugat`).

**Saem:**
- `exsugat` e `transvocatio`;
- as fusões `orbis`, `exhaustio`, `exsuctio`, `extractio` e `exinanitio`.

## 3. Regras gramaticais

- **R1.** Um filtro vale para o verbo logo depois dele.
- **R2. Sujeito.** É a última coisa nomeada antes do verbo: uma fonte, uma marca, ou o que o verbo anterior produziu ou
  moveu.
- **R3. Para onde.** É o `ubis` antes do verbo ou, sem ele, o ponto mirado. O raio da mira atravessa o próprio sujeito.
  - `iactare`: a força vai do sujeito até esse ponto.
  - `impediunt`: esse ponto é o centro, que por padrão é o conjurador.
  - `vocant`: esse ponto é o destino.
- **R4. Sinal.** `quantum` negativo inverte os verbos marcados como `reversible` no léxico. Nos outros, o espírito
  recusa. O custo usa o valor absoluto.
- **R5. Origem.**
  - Sem `tenet`, a fonte vem do corpo.
  - `firmo tenet` pega a matéria sólida ao seu alcance, de qualquer substância.
  - `firmo m1 tenet` pega perto de m1: marcas e números antes do `tenet` dizem de onde.
- **R6. Força numa fonte que ninguém materializou** (`igni iactare`). A energia sai como um fluxo do estado:

  | Fonte | Fluxo |
  |---|---|
  | `igni` | onda de calor |
  | `aura` | rajada |
  | `aqua` | jato de pressão |
  | `firmo` | onda de choque no chão |
  | `vis` | empurrão puro |

  As leis de intensidade que já existem (calor, pressão, densidade) viram a física desses fluxos.
- **R7. `chronos`.** Com duração, a ação se mantém pelo tempo dado. Com `chronos 0`, concentra tudo num instante.

## 4. Como os feitiços antigos ficam escritos

| Antes | Agora |
|---|---|
| `igni iactare` (lança de fogo) | `igni vocant iactare` |
| `igni exsugat` (absorver fogo) | `igni quantum -10 vocant` |
| `firmo exsugat quantum iactare` | `firmo tenet quantum iactare` |
| `igni exsugat vertere aqua` | `igni tenet vertere aqua` |
| `igni exsugat vocant` | `igni tenet vocant` |
| `m1 exsugat` (puxar a coisa marcada) | `m1 quantum -10 iactare` |
| `aqua impediunt` (barreira no ponto mirado) | `aqua 10 ubis chronos 10 impediunt` |
| `m1 vocant iactare` | igual |
| `m1 transvocatio` (troca de lugar) | não existe mais |

Casos que passam a existir sem ninguém programar:
- `igni 10 40 50 ubis vocant iactare`: o fogo surge no ponto e voa até onde você mira.
- `m1 quantum -20 iactare`: puxa m1 até você.
- `firmo m1 tenet 5 ubis vocant`: tira terra de perto de m1 e a põe 5 blocos à frente.
- `aura quantum -30 chronos 5 impediunt`: puxa o ar para você durante 5 segundos, um vórtice.

## 5. O que quebra

Feitiços antigos com `exsugat`, `transvocatio` ou as fusões removidas deixam de funcionar, e o espírito diz o que
escrever no lugar. `igni iactare` e `aqua impediunt` mudam de sentido.

## 6. Etapas

Cada etapa é um commit no PR, com os testes passando.

1. **Léxico e gramática** (parte pura):
   - tirar e pôr as runas da seção 2;
   - `quantum` com sinal e flag `reversible`;
   - sujeito vindo do verbo anterior;
   - operandos no `tenet`, e a captura do fluxo passando a sair dele;
   - catálogo de feitiços reescrito.
2. **Modelo da matéria** (parte pura):
   - substância, estado, porção de matéria, receitas com tolerância;
   - leis L1 a L5;
   - `materials.json` e as receitas das fusões.
3. **Ponte com o mundo:** ler um bloco, item ou entidade como matéria, e colocar matéria no mundo, tudo pela tabela de
   materiais.
4. **Operações:**
   - `transfer` nos dois sentidos, absorvendo para o vita;
   - `push` e `radiate`, com os fluxos da R6;
   - `vertere` como mudança de estado no mundo e no corpo.
5. **Mistura no mundo:** L4 e L5, inclusive o amálgama que se desfaz.
6. **Limpeza:** remover os handlers de exsugat e transvocatio, e atualizar textos, grimório e documentação.
7. **Verificação:** `./gradlew build` e os testes, mais um servidor com um jogador falso lançando os feitiços de
   exemplo, inclusive a criação de pedra por mistura.

## 6.1 Como a etapa 2 ficou

O modelo da matéria fica em `magic/matter`, sem nada de Minecraft, e os dados em
`src/main/resources/data/elderlexicon/lexicon/materials.json`.

- **A substância sai da composição.** Uma porção de matéria é só composição (a parte de cada primordial), estado e
  UMU. O que ela é não fica escrito nela: é a receita que a composição bate (`MaterialTable.identify`). Assim, matéria
  misturada na proporção certa vira aquela substância sem nenhuma regra para aquela substância.
- **As palavras do livro batem com as receitas.** A essência de cada fonte do léxico é uma composição: `lutum` é lama,
  `fusus` é magma, `igni` é fogo. A vis e a vita não batem com nenhuma, porque são energia, não matéria.
- **O UMU por bloco é da substância, não do estado** (`unit`). Assim um bloco de pedra derrete em um bloco de lava, e uma
  fonte de água congela em um bloco de gelo.
- **Mudar de estado mantém a matéria e o UMU.** O trabalho do espírito, 5% por degrau (a escada inteira para voltar a
  vis), é pago por quem muda o estado. Assim a conservação (L1) vale também na mudança de estado.
- **Numa mistura, o estado é o da maior parte do UMU** (o mais denso, se empatar).
- **Um amálgama se desfaz nas primordiais, cada uma no estado em que é encontrada.**
- **Cada receita tem uma zona só dela.** Duas receitas precisam estar a pelo menos 0,10 uma da outra em alguma parte,
  para que nenhuma mistura seja as duas. Com a tolerância de ±5%, cabem por volta de 280 receitas distintas. Se um
  dia faltar espaço para todas as coisas naturais, basta apertar a tolerância: com ±2,5% cabem por volta de 1.770.
- **Estado sem forma na tabela:** usa o estado vizinho mais próximo. Um gás ou plasma mostrado por um bloco ou item
  aparece como as partículas dele, porque o que flutua não é bloco.
- **Addons** trazem substâncias com um arquivo no mesmo formato: `Materials.extend(tabela -> tabela.read(leitor))`. Uma
  extensão que quebre a tabela é recusada: receita perto demais de outra, bloco lido como duas coisas, falta de uma
  primordial.

## 6.2 Como a etapa 3 ficou

A ponte com o mundo fica em `spell/matter/WorldMatter`, e o plano de como a matéria entra no mundo em
`magic/matter/Placement` (sem Minecraft, testado à parte).

- **Ler:** um bloco é o que a tabela diz dele. Um fluido só é matéria na fonte, porque o que escorre dela é a mesma água.
  Um item é o que ele segura (ou o bloco que ele coloca), vezes a quantidade. Um item no chão é o item dele. O que é
  feito (um baú, uma espada) não é matéria natural e não é lido.
- **Tirar:** ler um bloco e deixar o lugar vazio.
- **Colocar:** só blocos e itens inteiros. A partir do ponto, os blocos vão para os lugares livres mais próximos (ar,
  plantas, fluido escorrendo), nunca por cima de uma fonte de fluido, que também é matéria. Um gás aparece como
  partículas e vai todo para o mundo; ar solto se junta ao ar. Um amálgama assenta primeiro (L5) e cada parte é
  colocada como o que é. O que não faz uma unidade inteira, ou não acha lugar, sobra para quem colocou (L1).
- **Testes no jogo:** `./gradlew runGameTestServer` roda os GameTests (`gametest/MatterGameTests`) num servidor de
  verdade: pedra, lava e gelo lidos como matéria, pedra derretida colocada como lava, um amálgama assentando em terra
  e água, um bloco tirado, um gás solto.

## 6.3 Como a etapa 4 ficou

- **Escada no custo:** converter custa 5% por degrau (`firmo` sólido, `aqua` líquido, `aura` gás, `igni` plasma).
  Sair de vis não custa nada; voltar a vis custa a escada inteira (3 degraus). `igni` para `firmo` passou de 1 para 3.
- **R2 no mundo:** quando o verbo seguinte age sobre o resultado (o grimório marca isso como `handsOn`), o `vocant`
  entrega o que faz em vez de largar no ponto. A regra de onde ele surge é uma só: **no `ubis` do `vocant`, ou, sem ele,
  na mão de quem conjura**, já que é de lá que a matéria sai do corpo. Sem verbo depois, continua surgindo onde se mira.
  - `iactare` empurra o produto para o ponto (o `ubis` antes dele, ou a mira); `impediunt` para longe do centro; com
    negativo, ao contrário. O produto voa como um orbe e, onde bate, faz o que faria no ponto: a terra se assenta, o
    fogo acende. A quantidade do verbo de força é a energia do empurrão: mais energia, mais rápido.
  - Assim `igni vocant iactare` é a lança de fogo, `igni 10 40 50 ubis vocant iactare` faz o fogo surgir lá e voar até a
    mira, e `firmo tenet vocant iactare` arremessa a terra tirada do chão.
- **Transferência:** o léxico marca o `vocant` com `"transfers": true`. Com `tenet`, ele não captura energia: tira os
  blocos do estado da fonte mais perto (de quem conjura ou da origem escrita), como são, e os põe no destino. Pedra
  continua pedra. O que não acha lugar volta ao corpo como energia do estado (L3).
- **`vertere` no mundo** muda o estado e mantém a substância: pedra vira lava, lava vira pedra, gelo vira água, água vira
  gelo ou vapor. O trabalho, 5% por degrau, é pago por quem conjura. Voltar a vis desfaz a matéria, e o que sobra do
  trabalho entra no corpo como vis. Se a substância não tem forma declarada no estado alvo e teria de virar bloco ou
  item (ferro líquido), o espírito recusa. Gás e plasma sempre se dispersam. Vale também para `m1 vertere aqua` num
  bloco marcado; criaturas continuam convertendo o vita como antes.
  - **Isso muda um feitiço antigo:** `igni tenet vertere aqua` não troca mais fogo por água. O fogo muda de estado e
    continua sendo fogo. Para fazer água, é preciso trazê-la do corpo ou do mundo (`aqua vocant`, `aqua tenet vocant`).
- **`impediunt` invertido** abre uma zona que puxa para o centro durante o `chronos`, em vez de manter fora. O ar
  carrega tudo o que está solto, então `aura quantum -30 chronos 5 impediunt` é um vórtice. Com outros elementos, puxa
  o que for daquele elemento.
- **Testes no jogo:** `gametest/SpellGameTests` lança feitiços de verdade com um mago falso (com um canal de rede que
  engole os pacotes). Os testes cobrem:
  - terra que surge 2 blocos à frente e voa até a parede;
  - o chão tirado e posto diante da parede;
  - pedra derretida em lava e água congelada em gelo;
  - o vórtice puxando uma pedra solta;
  - o sinal negativo recusado no `vertere`.

## 6.4 Como a etapa 5 ficou

- **Matéria informe:** um bloco novo (`formless_solid` e `formless_liquid`) guarda a matéria exatamente como ela é, com
  composição, estado e UMU, num block entity. A cor é a mistura das cores dos quatro aspectos: terra marrom, água azul,
  ar claro e fogo laranja. Ele aparece em dois casos:
  - **Uma substância num estado em que a tabela não dá aparência a ela**, sólido ou líquido: terra derretida, ferro
    derretido, fogo sólido. Isso substitui a recusa da etapa 4, e agora `firmo tenet vertere aqua` sobre terra dá terra
    derretida. Gás ou plasma sem aparência continuam virando partículas e se dispersam.
  - **Um amálgama**, a mistura que não bate com nenhuma receita. Ele treme, dura `amalgam.seconds` (20 s no
    `materials.json`) e depois se desfaz de uma vez, o corpo inteiro, nas primordiais, cada uma no estado natural (L5).
    O que não fecha um bloco inteiro se dispersa no ar.
  - O líquido não flui, nada colide com ele, a mira atravessa e os fluidos do jogo não o lavam. Um bloco quebrado ou
    explodido se assenta no que é, junto com o corpo de que fazia parte.
  - Um bloco informe guarda qualquer quantidade, então nada sobra. O amálgama ocupa o espaço que as partes ocupariam
    separadas: um bloco de terra derretida e uma fonte de água misturados continuam sendo dois blocos.
- **Derramar (L4):** matéria fluida que chega onde há matéria fluida (uma fonte, matéria informe líquida; nunca o ar
  aberto) se mistura com o corpo onde caiu, que são os blocos iguais ligados a ele, até 64.
  - Se a mistura continua sendo o que o corpo era (água na água, um pouco de água num lago de lava), o corpo fica e
    cresce.
  - Se não, o corpo é recolhido e a mistura é posta no lugar: uma substância, se bater com uma receita, ou um amálgama.
  - Vale para o `vocant` do corpo que cai dentro de um fluido (`aqua quantum 1 ... ubis vocant` numa poça de terra
    derretida) e para o que chega pela força (`igni vocant iactare` numa lava).
  - A matéria que uma fonte traz do corpo é a substância da sua essência, no estado natural (L3): `aqua` é água e
    `lutum` é lama.
  - Com `chronos`, o `vocant` continua temporário e não se mistura.
- **Transferir junta:** os fluidos que um `vocant` com `tenet` traz ao mesmo lugar se misturam antes de pousar (L4).
  Sólidos continuam separados.
- **O espírito conta o resultado:** quando a mistura vira outra coisa, ele diz o que virou ("A mistura virou
  ardósia."), ou que é um amálgama e em quanto tempo se desfaz. É assim que as receitas são descobertas.
- **Pedra por mistura**, que é o teste `stoneIsMadeByMixingThePrimordials`, com feitiços de verdade:
  1. `firmo quantum 16 X Y Z tenet vertere aqua` derrete 32 blocos de terra.
  2. Uma de água vira ardósia derretida; uma de ar vira um amálgama.
  3. Duas de fogo fecham a proporção da pedra (8 : 0,5 : 0,5 : 1). A mistura vira pedra derretida, que aparece como 13
     fontes de lava.
  4. `aqua quantum 19 X Y Z tenet vertere firmo` esfria a lava em 13 blocos de pedra.
- **Nota:** quando uma mistura vira uma substância que o jogo mostra como bloco próprio (lava, água), ela assume a
  receita exata dessa substância, porque o bloco não guarda a diferença dentro dos 5%. Só a matéria informe guarda a
  composição exata.

## 7. Segunda fase: próximo plano, depois desta

- **Corpos.**
  - Partes (osso, carne) juntadas por `firmo ligabis`, que já liga a integridade das coisas, formam um corpo.
  - Um corpo cuja proporção de partes bate com uma receita de corpo vira um corpo sem vida, como um monstro de
    Frankenstein, não um cadáver que volta.
- **Pseudo-vida.**
  - Um corpo com energia própria e um feitiço inscrito nele, que ele recita sozinho quando uma condição acontece.
  - O homúnculo que come fogo seria um corpo cheio de igni com a inscrição "quando o igni estiver baixo, absorver
    igni".
  - Isso pede um desenho próprio de gatilhos, que ainda não existe no mod.

## 8. Decisões tomadas

1. Escada de estados, não círculo.
2. Absorver desfaz a substância.
3. Receitas descobertas por tentativa e erro, nunca reveladas pelo `surgit`.
4. A mistura só acontece com matéria fluida; a tolerância das receitas é de ±5%.
5. As fusões de fonte viram receitas conhecidas.
6. `tenet` reaproveita o glifo `F`.
7. Pode quebrar feitiços antigos.
8. `quantum` sem número e negativo ("tudo ao contrário") fica para depois.
9. As fusões de verbo que sobraram (`transiectio`, `aversio` e as sem composição) ficam como estão e são revistas depois.
