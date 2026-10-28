# A universidade como campus: a arquitetura

> "Um mago não é o que tem mais força. É o que entende melhor." (Fundamentos de Siglística, abertura)

**Status:** plano (20/10/2026), revisto com o autor no mesmo dia: um prédio só para todas as salas de aula, os
outros prédios pelo que cada um faz, e uma vila que se forma em volta. Nada implementado.

Hoje a universidade é **uma peça só**: uma caixa de 137 blocos, murada, desenhada por código (`UniversityPiece`), com a
terra moldada à caixa inteira (`beard_box`; docs/mago-design.md, 5g). Ela não cabe nos nove cursos e nas aulas
(docs/universidade-cursos-design.md). O autor quer **um complexo**: prédios, torres, casas e castelos, como uma escola
de magia de castelo e como as vilas do jogo.

## 1. A ideia

- **Os prédios se dividem pelo que se faz neles, não pelo curso.** Como numa universidade de verdade: **um prédio de
  aulas** com todas as salas de aula e as salas dos mestres, e em volta os prédios de uso: refeitório, auditório,
  oficina, arena, campo de prática, dormitórios, biblioteca.
- **Uma vila se forma em volta.** Uma universidade é um ponto comercial forte: quem vende papel, tinta, comida e
  gemas mora perto. Ao lado do campus nasce **uma vila do jogo**, do bioma, ligada a ele pela estrada.
- **O campus tem cara de castelo**: torres, agulhas, claustros, pontes entre prédios onde o terreno pede. A muralha
  fica só no núcleo (o Salão Central e a Biblioteca), com o portão.

## 2. Os prédios do campus

| Prédio | O que é | Quem está |
|---|---|---|
| **Salão Central** (a reitoria) | o Salão gótico de hoje: o estrado do Rector, a mesa do conselho, a admissão e os exames do conselho | o Rector |
| **Biblioteca e Arquivo** | as estantes, a galeria, a sala de leitura, a sala fechada (como hoje) | o guardião do Arquivo, os bibliotecários |
| **O Prédio das Aulas** | **todas as salas de aula**: as do primeiro ano e uma por curso; e **as salas dos nove Magisters**. Vários andares, uma ala por grupo de cursos, escadas e corredores | os nove Magisters, alunos |
| **O Auditório** | a sala grande em degraus, com o estrado: as aulas do primeiro ano para muitos, as demonstrações (o mestre conjura diante de todos) | alunos, nas horas de aula |
| **O Refeitório** | as mesas compridas e **a cozinha** (fornos, defumadores, fogões: a aula do `vertere`, comer e cozinhar) | cozinheiros (aldeões) |
| **A Oficina** | as mesas de galdraria, as bigornas, a forja, os rebolos, a mesa de invocação, a loja do Artífice (a Artificiaria; a aula do `reframe` na bigorna) | o Artífice, aprendizes |
| **A Arena** | o anel de duelos com arquibancada (Duelo Supervisionado); **o fosso das criaturas** embaixo (a aula do `impediunt`: ver-se cercado) | — |
| **O Campo de Prática** | os lugares de viver as runas do primeiro ano (seção 3) | — |
| **Os Dormitórios** | um prédio de vários andares, cheio de quartos; **um andar silencioso de vidro roxo, o dormitório do sonho** (onde o sonho vem com certeza; a Oneirologia) | alunos |
| **O Observatório** | a torre redonda com a cúpula de hoje (a Visão; a luneta da aula do `surgit`) | — |
| **O Laboratório** | cobre que verdeja, uma estufa: caldeirões, suportes de poção (a Conversão; a poção da aula do `vertere`) | — |
| **Os Estábulos** | baias, um pasto, um pombal com o correio (a Vinculação; a aula do `ligabis`) | cavalos, bichos |
| **A Enfermaria** | camas, os curandeiros que medem (o livro: ninguém cura o corpo de outro) | clérigos |

O pátio entre eles: a fonte, jardins, árvores, postes de luz, as avenidas.

## 3. O Campo de Prática

Uma área aberta ao lado do Prédio das Aulas, com uma estação para cada aula de Fonte e de Função que precisa de lugar:

| Aula | Estação | O que se vive ali |
|---|---|---|
| `igni` | **a câmara fria** (casa de gelo cavada no chão, neve fofa) e **a fornalha** (um poço de magma cercado) | o frio, a hipotermia, congelar; o calor, a febre, arder |
| `aqua` | **o tanque** (fundo, com escada) e **o canal** (água corrente) | afogar-se; ser levado pela correnteza |
| `aura` | **a torre da queda** (vinte e poucos blocos, um poço de feno embaixo) e **o fosso da explosão** | a queda longa; o sopro de uma explosão |
| `firmo` | **o poço de areia** e **a Descida**: um poço com escadas e patamares até abaixo do zero | ser soterrado; as profundezas |
| `iactare` | **o campo de tiro** (alvos, distâncias marcadas) e **o lago de pesca**, com o cais | soltar uma flecha, arremessar; pescar |
| `surgit` | **o sítio de escavação** (areia suspeita de verdade) | escovar um achado |

**Como ficou** (27/10/2026, `CampusBuildings.campo`, a peça `CAMPO` ao sul do anel, com um ramal da estrada): cada
estação fica onde o próprio jogo sente a experiência da runa (`nahva/Understanding`), então viver ali já conta para a
aula, sem passo novo.

- **A torre da queda** no meio, o marco do campo: escada em caracol por dentro, uma prancha para fora no alto, 22 blocos
  sobre um fosso de feno (o jogo conta a queda a partir de 20).
- **O fosso da explosão**, forrado de ardósia, com escada de mão; na borda, um barril com 16 TNT e um isqueiro, e um
  parapeito para se abrigar. (Um dispensador ao lado da explosão seria destruído.)
- **O tanque**, sete de fundo, e **o canal**: uma nascente na cabeça e um degrau a cada oito blocos, para a água correr
  o canal inteiro.
- **A câmara fria**, casinha redonda de cone de neve, gelo compactado e neve fofa de dois de fundo; **a fornalha**, um
  fosso de fogo eterno sobre netherrack, cercado, com portão.
- **O poço de areia**: quatro celas; uma tocha na parede segura a areia acima da cabeça, e tirá-la a derruba. Um baú ao
  lado tem areia e tochas para remontar.
- **A Descida**: uma torrezinha sobre um poço redondo que desce até a altura -4 do mundo, a escada em caracol em volta de
  um pilar, glowstone nas paredes.
- **O campo de tiro** (alvos a 10, 20 e 30), **o lago com o cais**, **o sítio de escavação** com areia suspeita de
  verdade.

As outras aulas têm o lugar delas nos prédios: a cozinha e o Laboratório (`vertere`), os Estábulos (`ligabis`), a
Oficina (`reframe`), a Arena (`impediunt`), a Biblioteca e o Observatório (`surgit`); `vocant` se vive em qualquer
lugar. As aulas ganham **um passo "estar em"**: o mestre manda o aluno a uma estação, e o jogo confere que ele está
lá.

### A Arena (27/10/2026, `CampusBuildings.arena`, a peça `ARENA` a oeste do Campo de Prática)

- **O anel de duelos:** muralha redonda aberta para o céu, com arcos em volta e parapeito; dentro, a arquibancada em
  cinco degraus virada para o meio; o chão de duelo de arenito, com mureta e uma grade de ferro no centro; quatro
  torres de cone nas diagonais; o portal a leste, por baixo da arquibancada, com um ramal da estrada do anel.
- **O fosso das criaturas:** sala redonda e escura sete blocos abaixo do chão, um gerador de **zumbis** no meio
  (vem de perto, não atira pela grade, não sobe escada de mão). Desce-se por uma escotilha perto do portal e uma escada
  de mão; os monstros não abrem escotilha. Com quatro em volta, o jogo conta "cercado" (a aula do `impediunt`).
- **O Duelo Supervisionado** ainda não tem mecânica: a Arena fica pronta para ele.

### O Laboratório e os Estábulos (27/10/2026, peças `LABORATORIO` e `ESTABULOS`, a leste do Campo de Prática)

