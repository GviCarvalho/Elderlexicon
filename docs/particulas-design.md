# Partículas: a física do mod

**Status:** desenho aprovado (30/09/2026). Etapas 1 a 6 implementadas: a parte pura, criar pelo código, a retirada
das runas fundidas e a transformação de blocos, itens, seres e jogadores. Continua
`plano-materia-emergente.md` e
`interacoes-design.md`, e junta as duas físicas que existem hoje com a lei do corpo (`vita-design.md`).

## A ideia

Ideia do usuário (30/09/2026):

- Cada bloco tem **4096 voxels** (16³, a mesma grade dos pixels de um bloco), e cada voxel é uma **partícula** de
  igni, aqua, aura ou firmo.
- Um bloco com as 4096 de um elemento **é** aquele elemento: firmo é terra, aqua é água, igni é fogo, aura é ar.
- Cada partícula age nas outras: **igni agita**, **aqua umidifica**, **firmo dá massa**, **aura dispersa**.
- **A falta dá o efeito reverso:** mais igni é mais calor, menos igni é mais frio. Menos firmo é menos massa, menos
  aura é menos dispersão, menos aqua é menos coesão.

Hoje há duas físicas lado a lado:
- a da matéria (`magic/matter`: composição, estado, UMU, `Qualities`, opostos que reagem) descreve o bloco;
- o estado físico (`spell/nature/NatureField`) guarda temperatura, pressão e gotículas como números à parte, com uma
  tabela fixa de materiais (`Material`). Uma pedra aquecida não ganha igni.

Com partículas as duas viram uma só, e é a mesma lei que o corpo já segue.

## No livro

- A vis é "a partícula perfeita": um quarto de cada elemento, fundidos numa só (3.1). As quatro partículas são as
  peças; a vis é uma de cada, fundida.
- As qualidades que o livro dá aos elementos são as dos verbos (3.1): terra, estabilidade e estrutura; fogo, energia
  e transformação; água, fluidez e coesão; ar, movimento e expansão.
- O calor da fervura de uma caldeira é UMU de fogo (cap. 3): o calor é igni.
- Aquecer as mãos puxa o calor do ambiente (9.2): há calor sem chama por perto, e tirá-lo esfria o ar em volta.
- Água e fogo fundidos explodem em vapor (10.1.1).

## 1. A partícula

- **Toda partícula vale a mesma UMU: 256 fazem uma** (decidido em 30/09/2026). Um item vale 1 UMU e um bloco 16.
- **4096 é o bloco cheio natural.** Menos é rarefeito, até o vácuo. Mais é comprimido, que é a condensação.
- **A pressão de um bloco** é quantas partículas ele tem sobre 4096.
- **Um item é uma camada: 256 partículas.**
  - O modelo de item do Minecraft é a textura 16×16 com um pixel de espessura: uma camada da grade do bloco.
  - 16 itens fazem um bloco.
  - Os itens que o jogo junta num bloco dizem a sua conta (`particles` no `materials.json`): 455 para os nove de um
    bloco (carvão, minérios brutos), 1024 para os quatro (bola de argila, bola de neve), 1365 para os três (osso). Como
    4096 não se divide por 9 nem por 3, esses blocos ficam uma partícula mais ricos que os seus itens.
- **Os códigos viram contagens.** A pedra (terra 80, água 5, ar 5, fogo 10) é 3277 firmo, 205 aqua, 205 aura e 409
  igni. Um osso (terra 60, água 20, fogo 20) é 154 firmo, 51 aqua e 51 igni.
- **Partícula não se divide**, então a conservação (L1) fica exata. Hoje a composição é `double` com margem de erro.
- **As unidades por coisa saem** (terra 0,5, pedra 1,5, água 3, obsidiana 50). O que é mais denso é o que está
  comprimido.

## 2. Os impulsos

Desenho fechado com o usuário em 02/10/2026.

- **Cada partícula tem um único impulso, e o aplica a qualquer partícula perto dela.** Não há interação escrita por
  par. Cada uma reage do seu jeito ao impulso das outras, e esse jeito sai do impulso dela mesma. São como mini seres:
  - **igni quer se mexer:** sacode tudo em volta (agitação);
  - **aura quer espaço:** empurra tudo para longe e vai para onde há menos (expansão);
  - **aqua quer se juntar:** puxa tudo para perto (coesão);
  - **firmo quer ficar:** dá peso e prende tudo no lugar (massa).
- **Os impulsos brigam em dois pares:** mexer contra juntar (igni e aqua), espalhar contra ficar (aura e firmo). São
  os opostos que o mod já usa.
- **O estado de um bloco é quem vence:**
  - a massa vence: sólido;
  - a coesão vence: líquido;
  - a expansão vence: gás;
  - a agitação vence todos: plasma.
- **Quanto mais paradas as partículas, mais frio** (usuário, 03/10/2026). Num sólido tudo está parado: tudo nele fica,
  como a massa, e segura o ar dos poros (a neve no frio). Num líquido só a massa segura o ar; a água se junta e o
  empurra para fora (a neve derretida solta o ar, a lava guarda o gás).
- **Os impulsos agem dentro do bloco e atravessam as faces para os vizinhos.**

**Como cada partícula reage a cada impulso:**

| | sacudida (igni) | empurrada (aura) | puxada (aqua) | presa (firmo) |
|---|---|---|---|---|
| **igni** | se espalha mais | vai junto: o calor viaja com o ar | é contido: a água bebe o calor | fica preso: é o fogo guardado no combustível e no magma |
| **aura** | expande mais, fica leve e sobe | vai para onde há menos: vento | fica presa em gotas e bolhas | fica nos poros da terra: areia |
| **aqua** | resiste e bebe a agitação; quando ela vence, se solta: vapor | vira gotículas: névoa, nuvem | gruda em si: gota, poça, correnteza | fica presa na terra: barro |
| **firmo** | resiste e passa a agitação adiante (conduz); quando ela vence, derrete | só o que é fino sobe: pó | gruda: barro, lama | assenta e sustenta: rocha; afunda no que é mais leve |

**O calor e a queima:**

- **O calor é o igni solto.** O igni do código de uma coisa está preso pela massa e pela coesão dela: faz parte do que
  ela é e não esquenta nada (a madeira tem 20 partes de fogo e não está quente). O igni além do cerne fica solto e
  agita as outras partículas, e essa agitação é o calor.
- **A temperatura depende de quem é agitado:** o ar quase não segura (esquenta e esfria rápido), a terra segura pela
  massa, a água bebe a agitação (esquenta devagar e guarda o calor).
- **Mudar de estado gasta agitação:** a água fica no ponto de fervura enquanto ferve. O ponto de fusão sai da
  composição: muita terra pede muito calor.
- **A agitação se espalha** pelas faces, do mais agitado para o menos: rápido pela terra, devagar pelo ar. Ar quente
  sobe, frio desce.
- **A agitação brilha:** de uns 525 °C para cima tudo brilha (o ponto de Draper, o mesmo para toda matéria), e o
  brilho leva agitação embora como luz: lava, ferro em brasa, chama.
- **Fogo e raio são ar agitado** (usuário, 03/10/2026; os limites são proposta, a confirmar):
  - o gás que brilha é chama: fogo;
  - de uns 5600 °C para cima (a superfície do Sol) a agitação vence a expansão, e o ar vira plasma: raio.
