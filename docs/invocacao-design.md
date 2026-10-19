# Mesa de invocação: a magia dá a matéria, a mesa dá a forma

Decidido com o autor em 16/10/2026. **Status:** primeira versão implementada em 16/10/2026 (seção 9); a gravação de
galdraria em blocos postos (5.2) e o pagamento por vínculo (6) ainda não.

O livro diz que uma espada não tem código (cap. 2): a siglística sabe dizer do que ela é feita, mas não sabe fazê-la.
Isso seria da Forma, a quinta família, que ninguém trouxe de Nahvá (cap. 4). A mesa de invocação não resolve esse
mistério; ela o contorna. **A forma vem das mãos, num molde; a matéria vem do feitiço.** Os mestres da invocação não
sabem a Forma: eles a emprestam de uma mesa de trabalho.

Com ela voltam os **feitiços com forma** que o mod perdeu quando as formas fundidas (`hasta`, `murus`...) saíram da
língua (docs/magia-modular-design.md, nota de 30/09/2026): uma lança de fogo volta a ter o formato de uma lança, mas
agora feita pelas runas que existem, sem runa nova.

**Nada aqui é predefinido além da própria matéria.** Não há frase para fazer madeira nem receita de espada de fogo: há
a composição exata de cada coisa e os formatos da mesa, e o resto o mago escreve.

## 1. A mesa

- **Não tem receita.** Uma mesa de trabalho comum vira mesa de invocação quando o mago **marca o grid dela**: com o
  **buril** à mão, clique direito na mesa de trabalho (seção 9).
- O mago dá **uma marca a cada casa**, escrita à mão, como em qualquer outra marca do mod
  (as regras de docs/marks.md: minúsculas, `_`, nada de runa). Não há marcação automática: nomear as casas é parte do
  ofício, como nomear qualquer outra coisa.
  - Não é preciso marcar as nove; uma casa sem marca só não recebe invocação (ainda aceita item à mão).
  - Tirar todas as marcas devolve a mesa de trabalho comum.
- **Por fora, é quase a mesma mesa**: a mesa de trabalho com o tampo riscado pelo buril (as nove casas gravadas). Nada
  que se veja de longe.
- O grid funciona como o de uma mesa de trabalho: pôr e tirar itens à mão, resultado na saída.

## 2. Encher uma casa

O exemplo do autor, com casas marcadas `g1` a `g9`:

```
vis quantum 30 vertere igni g2 ubis vocant
vis quantum 30 vertere igni g5 ubis vocant
... g8 ubis vocant
```

### 2.1 Pela matéria (o jeito principal)

- **Cada coisa tem um código exato**: quantas partículas de fogo, de terra, de água e de ar ela tem
  (docs/particulas-design.md). É isso, e só isso, que a define: um bloco de terra, um de ferro, uma tora, um vidro, um
  lingote, um graveto.
- O `vocant` com `ubis` numa casa põe matéria nela, e a casa vê **qual coisa tem exatamente aquela composição**. Bateu,
  a casa vira aquela coisa (as partículas do ferro viram um lingote; as da madeira, um graveto).
- **Não bateu** com coisa nenhuma: a casa guarda a matéria como ela é (fogo puro, água solta, uma mistura sem nome). É
  assim que uma lâmina de fogo existe: fogo puro na casa, sem pretender ser item de jogo nenhum.
- Não há frase pronta para nada: chegar às quantidades certas com as runas que existem é o que o mago tem de saber
  fazer. Conhecer os códigos das coisas é o conhecimento que separa um invocador de um aprendiz.
- Criar do nada custa pela **massa**: um lingote é 1/9 de um bloco, e um diamante custa muito mais que um graveto.

### 2.2 Por um item marcado

`lingote_a g2 ubis vocant` traz para a casa um item que já existe. É menos bonito (não cria nada; é trabalhar à
distância), mas é coerente com o que a língua já faz, e fica.

## 3. Formatos: a mesma coisa, de outro material

**Trocar o material principal de uma coisa por outro faz a mesma coisa, desse outro material.** A receita de uma
picareta de ferro com água no lugar do ferro faz uma picareta de água. Parece besta, mas o sistema permite, então o mod
também.

