# Estudo: sistemas emergentes e magia modular

O objetivo é entender como outros jogos e mods fazem química, física e magia emergirem de poucas regras, e trazer o que
serve para o Elderlexicon. Os dois problemas que apareceram nos testes (o lançamento condensado e a falta de impacto)
são sintomas da mesma coisa: faltam regras gerais de interação, e o código trata cada caso à mão.

## 1. As referências

### Zelda: Breath of the Wild, o "motor de química"

- A Nintendo apresentou na GDC 2017 um motor de química ao lado do motor de física. Se a física calcula movimento por
  regras, a química calcula **estado** por regras.
- **Elementos** são o que não tem estado fixo: fogo, água, gelo, vento, eletricidade. **Materiais** são as coisas
  sólidas: árvores, pedras, o próprio jogador.
- São só três regras:
  1. um elemento muda o estado de um material (o fogo acende a árvore);
  2. um elemento muda o estado de outro elemento (a água apaga o fogo);
  3. um material não muda o estado de outro material.
- Com poucas regras e muitas combinações, os designers chamaram o resultado de **jogabilidade multiplicativa**: cada
  elemento que o jogador acrescenta multiplica as soluções possíveis.

**Para nós:** as leis devem falar de propriedades (inflamável, condutor, pesado, quente), nunca de coisas com nome. É o
que as `Qualities` começaram a fazer. Separar "o que age" (energia, os fluxos dos aspectos) de "o que sofre" (matéria)
deixa as regras poucas e claras.

### Noita

- Cada pixel do mundo é um material com propriedades próprias: densidade, se é líquido, se queima, no que se transforma
  ao queimar ou derreter.
- As reações ficam no `materials.xml`, com **reagentes, produtos e uma taxa** (a chance de acontecer a cada contato).
  - Lava derrete metal, ácido corrói o chão, vapor condensa em água, o ouro líquido transmuta tudo.
- As reações são escritas por **etiquetas** (tudo que é "meltable", tudo que é "[fire]"). O jogo expande isso em uma
  tabela concreta e sorteia pela taxa.

**Para nós:**
- Uma única propriedade numérica já gera muito comportamento. Só pela densidade, os líquidos se separam em camadas
  sozinhos.
- Uma **taxa**, em vez de "sempre acontece", deixa as reações vivas e menos binárias.
- Regras por etiqueta, e não por par de materiais, é o que mantém a tabela pequena.

### Create: Destroy (química emergente)

- Simula química no nível da molécula: termodinâmica, cinética de reação e um sistema expansível de **reações por grupo
  funcional**, que é o que permite o "potencialmente infinito".
  - Uma reação genérica vale para qualquer molécula que tenha o grupo (um álcool, qualquer que seja, reage do jeito de
    um álcool).
  - Poucas reações são específicas.
- A **Vat** é o recipiente. Ela guarda uma mistura com temperatura e pressão, e a temperatura ambiente vem do bioma. A
  centrífuga separa as fases gás e líquido.
- Tudo é dado, em JSON:
  - elementos com massa e eletronegatividade;
  - moléculas com estrutura e propriedades físicas;
  - reações com reagentes, produtos, catalisadores e cinética.
  - Quando a energia de uma reação é omitida, ela é derivada pela lei de Hess, de forma coerente com as outras.
- O próprio mod diz que raramente há um jeito certo de fazer as coisas: o jogador combina, transforma e extrai, e isso
  parece mais Minecraft do que seguir instruções.

**Para nós:**
- Reações escritas por **traço** (o grupo funcional), e não por substância. O equivalente nosso é a direção da mistura
  na rosa (seção 2): "tudo que é quente e úmido", não "vapor".
- A energia de cada reação vem de um **potencial coerente**, então nada aparece do nada. É a nossa L1 e a tensão da
  seção 2.
- Um **recipiente** com temperatura e pressão: a poça de matéria informe já é isso.

### Thaumcraft

- Seis aspectos primais (ar, água, fogo, terra, ordem, entropia) se combinam dois a dois em aspectos compostos, em
  camadas: terra com água dá vida. Todo item do jogo tem uma lista de aspectos.