- **O Laboratório** (a Conversão, a aula do `vertere`): a torre de cone de cobre que verdeja, o recipiente da
  conversão no meio (caldeirão d'água sobre pedra talhada), quatro caldeirões em volta, três suportes de poção nos
  balcões, barris, estantes em cima; e a **estufa** redonda de vidro entre nervuras de cobre, com cúpula de vidro,
  canteiros de fungo do Nether, cenoura, batata e cana. Entra-se pela estufa, a oeste.
- **Os Estábulos** (a Vinculação, a aula do `ligabis`): a estrebaria redonda de cone baixo de madeira, oito baias com
  portões e feno, o pátio de feno com o bebedouro, **cavalos nas baias**; **o pombal**, torrezinha de ardósia com a
  parede cheia de buracos de ninho, o "correio" no térreo (só cenário por enquanto); **o pasto** cercado com
  bebedouro, **vacas e ovelhas**.
- A estrada do anel desce pelo lado leste do Campo de Prática, com um ramal até cada porta.

### O Auditório (27/10/2026, peça `AUDITORIO`, ao norte do núcleo)

- Uma rotunda grande com parapeito e cone largo, duas torrezinhas de cone atrás. Dentro, um teatro: fileiras curvas em
  oito degraus subindo em leque a partir do estrado do mestre (o atril, os estandartes), um corredor no meio e um de
  cada lado; diante do estrado, um chão de pedra lavrada para as demonstrações; janelas altas em volta; uma galeria
  pela parede do fundo, por escada de mão nas torrezinhas; as portas na frente, ao lado do estrado.
- **O caminho:** a muralha do núcleo ganhou um **portão norte**; um caminho de pedra sai do caminho de dentro do núcleo,
  passa entre a Reitoria e a Torre de Pesquisa, sai pelo portão e chega à porta sul do Auditório.
- Nenhum Magister nasce ali ainda: as aulas para muitos e as demonstrações não têm mecânica.

## 4. A vila em volta

- **Uma vila do bioma**, gerada junto com o campus, pelo mesmo modo que o jogo usa para as suas (as peças das vilas de
  planície, deserto, taiga, savana ou neve), do lado do arco de entrada, e ligada a ele pela estrada.
- **O que a universidade traz à vila** (proposta, mais adiante): aldeões que vendem o que a casa precisa (papel,
  tinta, livros, gemas), e mais deles do que numa vila comum.
- **As outras vilas** deixam de ser afastadas: a zona de exclusão de 10 chunks (docs/mago-design.md, 5g) sai, e vilas
  podem nascer perto, como nasceriam em qualquer lugar.

## 5. Como se gera

### 5.1 Várias peças em vez de uma

Como as vilas: **um planejador** monta o traçado no começo da estrutura, e cada prédio vira **uma peça própria**
(`StructurePiece`), com a sua caixa, a sua altura de chão e a sua rotação.

1. **O núcleo:** o Salão Central e a Biblioteca, com a muralha curta, no ponto mais alto de uma área perto do centro.
2. **As avenidas:** do portão do núcleo até o arco de entrada, e em volta do núcleo; elas **seguem o terreno** bloco a
   bloco, com escadas onde a subida é forte.
3. **Os lotes**, ao longo das avenidas: cada prédio da seção 2 tem um tamanho, e o planejador os assenta sem que um
   pise no outro nem na rua; o Campo de Prática fica num lote grande e plano.
4. **Cada prédio** fica na altura do chão do seu lote, virado para a avenida, com o alicerce até a terra.
5. **O terreno se molda a cada peça:** de `beard_box` para `beard_thin`, como as vilas.
6. **A vila:** a montagem de vila do próprio jogo (`JigsawPlacement`), começando do lado de fora do arco, com as peças
   do bioma.

### 5.2 Os limites do jogo

- **O alcance:** o jogo só gera as peças a até 8 chunks (128 blocos) do chunk onde a estrutura começa. Campus e vila
  juntos cabem nisso se a estrutura começar entre os dois: o campus de um lado (uns 70 blocos de raio), a vila do
  outro (uma vila do jogo vai até uns 80 blocos do centro dela; aqui, um pouco menor).
- **O que se reaproveita:** `Masonry` e `Hall`; o Salão Central, a Biblioteca, o Observatório, a oficina, a
  enfermaria, o refeitório e o dormitório de hoje viram peças.
- **Os mundos que já têm universidade** ficam com a deles: a peça antiga continua registrada para o jogo ler o que já
  foi gerado. A nova só nasce em terra nova.
- **A luz:** nenhum lugar escuro onde um monstro nasça, em cada peça.

### 5.3 Os lugares para as aulas

Cada peça sabe **o que ela é** (`camara_fria`, `arena`, `dormitorio_do_sonho`...). Para saber se o aluno está num
lugar, basta perguntar ao jogo que peça da universidade está naquele ponto. Nada a mais para salvar.

## 6. A ordem

1. **O esqueleto:** o planejador, o núcleo (Salão Central e Biblioteca), as avenidas, os lotes, `beard_thin`; os
   prédios de hoje como peças soltas.
2. **O Prédio das Aulas**, com as salas e os nove Magisters (junta com o passo 5 dos cursos); **os Dormitórios** com o
   andar do sonho.
3. **O Campo de Prática**, o passo de aula "estar em".
4. **O Auditório, a Arena, a Oficina, o Laboratório, os Estábulos.**
5. **A vila** ao lado, e o que a universidade traz a ela.
6. **O acabamento:** pontes e escadarias entre níveis, mais variação.

## 6.1 Passo 1, o esqueleto (20/10/2026)

- **A estrutura** (`UniversityStructure`) planeja o campus quando começa: das quatro maneiras de deitar o campus (a
  vila a leste, oeste, norte ou sul), a que põe o núcleo mais alto. O campus fica 56 blocos atrás do ponto onde a
  estrutura começa; a vila, 72 à frente.
- **Cada prédio é uma peça** (`CampusPiece`), na altura do chão dele (a terra amostrada numa grade de 5 por 5, um pouco
  abaixo do meio): o núcleo, o pátio da fonte, o Prédio das Aulas, os Dormitórios, o Refeitório, a Oficina, a
  Enfermaria e o arco. O terreno se molda a cada um (`beard_thin`).
- **As avenidas** (`CampusRoadPiece`) são trechos de até 12 blocos, cada um de uma altura à outra, nunca mais que um
  bloco a cada dois: do portão do núcleo ao pátio, do pátio ao Prédio das Aulas, a avenida até o arco, a do
  Refeitório, o anel em volta do Prédio das Aulas e um ramal até cada porta. Postes de luz nas largas.
- **O núcleo** é o Salão Central, a Biblioteca e o Observatório de antes, deslocados para dentro de uma muralha curta
  com torres e o portão para o pátio.
- **O Prédio das Aulas** em cruz: o bloco central do primeiro ano (o hall, o corredor, a escadaria, quatro salas em
  cada andar) e as três alas (corredor no meio, salas dos dois lados, uma escada no fim). Salas de aula com o atril
  do mestre, bancos e estantes; salas de mestre com mesa e estantes. **Os Magisters de hoje**, até os nove (passo 5
  dos cursos), ficam nas salas das cadeiras que dão: o das Fontes em Fundamentos I, o das Funções em Fundamentos II,
  o da Vita em O Corpo e o Cálice, o de Nahvá em Transe I, o dos Fatos em Segurança e Ética, o dos Filtros numa sala
  de mestre da Ala da Força, o da Galdraria na sala da Artificiaria.
- **Os Dormitórios**: quatro andares de quartos (duas camas e um baú cada), a sala comum com lareira no térreo, a
  escada em vaivém, e o último andar, o do sonho: vidro roxo, tapete roxo, uma cama por quarto. Dormir nos
  Dormitórios é o "dormir na casa" das aulas (a vila não conta).
- **A vila** é a do próprio jogo, do bioma do lugar do poço (planície, deserto, savana, neve, taiga), montada com as
  peças das vilas a até 56 blocos do poço. A zona de exclusão das vilas saiu (o arquivo do jogo voltou a ser o
  original).
- **O Emissário** traz o jogador ao pátio da fonte.
- **Ainda não:** o Auditório, a Arena, o Campo de Prática, o Laboratório e os Estábulos (os lotes ficam de grama), o
  quarto do jogador, os ofícios na vila.
- As universidades já geradas continuam como eram: a nova só nasce em terra nova.

## 6.2 A arquitetura refeita sobre as plantas (20/10/2026)

- **A casa do campus** (`CampusHouse`, no lugar do `Hall` que as universidades antigas guardam): embasamento de pedra
  bruta com musgo, faixa de pedra polida em cada andar, peitoril e verga em cada janela, contrafortes nas paredes
  longas, mísulas sob o beiral, empenas com duas lancetas e um óculo nas largas, lucarnas acesas no telhado.
- **Os prédios de uso são de enxaimel em cima da pedra**, cada um com a sua madeira: o Refeitório e os Dormitórios
  de carvalho-escuro e calcita, a Oficina de abeto e barro, a Enfermaria de bétula. Chaminés onde há fogo.
- **O Prédio das Aulas** ganhou a torre sobre o hall de entrada: o hall de pé-direito duplo com a galeria atrás
  (o "vazio sobre o hall" das plantas), o portal em arco ogival, óculos nas faces, o campanário com o sino, a agulha.
  As alas ganharam a sala de estudo (térreo) e a biblioteca da ala (em cima), como na prancha 02.
- **O Refeitório** segue a prancha 05: o salão das mesas ao norte, a cozinha ao sul com duas lareiras sob as
  chaminés, a despensa ao lado. **A Oficina** também: as mesas de galdraria e a forja ao norte, a mesa de invocação,
  o balcão do Artífice e o depósito ao sul, um alpendre de trabalho do lado de fora.
- **Os Dormitórios viraram uma torre redonda** de oito níveis (21/10/2026, escolhida pelo autor entre uma quadrada e
  uma redonda): no térreo a sala comum com a lareira e o hall de entrada; em cada andar acima, uma parede no diâmetro
  faz meio andar de quarto para dois alunos (cama, baú, escrivaninha) e meio de hall com a escada em vaivém; no alto,
  o andar do sonho, de vidro e camas roxas. São 14 camas. Por fora: o pé alargado, uma faixa a cada andar, janelas em
  volta, a chaminé da lareira pela face oeste, mísulas e ameias, um cone íngreme com janelas roxas acesas.
- **Os andares da torre são flats, e se sobe lendo** (21/10/2026, ideia do autor; o atril em 27/10/2026): é uma
  casa de magos, ninguém precisa de escada. No hall do térreo, diante da parede do meio, o **Grimório dos andares**
  preso a um atril: uma página por andar, `ego aN_<código> ubis vocant`. O aluno abre o atril na página do seu andar
  e lê com `surgit` mirando o atril (docs/grimorio-design.md, o atril). No chão de cada flat está o pergaminho de
  chegada, marcado `aN_<código>`, cujo texto (`ego a0_<código> ubis vocant`) leva de volta ao pergaminho `a0` do hall.
  O `<código>` são três letras da própria casa, tiradas de onde ela está, para duas universidades no mesmo mundo nunca
  responderem à mesma marca. Quem não tem energia para ler pede ao **Auditor que guarda a torre**, no hall: ele marca o
  aluno por um instante e lê `aN_<código> ubis <marca> vocant` por ele, a casa pagando com a gema do seu bastão, como o
  Emissário. Para emergências, **uma torre de escada** colada na face sul (27/10/2026): a escada de mão sobe por dentro
  dela, com um patamar e uma lanterna em cada andar diante da porta de ferro do flat, que só abre por dentro (um botão
  no quarto), para quem sobe não entrar no quarto dos outros; no pé, uma porta para fora. Cada flat é inteiro de dois
  alunos: camas, baús, escrivaninhas, mesa, estantes, guarda-roupa.
- **A torre é a identidade das construções do mod** (27/10/2026, decidido com o autor): toda casa de magos tem ao menos
  uma torre redonda com cone de ardósia. Os prédios de uso ganharam a sua (substituído logo depois: ver abaixo), num canto, aberta para dentro como um cômodo
  a mais: o Refeitório, a **torre da cozinha** (a grande lareira, a chaminé saindo pela encosta do cone); a Oficina, a
  **torre da forja**, de cone de cobre que verdeja com os anos (a fornalha de magma, os altos-fornos, a bigorna); a
  Enfermaria, a **torrezinha de ervas** (as bancas de poção embaixo, um sótão de secar ervas por escada de mão).
- **O resto do campus na mesma identidade** (27/10/2026): o Prédio das Aulas ganhou seis torrezinhas redondas nos
  cantos de fora das alas (abertas para o canto das salas, nos dois andares, com janelas acima do telhado); a portaria
  trocou as torres quadradas por **duas torres redondas com cone**, cada uma com porta para o campus e um sótão por
  escada de mão; o pátio ganhou a taça de cima da fonte, bancos voltados para ela (o caminho norte–sul livre) e
  canteiros de flores nos cantos. O núcleo antigo fica para depois (é do `UniversityPiece`).
- **Os prédios de uso feitos de torres** (27/10/2026, pedido do autor: "torrificar", em vez de casas com torres
  acopladas): cada um é um grupo de três torres redondas de alturas diferentes, abertas umas para as outras onde as
  paredes se cruzam. A torre larga e baixa leva parapeito com ameias e o cone por dentro; as finas e altas, beiral
  com mísulas e cone íngreme. Todas com o pé alargado, faixa em cada andar, janelas de vidro inteiro em volta.
  O **Refeitório**: o salão (raio 8, mesas compridas dos dois lados do caminho da porta à lareira, despensa no andar
  de cima), a cozinha alta ao sul (lareira, fogões, defumadores) e a torre da escada a nordeste (escada de mão até a
  despensa e um mirante). A **Oficina**: a oficina larga (mesas de galdraria, mesa de invocação, balcão do Artífice,
  sótão), a forja, a mais alta, de cone de cobre (a fornalha de magma sob a chaminé, a bigorna, o alto-forno) e o
  depósito baixo com os baús. A **Enfermaria**: a ala (camas brancas em duas fileiras, cabeceira na parede, as bancas
  dos curandeiros no fundo), a torre das ervas (poções embaixo, sótão de secar ervas) e uma torrezinha de vigia com a
  luz na janela alta. As chaminés sobem por dentro e saem pela encosta do cone.
- **O Prédio das Aulas feito de torres** (27/10/2026, no lugar das alas retangulares): na frente a **torre do
  sino**, a mais alta, com o portal para o pátio e o sino em arcos abertos sob o cone; atrás dela a **torre central**
  (o hall de entrada, a escada em caracol em volta de um pilar, salas de estudo nos andares 6 e 12, a biblioteca da
  casa nos andares 18 e 24); e uma **ala em cada lado** (Força a oeste, Sentidos a leste, Ofício ao sul), cada uma uma
  torre larga de três andares em que **cada andar é um curso**: a sala de aula do lado da torre central, a sala do
  Mestre além de uma parede com porta. As alas se abrem para a torre central em todos os andares, e a única escada
  serve o prédio inteiro. Na ponta de cada ala, uma torrezinha fina com escada de mão até um mirante. O prédio desceu
  dois blocos para o sul, e o anel de estrada em volta dele passou para z 64.
- **O núcleo em torres** (27/10/2026; as universidades antigas, do `UniversityPiece`, continuam como eram):
  - **a Reitoria** (o Salão Central): a rotunda do conselho, larga e com parapeito, a mesa redonda no meio com as
    cadeiras em volta, o tapete da porta até ela, o estrado do Reitor ao norte (o assento, o atril, os estandartes);
    duas torres de agulha ladeando o portal de porta dupla, a rosácea entre elas; atrás, a torre do Reitor, a mais alta
    do núcleo, com os aposentos dele por escada de mão;
  - **a Biblioteca** (o Arquivo): a torre larga com as galerias em anel em volta de um poço, a escada em caracol no
    poço com uma ponte para cada galeria, estantes do chão ao teto, estantes em raio no térreo; o Arquivo fechado numa
    torre atrás da porta de ferro (que continua sem abrir, até se decidir quem abre); a sala de leitura numa torre na
    frente, onde ficam o Magister do Arquivo e o Lector;
  - **a Torre de Pesquisa** no lugar do observatório (docs/torre-pesquisa-design.md).
- **O arco** virou uma portaria: duas torres quadradas com seteiras e ameias, o arco ogival sobre a avenida, o
  passadiço em cima, os estandartes da casa.

## 7. Decidido com o autor (20/10/2026)

1. **A vila é colada ao campus**, gerada junto com ele (seção 4).
2. **O Salão Central fica separado** do Auditório: é a reitoria.
3. **O aluno admitido ganha um quarto** nos Dormitórios, que vira o lugar onde renasce.
4. **O Prédio das Aulas tem uma ala por grupo de cursos:**
   - **a Ala da Força** (oeste): Evocação, Guarda, Conversão, os que agem no mundo;
   - **a Ala dos Sentidos** (leste): Visão, Vinculação, Contramagia, os que veem, ligam e desarmam;
   - **a Ala do Ofício** (sul): Ritual, Oneirologia, Artificiaria, os que preparam, sonham e fazem;
   - **o bloco central**: a entrada, a escadaria e as salas do primeiro ano.

As plantas (o campus e a vila, o Prédio das Aulas, os Dormitórios, o Campo de Prática e os outros prédios) estão no
artefato "Plantas da Universidade".