- A receita é lida por **papéis**, e não por itens. Cada formato tem as suas casas, e um papel é o **principal** (o
  material daquilo): a lâmina da espada, a cabeça da picareta e do machado, a ponta da lança e da flecha.
- **Se a matéria de cada papel é a de uma receita do jogo** (ferro na cabeça, madeira no cabo), sai o item do jogo: uma
  picareta de ferro comum.
- **Se não é** (água na cabeça, madeira no cabo), sai o **item conjurado** daquele formato: uma picareta cuja cabeça é
  água. É um item por formato, e a composição de cada papel fica no NBT. O que ele faz sai da composição, pelas mesmas
  leis da matéria que valem para o resto do mod (o fogo queima o que toca, a água encharca...), sem lista de efeitos.
- O que é modular no código: **um formato conhece só os seus papéis, e uma matéria conhece só as suas qualidades.**
  Qualquer partícula enche qualquer papel; nenhuma combinação é programada.

### 3.1 Os formatos

| Formato | Origem | Papel principal |
|---|---|---|
| espada | receita do jogo | lâmina |
| picareta, machado, pá, enxada | receitas do jogo | cabeça |
| flecha | receita do jogo | ponta |
| escudo | receita do jogo | proposta: o corpo (as tábuas) |
| lança | formato do mod (o jogo 1.20.1 não tem lança) | ponta |
| cubo, disco, esfera | formatos do mod | a casa toda (são só matéria com forma) |

- Os formatos do mod precisam de um desenho no grid. Proposta, a decidir:
  - **cubo:** as nove casas cheias;
  - **disco:** a linha do meio;
  - **esfera:** a cruz (`_X_ / XXX / _X_`);
  - **lança:** a ponta num canto e o cabo na diagonal até o canto oposto.
- A lista fica aberta: um formato novo é um desenho e os seus papéis, e nada mais.

## 4. Volátil ou estável

- **Matéria sólida fica** (a massa vence: sólido, docs/particulas-design.md). Uma espada de ferro invocada é ferro de
  verdade e dura para sempre.
- **Matéria que não é sólida é volátil.** Uma lâmina de fogo puro dura **2 segundos** se o feitiço não diz o tempo.
  - **Os 2 segundos contam da entrega**, não da criação: quando a mesa põe o item na mão de alguém, ou quando o lança e
    ele chega ao alvo. Enquanto está na mesa, esperando, não se desfaz.
  - Com `chronos`, dura o tempo escrito, e custa por isso: segurar o fogo com a forma de uma lâmina é uma torneira
    aberta, como todo feitiço estendido.
  - **No estado de fluxo** (docs/fluxo-design.md), a arma pode ser mantida enquanto o mago sustenta o fluxo, pagando a
    cada segundo.
  - Quando o tempo acaba, a parte volátil se desfaz (a lâmina vira fogo solto onde está), e o que era sólido fica (o
    cabo de madeira cai).

## 5. As duas gravações

São duas coisas diferentes, e não se confundem.

### 5.1 A gravação da mesa de invocação (na tela dela)

- A tela da própria mesa tem uma linha para um feitiço (até 10 colunas, como as da galdraria).
- Ela é lida **quando um item fica pronto na mesa**, e o **sujeito é sempre o item criado**:
  - `ego ubis vocant`: o item aparece na mão de quem conjura;
  - `ferit ille ubis iactare`: o item é lançado no alvo do último golpe;
  - qualquer outra frase que faça sentido com uma coisa como sujeito.
- Uma mesa **marcada** (a marca da própria mesa, não a das casas) pode ser disparada de longe, como qualquer coisa
  marcada (`mesa surgit`).
- Sem feitiço e sem marca, o item fica na saída e se tira com a mão.

### 5.2 A gravação de galdraria (em qualquer bloco)

- **Qualquer bloco** pode ser gravado na mesa de galdraria (docs/galdraria-design.md), seja uma mesa de invocação ou
  não. Essa gravação é do bloco, e não da invocação.
- Ela é lida **pelo gatilho que o próprio feitiço define**. O exemplo do autor: um bloco que se defende quando tentam
  destruí-lo, gravado só com os feitiços de defesa que já existem.