- **A queima precisa de calor, combustível e ar.**
  - O combustível é o fogo preso no código. Só calor não o solta: a madeira aquecida sem ar perde a água e o ar e vira
    carvão. Ela carboniza: a terra agarra o fogo, três de terra para dois de fogo (o código do carvão), e a terra que
    sobra sai como fuligem. Aquecida devagar vira carvão; de uma vez, derrete antes (usuário, 03/10/2026: carvão
    vegetal ou derrete).
  - **O fogo precisa de espaço para tremular** (usuário, 03/10/2026). A massa ocupa espaço: quanto mais terra por
    espaço no bloco, mais agitação para soltar o fogo. A madeira pega a uns 300 °C e o carvão a uns 600 °C; o ouro e a
    pedra derretem antes. Um pedaço pequeno, com espaço em volta, pega mais fácil.
  - A expansão do ar abre o combustível, e o fogo preso escapa como agitação para o ar que o abriu: a chama é esse ar
    agitado, e o combustível se aquece dela. A coisa perde o fogo do cerne e vira outra. Quanto mais quente, mais
    rápido queima, até o ar que chega limitar; o calor agita o combustível vizinho, e a queima se alimenta sozinha.
  - **O fogo precisa de muito ar,** como o de verdade: cada partícula de fogo leva cinquenta de ar. É esse ar que a
    chama aquece, e por isso ela não passa de uns 2100 K.
  - O ar não some: sai preso na fumaça e deixa de ser ar livre para respirar ou queimar. Sem ar a queima para (uma
    sala fechada apaga o fogo; terra por cima abafa).
  - A água apaga porque bebe a agitação: o combustível esfria abaixo do ponto em que solta o fogo.
  - O bloco de fogo é a chama, ar agitado até brilhar: esquenta o que toca, faz pegar fogo o que for combustível se
    houver ar, e some quando o calor se espalha e não sobra o que queimar.
- **O que sai disso sem regra por par:** os seis compostos da tabela (lama, pó, névoa, vapor, magma e raio, que é ar
  agitado até virar plasma), o vento, o ar quente que sobe, a água que apaga, a terra que abafa, o que flutua e o que
  afunda.

## 3. A falta: o efeito reverso

- **O zero é o natural.** Num bloco, é o código do que ele é: a pedra com os 409 igni dela não queima ninguém. Num
  ser, é a proporção do tipo dele.
- **Acima do zero, o efeito; abaixo, o efeito reverso:**

  | | A mais | Em falta |
  |---|---|---|
  | igni | calor: agita, derrete, ferve | frio: puxa o calor das vizinhas, congela |
  | aqua | umidade: gruda, apaga o fogo | secura: puxa a água das vizinhas (areia seca bebe água) |
  | aura | expansão: empurra as vizinhas | contração: puxa as vizinhas para dentro (o vácuo de hoje) |
  | firmo | massa: pesa, afunda, segura | leveza: sobe, e o mais pesado desce para o lugar |

- **O excesso empurra para as vizinhas e a falta puxa delas.** Por isso tudo tende a voltar ao natural, e nada é
  criado no caminho.
- **A falta passa de zero.** A água não tem igni para perder, mas pode ficar devendo: a conta fica negativa, uma
  dívida de fogo. A ausência é contada e age.
- **A falta de agitação tem fundo** (usuário, 02/10/2026). Todo bloco natural já tem a agitação do mundo em repouso; o
  igni solto soma a ela e a falta tira. Quando a falta tira toda, as partículas param: é o zero absoluto, e a falta não
  passa dele.
  - Quem puxou o igni ficou com ele (L1).
  - Dívida e excesso se anulam quando se encontram: água gelada num ferro quente deixa os dois mornos.
  - O feitiço 9.2 do livro ganha uma consequência: o ar em volta de quem aquece as mãos esfria.
- **É a lei do corpo.** A tabela de excesso e falta do `vita-design.md` (fogo em falta congela, ar em falta sufoca,
  terra em falta definha) passa a valer para toda a matéria.
- **Muda a base de 27/09** (`interacoes-design.md`). Lá o contrário do calor era a coesão, em dois eixos. Agora há
  as duas coisas: o contrário de cada elemento é a falta dele (quatro eixos, cada um com dois sentidos), e os impulsos
  brigam em pares (agitação contra coesão, expansão contra massa).

## 4. O que sai sem ninguém escrever

- Fogo na água vira vapor; preso, explode (10.1.1).
- Pedra agitada o bastante derrete em lava e volta a ser pedra quando o calor vai embora.
- Madeira aquecida perde a água como vapor e o ar como fumaça. O que sobra, terra e fogo, fica perto do código do
  carvão.
- Uma criatura num bloco frio perde igni para ele. O corpo tenta repor puxando do ambiente (3.2: respiração,
  alimentação, descanso), não consegue, entra em falta de fogo e congela. Na lava, o contrário. Ninguém escreve
  "dano de frio na neve".
- A água que um `vocant` põe num bloco empurra para as vizinhas o ar que estava ali.

## 5. Como roda

- **Não se simula voxel a voxel.** É o que Noita e The Powder Toy fazem em 2D, pixel a pixel. Em 3D a conta não
  fecha:
  - uma seção de chunk tem 16,8 milhões de partículas;
  - o mundo carregado passa de 10¹¹;
  - só as 20 mil células que o `NatureField` aceita seriam 82 milhões por tick.
- **Cada bloco vivo guarda quatro números com sinal**, um por elemento: quantas partículas ele tem, ou a dívida,
  quando falta abaixo de zero. Por dentro ele é uma mistura bem mexida; as trocas acontecem pelas seis faces.
- **Só o perturbado roda.** O mundo em repouso está no zero, em equilíbrio. Um bloco acorda quando algo o tira do
  natural e é esquecido quando volta, como o `NatureField` já faz.
- **O desenho sai dos números.** O cliente recebe os quatro números de um bloco vivo e arruma os voxels: firmo
  embaixo, aqua por cima, aura no topo, igni espalhado e tremulando. Um bloco meio água, meio ar aparece meio cheio.

## 6. Alquimia: criar o que é natural e transformar

**O objetivo** (usuário, 30/09/2026): criar qualquer coisa natural (osso, carne, plantas, frutas) e, com isso, criar
homúnculos e **transformar qualquer ser, jogador, item ou bloco em outro**.

### Uma tabela só

- Hoje há duas tabelas:
  - o `materials.json`: 27 coisas, em terra, água, ar e fogo;
  - a proporção dos seres (`Animation.Kind`): 12 seres, em água, ar, fogo e terra.
- Elas viram **uma tabela de códigos em partículas**, para blocos, itens e seres.
- **Matéria com âncora é ser.** A âncora de 100 UMU de vis (livro 3.2) escolhe a seção da tabela:
  - sem âncora, a mistura é a coisa de código mais próximo;
  - com âncora, é o ser de proporção mais próxima.
  - A neve (água 70, ar 30) fica perto do golem de neve (água 72, ar 24, terra 4): neve com âncora vira golem de
    neve, um pouco imperfeito (ar demais, terra de menos).
- Toda coisa natural e todo ser precisam de código. O que é feito (espada, baú) não tem código (ver "Forma").

### Criar

- **Criar é juntar partículas no código.** O que se forma aparece como a coisa natural de código mais próximo,
  que já é a regra (pedra derretida é lava). Osso e carne já têm código e aparecem como item (`minecraft:bone`,
  `minecraft:rotten_flesh`).
- **Os itens naturais são ingredientes:** pacotes de partículas de código conhecido. Em vez de medir elemento por
  elemento, o alquimista junta coisas.
- **O homúnculo:**
  - um quarto de carne, pouco mais de um terço de ar e dois quintos de água, com a âncora, dão a proporção de uma
    pessoa (55/38/2/5), dentro do "são";
  - osso e carne sozinhos não bastam: falta o sopro, o ar.
- **Uma mistura perto de código nenhum** é matéria informe, como hoje: a alquimia errada dá uma massa sem nome.
- **Os códigos continuam escondidos** e são descobertos tentando (decisão 3 do `plano-materia-e-forca.md`). O
  espírito diz o que a mistura virou.

### Transformar: o cerne