**Para nós:**
- É o parente mais próximo da ideia do "código" de cada coisa.
- A combinação por tabela (terra mais água é vida porque a tabela diz) é justamente o que queremos evitar. A lição é
  manter o código de cada coisa, mas trocar a tabela de combinações por geometria.

### Ars Nouveau (magia modular)

- Um feitiço é uma **Forma**, depois um ou mais **Efeitos**, com **Aumentos** que modificam o glifo à esquerda deles.
  - A Forma decide como o feitiço sai da mão: projétil, toque, em si, raio.
  - Os Efeitos acontecem em ordem no alvo, ou onde o feitiço bate.
- Cada glifo tem um custo de mana.

**Para nós:**
- A separação entre **entrega** (como chega) e **efeito** (o que faz ao chegar) é exatamente o que falta no lançamento
  condensado.
- `igni quantum chronos 0 vocant iactare` deveria ser: o `vocant` junta o fogo, o `iactare` entrega, e o fogo condensado
  age **onde bate**. Hoje o `iactare` lança outra coisa.
- A nossa gramática já é parecida: fonte, verbo, filtro. Os filtros valem para o verbo seguinte, como os Aumentos.

### Hex Casting (magia modular)

- Magia como linguagem de programação de pilha. Padrões executam ações, que manipulam uma pilha de **iotas**: números,
  vetores, entidades, listas.
- Cada feitiço custa mídia (a mana deles). Sem mídia suficiente, versões antigas cobravam da vida; hoje o feitiço
  falha.
- **Contratempos** (mishaps) dão consequências concretas e visíveis ao erro: faíscas de uma cor por tipo de erro, lixo
  na pilha, itens arrancados da mão.
- Há um **alcance** de cerca de 32 blocos.

**Para nós:**
- **Vetores como valores de primeira classe.** Um ponto na rosa dos elementos é um vetor, e isso encaixa.
- Erros com **consequência e sinal visual** em vez de silêncio. É o que o espírito já faz, e dá para ir além.
- Limites claros de alcance e custo mantêm uma magia muito livre equilibrada.

## 2. A proposta: a rosa dos elementos

A ideia veio de você: um plano cartesiano em que cada elemento puxa para a sua ponta e a vis puxa para o centro. O mod
já tem os dois eixos (`Aspect`): o térmico, com fogo contra água, e o mecânico, com ar contra terra.

```
                aura (↑ expansão, leveza)
                     │
      névoa          │         relâmpago
   (aqua + aura)     │       (igni + aura)
                     │
aqua ◄──── frio ─────●───── calor ────► igni
 (coesão)           vis             (calor)
                     │
       lama          │          magma
   (aqua + firmo)    │       (igni + firmo)
                     │
                firmo (↓ massa, peso)
```

Cada porção de matéria ou de energia tem:

| Grandeza | O que é | De onde vem |
|---|---|---|
| **Posição** (x, y) | o caráter: x vai do frio ao calor, y do peso à leveza | fogo menos água, ar menos terra |
| **Tensão** (térmica, mecânica) | quanto dos opostos se anulou para chegar ali: energia guardada | o menor de cada par de opostos |
| **UMU** | quanto há | como hoje |
| **Estado** | como está (sólido, líquido, gás, plasma) | a escada, como hoje |

Posição e tensão guardam as quatro partes por inteiro: é uma troca de coordenadas, não uma simplificação. O vapor tem
muita tensão térmica, e a vis pura tem tensão máxima nos dois eixos. É por isso que vis condensada explode de forma
absurda.

### As regras (poucas, no espírito do Breath of the Wild)

1. **Misturar é somar puxões.** Duas porções fluidas no mesmo lugar viram uma, com a posição média pelos UMU (como a L4
   já faz).
2. **Opostos se anulam e liberam a tensão.** O que se anula vira energia solta, com força proporcional ao que se anulou:
   - fogo com água é explosão de vapor;
   - ar com terra é estilhaço e onda de pressão.
   
   Isso substitui a separação em gás da L5 atual, e junta as "meetings" antigas do sistema de cenas numa lei só.