- Numa mesa de invocação, as duas convivem: a gravação de galdraria cuida da mesa (defendê-la, por exemplo), e a da
  tela cuida do que a mesa cria.
- **No código hoje:** a galdraria grava blocos em forma de item, mas não encontrei nada que leia a gravação de um bloco
  já posto no mundo, nem que a guarde quando ele é posto. Isso é trabalho à parte, e vale para todo bloco.

## 6. Quem paga

- Como nos rituais: **se a mesa não está vinculada à vis de ninguém** (nem de um alvo), **paga quem conjura**: quem
  invocou para as casas e quem disparou a gravação.
- Vinculada (`ligabis`), paga o dono do vínculo, onde quer que ele esteja.
- É o mesmo perigo dos rituais: uma mesa vinculada que alguém dispara em laço esvazia o dono.

## 7. Casa errada

- **Não sai formato nenhum.** Se o que está no grid não forma nenhum formato, o "item criado" é **o próprio conteúdo das
  casas, junto**: a gravação leva para a mão do mago, ou lança no alvo, a madeira e o ferro soltos que estavam lá.
- Não há explosão por padrão. O erro custa o que foi pago para invocar, e mais nada.

## 8. Em aberto

- **A tabela de códigos.** Cada item precisa da sua composição exata (fogo, terra, água, ar). Os blocos naturais já têm
  a sua (docs/particulas-design.md); faltam os itens (lingote, graveto, pederneira, pena...), e a regra para os que são
  feitos de outros (o lingote sai do bloco de ferro dividido por 9?).
- **Os desenhos dos formatos do mod** (cubo, disco, esfera, lança) e o papel principal do escudo.
- **Gravações em blocos postos** (seção 5.2): guardar a gravação quando o bloco é posto e lê-la pelo gatilho.
- **No livro.** A mesa é mecânica do mod; no livro, cabe como a arte dos moldes, ensinada como contorno da Forma, no
  último capítulo, sem resposta sobre a Forma em si.

## 9. Implementação (16/10/2026)

Pacote `invocation`. O que ficou decidido no caminho, e o que ainda não está:

- **A mesa** (`InvocationBenchBlock`, `InvocationBenchBlockEntity`, `InvocationBenchScreen`; tela refeita em 17/10/2026):
  clique direito numa mesa de trabalho com o **buril à mão** (em qualquer mão ou na hotbar) abre a mesa de invocação.
  É uma tela só, mais larga que a da mesa de trabalho: as marcas aparecem **em baixo-relevo** no fundo de cada casa,
  debaixo do que estiver nela, e a **linha** da mesa fica logo depois do item resultante, porque o resultado é o
  sujeito dela, entalhada também, em **dez células como as da página do grimório** (docs/grimorio-design.md): uma
  palavra por célula, Espaço passa à próxima, célula vazia é pausa, o nome da runa vira o glifo ao sair da célula, uma
  marca fica numa moldura de selo, sem guias de coluna. Com o buril à mão, clicar numa casa vazia (sem nada no cursor) grava a marca dela, e a
  linha pode ser escrita; sem o buril, as duas só se leem. Enter grava, Esc desiste. Cada marca nova e cada runa de uma
  linha nova gastam o buril em um. Uma palavra que não pode ser marca (runa, uma letra só, só números) ou a mesma marca
  em duas casas recusam tudo. Fechada sem nenhuma marca, sem linha e sem nada em cima, volta a ser mesa de trabalho.
  Quebrada, dá a mesa de trabalho e derruba o que está nela; as marcas se perdem.
- **O grid é da mesa**, não da tela: fechar a tela não derruba nada, porque a matéria pode chegar a qualquer hora.
- **As casas** (`BenchSlots`): onde está cada marca de casa, salvo no mundo, para o feitiço achar `g2` mesmo com a mesa
  descarregada. A marca de uma casa também é um lugar (`g2 ubis` serve a qualquer verbo: é a mesa).
- **O `vocant` numa casa** (`VocantFunctionHandler.intoSquare`): paga como qualquer `vocant` (o `quantum`, e o `chronos`
  como torneira) e põe a matéria na casa; `vis` entra como um quarto de cada um dos quatro. O que a casa não aceita
  (ela guarda algo que não é matéria, como uma espada do jogo) volta ao corpo. `<marca> <casa> ubis vocant` traz um item
  marcado que está no chão.