Desenho do usuário (30/09 e 01/10/2026):

- **Cada coisa tem um cerne: cem partes divididas entre os quatro elementos.** O cerne é proporção, não quantidade. A
  pessoa (55/38/2/5) soma cem: são 2% de fogo, não 2 partículas de fogo.
- **O que a coisa é sai do cerne:** o ser, ou a coisa natural, de proporção mais próxima. Mudando o cerne, ela vira
  outra.
- **`fonte quantum N vertere m1` muda o cerne de m1.**
  - A fonte serve de filtro, como no `ligabis`: diz qual aspecto muda.
  - O `quantum` diz as partes novas daquele aspecto, das cem: `aqua quantum 16 vertere m1` faz a água ser 16% de m1.
  - O `quantum` vem antes do verbo, como o livro escreve (`igni quantum 20 iactare`, 4.3.2).
- **Mudar o cerne é converter, não trazer nem tirar** (usuário, 01/10/2026). O `vertere` converte uma energia em
  outra dentro do próprio alvo, que continua com o mesmo total. Nada falta: o que um aspecto ganha, os outros cedem.
  - Por isso a formiga feita de um humano tem a vida do humano.
  - Não há o que buscar na vis de quem conjura.
  - É o que o livro diz do `vertere` no corpo (8.1): converter fogo em água troca "o seu calor corporal por hidratação".
- **O `vocant` negativo é outra coisa:** ele só tira do alvo, sem mudá-lo, e o que tira vira falta.
- **Exemplo:** uma formiga é igni 1, aqua 2, aura 5, firmo 7, em proporção (1/15, 2/15, 5/15, 7/15). Num humano
  marcado m1, quatro linhas na mesma coluna fazem dele uma formiga:

  ```
  igni quantum 1 vertere m1
  aqua quantum 2 vertere m1
  aura quantum 5 vertere m1
  firmo quantum 7 vertere m1
  ```

- **As mudanças do mesmo instante contam juntas:** o espírito junta as linhas da mesma coluna sobre o mesmo alvo e
  relê o que ele é uma vez só. Em instantes diferentes, cada mudança transforma na sua vez.
- **O que não fecha um inteiro vira vis e sai do alvo como luz.** Dezesseis carnes (4096 partículas) com o código do
  osso são três ossos de 1365, e a partícula que sobra sai como luz.
- **O kern e a âncora ficam:** um jogador transformado continua sendo ele, noutro corpo. A incorporação já leva o kern
  de alguém para outro corpo; a transformação muda o corpo em que o kern já está.
  - Ele mantém tudo o que tem: o que carrega, a vida e a experiência (usuário, 01/10/2026).
  - Volta ao corpo de antes do mesmo jeito que foi transformado: mudando o cerne de volta ao que era (usuário,
    01/10/2026).
- **Um ser vale a sua vida mais a âncora** (usuário, 01/10/2026):
  - cada ponto de vida são 5 UMU de corpo, e o cerne são as porcentagens dessa vida;
  - a âncora são os 100 UMU de vis que todo ser vivo tem (livro 3.2). No jogador, a vis é a experiência, e varia;
  - o humano vale 200 UMU: 100 da vida (20 pontos) e 100 da mana natural;
  - mudar o cerne converte a vida; a vis não muda.

Regras propostas na etapa 4 e confirmadas ou corrigidas pelo usuário em 01/10/2026:

- **As partes que não se escrevem** dividem o resto como estavam entre si. A pessoa com `aqua quantum 16` fica com
  16% de água, e o ar e a terra mantêm a mistura de antes. Se elas não tinham nada (água pura com `aqua quantum 40`),
  dividem o resto por igual.
- **As partes escritas são porcentagens exatas, e o cerne sempre fecha cem.**
  - Partes que passam de cem, ou os quatro aspectos escritos sem somar cem, não fazem cerne nenhum. Nada muda, e o
    espírito diz quanto as partes somaram.
  - A formiga do exemplo se escreve com as suas porcentagens de verdade, que somam cem.
- **Sem número, os elementos dividem a vida por igual** (usuário, 01/10/2026). Vale para a linha sem `quantum` e para a
  linha com `quantum` sem número.
  - `aqua vertere m1` faz m1 todo água; com `aqua` e `igni`, metade de cada; assim por diante.
  - Com linhas numeradas no mesmo instante, as sem número dividem o que os números deixam. Os elementos não escritos
    ficam sem nada.
- **`quantum 0` continua recusado.** Para zerar um aspecto, escrevem-se os outros somando cem: a areia (terra 70, ar
  30) zera a água e o fogo da pedra.
- **O estado sai das partículas:** a coisa nova aparece no estado natural dela.
  - Lava feita toda água é água.
  - Se ainda tiver fogo, é o que esse fogo fizer: no código do vapor, vira vapor.
  - A mesma coisa fica como está, e o que fica sem nome mantém o estado até a física (etapas 9 e 10).
- **Itens continuam itens** quando a coisa nova tem item (ou bloco que vira item). Se não tem (água, fogo, ar), vão
  para o mundo como a matéria que são.
- **Cada linha paga o que converte, como todo `vertere`.** Assim os feitiços de transformação não são pré-programados
  e continuam modulares (usuário).
  - O preço é o trabalho da escada (L2): 5% de UMU por degrau, para cada partícula que aquela linha converte, pelo
    caminho mais curto.
  - Ele entra no custo do feitiço, na hora em que a linha é lida, medido no alvo como ele está.
  - A pedra feita terra leva o fogo três degraus abaixo, o ar dois e a água um: 0,36 UMU.
- **A marca acompanha** o que a coisa virou.

### Com aparelho e com feitiço

Decisão de 30/09/2026: as duas formas.

- **Aparelho:**
  - um recipiente (o caldeirão do jogo ou um bloco do mod) onde o que entra se desfaz em partículas e a mistura sai
    como o que ela for;
  - devagar e sem custo para o corpo;
  - combina com o livro: a magia é tabu, e para tarefa do dia a dia talvez nem se devesse usá-la (fim do cap. 9).
- **Feitiço:**
  - o mesmo, na hora, pagando UMU;
  - só com as runas que já existem: nenhuma fusão de runas entra (decisão do usuário, 30/09/2026). A gramática de pôr
    partículas num alvo está em aberto.

### Forma

As partículas dizem do que a coisa é feita, não a forma dela: uma espada é ferro e madeira, mas o que a faz espada
não é proporção. O livro cita uma quarta família de runas, a **Forma** (cap. 4: Fonte, Função, Filtro e Forma), e
nunca a ensina. Transformar em coisa feita fica para ela.

## 7. O que muda no que existe

| Hoje | Com partículas |
|---|---|
| `Composition`: partes em `double` | continua como a proporção (o código); as contagens ficam em `Particles` |
| `Qualities` e o limiar de um quarto | os verbos, agindo pelas faces |
| `MatterLaws.react` | sai dos verbos: agitação contra umidade, dispersão contra massa |
| `NatureField`: temperatura, pressão, gotículas | o igni, o aura e o aqua dos blocos; a tabela `Material` sai |
| `NatureField`: carga e relâmpago | continuam como estão, até serem revistos |
| `FormlessMatterBlockEntity` | guarda as contagens |
| `materials.json` | códigos em partículas, sem `unit` por coisa, e com os seres *(feito)*; entram as coisas naturais que faltam |
| `Animation.Kind` | vai para a tabela *(feito)* |
| `WorldSources` e a condensação: UMU por bloco = dureza | 16 UMU por bloco, e a intensidade é compressão (quantos blocos cabem num) |
| `VitaSystem`: o corpo volta ao equilíbrio sozinho, 10% por segundo | o corpo se reequilibra puxando do ambiente (3.2) |
| `Aspect.opposite` | os pares viram reação; o custo do `vertere` pela geometria de 27/09 continua até ser revisto |