3. **Impacto é física.** O que é empurrado tem massa (UMU vezes o peso, o lado da terra) e velocidade (pela energia do
   `iactare`). Ao bater, a energia cinética vira:
   - dano;
   - quebra de blocos mais fracos que ela, pela dureza do bloco;
   - som, com volume e tom pela energia.
   
   O caráter da coisa age junto: o que é quente queima onde bate.
4. **Intensidade é distância do centro vezes concentração.** As formas extremas que já existem viram regiões da rosa em
   intensidade alta: plasma, poço de gravidade, buraco negro, gelo VII, bomba barotérmica, a explosão de vis. O código
   delas continua; o que muda é que elas passam a ser escolhidas pela posição e pela intensidade.
5. **A vis puxa para o centro.** Somada a uma mistura, ela neutraliza, como um catalisador. Solta de uma vez, libera
   tensão pura.
6. **Entrega e efeito são separados** (lição do Ars Nouveau). O verbo de força entrega, e o que é entregue age onde bate,
   seja matéria, esfera condensada ou fluxo. Isso corrige o lançamento condensado.

### A aparência

Cada coisa natural tem um ponto na rosa e uma tensão, que são o código dela. Uma mistura aparece como a coisa mais
próxima (como hoje, com os 5 pontos de tolerância) ou como matéria informe da cor da sua posição. O grimório pode
desenhar a rosa: onde a mistura está e para onde cada runa a puxa. O `surgit` poderia revelar o ponto de uma coisa.

## 3. Perguntas antes do plano

1. **A escada de estados continua separada da rosa?** Fogo e terra são vizinhos na rosa, mas ficam em pontas opostas da
   escada. Minha proposta é: o estado é "como a matéria está", e a rosa é "o que ela é".
2. **Quanto o impacto pode destruir?** Quebrar blocos pela energia é o mais emergente, mas um feitiço forte pode abrir
   crateras. Uma opção de configuração ("magia quebra blocos: sim, não, só com a regra de griefing do mundo") resolve
   para servidores.
3. **As reações devem ter taxa (sorteio) ou ser sempre iguais?** A taxa, como no Noita, deixa mais vivo. Sempre igual,
   deixa mais previsível para quem estuda.

## 4. Etapas sugeridas

1. **Parte pura:**
   - a rosa (posição e tensão a partir da composição);
   - a energia liberada quando opostos se anulam;
   - a energia de impacto (massa e velocidade) e a quebra pela dureza.
2. **Mundo:**
   - um resolvedor único de impacto (dano, blocos, som, e o caráter da matéria agindo);
   - a entrega da esfera condensada ao verbo de força.
3. **Unificar:**
   - as "meetings" do sistema de cenas e a L5 viram a regra 2;
   - as formas extremas viram regiões da rosa.
4. **Grimório:** o desenho da rosa e a leitura pelo `surgit`.

## Fontes

- Breath of the Wild, motor de química (GDC 2017):
  [Thumbsticks](https://www.thumbsticks.com/gdc-17-breath-of-the-wild-science-lies/),
  [GamesBeat](https://gamesbeat.com/the-legend-of-zelda-breath-of-the-wild-makes-chemistry-just-as-important-as-physics/),
  [Cheat Code Central](https://www.cheatcc.com/articles/breath-of-the-wild-s-chemistry-engine-explained/)
- Noita: [Alchemy](https://noita.wiki.gg/wiki/Alchemy),
  [Table of Alchemical Reactions](https://noita.wiki.gg/wiki/Table_of_Alchemical_Reactions),
  [Making a custom material](https://noita.wiki.gg/wiki/Modding:_Making_a_custom_material)
- Create: Destroy: [Modrinth](https://modrinth.com/mod/destroy), [GitHub](https://github.com/NHblock714/Destroy),
  [Functional groups](https://destroymod.miraheze.org/wiki/Functional_groups)
- Thaumcraft: [Aspects (FTB Wiki)](https://ftb.fandom.com/wiki/Aspects_(Thaumcraft_4))
- Ars Nouveau: [Spell Casting](https://ars-nouveau.fandom.com/wiki/Spell_Casting),
  [Glyphs](https://ars.guide/1.21.1/docs/spell_theory/glyphs/)
- Hex Casting: [documentação](https://hexcasting.hexxy.media/v/0.11.4/1.0/en_us/)