- **Os códigos** (2.1): a casa vira a coisa cuja composição bate com a mesma tolerância de sempre (5 pontos de cada
  parte) e cuja quantidade fica a 5% da dela; primeiro os itens, depois os blocos. Entraram na tabela de matéria o
  lingote de ferro, de ouro e de cobre (455 partículas, um nono do bloco, como o minério bruto: o lingote vem antes
  dele, então a casa faz lingote) e o graveto (512 partículas de madeira, um oitavo do bloco: proposta, a confirmar).
- **Os formatos** (`data/elderlexicon/invocation/formats.json`): os da seção 3.1, com os desenhos propostos. O dano é o
  do ferro vezes o peso da matéria contra o do ferro; o quente soma de novo o dano do ferro vezes a parte de fogo, e
  incendeia; o molhado apaga; o leve empurra. Uma ferramenta cava tão rápido quanto pesa, e só dá o que quebra se pesa
  como madeira, pedra ou ferro (nunca diamante).
- **O que a mesa manda** (`BenchDelivery`): depois de **2 segundos sem nada novo** chegando (para as várias linhas de
  um feitiço chegarem todas), se a mesa tem linha gravada, o que ela fez (ou tudo o que está no grid, se nada se forma)
  é posto em cima dela com uma marca que ninguém escreveu (`obra` e oito letras, por 5 segundos), e a linha é lida por
  quem invocou por último com essa marca como **sujeito já nomeado** (`SpellGrammar.withSubject`): as palavras da linha
  não mudam, e cada verbo dela age sobre o que a mesa fez, como em `m1 vocant iactare`. Primeiro a marca era escrita na
  frente da linha, e isso quebrava frases com lugar: em `obra… ego 5 ubis vocant alvo ubis iactare` as três palavras
  antes do `ubis` viravam coordenadas, a linha ficava sem sujeito, o `vocant` invocava vis e o `iactare` lançava uma
  esfera de mana, com a espada parada na mesa (16/10/2026). Uma fonte escrita na linha continua sendo o novo sujeito;
  só a fonte implícita (vis, numa linha sem fonte) não apaga o sujeito já nomeado.
- **A linha espera a condição dela** (16/10/2026, depois do primeiro teste no jogo): o `ferit` só vale no segundo
  depois de um golpe, e a mesa lê a linha 2 s depois da última invocação; por isso `ferit ille ubis iactare` só
  funcionava às vezes. Agora, quando a mesa se assenta, o que ela fez sai do grid e **fica pronto** em cima dela, e a
  linha é lida assim que as condições dela valem para quem invocou: na hora, se não tem condição ou se o golpe acabou de
  acontecer; no próximo golpe, se não. É o "último/próximo golpe" do desenho. Quebrar a mesa derruba o que esperava.
- **Lançado como golpe** (`Strikes`, `ThrownThing`): `<marca> iactare` sobre uma arma ou ferramenta caída no chão, ou
  sobre qualquer coisa que a mesa fez (inclusive cubo, disco, esfera e matéria sem nome), não empurra o item: lança-o de
  ponta, reto, como as espadas lançadas por magia. Acerta com o dano que a coisa tem na mão (1 do punho e o que ela
  soma); o que não é arma, pela lei do impacto (massa da matéria e velocidade). O quente incendeia, o molhado apaga, o
  leve empurra. Depois de acertar, cai; no chão, volta a ser o item, e não some como uma flecha. O `quantum` é a força
  do lançamento, como para qualquer coisa marcada. Outros itens marcados (uma pepita, um lingote) continuam sendo
  empurrados como antes.
- **Guardados também** (16/10/2026): o `iactare` acha a arma marcada onde ela estiver guardada, e todas saem num golpe
  só. No inventário de quem conjura, saem ao redor dele, em leque atrás dos ombros; no de outro jogador, de perto dele (o
  nome roubado do livro: quem sabe a marca tem o poder); num baú, barril ou qualquer recipiente carregado a até 32 blocos,
  de cima dele. Uma pilha marcada (flechas) sai uma por uma. Cada lâmina é um golpe à parte: a janela de invulnerabilidade
  depois de um dano não desvia a seguinte, então uma saraivada acerta com todas.