## 8. Etapas

Cada etapa é um commit, com os testes passando.

1. **Parte pura** *(feita)*:
   - a partícula e a conta com sinal;
   - a conservação exata;
   - a tabela única em partículas;
   - a identidade: a coisa ou o ser mais próximo.
2. **Criar pelo código** *(feita)*: `vocant` e a mistura produzindo itens, blocos e seres pela tabela; o homúnculo por
   ingredientes.
3. **Retirar as runas fundidas** *(feita)*: todas as 24 que o léxico marca como fusão saem, inclusive `vita`
   (usuário, 30/09/2026).
   - Saem as fontes `fusus`, `caligo`, `lutum`, `pulvis`, `nebula`, `fulmen` e `vita`; as formas (`hasta`, `murus`,
     `vortex`...); e os verbos (`transiectio`, `aversio`, `cohaesio`...).
   - A lama, a poeira, a névoa, o vapor, o magma e o raio continuam no `materials.json` como coisas naturais. O que
     sai é a runa que os nomeava.
   - Sai também o que só existia por elas: a origem "fusão", os componentes, os atalhos que se desdobram, a classe
     das formas, o `Fusions`.
   - Quem escrever uma delas ouve do espírito que ela não existe mais e o que escrever no lugar. O léxico recusa uma
     fusão que um addon traga.
4. **Transformar blocos e itens** *(feita)*: o cerne pelo `vertere`, em proporção, convertendo o que o alvo já tem.
5. **Transformar seres** *(feita)*: o cerne pelo `vertere`, as mudanças do instante juntas, a vida que fica.
6. **Transformar jogadores** *(feita)*: o corpo em que o kern está muda, e o jogador continua com tudo o que tem.
7. **O caldeirão.**
8. **A captura e a condensação em partículas:**
   - a energia tirada do mundo com `tenet` passa a valer 16 UMU por bloco, como a matéria;
   - a intensidade da condensação passa a ser compressão: quantos blocos cabem num;
   - as faixas do fogo ficam iguais (uma chama valia 1 UMU, e 44 chamas num ponto continuam calor 44); as da água, do ar
     e da terra são refeitas.
9. **Os verbos no mundo:** matéria informe com partículas, trocas pelas faces. A parte pura, o laboratório, está
   feita (§8.7); falta ligá-la ao mundo no lugar das três camadas antigas.
10. **Calor e frio:** o igni e a dívida de igni no lugar da temperatura do `NatureField`.
11. **Pressão e umidade:** aura e aqua; sai a tabela `Material`.
12. **O desenho dos voxels.**

As etapas 4 a 7 chegam ao objetivo sem esperar a física (9 a 12), porque criar e transformar dependem da tabela e da
identidade, não das trocas entre blocos.

## 8.1 Como a etapa 1 ficou

A parte pura fica em `magic/matter`, sem nada de Minecraft.

- **A partícula** (`Particles`): quatro contagens inteiras com sinal, uma por primordial, na ordem da escada (firmo,
  aqua, aura, igni).
  - `BLOCK` é 4096 e `ITEM` é 256.
  - `Particles.in(código, n)` põe um código em n partículas inteiras que somam exatamente n. Cada primordial fica com
    a parte inteira da sua fração, e o que sobra vai, uma a uma, para as maiores frações (a mais densa, no empate).
    Pedra num bloco é 3277/205/205/409; um osso num item é 154/51/0/51.
  - `split(n)` parte em n donos que somam o todo, cada um com o mesmo que outro, com diferença de uma no máximo: um
    bloco de pedra são 16 itens de pedra.
- **A conta com sinal:**
  - tirar o que não há deixa dívida (`owed`); dívida e partícula se anulam;
  - o que se tem (`present`) diz o que a coisa é (`composition`); a dívida diz como ela está;
  - a pressão é o total com sinal sobre 4096. O que se deve também tira lugar: a água que deve 200 de igni fica tão
    rarefeita quanto a pedra que perdeu 200 dos seus. Frio contrai.
- **A conservação exata:** nenhuma conta arredonda, e uma que transbordaria é recusada em vez de dar a volta. Um
  teste faz 10 mil trocas ao acaso entre 8 donos, com dívidas no meio, e a soma continua a mesma, partícula por
  partícula.
- **A tabela única:**
  - o `materials.json` ganhou a seção `beings`: os 12 seres da lei da animação, cada um com a proporção do corpo e a
    criatura que o mostra;
  - a `MaterialTable` guarda coisas e seres. Um addon traz seres no mesmo formato, e o `remove` tira coisa ou ser;
  - são recusados: uma criatura que mostraria dois seres, um nome que é coisa e ser, um ser sem criatura;
  - `beingShownBy("minecraft:cow")` diz que ser uma criatura do jogo é.
- **A identidade** (`Identity`, `MaterialTable.identify(partículas, âncora)`): `Nothing` (nada se tem: vácuo ou só
  dívida), `Thing`, `Formless` e `Creature` (com o desvio em cada primordial, e se está são, abaixo de 3 pontos). Os
  testes mostram:
  - a água que deve fogo continua sendo água;
  - neve com âncora vira golem de neve, imperfeito: ar demais (+6), terra de menos (−4);
  - carne (1024), ar (1454) e água (1638), com âncora, dão um homúnculo são;
  - carne sozinha com âncora dá um slime: falta o sopro;
  - de porco a vaca, em dez passos, nenhum passo sai do são.
- **Empates:** corpos inteiros caem com frequência exatamente entre dois seres (30 firmo, 105 aqua, 48 aura e 27 igni
  estão a 72/210 da vaca e do porco). A tabela dá o empate ao primeiro da lista. A lei da animação de hoje deixa o
  arredondamento escolher: em 8/45/44/53, a 94/150 do porco e do blaze, ela escolhe o blaze. Fora dos empates, as
  duas leem qualquer corpo como o mesmo ser, com o mesmo desvio (teste com 2000 corpos ao acaso).
- **O que ainda não mudou:**
  - o mundo (colocação, `vocant`, matéria informe) continuava em UMU, com `unit`; saiu na etapa 2, com a escala;
  - a lei da animação continuava com o seu `Animation.Kind`, guardada por um teste até a etapa 2 trocá-la pela tabela.

## 8.2 Como a etapa 2 ficou

- **A escala** (`Particles.PER_UMU`, `Particles.BLOCK_UMU`): 256 partículas fazem uma UMU, e um bloco vale 16.
  - A `unit` de cada substância saiu do `materials.json` e do código. Uma forma diz quantas partículas guarda
    (`Form.particles`): um bloco 4096, um item 256, salvo os que o jogo junta num bloco.
  - A matéria informe ocupa um bloco a cada 16 UMU.
  - Como 256 é potência de 2, um número inteiro de partículas é exato em UMU (`double`): somar e tirar matéria não
    acumula erro.
- **O `vocant` vale 16 UMU por padrão:**
  - sem quantidade, ele custa o que custava e faz um bloco, como antes;
  - a matéria que sai do corpo é a que a tabela diz da essência da runa (terra é terra, `lutum` é lama), em blocos
    inteiros, um a cada 16 UMU;
  - o que não fecha um bloco, ou não acha lugar, volta ao corpo: `firmo quantum 40 vocant` faz dois blocos e devolve
    8; `firmo quantum 10 vocant` não faz nenhum, e o espírito avisa;
  - os efeitos passageiros (fogo, ar, vis) medem a força contra esses 16: um `igni vocant` de 16 queima como um de 10
    queimava;
  - a imagem de um `vocant` (`surgit`) mostra um bloco a cada 16 UMU, e o `tenet … vocant` também traz um bloco por
    padrão.
