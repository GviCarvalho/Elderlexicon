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