- **Lâmina que persegue**: lançada numa criatura (`ille ubis`, ou a marca de uma criatura, ou a criatura mirada), a coisa
  corrige o rumo a cada tick na direção do meio do corpo dela, mantendo a velocidade, até acertar ou cair (3 s de voo
  reto). Lançada num lugar, voa reto para ele. Sem isso, um alvo que se mexe era errado quase sempre.
- **Volátil** (`Conjured`): a contagem começa no primeiro tick fora da mesa (na mão, no inventário ou no chão). No
  fluxo, cada segundo a mais custa a matéria volátil inteira a cada dois segundos, como uma torneira.

**Ainda não:**

- quem paga quando a mesa está vinculada à vis de alguém (6): hoje paga sempre quem invocou por último;
- a gravação de galdraria em blocos postos (5.2);
- o escudo, o cubo, o disco e a esfera ainda não fazem nada próprio da matéria (o escudo bloqueia como qualquer escudo);
- numa arca o item não conta o tempo (não recebe tick): uma lâmina de fogo guardada não se desfaz;
- as flechas não devolvem o graveto e a pena quando se desfazem.

## O quadrado é um volume (17/10/2026)

Cada quadrado da bancada é o espaço de um item, que guarda 1 UMU tão frouxo quanto a terra. Por isso, o quanto há num
quadrado é o empacotamento da matéria ali:
- **Exemplo:** um lingote de ferro tem 10 UMU num quadrado, então o empacotamento dele é 10, o mesmo de um bloco de ferro.
- **O que a matéria é:** a coisa mais próxima em código e empacotamento juntos (docs/particulas-design.md, "Identidade").
  Se a quantidade bate com um item do jogo, ela é esse item. Se não bate, continua matéria, com o nome dessa coisa.
- **O item conjurado guarda quantos quadrados ocupa** (`ConjuredRoom`): uma espada ocupa dois; cada uma de quatro
  flechas ocupa um quarto.
- **O peso** é a massa no volume, comparada à do ferro: o peso de 1 UMU da matéria × o empacotamento ÷ o mesmo para o
  ferro. A espada de lingotes de ferro pesa 1. Uma espada com 200 UMU de terra em dois quadrados pesa bem mais que a
  de ferro.
- **O calor:** o fogo apertado em cada quadrado é o calor da lâmina, e 1 UMU é uma chama comum. O dano extra do fogo
  cresce com 1 + ln(calor): 30 UMU de fogo numa espada de dois quadrados dão um calor de 15.

## O golpe mostra a sua força (17/10/2026)

Uma coisa lançada mostra o poder que carrega. A medida é o quanto de cada aspecto está apertado em cada quadrado, e 1 UMU
por quadrado é o elemento comum.
- **No voo:** ela deixa um rastro de cada aspecto que tem. Quanto mais forte, mais denso e mais largo é o rastro.
  - **Fogo:** chama; a partir de 3, chama azul; a partir de 10, lava e fumaça; a partir de 30, faíscas de plasma.
  - **Água:** gotas; a partir de 10, gelo.
  - **Ar:** nuvem.
  - **O som da partida** fica mais alto e mais grave quanto mais forte ela é.
- **Ao bater, a matéria volátil se solta** (fogo, água, ar: o que não se segura sozinho). Primeiro vem o golpe; depois,
  todas as partículas entram no mundo ali:
  - **o fogo** vira calor no bloco, e a física decide o resto: chama, derreter pedra. A partir de 30 por quadrado, ele
    explode como plasma;
  - **a água e o ar** entram no ar do bloco e empurram tudo em volta, com mais força quanto mais apertados estavam. O ar
    a partir de 60 UMU por quadrado explode como a bomba de pressão;
  - **a terra de uma coisa volátil** vira poeira.

  Nada se perde, e as partes sólidas (o cabo) caem ali.
- **O que é sólido** (uma espada de ferro, de pedra) bate e fica caído, como antes.
- **Exemplos:** uma espada de 30 UMU de fogo em dois quadrados tem calor 15, então derrete a pedra onde bate. Uma de 60
  UMU explode como plasma.