- **Os seres pela tabela:** a lei da animação lê os seres do `materials.json` e pesa o corpo em partículas
  (`Animation.quicken(Particles, vis)`). O `Beings` cria a criatura pelo id que a tabela dá, e o `Animation.Kind` saiu.
  Um ser que um addon traga pode nascer.
- **O homúnculo por ingredientes, só por feitiço:**
  - o `tenet … vocant` pega também itens naturais soltos no chão, itens inteiros, os mais perto primeiro; uma origem
    escrita sobre o monte leva exatamente eles;
  - o que ele traz espera o fim do instante (`Quickening`), junto com o que os outros `vocant` soltam ali. Com uma
    âncora no mesmo lugar, vira o corpo de um ser;
  - um GameTest faz nascer um homúnculo de 25 carnes do chão (`firmo quantum 25 … tenet … ubis vocant`) com
    `aura quantum 36`, `aqua quantum 40` e `vis quantum 100` no mesmo ponto, e a carne some nele.
- **Testes do mundo:**
  - os números passaram para a escala nova;
  - dois testes já falhavam antes desta etapa (`stoneIsMadeByMixingThePrimordials` e
    `fireThrownIntoAPoolBoilsItAway`). Desde a lei da animação, o que um `vocant` traz espera o fim do tick, e eles
    olhavam o resultado no mesmo tick. Agora esperam;
  - os 25 passam.
- **O que ficou para a etapa 8:** a captura de energia do mundo (`WorldSources`) e a condensação ainda medem pela
  dureza (terra 0,5 por bloco, pedra 1,5, água 3 por fonte). Até lá, capturar um bloco como energia rende menos do que
  criá-lo custa, então não há como tirar UMU do nada.
- **Mundos já salvos:** a matéria informe guardada antes desta etapa continua com o UMU da escala antiga.

## 8.3 Como a etapa 3 ficou

- **O léxico** (`runes.json`) perdeu as 24 runas fundidas.
  - Cada uma ganhou uma linha em `retired`: quem a escreve ouve que ela não existe mais e, quando dá, o que escrever no
    lugar (`transiectio`: "escreva vertere e iactare"; `lutum`: "a lama é terra e água").
  - O repertório padrão perdeu o `murus`: são 8 runas, e o nono espaço fica vazio.
  - A nota "Runa de fusão." virou "Runa sem descrição no léxico.", e o aviso de fusão sem composição saiu.
- **O código que só existia por elas saiu:**
  - `Fusions` e `Origin`;
  - os componentes, a expansão de atalhos e a forma em `Rune`;
  - a classe `FORM`;
  - o passo da gramática que desdobrava atalhos;
  - a fusão de fontes do `reframe`;
  - a regra do grimório de aceitar só runas originais (`FusionResolver`), que nunca mais dispararia.
- **O léxico recusa uma fusão:** um arquivo de addon com uma forma, uma origem "fusão", componentes ou um atalho que se
  desdobra não carrega, e diz que as runas fundidas saíram da língua.
- **O que ficou:** o campo `shapes` das ações e o tipo `SHAPE` do leitor antigo do terminal e do repertório continuam,
  vazios. Nada mais os preenche, e tirá-los mexeria em telas fora desta etapa.
- **Testes:**
  - os que testavam fusões saíram ou passaram a conferir que elas saíram;
  - o catálogo de feitiços perdeu os 15 que usavam runas fundidas e ganhou um caso do aviso;
  - os 522 testes de unidade e os 25 do mundo passam.
- **Documentos:** a `GRIMOIRE_REFERENCE.md` não lista mais fontes fundidas, formas nem verbos fundidos. A página e os
  círculos aparecem como o que são: o que está na mesma coluna ou no mesmo anel age no mesmo instante, e nada se funde.

## 8.4 Como a etapa 4 ficou

- **A parte pura** (`Core`):
  - `reshape` faz o cerne novo, em partes de cem, com as regras de "Transformar: o cerne";
  - `convert` converte as partículas no cerne novo, o mesmo total (L1);
  - `work` é o trabalho do espírito (L2): o caminho mais curto na escada, 5% de UMU por degrau de cada partícula;
  - `state` mantinha a distância do estado natural; depois passou ao estado natural da coisa nova (§8.5).
- **A gramática:**
  - o `vertere` aceita uma marca depois dele também quando o sujeito é uma fonte. Com marca antes, continua mudando a
    marca (`m1 vertere m2`);
  - sem `quantum`, o espírito perguntava quanto; depois passou a manter o cerne (§8.5);
  - antes, `igni vertere m1` era recusado ("requer uma fonte alvo").
- **No mundo** (`Cores`):
  - cada linha pede; os pedidos do mesmo instante para a mesma coisa se juntam no fim do tick. O mesmo aspecto pedido
    duas vezes vale o último, e o espírito avisa;
  - um bloco vira, no lugar, o que o cerne novo for: o bloco da coisa, matéria informe, água, fogo, ou nada (o ar e o
    vapor sobem);
  - um item no chão continua item quando a coisa nova tem item, e senão vai ao mundo como matéria;
  - o que não fecha um inteiro sai como luz (`VisSpots.light`);
  - a marca passa ao que a coisa virou: os blocos postos e os itens;
  - quem pediu pagava o trabalho no fim do instante, cada linha a sua parte. Depois o usuário pediu que cada linha
    pague o que converte, na hora, como todo `vertere` (§8.5).
- **O que o `vertere` ainda não muda:**
  - os seres e os jogadores (etapas 5 e 6). O espírito diz que o cerne deles ainda não se deixa mudar;
  - o que é feito (uma espada, um baú): não tem código, e o espírito diz isso.
- **O que o espírito diz:**
  - o resultado: "“m1”: pedra virou terra.";
  - matéria sem nome com a descrição de sempre;
  - "continua pedra" quando a mudança cabe no código dela, e então nada muda.
- **A página do grimório** descreve a linha ("Muda o cerne do que tem a marca “m1”: água, 16 das cem partes.") e chama a
  página de "Transformação". As fontes das linhas de cerne não contam como elementos que se encontram.
- **Testes:**
  - 10 de unidade para o `Core`, 2 da descrição, 2 da gramática e 4 linhas novas no catálogo;
  - 5 no mundo: pedra que vira terra e, pela mesma marca, água; areia por duas linhas no mesmo instante, e não em
    instantes diferentes; pedra feita água e lava feita vapor; dezesseis carnes que viram três ossos marcados; uma
    espada que fica como está;
  - os 535 testes de unidade e os 30 do mundo passam.

## 8.5 Como a etapa 5 ficou

- **As correções da etapa 4** (usuário, 01/10/2026):
  - as partes escritas são porcentagens exatas. Partes que não fecham cem são recusadas (`Core.reshape` diz vazio), e
    o espírito diz quanto elas somaram;
  - sem `quantum`, o cerne fica como está, e a gramática não reclama mais;
  - o estado sai das partículas: a coisa nova aparece no estado natural dela;
  - cada linha paga, quando é lida, o trabalho do que ela sozinha converteria no alvo como ele está. O pagamento no fim
    do instante saiu.
- **O cerne de um ser** (`Beings.coreOf`):
  - é o que uma transformação lhe deu, guardado nele;
  - se nenhuma transformação lhe deu um cerne, é o do seu tipo na tabela, com o desvio com que nasceu, se nasceu
    imperfeito;
  - uma criatura que a tabela não tem (um aldeão, um zumbi) não tem cerne conhecido, e o espírito diz isso.
- **O corpo** é a vida, 5 UMU por ponto, na proporção do cerne. A âncora é a de sempre, 100 de vis.
- **O que o ser vira** sai da lei da animação: o tipo de proporção mais próxima.
  - Do mesmo tipo, só o desvio muda: um corpo afastado do tipo adoece como os imperfeitos, e um que volta ao tipo sara.
  - De outro tipo, a criatura do tipo novo toma o lugar da antiga (`Beings.become`), com:
    - a mesma vida (a vida máxima também);
    - o nome;
    - a idade de filhote;
    - os efeitos e o fogo;
    - o kern, ou a falta dele: quem nasceu sem kern continua sem;
    - a marca.
- **O jogador** ainda não muda (etapa 6). O espírito diz isso.
- **Testes:**
  - no mundo, 3 novos:
    - uma galinha com o cerne da vaca vira uma vaca com os 4 de vida da galinha e a marca dela;
    - um aldeão fica como está;
    - partes que somam 160 não mudam a pedra;
  - os testes de unidade do `Core` passaram às porcentagens exatas;
  - os 534 testes de unidade e os 33 do mundo passam.

## 8.6 Como a etapa 6 ficou

- **O `vertere` sem número** (usuário, 01/10/2026): os elementos escritos sem número dividem por igual o que os
  números deixam (`Core.reshape` com o conjunto dos sem número). Um `quantum` sem número conta como sem número.
- **Os seres da tabela:** os 64 que faltavam (os mobs do jogo, menos os chefes) ganharam uma proporção proposta, na
  seção "Os seres propostos" logo abaixo, para o usuário revisar.
  - O zumbi ficou de propósito perto da carne: carne com âncora é zumbi, carne morta que anda.
  - Os testes que olham um empate ou um desvio entre poucos seres restringem a tabela a eles, para não depender dos
    vizinhos novos.
- **O cerne do jogador é o da Vita** (`VitaData`): a pessoa, 55/38/2/5, até um `vertere` mudá-lo.
  - A vida perdida e a ganha vão na proporção do cerne, e o corpo volta ao equilíbrio na mesma proporção.
  - As faixas de falta e excesso ficam proporcionais ao cerne. A recuperação (`VitaRecoverySystem`) também segue o
    cerne.
  - O `vertere` converte o que a Vita tem na proporção nova (`VitaSystem.reshape`), com o mesmo total.
- **O corpo do jogador** (`Forms`):
  - é o ser de proporção mais próxima do cerne. A proporção da pessoa (o homúnculo da tabela) é o corpo do próprio
    jogador;
  - o jogador continua o mesmo: inventário, vida, experiência e marca ficam. Só a forma muda;
  - ele fica do tamanho do ser (a caixa de colisão e a altura dos olhos);
  - todos que o veem o desenham como aquele ser, andando como o jogador anda (`FormPacket`, `ClientForms`,
    `FormRenderer`). As mãos de gente não aparecem em primeira pessoa;
  - um cerne longe do tipo adoece o jogador como adoece os seres imperfeitos;
  - ao morrer, o corpo fica para trás: o jogador volta como gente, e a Vita, como sempre, recomeça.
- **Os testes no mundo usam marcas novas a cada execução.** O servidor de testes guarda o mundo de uma vez para outra,
  e uma marca da execução anterior podia apontar para o bloco de outro teste. Foi o que fez o teste do fogo na poça
  falhar.
- **Testes:**
  - de unidade: as linhas sem número no `Core`;
  - no mundo, 2 novos:
    - pedra com água e fogo sem número vira vapor;
    - o mago com o cerne da vaca tem corpo e tamanho de vaca e continua com o que carrega, e com o cerne de gente
      volta ao seu corpo;
  - os 535 testes de unidade e os 35 do mundo passam;
  - o desenho do jogador como outro ser no cliente compila, mas só se vê dentro do jogo.

## 8.7 O laboratório dos impulsos

A física dos quatro impulsos (seção 2), pura e sem Minecraft, numa caixa de blocos (`magic/physics`: `Drives` e
`Box`). Nenhuma lei nomeia uma substância ou um par de elementos. Os números são por tipo de partícula, mais uns poucos
iguais para tudo: onde a água congela e ferve, onde tudo brilha, onde o ar vira plasma.

**O que cada bloco guarda:**
- a matéria presa (sólida ou líquida), com o fogo preso nela (o combustível), a parte dele que a terra agarrou em
  carvão, e o ar dos poros;
- as partículas soltas no ar: ar, vapor, pó, e o combustível que o pó leva (fuligem, o vapor de um combustível que
  ferveu), que queima onde encontra ar;
- a fumaça: o ar que o fogo usou, que não alimenta mais fogo;
- o fogo que o vapor guarda: ferver gasta agitação, e o vapor a guarda até condensar;
- a agitação solta (o calor), que pode ficar devendo até o zero absoluto.

**A escala, o fogo de verdade (usuário, 03/10/2026: perto do real):**
- A temperatura é medida em "mundo em repouso": 1 são uns 20 °C, e um passo de 1 são uns 293 K.
- Uma partícula de fogo aquece 80 de terra, 20 de água ou 320 de ar em um passo. Assim o fogo de um bloco de madeira,
  solto, esquentaria um bloco de pedra em 15 passos, como o de verdade. Antes, nem 60 K.
- Ferver uma partícula de água guarda 1/9 de partícula de fogo: o vapor é 9 de água para 1 de fogo. O código do vapor
  na tabela mudou para isso (era metade de cada).
- Cada partícula de fogo leva 50 de ar, que vira fumaça.

**As leis, uma vez por passo:**
1. A agitação passa pelas faces do mais agitado para o menos:
   - conforme o que conduz;
   - e como luz, tanto mais quanto mais brilha (a quarta potência da agitação), entre blocos que têm matéria. O ar
     limpo deixa a luz passar.
2. O fogo preso se solta onde a agitação passa da ignição e há ar livre encostado:
   - a ignição sobe com a massa por espaço no bloco, ao cubo (o espaço é um volume);
   - quanto mais passa da ignição, mais rápido queima, até o dobro dela; dali em diante quem limita é o ar que chega;
   - o fogo solto vai para o ar que abriu o combustível: a chama;
   - o combustível se desfaz em cinza junto, e o ar usado vira fumaça onde estava.
3. A matéria muda:
   - um sólido está parado e segura o ar dos poros; se o ar espalha mais do que isso, ele se desfaz em pó, e o fogo
     dele vai junto, no ar, ainda combustível;
   - num líquido só a massa segura o ar;
   - o fogo que nenhuma massa segura é agitação;
   - a água ferve e leva fogo como vapor; o vapor que esfria condensa e devolve o fogo;
   - a terra que ferve leva o fogo dela para o ar, como combustível;
   - um combustível sólido além da ignição carboniza, com ar ou sem. A terra agarra o fogo, 3 para 2. A terra que
     sobra sai como fuligem, o ar dos poros sai gasto, como fumaça, e a água sai como vapor. O fogo agarrado segura como
     a terra, e o carvão não derrete onde a madeira derreteria;
   - o combustível no ar queima onde houver ar, pela mesma lei;
   - o pó assenta quando segura mais do que o ar em volta espalha. O ar quente espalha mais e segura a fuligem no alto.
4. O gás vai de onde aperta mais para onde aperta menos, levando o seu calor.
5. O gás mais leve sobe através do mais pesado.

Parte de partícula não existe. O que moveria uma parte move uma partícula inteira com a chance daquela parte, e na
média sai o certo.

**As cinco respostas do usuário (03/10/2026) e como ficaram:**
1. **Fogo perto do real, forte o bastante para passar de tronco em tronco.**
   - A escala acima.
   - Uma chama segurada na ponta de uma fileira de cinco troncos acende todos, um depois do outro.
   - A chama de madeira chega a uns 1300-1400 K, a de carvão a uns 1450-1600 K.
   - A pedra ao lado do carvão em brasa esquenta (uns 1000 K) sem derreter.
2. **O fogo precisa de espaço para tremular; o que concentra firmo resiste.** A ignição sobe com a massa por espaço:
   - folhas e carne, uns 540 K; madeira, 570 K; carvão, 870 K;
   - um pedaço de carvão com espaço em volta, 540 K;
   - magma, 640 K (em repouso fica como está);
   - ouro, obsidiana e cobre, 1780 K: derretem antes;
   - pedra e ferro: nunca, derretem antes.
3. **A neve depende da agitação.**
   - Abaixo de 0 °C a água está parada e segura o ar entre ela: a neve fica neve.
   - A 20 °C ela derrete e o ar sai: vira água, não névoa.
4. **Quando o ar agitado é fogo e quando é raio** (proposta, a confirmar).
   - Fogo é o gás que brilha, de uns 525 °C para cima. É o ponto de Draper, onde toda matéria começa a brilhar, igual
     para tudo.
   - Raio vem de uns 5600 °C para cima (a superfície do Sol): a agitação vence a expansão, e o ar vira plasma.
   - O código do raio na tabela (meio ar, meio fogo solto) sai a uns 31 500 K, como um raio de verdade (uns 30 000 K).
   - O do fogo puro sai a uns 47 000 K: é o mais agitado de todos.
5. **Carvão vegetal ou derrete, com chance baseada no real** (proposta, a confirmar).
   - Em vez de sorteio, a chance saiu da física: a carbonização e a fusão competem, e quem decide é a velocidade do
     aquecimento. É o que acontece na pirólise de verdade: lenta faz carvão, rápida faz líquido.
   - A madeira aquecida devagar sem ar vira carvão, que é o código do carvão. O item de carvão vegetal virou uma forma
     do carvão, porque o código próprio, dois para um, ficaria a 3 pontos do ouro.
   - Aquecida de uma vez, ela derrete antes de carbonizar, e o que é líquido não carboniza.

**Os 19 cenários que passam:**
- o ar vaza para o vazio e se divide por igual; o ar quente sobe;
- a pedra derrete em lava (1627 K), brilha, guarda o gás e, fria, volta a ser pedra;
- a água ferve em vapor, 9 para 1, e o vapor que esfria volta a ser água e devolve o fogo;
- a lama fica lama; terra e ar meio a meio voam como pó; a areia segura o ar;
- a neve fica neve no frio e vira água no morno;
- uma fileira de troncos pega fogo um do outro; a chama brilha (é fogo) e não vira raio;
- o carvão queima mais quente que a madeira, e a pedra ao lado não derrete;
- o carvão queima com ar; sem ar guarda o fogo e, quente, vira coque;
- a mesma agitação que faz a madeira soltar o fogo não basta ao carvão, mas basta a um pedaço do mesmo carvão com
  espaço em volta; o magma em repouso guarda o fogo; o ouro derrete antes de soltar o dele;
- o calor que acende a madeira seca vai para a água da madeira molhada;
- uma sala fechada apaga o fogo quando o ar acaba, com combustível sobrando;
- meio bloco de água jogado num bloco de carvão em brasa apaga o fogo; com um quarto, a chama em volta reacende;
- o ar agitado brilha como fogo; o código do raio é plasma; o do fogo, mais ainda;
- a madeira aquecida devagar sem ar vira carvão e não derrete; aquecida de uma vez, derrete;
- nada se cria nem se perde, partícula por partícula; nada esfria abaixo do zero absoluto.

**Cada coisa da tabela no laboratório:**
- Em repouso, todas ficam como a tabela diz, menos a neve, que derrete, e a névoa, que só existe no ar.
- Pontos de fusão: ferro 1718 K (o real é 1811), ouro 1340 K (real 1337), pedra 1627 K, obsidiana 1421 K, terra
  1912 K. O carvão e o carvão vegetal só derretem como a terra, a uns 1910 K.
- Madeira, folhas e carne secam (400-500 K) e carbonizam em carvão (520-560 K).
- O raio e o fogo da tabela são plasma.

**O que o laboratório mostrou e pede decisão:**
- **Códigos com água e ar mudam de nome quando aquecidos.**
  - O cobre seca e passa por "pedra".
  - A areia perde o ar dos poros e passa por "cascalho".
  - A pedra infernal aquecida sem ar vira carvão.
  - O osso, secando, passa por "obsidiana".

  É a física tirando a água e o ar dos códigos. Pede olhar os códigos, não as leis.
- **Uma caixa fechada pequena vira forno.** O calor do fogo não sai, todo o ar passa do ponto de ignição, e o carvão
  apagado com pouca água reacende. No mundo o calor sai pelo céu; no laboratório, não.
- **A economia dos feitiços muda.** Com o fogo forte:
  - uma chama num bloco de ar pede só ~0,1 UMU de igni;
  - 1 UMU (256 partículas) leva o bloco de ar perto do plasma, quase raio;
  - um bloco de fogo puro (16 UMU) derreteria uns 16 blocos de pedra.

  Os feitiços de fogo vão precisar de quantias pequenas.

**Ainda não estão no laboratório:**
- o calor que o derreter gasta;
- a água e a areia que escorrem e caem;
- a luz que atravessa o ar até a próxima coisa;
- o raio que corre (por enquanto só o plasma parado);
- a ligação com o mundo.

## Os seres propostos

Proporções propostas na etapa 6, para o usuário revisar. As colunas são as partes de cem: terra, água, ar e fogo.
Ficaram de fora os chefes (dragão do End e wither) e os que o jogo não faz nascer (gigante e ilusionista).

**Bichos de sangue quente (perto da pessoa e da vaca: muita água, bastante ar, um pouco de terra e de fogo)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| cavalo | 9 | 56 | 32 | 3 |
| burro | 10 | 58 | 29 | 3 |
| mula | 11 | 56 | 30 | 3 |
| lhama | 8 | 53 | 36 | 3 |
| lhama de comerciante | 9 | 52 | 36 | 3 |
| camelo | 9 | 48 | 39 | 4 |
| gato | 4 | 52 | 41 | 3 |
| jaguatirica | 4 | 49 | 44 | 3 |
| lobo | 7 | 55 | 34 | 4 |
| raposa | 5 | 52 | 38 | 5 |
| urso-polar | 13 | 57 | 27 | 3 |
| panda | 11 | 59 | 27 | 3 |
| cabra | 10 | 51 | 36 | 3 |
| coelho | 3 | 50 | 45 | 2 |
| vacogumelo | 9 | 61 | 27 | 3 |
| farejador | 16 | 55 | 26 | 3 |

**Gente (perto da pessoa, 5/55/38/2, cada um um pouco ao seu modo)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| aldeão | 6 | 57 | 35 | 2 |
| vendedor ambulante | 5 | 53 | 40 | 2 |
| saqueador | 8 | 52 | 37 | 3 |
| vingador | 10 | 50 | 36 | 4 |
| evocador | 4 | 48 | 41 | 7 |
| bruxa | 4 | 51 | 37 | 8 |

**Os que voam (o ar passa da metade)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| papagaio | 2 | 40 | 56 | 2 |
| abelha | 8 | 33 | 53 | 6 |
| phantom | 5 | 24 | 69 | 2 |
| allay | 1 | 14 | 82 | 3 |
| vex | 1 | 8 | 79 | 12 |
| ghast | 2 | 22 | 63 | 13 |

**Os da água (a água passa de dois terços, o ar é pouco)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| lula brilhante | 6 | 80 | 10 | 4 |
| bacalhau | 6 | 80 | 12 | 2 |
| salmão | 7 | 77 | 13 | 3 |
| peixe tropical | 5 | 81 | 12 | 2 |
| baiacu | 8 | 74 | 16 | 2 |
| golfinho | 6 | 69 | 23 | 2 |
| guardião | 15 | 72 | 8 | 5 |
| guardião ancião | 21 | 70 | 5 | 4 |
| axolote | 6 | 72 | 20 | 2 |
| sapo | 6 | 65 | 27 | 2 |
| girino | 4 | 78 | 16 | 2 |
| tartaruga | 22 | 63 | 12 | 3 |
| afogado | 14 | 76 | 8 | 2 |

**Os de casca (mais terra: a quitina)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| aranha | 18 | 40 | 38 | 4 |
| aranha da caverna | 15 | 44 | 35 | 6 |
| traça | 27 | 30 | 39 | 4 |
| endermite | 21 | 29 | 40 | 10 |

**Os mortos-vivos (quase sem fogo e com pouco ar: não aquecem nem respiram; os esqueletos são osso)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| zumbi | 20 | 62 | 14 | 4 |
| aldeão zumbi | 18 | 63 | 17 | 2 |
| zumbi-múmia | 30 | 36 | 29 | 5 |
| cavalo zumbi | 22 | 60 | 15 | 3 |
| esqueleto | 60 | 15 | 15 | 10 |
| errante | 56 | 30 | 14 | 0 |
| esqueleto wither | 55 | 5 | 15 | 25 |
| cavalo esqueleto | 58 | 18 | 16 | 8 |
| piglin zumbificado | 17 | 55 | 15 | 13 |
| zoglin | 22 | 47 | 14 | 17 |

**Os do Nether (mais fogo)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| piglin | 10 | 45 | 30 | 15 |
| piglin bruto | 15 | 42 | 28 | 15 |
| hoglin | 20 | 40 | 22 | 18 |
| lavagante | 15 | 20 | 15 | 50 |

**Os do End (o ar que dispersa: eles somem e reaparecem)**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| enderman | 10 | 30 | 50 | 10 |
| shulker | 56 | 10 | 29 | 5 |

**Outros**

| Ser | terra | água | ar | fogo |
|---|---|---|---|---|
| creeper | 15 | 40 | 35 | 10 |
| devastador | 26 | 46 | 24 | 4 |
| warden | 46 | 35 | 14 | 5 |

## Decisões tomadas

1. 4096 voxels por bloco, cada um uma partícula; 4096 de um elemento é aquele elemento (usuário, 30/09).
2. Os verbos: igni agita, aqua umidifica, firmo dá massa, aura dispersa (usuário, 30/09).
3. A falta dá o efeito reverso, e a conta passa de zero (usuário, 30/09).
4. Toda partícula vale a mesma UMU.
   - 4096 é o cheio natural, menos é rarefeito, mais é comprimido.
   - A pressão é partículas ÷ 4096.
   - As unidades por coisa saem.
5. Contar partículas por bloco, e só onde há perturbação.
6. Alquimia é criar as coisas naturais pelo código e transformar qualquer coisa natural ou ser em outro, com aparelho
   e com feitiço (usuário, 30/09).
7. A escala: 256 partículas fazem uma UMU; um bloco vale 16 UMU e um item 1 (usuário, 30/09).
8. O `vocant` vale 16 UMU, um bloco, por padrão; o que não fecha um bloco volta ao corpo (usuário, 30/09).
9. A captura de energia e a condensação passam às partículas numa etapa própria, com a intensidade medida como
   compressão (usuário, 30/09).
10. Os ingredientes de um ser entram só por feitiço, com `tenet … vocant`, como a água e o ar (usuário, 30/09).
11. Nenhuma runa fundida fica na língua, nem as que já existiam, nem `vita`: criar e transformar se escrevem com as
    runas que restam (usuário, 30/09).
12. Transformar é mudar o cerne: `fonte quantum N vertere m1` faz aquele elemento ser N das cem partes de m1, e o que
    m1 é sai do cerne (usuário, 30/09; em partes de cem, 01/10).
13. As mudanças de cerne do mesmo instante contam juntas (usuário, 30/09).
14. O que sobra vira vis e sai do alvo como luz (usuário, 30/09). Com o cerne em proporção, o que sobra é só o que não
    fecha um inteiro.
15. O `quantum` vem antes do verbo, como no livro (usuário, 30/09).
16. O cerne é proporção, cem partes. Mudá-lo converte o que o alvo já tem, de um elemento em outro, e nada falta nem
    vem de fora (usuário, 01/10).
17. As partes escritas são porcentagens exatas e o cerne sempre fecha cem; as não escritas dividem o resto como
    estavam (usuário, 01/10).
18. Sem número, os elementos escritos dividem a vida por igual: `aqua vertere m1` faz m1 todo água (usuário, 01/10).
19. O estado sai das partículas: a coisa nova aparece no estado natural dela (usuário, 01/10).
20. Itens continuam itens quando a coisa nova tem item (usuário, 01/10).
21. Cada linha de `vertere` paga o que converte, como todo `vertere`; nada de preço próprio para transformar (usuário,
    01/10).
22. Um ser vale a sua vida, 5 UMU por ponto, mais a âncora de 100 de vis; o humano vale 200 UMU (usuário, 01/10).
23. O jogador transformado mantém o que carrega, a vida e a experiência, e volta ao corpo de antes mudando o cerne de
    volta (usuário, 01/10).
24. Cada partícula tem um único impulso, que aplica a qualquer partícula; o jeito de reagir sai do impulso de quem
    recebe, e nada se escreve por par (usuário, 02/10).
25. Os impulsos: igni agita (quer se mexer), aura expande (quer espaço), aqua une (quer se juntar), firmo pesa e prende
    (quer ficar) (usuário, 02/10).
26. O calor é o igni solto; o do código fica preso. A queima solta o fogo preso quando o calor e o ar vencem o que o
    prende, e o ar sai preso na fumaça (usuário, 02/10).
27. Menos agitação é frio; sem agitação as partículas param, no zero absoluto, e a falta não passa dele (usuário,
    02/10).
28. O fogo fica perto do real: forte o bastante para passar de tronco em tronco (usuário, 03/10).
29. As partículas de fogo precisam de espaço para tremular. A massa ocupa espaço, e o que concentra firmo resiste a
    pegar fogo (usuário, 03/10).
30. Se a neve segura depende da agitação: quanto mais paradas as partículas, mais frio (usuário, 03/10).
31. O ar agitado vira fogo e, mais agitado, raio (usuário, 03/10). Os limites estão em aberto.
32. O que deveria carbonizar vira carvão vegetal ou derrete, com chance baseada no real (usuário, 03/10).

## Decisões em aberto

1. **As proporções dos 64 seres propostos** ("Os seres propostos"): esperam a revisão do usuário. Os chefes ficaram de
   fora.
2. **O que o corpo novo sabe fazer:** hoje o jogador transformado só tem a forma e o tamanho do ser. Voar como um
   morcego, respirar na água como um peixe, aguentar o fogo como um blaze ainda não vêm com o corpo.
3. **A tolerância dos códigos:** com centenas de coisas e seres na mesma tabela, ±5% não basta (cabem ~280
   códigos); ±2,5% dá ~1.770.
4. **As variantes** (carvalho e bétula, as cores da lã): código próprio, ou a mesma coisa com outra forma.
5. **O caldeirão:** como as coisas entram, se desfazem e saem.
6. **A Forma:** se e como ensinar, agora sem as formas fundidas.
7. **Os limites do fogo e do raio** (§8.7): o gás brilha e é fogo a partir de uns 525 °C, e vira plasma (raio) a partir
   de uns 5600 °C.
8. **A chance entre carvão e derreter:** saiu da velocidade do aquecimento, sem sorteio. Fica assim, ou entra sorteio?
9. **As escolhas para ficar perto do real:** o vapor de 9 para 1 (mudou na tabela) e o fogo que leva 50 de ar.
10. **Os códigos que mudam de nome quando aquecidos:** cobre, areia, pedra infernal, osso (§8.7).
