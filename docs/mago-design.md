# O Mago, as torres e as universidades

> "Tudo o que é medido pode ser transformado." (Grimório de EVL, cap. 3)

**Status:** passos 1 e 2 feitos (13/10/2026): a mente comum, o Mago com as trocas dele (5a a 5c) e o bibliotecário
(5d); passo 3 feito (13/10/2026): as torres (5e); passo 4 feito (13/10/2026): a casa do mago nas vilas
(5f); passo 5, primeira parte (13/10/2026): a universidade, o lugar e quem mora (5g); segunda parte: o exame, a mensalidade, os graus, e conjuradores civilizados (5h); as aparências (5i). Segue docs/feiticeiro-design.md (seção 10a).

A segunda fase dos conjuradores do mod. O Feiticeiro mostrou que uma criatura pode conjurar pelas regras do jogador;
agora a língua ganha quem a usa sem ser inimigo, e lugares onde ela é ensinada.

## 1. O Mago (*Wizard*)

A versão neutra do Feiticeiro. **Funciona igual** (a mesma mente do pacote `mind`: catálogo, intenções,
personalidade, grimório escrito fora de combate, memória do oponente, cópia de frases, aprender runas vivendo-as e
sonhando-as), mas **não é maligno**: não caça ninguém. Se for atacado, se defende, e lembra de quem o atacou.

- **Que criatura é.** Proposta: um `AbstractVillager`, como o mercador ambulante: anda com objetivos (o que a mente e
  o transe já usam), negocia pela tela de trocas de vanilla e é contado como gente pelos golens e pelos Illagers
  (que o caçam como caçam aldeões).
- **Quem é seu alvo.** Quem o feriu, e **qualquer um que ferir um aldeão** perto dele, como um golem de ferro faria
  (decidido em 12/10/2026). Esquece depois de um tempo em paz. Nunca quem não feriu ninguém.
- **A mesma mente, sem copiar o código.** Hoje a mente vive dentro do `Sorcerer`. Ela sai para uma peça própria
  (o conjurador: grau, personalidade, runas, entendimento, páginas escritas, memória, transe), usada pelos dois. O que
  é só do Feiticeiro (o bando, as marcas, a pose de evocador, a troca nas mansões e raids) fica nele.
- **O que o diferencia.** Sem bando para chamar. Com uma personalidade a mais, talvez: o **estudioso**, que pesa mais
  sumir e se afastar do que ferir (um mago foge antes de matar).
- **Onde vive.** Dentro das vilas (numa casa própria) e fora delas (numa torre isolada). Volta para casa à noite.

## 2. As trocas

O Mago negocia; e os aldeões que já têm a ver com magia passam a oferecer coisas da língua. Nada disso ensina runas:
**ler não ensina** (docs/aprendizado-runas-design.md). Um guia diz o que uma runa é e **como vivê-la** para entendê-la;
o sonho continua sendo o caminho.

| Quem | Oferece |
|---|---|
| **Mago** | varinhas (comuns e montadas), pergaminhos com uma frase pronta, grimórios com páginas prontas (como o que o Feiticeiro deixa), engastes carregados |
| **Bibliotecário** | **guias de runas**: um livro por runa, com o que ela é e as experiências que levam a entendê-la; guias de gramática (como os filtros, as marcas, os tempos funcionam) |
| **Clérigo** | o que é da Vita: o diagnóstico do corpo (quanto falta de cada elemento), receitas de cura |
| **Armeiro / ferramenteiro** | armas e ferramentas com runas **gravadas** (galdraria): uma espada com `ferit igni iactare`, uma picareta com o que for útil |

Proposta: começar pelo Mago e pelo bibliotecário; os outros depois, um a um.

**Os aldeões da magia se defendem com magia** (decidido em 12/10/2026). O bibliotecário, o clérigo, o armeiro e o
ferramenteiro têm a âncora de Vis de qualquer ser e algumas frases que sabem: feridos, entram em transe e lançam uma.
Os aldeões de vanilla não andam por objetivos (têm um "cérebro" próprio), então o transe deles é mais simples que o do
mago: param onde estão pelos dois segundos e leem uma frase da profissão (o clérigo tira e cura, o armeiro lança,
o bibliotecário some).

## 3. As estruturas, geradas por código

O autor prefere que eu gere as construções. Duas formas, e a proposta usa as duas:

- **Peças desenhadas por código** (`StructurePiece`): a torre e a universidade são desenhadas bloco a bloco, com
  variação (altura, número de andares, materiais do bioma). Não pedem nenhum arquivo `.nbt`.
- **A casa do mago na vila** precisa entrar no sistema de peças das vilas de vanilla (jigsaw), que só aceita
  estruturas salvas (`.nbt`). Proposta: um comando de desenvolvimento que **constrói a casa por código num mundo e a
  salva** como estrutura, e o arquivo vai para o mod; assim ela continua "gerada", sem ser montada à mão.

| Estrutura | Quem mora | Onde |
|---|---|---|
| **Torre do feiticeiro** | um Feiticeiro (às vezes com o bando dele em volta) | florestas escuras, lugares ermos; rara |
| **Torre do mago** | um Mago | fora das vilas, em qualquer bioma de superfície; rara |
| **Casa do mago** | um Mago | dentro das vilas, como uma casa a mais |
| **Universidade** | vários magos, mestres, aldeões | uma por região grande; muito rara |

A torre clássica: redonda, de pedra, três a cinco andares ligados por escada em caracol; embaixo, a entrada e um
depósito; no meio, a biblioteca (estantes, um atril, a mesa de galdraria); no alto, o quarto e o observatório
(telhado cônico). A do Feiticeiro, mais escura (ardósia, velas), com uma sala de estudo; a do Mago, mais clara.

## 4. As universidades

O sistema que o autor tem em mente vem de uma universidade de magia da ficção (só como referência: **nada no jogo cita
o livro**, e nenhum nome vem dele). Traduzido para o mod:

### 4.1 Entrar: o exame

- Para estudar, o jogador **pede admissão**. Um conselho de mestres o examina, numa **tela de diálogo** como as dos
  jogos de RPG (a fala do mestre, e as respostas como escolhas; decidido em 12/10/2026): perguntas sobre a língua (o que uma
  runa é, o que uma frase faz, como um filtro age), geradas do léxico; e, para quem já conjura, um pedido prático
  ("faça a água sair daquele balde", "acenda a vela daqui").
- O exame fixa a **mensalidade**, em esmeraldas (decidido em 12/10/2026), por período: quem responde mal paga caro; quem vai muito bem paga
  pouco, nada, ou até **recebe** da universidade. A cada período há novo exame, e a mensalidade muda com ele.
- Um período: **sete dias** do jogo (decidido em 12/10/2026).

### 4.2 Os graus

Três graus de estudante, depois o de mestre, com nomes na Língua Antiga (decidido em 12/10/2026; no jogo são palavras
da Língua Antiga, nunca de uma língua do mundo real). Cada grau abre mais da universidade.

| Grau | Como chega | O que abre |
|---|---|---|
| **Auditor** (o que ouve) | ser admitido | as aulas (os mestres respondem perguntas), a sala de leitura do Arquivo |
| **Scriba** (o que escreve) | ser aprovado no exame de um mestre, numa disciplina | o acervo do Arquivo; a oficina de galdraria (vender o que grava) |
| **Lector** (o que lê) | ser aprovado em várias disciplinas e ter um mestre que o aceite | as salas fechadas do Arquivo; a enfermaria como curador |
| **Magister** (o mestre) | (mais adiante) | a cadeira de uma disciplina |

O conselho é presidido pelo **Rector**.

### 4.3 Os lugares

- **O Arquivo:** a biblioteca. Guias de runas, gramáticas, grimórios antigos (páginas compostas por magos que
  existiram: o catálogo da mente, escrito como obra de autor). O acesso vai pelo grau: a sala de leitura para todos,
  o acervo para escribas, as salas fechadas para leitores. Ser pego com fogo dentro do Arquivo é falta grave.
- **A oficina:** galdraria. O estudante grava runas em objetos com a mesa de gravação; a universidade vende o que ele
  faz e lhe paga uma **comissão**: o jeito de pagar a mensalidade trabalhando.
- **A enfermaria:** a Vita. Curar com diagnóstico (ler quanto falta de cada elemento num ferido e dar exatamente o
  que falta): o que o vita-design chama de próximo passo.
- **As salas dos mestres:** uma por disciplina.

### 4.4 As disciplinas e os mestres

Uma por família e arte da língua; cada uma com um mestre (um Mago ancião):

| Disciplina | O que ensina |
|---|---|
| Fontes | as quatro energias e a Vis |
| Funções | os verbos |
| Filtros | quanto, quando, onde, de onde |
| Fatos | as condições e a cena (`ego`, `ille`) |
| Galdraria | gravar runas nas coisas |
| Vita | o corpo, a cura, a alquimia dos seres |
| Nahvá | o sonho, o kern, o aprender |
| O Arquivo | quem guarda os livros |
| O Reitor | quem preside o conselho e os exames |

### 4.5 A disciplina da casa

Conjurar contra alguém dentro da universidade, roubar do Arquivo, pôr fogo nos livros: o jogador é chamado diante do
conselho. A pena vai de multa e suspensão do Arquivo até a expulsão. Quem é expulso não volta a ser examinado por um
tempo.

### 4.6 Quem mais vive lá

Estudantes (magos aprendizes, com a mesma mente: aprendem conforme vivem), mestres (magos anciões), e aldeões que
servem a casa: bibliotecários do Arquivo, clérigos da enfermaria, o mercador da oficina.

## 5. Ordem proposta

1. ~~A mente sai do `Sorcerer` para uma peça comum; o **Mago** nasce dela, neutro, com as trocas dele.~~ Feito.
2. ~~O **bibliotecário** com os guias de runas.~~ Feito (seção 5d).
3. ~~A **torre do feiticeiro** e a **torre do mago** (peças por código).~~ Feito (seção 5e).
4. ~~A **casa do mago** nas vilas.~~ Feito (seção 5f).
5. A **universidade**: ~~primeiro o lugar e quem mora~~ (feito, seção 5g); ~~depois o exame, a mensalidade e os graus~~
   (feito, seção 5h); depois o Arquivo e a oficina; por fim a disciplina da casa.
6. Os outros aldeões (clérigo, armeiro, ferramenteiro).

## 5a. Como ficou o passo 1 (12/10/2026)

- **A mente comum (`caster/CasterMind`).** Tudo o que fazia do Feiticeiro um conjurador saiu dele: grau,
  personalidade, runas, entendimento e sonho, páginas escritas, memória, variações e cópias, o transe (escolher,
  recitar, lançar, mirar, quebrar), o grimório, e os objetivos de transe e de escrita. A criatura dona dá a ela um
  corpo (`CasterMind.Body`): como o transe aparece, quem são os aliados, o que mais pode ler, se pode escrever agora.
  A cópia de frases vale para qualquer conjurador (`caster/RuneCaster.Witnesses`). O `Sorcerer` ficou com o que é dele:
  o bando, as marcas, os braços de evocador e o loot.
- **O Mago (`wizard/Wizard`, `elderlexicon:wizard`, com ovo na aba do mod).** Um `AbstractVillager` (como o mercador
  ambulante), com a mesma mente. Não caça ninguém: o alvo é quem o feriu (e os magos perto são avisados) e quem feriu
  um aldeão a até 16 blocos, nos últimos 5 segundos. Esquece o alvo quando ele morre ou se afasta mais de 32 blocos.
  Na briga, mantém distância (se afasta de quem chega a menos de 5 blocos). Escreve as páginas quando está em paz e
  não negocia nesse meio-tempo.
- **As trocas dele:** varinha improvisada (4 esmeraldas), varinha (14), uma página do grimório dele arrancada (6
  esmeraldas e um papel; aparece depois que ele escreve) e o grimório inteiro (24 esmeraldas e um livro). As ofertas
  são refeitas quando ele escreve páginas novas.
- **A aparência** é provisória: o modelo de aldeão com a textura do mercador ambulante (a do jogo, só referida). Falta
  uma textura própria.
- Ainda não aparece sozinho no mundo: só pelo ovo, até as estruturas (passos 3 e 4).
- `/runas mente` mostra o mago ou o feiticeiro mais próximo.
- GameTests `aWizardDefendsAHurtVillager`, `aWizardLetsBeAndTrades`, `aWizardFightsWhoeverHurtsIt`.

## 5b. A conversa entre conjuradores (pedido do autor, 12/10/2026)

- **Neutros entre si.** O Feiticeiro não caça Magos (os aldeões continuam sendo presa dele) e o Mago não caça
  Feiticeiros. O Mago ainda ataca o Feiticeiro que ferir um aldeão perto dele, e qualquer um revida a quem o fere.
- **Eles conversam, na Língua Antiga.** Em paz e sem estar escrevendo o grimório, de vez em quando um conjurador
  procura outro a até 10 blocos, os dois se aproximam e se olham, e falam por turnos (4 a 8 falas, uma a cada 2,5 s).
  As palavras aparecem sobre a cabeça de quem fala, em runas, como numa leitura, só que sem transe e sem efeito. O
  jogador vê *que* eles conversam e como cada fala foi recebida (partículas de aldeão feliz ou bravo, e o som: "sim"
  e "não" do aldeão para o Mago, o evocador para o Feiticeiro), mas só entende quem conhece as runas.
- **O que dizem:** algo próprio (uma página que escreveu, uma frase que compôs ou copiou, ou qualquer frase que sabe
  ler); a frase do outro dita de volta (concordar); ou a frase do outro *corrigida* (o mesmo feito com outra energia
  ou outro número).
- **Como recebem:** ser corrigido irrita mais; ouvir runas que não conhece irrita um pouco; ouvir a própria frase de
  volta agrada. O temperamento pesa (agressivo 1, trapaceiro 0,6, cauteloso 0,35), e um Feiticeiro e um Mago se irritam
  uma vez e meia mais um com o outro (escolas diferentes). Ouvir também ensina: as runas desconhecidas da fala contam
  como uma experiência delas.
- **A briga.** Quando a irritação com o outro passa de 2, os dois partem para um duelo. O duelo acaba quando um cai
  abaixo de 35% da vida: ele cede, os dois esquecem o alvo e ficam cerca de 5 minutos sem conversar. Fora do duelo,
  a irritação cai pela metade a cada conversa. As magias são de verdade, e um duelo pode matar.
- Os conjuradores aprendem também vendo outros conjuradores lançar (antes só aprendiam com jogadores).
- Testes: `aSorcererLeavesAWizardBe`, `twoCastersTalk`, `aQuarrelLastsUntilOneYields`.

## 5c. Mais vivos: animações, voz e os blocos da magia (pedido do autor, 12/10/2026)

- **Sem partículas ao escrever.** As runas flutuando (partículas de encantamento a cada 4 ticks) pesavam com muitas
  criaturas. No lugar delas, o conjurador segura o grimório aberto diante do peito, com as páginas virando, como o
  livro da mesa de encantamento (`caster/client/GrimoireBookLayer`). É só desenho no cliente, sincronizado por um
  dado da entidade; não custa nada ao servidor. Continua o som de páginas.
- **Os braços do evocador para todos.** O Feiticeiro já levantava os braços no transe (é um evocador). O Mago ganhou
  um modelo próprio (`wizard/client/WizardModel`): o corpo de aldeão com braços separados, recortados de onde a
  textura de aldeão guarda os braços. No transe, os braços cruzados se abrem e sobem balançando, como os do
  evocador, com a mesma luz de feitiço nas mãos.
- **A voz.** O Mago já tinha os sons de aldeão (ambiente, dano, morte, troca), e um GameTest confirma que o servidor
  os toca. Ganhou uma voz um pouco mais grave. Os sons de aldeão saem na categoria "criaturas amigáveis" do volume do
  jogo, e os do Feiticeiro na de "criaturas hostis". Se o Mago parecer mudo, vale conferir esse volume.
- **Os blocos da magia (`caster/Station`).** Em paz, com as páginas escritas, de vez em quando (a cada 30 a 60 s)
  o conjurador procura a até 10 blocos um bloco do ofício, vai até ele e trabalha ali um tempo:

  | Bloco | O que faz lá | Como se vê |
  |---|---|---|
  | estante, estante entalhada, atril | lê frases escritas por outros, nas runas que conhece: 1, mais 1 a cada 5 estantes ao redor (como a mesa de encantamento conta) | o grimório aberto, páginas virando |
  | mesa de encantamento | junta Vis: 2 UMU/s, mais 20% por estante ao redor | o livro aberto, som de ametista |
  | suporte de poções | cuida dele e se cura (1 de vida a cada 2 s); vai mais quando está ferido | um frasco na mão, borbulhas, som de poção |
  | mesa de galdraria | grava uma página sua (uma linha, até 10 colunas) numa varinha improvisada | o buril na mão, som de ferramenta |

  - Ler na estante **não ensina runas** (ler nunca ensina; as runas se aprendem vivendo e sonhando). Ensina o que dá
    para dizer com as runas que ele já conhece: frases novas, que entram nas opções da luta e no grimório como
    "lida". Guarda até 8 e salva com a criatura.
  - O Mago põe à venda as varinhas que gravou (até 3, 8 esmeraldas cada); vendida uma, grava outra. O Feiticeiro
    guarda a sua e a deixa cair quando morre.
  - A escolha pesa a necessidade: o suporte de poções quando está ferido, a mesa de encantamento quando falta Vis.
  - O estudo dos Feiticeiros das mansões usa o atril e as estantes que `Sorcerers.study()` já põe ao lado deles.
- Testes: `aWizardIsHeard`, `aCasterReadsAtTheShelves`, `aWizardEngravesAWandToSell`.

## 5d. O bibliotecário (13/10/2026)

- **Os guias (`library/RuneGuides`).** Livros escritos, feitos só do léxico (uma runa de addon ganha o seu guia sem
  código), com "A Biblioteca" como autora:
  - **Guia de uma runa:** a capa com o glifo e o nome; o que ela é (a descrição do léxico); como entra na frase (o
    papel dela, do léxico); até três frases curtas do catálogo que se leem bem, com o que cada uma faz; e **como
    vivê-la**: as experiências da lição da runa (`lexicon/understanding.json`). Fecha com "Ler não ensina a runa:
    ensina o que ela é. Ela vem num sonho, depois de vivida."
  - **Guia de uma família** (a gramática): Fontes, Funções, Filtros ou Fatos, o que a família é, e cada runa dela numa
    linha.
  - Hoje só o `igni` tem lição escrita. Os guias das outras runas dizem que não sabem como ela se vive; quando cada
    runa ganhar a sua lição, o guia passa a dizê-la sozinho.
- **As trocas.** Entram no conjunto de ofertas do bibliotecário, por nível: Fontes (1), Funções (2), Filtros (3),
  Fatos (4), qualquer runa (5); em cada nível, o guia de uma runa sorteada e o guia da família. Um livro e de 4 a 10
  esmeraldas. Como no jogo base, cada nível sorteia duas ofertas do conjunto, então nem todo bibliotecário tem um guia
  em cada nível. Os bibliotecários que já existem só mostram guias nos níveis que ainda vão subir.
- **A defesa.** Com um monstro a até 8 blocos (a memória de "hostil mais próximo" do cérebro do aldeão) ou logo
  depois de um golpe, o bibliotecário fica parado os dois segundos do transe, com as runas sobre a cabeça, e lê o
  véu mais longo do catálogo (`chronos 6 surgit ego quantum 0`). Um golpe no meio quebra o transe. Depois, 30 s até
  ler de novo (5 s se foi quebrado).
- **O véu agora esconde dos monstros.** Antes ele só mudava o desenho. Agora quem está menos visível é percebido
  tanto menos (o invisível, como na invisibilidade do jogo, ainda um pouco), e quem some de todo é perdido por quem o
  caçava. Vale para todos: jogador, Feiticeiro, Mago, bibliotecário.
- `/runas guia <runa|fontes|funcoes|filtros|fatos>` dá o guia na mão, para testar.
- Testes: `aRuneGuideTellsTheRune`, `aLibrarianKeepsGuides`, `aLibrarianVanishesFromItsHunter`.

## 5e. As torres (13/10/2026)

- **Uma estrutura, dois donos** (`tower/`): um tipo de estrutura (`elderlexicon:caster_tower`) desenhado por código
  (`TowerPiece`), com o dono como dado (`"kind": "sorcerer" | "wizard"`). Onde aparecem e quão raras são é dado de
  mundo, ajustável por datapack: `worldgen/structure/*_tower.json`, `worldgen/structure_set/*_towers.json` e as
  etiquetas de bioma `has_structure/*_tower`.

  | | Torre do Feiticeiro | Torre do Mago |
  |---|---|---|
  | Biomas | floresta escura, taigas, pântanos | planícies, prados, florestas, savana, planície nevada, cerejeiras, colinas |
  | Raridade | uma a cada ~56 chunks de grade (no mínimo 24) | uma a cada ~48 (no mínimo 20) |
  | Pedra | tijolos de ardósia (alguns rachados), ardósia lascada na base | tijolos de pedra (alguns musgosos), pedregulho na base |
  | Madeira, luz, vidro | carvalho escuro, lanterna de almas, vidro preto | abeto, lanterna, vidro |
  | Telhado | telhas de ardósia | terracota azul |

- **A forma.** Redonda (raio 5), de 3 a 5 andares de 5 blocos. Uma escada em caracol, um degrau por bloco, sobe em
  volta de uma coluna central da porta até o observatório. A fundação desce até o chão firme (até 12 blocos).
- **Os andares:**
  - **Térreo:** a porta, ao sul, com o caminho limpo até ela; o depósito, com dois baús (loot próprio:
    `chests/sorcerer_tower` e `chests/wizard_tower`), barris e uma mesa de trabalho.
  - **Primeiro andar:** a biblioteca, com estantes de três de altura em volta, um atril com um guia de runa aberto, a
    mesa de galdraria e a mesa de encantamento.
  - **Andares do meio:** a alquimia (suporte de poções e caldeirão com água), com quatro andares ou mais; o estudo
    (estantes e outro atril), com cinco.
  - **Último andar:** o quarto, com cama, baú e tapete (e o suporte de poções, se a torre tiver só três andares).
  - **Observatório:** no alto, aberto em vidro entre oito pilares, sob o telhado cônico com beiral e um para-raios
    na ponta.
- **Quem mora.** O dono nasce na biblioteca, preso à torre (fica a até 16 blocos dela em paz; vai atrás de um inimigo
  aonde for). A casa é guardada com ele (`Home` na mente). Na torre do Feiticeiro, metade das vezes, um ou dois do
  bando (vingador ou saqueador) estão à porta. Como a torre tem estante, atril, encantamento, poções e galdraria, o
  morador usa tudo (seção 5c).
- **Por dentro:** tudo é desenhado em coordenadas do mundo e só dentro do chunk em geração; o que é sorteado (pedras
  rachadas, o guia do atril, o bando) sai da posição, para os chunks concordarem.
- `/locate structure elderlexicon:wizard_tower` (ou `sorcerer_tower`) acha a mais próxima.
- Testes: `theTowersAreStructures`, `aWizardsTowerIsBuilt`.

## 5f. A casa do mago nas vilas (13/10/2026)

- **A casa** (`village/WizardHouse`): um cômodo de 7 por 8 sob telhado de duas águas com beirais. A porta dá para a rua.
  Dentro: estantes na parede do fundo, com uma lanterna em cada ponta; diante delas o atril (com o guia do `vocant`
  aberto) e a mesa de galdraria; a cama roxa, o suporte de poções e um baú (`chests/wizard_house`); tapete roxo no
  meio. Os materiais são os de cada vila:

  | Vila | Paredes e piso | Cantos | Telhado | Porta |
  |---|---|---|---|---|
  | planície | carvalho | tronco de carvalho descascado | escada de carvalho | carvalho |
  | deserto | arenito liso | arenito talhado | escada de arenito liso | selva |
  | savana | acácia | tronco de acácia | escada de acácia | acácia |
  | neve | abeto | tronco de abeto descascado | escada de abeto | abeto |
  | taiga | abeto | tronco de abeto | escada de abeto | abeto |

- **Gerada por código, salva como estrutura.** O sistema de vilas (jigsaw) só aceita estruturas salvas. A casa é
  desenhada por código num mundo, com o Mago dentro, e salva em `structures/village/<vila>/wizard_house.nbt`. Quem faz
  isso é um GameTest (`theWizardHousesAreSavedAsDrawn`): desenha cada estilo, compara com o arquivo que o mod leva e,
  se faltar ou for diferente, salva de novo nos recursos do projeto e pede outra rodada. Assim o arquivo nunca se
  afasta do código: para mudar a casa, muda-se o desenho e rodam-se os testes duas vezes.
- **A ligação com a rua** é o bloco de quebra-cabeça (jigsaw) diante da porta, igual ao das casas do jogo
  (`building_entrance`, junta alinhada).
- **Na vila.** Ao ligar o servidor, a casa entra no conjunto de casas de cada um dos cinco tipos de vila, com peso 2
  (as casas do jogo pesam de 1 a 4). As listas do conjunto não são abertas para acréscimo e são alcançadas por
  reflexão (`VillageHouses`). As vilas zumbis ficam de fora.
- **O Mago chega.** O Mago salvo na casa vem marcado como "chegando". No primeiro tique depois de posta a casa, ele
  nasce de novo (sorteia grau, personalidade e runas; cada vila tem o seu) e faz da casa o seu lar, a até 24 blocos.
  Na vila, ele defende os aldeões (seção 1) e usa a estante, o atril, a galdraria e as poções da casa (seção 5c).
- Testes: `theWizardHousesAreSavedAsDrawn`, `theWizardsHouseIsInTheVillages`, `aWizardMovesIntoItsHouse`.

## 5g. A universidade: o lugar e quem mora (13/10/2026)

Primeira versão testada pelo autor: saiu pequena demais e mal assentada (sob uma colina, sobre um bloco de terra). Refeita
no mesmo dia a partir do conceito dele (a universidade de Raynus: muralha, Salão Central gótico, biblioteca, ala de
pesquisa, torre do observatório, dormitórios, pátio, jardins).

- **Assentada no mundo.** A peça é uma caixa que começa no chão do campus e sobe até a agulha mais alta; a estrutura usa
  `"terrain_adaptation": "beard_box"`: o próprio gerador de terreno molda a terra à caixa (enche por baixo, escava por
  dentro, com uma encosta suave de uns 12 blocos em volta), como faz com as cidades antigas. A altura é a da terra sob o
  campus (amostrada numa grade de 7 por 7, um pouco abaixo da mediana, nunca abaixo do mar), não só a do meio.
- **Só três no mundo.** O posicionamento é o das fortalezas: `concentric_rings` com `count: 3`, as três no primeiro
  anel, a uns 1.500 blocos da origem, preferindo os biomas de `has_structure/university` (quase toda terra firme).
- **O campus**, 113 blocos de muralha a muralha:
  - **a muralha**: dois blocos de espessura, nove de altura, com o passeio e as ameias; torres redondas nas quatro
    quinas e no meio dos lados, com seteiras e cones de ardósia; ao sul, **o portão** entre duas torres, com a grade
    erguida e lanternas sob o lintel;
  - **o pátio principal**, calçado em andesito e tijolo de pedra, com a **fonte** redonda no meio e quatro **jardins**
    cercados de sebe, com flores e uma árvore cada; avenidas calçadas até cada porta; gramados com árvores;
  - **o Salão Central** (norte): uma nave de 41 por 37 com paredes de 20 blocos, contrafortes e janelas altas, telhado
    íngreme de ardósia, **duas torres com agulhas** diante da fachada, a **rosácea** sobre o portal e a **agulha
    central** sobre a nave. Dentro: duas fileiras de colunas, o estrado com a cadeira do Rector e um atril, a mesa do
    conselho com seus bancos, o tapete vermelho, lustres de corrente;
  - **a Biblioteca** (oeste), o Arquivo: 25 por 49, paredes de 16; fileiras de estantes de quatro de altura, uma
    **galeria** em volta com guarda-corpo e escada, estantes na galeria, a sala de leitura com mesas e atris com guias,
    e ao norte a **sala fechada** atrás da porta de ferro (estantes, mesa de encantamento, os baús do Arquivo);
  - **a Ala de Pesquisa** (leste): dois andares, um corredor no meio e salas dos dois lados; no térreo, a oeste, as sete
    salas das cadeiras (cada uma com estantes, o atril com o guia da família e o que é do ofício); a leste e no andar de
    cima, salas de estudo com estantes e mesas;
  - **a Torre do Observatório** (sudeste): redonda, 28 de altura, escada em caracol, a sala do alto com a mesa de
    cartografia e uma luneta, sob uma **cúpula de vidro**;
  - **o dormitório** (dois andares de camas e baús), **o refeitório** (duas mesas compridas, defumadores), **a oficina**
    (mesas de galdraria, rebolos, baús) e **a enfermaria** (camas, suportes de poções, caldeirões), ao sul.
- **Quem mora** (cada um em casa onde nasce): **o Rector** no estrado do Salão Central; **os sete Magisters** nas suas
  salas da Ala de Pesquisa e **o do Arquivo** na Biblioteca; **seis estudantes** (três Auditores, dois Scribas, um
  Lector) pelo pátio, na biblioteca e no observatório; **dois bibliotecários** na sala de leitura; **dois clérigos** na
  enfermaria.
- **O título** é do Mago: o grau (`Rank`: Auditor, Scriba, Lector, Magister, Rector) e a cadeira (`Discipline`) ficam
  guardados nele, nascem com o grau de mente que pedem (Auditor aprendiz, Scriba e Lector mestres, Magister e Rector
  anciões) e aparecem como o nome ("Magister das Fontes", "Rector", "Auditor").
- **Segunda revisão (13/10/2026), depois de o autor testar:**
  - **as vilas não brigam mais com ela:** o conjunto das vilas do jogo (`minecraft:villages`) ganhou uma zona de
    exclusão: nenhuma vila nasce a menos de 10 chunks (160 blocos) de uma universidade. Elas ainda podem nascer à vista,
    fora dos muros. O arquivo do jogo é substituído pelo do mod, igual ao original fora isso (um mod que também mude as
    vilas pode brigar com ele);
  - **a entrada nunca fica fechada:** uma segunda peça, a estrada que sai do portão (`UniversityRoadPiece`, 40 blocos,
    calçada e com postes), tem o terreno moldado a ela também: um corte no morro ou um aterro;
  - **mais espaço:** muralha a 62 do meio (125 de lado); a Biblioteca e a Ala de Pesquisa afastadas das torres da
    fachada, que fechavam as portas delas; as portas no meio de salas inteiras; a escada da galeria sai num patamar
    com uma abertura no guarda-corpo; a porta do observatório para o oeste, que tem espaço;
  - **a fonte** tem borda maciça de pedra (a água não vaza mais);
  - **cercas, grades e muros** se juntam aos vizinhos: depois de assentar tudo num chunk, cada um é recalculado
    pelas formas ao redor (`Masonry.join`);
  - **luz em tudo:** os forros são de meia-laje (nada nasce em meia-laje, e o sótão escuro não cria mais monstros); a
    cumeeira e as bordas dos cones de ardósia também; nas salas altas as lanternas descem em correntes até uns seis
    blocos do chão; lanternas em cada sala da Ala de Pesquisa, sob a galeria, em cada andar das torres e nas paredes
    do observatório; postes de lanterna pelo pátio, pelos gramados, junto à avenida e ao longo da muralha; lanternas
    no passeio da muralha. O GameTest conta os lugares onde um monstro poderia nascer no escuro, do chão às agulhas:
    **nenhum**.
- **Por dentro:** `UniversityPiece` (o traçado), `Hall` (um prédio: paredes, contrafortes, janelas, andares, telhado,
  luzes, escadas), `Masonry` (assentar blocos só dentro do chunk, pelo deslocamento do meio). O GameTest
  `aUniversityIsBuilt` constrói o campus inteiro, confere, e deixa duas imagens (de cima e do sul) em
  `run/pictures/`, para ver a composição sem abrir o jogo.
- `/locate structure elderlexicon:university` acha a mais próxima.

## 5h. O exame, a mensalidade e os graus; a paz entre os conjuradores (13/10/2026)

- **A tela de diálogo** (`dialogue/`): um painel ao pé da tela, com quem fala desenhado num quadro ao lado (olhando
  para o cursor), o nome dele, a fala escrita aos poucos (um clique ou espaço mostra tudo) e as respostas embaixo,
  clicáveis ou pelo número (1 a 9). O mundo segue atrás dela (não pausa). Tudo é decidido no servidor: o cliente só
  mostra a fala e diz qual resposta foi escolhida. Serve para os outros aldeões depois.
- **Falar com um mestre.** Clicar no Rector ou num Magister abre o diálogo em vez da troca; o mestre para e olha para o
  jogador enquanto falam. O Magister tem a opção de negociar, que abre a troca de sempre.
- **O exame** (`Exam`): quatro respostas por pergunta, uma certa. A maioria vem do léxico (o que é a runa X, qual runa
  diz X, de que família é X, o que faz esta frase), então uma runa que um addon traga também cai no exame; o resto é o
  que a casa ensina de cada arte (os aspectos das fontes, o que faz uma quantidade negativa, onde vai um filtro, ego e
  ille, a galdraria, a Vita, como se aprende uma runa, as regras do Arquivo). O Rector pergunta de tudo; cada Magister,
  da sua disciplina.
- **A admissão e a mensalidade.** O conselho (o Rector) faz seis perguntas. Menos de três certas: não é admitido, e
  volta no dia seguinte. Três ou mais: é admitido como **Auditor**, e o exame fixa a mensalidade dos próximos sete
  dias, paga ao Rector em esmeraldas: 3 certas, 20; 4, 12; 5, 5; **6, a casa paga 3 a ele**. Enquanto não paga, não
  está em dia. Ao fim dos sete dias, um novo exame fixa a mensalidade do período seguinte; quem não passa nele fica fora
  de dia até passar.
- **Os graus.** Um Magister examina quem está em dia, na sua disciplina: cinco perguntas, quatro certas passam, e quem
  erra volta no dia seguinte. A primeira disciplina faz do Auditor um **Scriba**. Quem passou em três, uma delas sem
  errar nada, pede ao Rector o grau de **Lector** (aquele mestre o aceita). O livro da casa é um só no mundo
  (`Enrollments`, salvo com o mundo): quem estuda numa universidade estuda em todas. O que cada grau abre vem com o
  Arquivo e a oficina.
- **Conjuradores civilizados.** Numa vila (camas e sinos tomados por aldeões, ou três aldeões a até 32 blocos), no
  terreno de uma universidade, ou sendo membro de uma (todo Mago com grau), uma conversa que azeda não vira briga:
  quem se ofendeu dá as costas e se afasta, com a cara fechada, e os dois ficam uns cinco minutos sem conversar. No
  ermo, a briga continua como antes (seção 5b). Assim a universidade não se esvazia com o tempo.
- **Para testar:** `/mago matricula ver` (como o jogador está no livro), `/mago matricula vencer` (o período acaba
  agora, sem esperar sete dias), `/mago matricula esquecer` (sai do livro).
- Testes: `anExamIsMadeOfTheLexicon`, `theHouseKeepsItsStudents`, `theCouncilAdmitsAPlayer` (o diálogo inteiro com o
  Rector e um Magister, respondendo certo), `castersKeepThePeaceInAVillage`.
- **Ainda não:** o pedido prático do exame ("acenda a vela daqui") para quem já conjura.

## 5i. As aparências: a barba, as vestes, os cargos (pedido do autor, 13/10/2026)

- **A barba diz o grau**, de Mago ou de Feiticeiro: aprendiz sem barba; mestre com barba preta média (até um pouco
  abaixo do queixo, com costeletas); ancião com barba branca longa (desce pelo peito, some atrás das mãos cruzadas) e
  sobrancelhas brancas. É uma camada própria (`BeardLayer`), presa à cabeça; o grau vai ao cliente por dado
  sincronizado e cresce junto quando o conjurador aprende runas. **O Feiticeiro usa a barba de mestre de artes
  marciais de filme antigo:** nada sobre a mandíbula; um bigode que cai em dois fios longos ao lado do nariz e uma
  barbicha fina no queixo (pretos no mestre; brancos e mais longos no ancião, os fios se abrindo, a barbicha em ponta),
  e as sobrancelhas do ancião crescem para fora do rosto e caem até as bochechas.
- **O Mago se veste pelo lugar onde vive** (`Dwelling`, salvo com ele): o **da vila**, túnica azul com ouro e um gorro
  macio; o **da torre**, índigo semeado de estrelas, bordas de prata e o **chapéu pontudo**; o **dos ermos**, manto de
  musgo e terra, remendado, barra esfarrapada, **capuz levantado**. Quem não tem casa (ovo, comando) é dos ermos; um
  salvo antes disso é da universidade se tem cargo, senão dos ermos.
- **Na universidade, pelo cargo** (`WizardLook`): **Auditor**, beca cinza lisa; **Scriba**, beca azul-tinta, manchas de
  tinta nos punhos, alça de bolsa, **boina com pena**; **Lector**, beca preta com estola azul e o capuz forrado nas
  costas; **Magister**, preto e veludo vinho, três faixas douradas nas mangas, **barrete quadrado**, e a **estola e a
  borla na cor da cátedra** (Fontes laranja, Funções azul, Filtros verde, Fatos amarelo, Galdraria prata, Vita rosa,
  Nahvá roxo, Arquivo marrom); **Rector**, escarlate com capa de arminho, corrente de ouro e **gorro alto de veludo**.
- **As poses.** Lendo o grimório (estante, altar, escrevendo), descruza os braços e segura o livro aberto diante do
  peito, a cabeça inclinada sobre as páginas. No transe, os braços erguidos (como antes) e o **foco na mão direita**;
  ao soltar o que leu (enquanto as linhas saem e as torneiras correm), **aponta o foco para onde mira**, o braço
  estendido, o outro ao lado do corpo. O foco é só desenhado: varinha improvisada no aprendiz, varinha feita no mestre
  e no ancião (cajados e cetros depois). Não é um item na mão, então não conduz nada: o custo do feitiço da criatura
  não muda (decidido pelo autor em 13/10/2026: fica só visual).
- **O que sai do conjurador sai da mão**, do jogador e das criaturas (`WorldSources.handOf`): à frente, do lado da mão
  principal e abaixo dos olhos, onde o jogador vê a própria mão e onde a criatura aponta o foco. O raio do `iactare`, o
  jato d'água, o sopro de ar, a corrente de luz e as orbes condensadas partem dali **em direção ao ponto que o olhar
  acerta**, então a mira continua a dos olhos. Quem acerta o quê continua decidido pelos olhos; só o caminho visível
  mudou. Antes saíam de diante do rosto e cegavam quem conjurava.
- As texturas saem de `tools/caster_skins.py` (desenhadas pixel a pixel sobre o molde do aldeão; rodar de novo dá os
  mesmos arquivos). O Feiticeiro continua com a pele do evocador.

## 5j. O Emissário (pedido do autor, 17/10/2026)

A universidade tem dois papéis no mod: deixar o mundo mais vivo e **ser o tutorial**. O jogador não precisa de um
livrinho explicando o mod; ele aprende estudando lá. O Emissário é quem leva o jogador até ela.

- **Quem é.** Um Mago da universidade (`Dwelling.EMISSARY`), mestre na língua (barba preta), com a mesma mente dos
  outros: se defende, guarda a paz. Não negocia. Veste a capa de viajante cinza-azulada, com uma pequena capa sobre os
  ombros, o selo dourado da casa no peito e nas costas, a bolsa das cartas do lado, a poeira da estrada na barra, e um
  chapéu de feltro de aba larga.
- **O convite.** De dois a quatro dias depois de o transe despertar, se o jogador não estuda em nenhuma casa, há uma
  pequena chance a cada momento do dia (mais ou menos uma vez por dia) de um Emissário vir andando até ele, de uns 25
  blocos. Ele chama o jogador pelo nome no chat e espera ser ouvido (cinco minutos; depois deixa a carta e vai embora).
  Falando com ele, o jogador pode:
  - **aceitar e ir com ele**: recebe a carta e o mapa, e ele o leva (abaixo);
  - **aceitar e ir sozinho**: recebe a carta e o mapa;
  - **recusar**: ele vai embora, e a casa tenta de novo depois de uns cinco dias.
- **A carta** é um livro escrito pelo Rector, já com o tutorial: o que a casa ensina (as runas, como vivê-las, as quatro
  famílias), que os mestres respondem e o Arquivo guarda os guias, como é a admissão, que se paga em esmeraldas a cada
  sete dias pelo que se sabe, e para onde ir. **O mapa** é como o mapa de explorador do cartógrafo, com a universidade
  mais próxima marcada com um X.
- **Levar o jogador: marca + `vocant`** (o autor, 18/10/2026; substituiu a caminhada guiada, que não dava conta de
  lagos, neve fofa e quedas). Aceitando ser levado, o jogador recebe a carta e o mapa, e o Emissário:
  1. **marca o jogador** com uma marca só dele (`rumo_<8 letras do UUID>`); a marca que o jogador tinha é guardada e
     devolvida depois da viagem;
  2. entra em transe e lê o **chamado da casa**, duas linhas: `x y z ubis <marca> vocant` (o jogador aparece no pátio)
     e, um passo depois, `x y z ubis ego vocant` (ele vai junto). É a regra das marcas: **marca + `vocant` =
     teletransporte** (docs/marcas-como-runas-design.md); o lugar são as coordenadas do pátio, ao sul da fonte, de
     frente para o salão do conselho;
  3. chegando, diz onde fica o Rector, e vai embora pouco depois.
  Um golpe que quebra o transe faz ele ler de novo (até três vezes); se mesmo assim não leva, ele manda seguir o mapa.
  O pátio é desenhado na hora se ninguém esteve lá ainda (o pedaço de mundo é gerado).
- **A energia vem do engaste da casa.** Levar alguém custa massa × distância (0,01 UMU por massa por bloco): um jogador
  e o Emissário por 1500 blocos dão uns 660 UMU, mais do que qualquer conjurador tem. A universidade põe na varinha do
  Emissário o **Selo da Universidade**, um engaste que só ela tem (`university_seal` em `lexicon/foci.json`, até 2000
  de vis, sem item que o faça), carregado com o custo da viagem e 20% a mais. A leitura paga dele pela regra de
  reserva que já existe (`SettingReserveModule`), antes do corpo do Emissário. Ele segura a varinha de verdade, e ela
  não cai quando ele morre.
- **Ele protege quem procura.** O jogador é um aprendiz: enquanto vai até ele e espera ser ouvido, o Emissário ataca
  primeiro qualquer criatura hostil a até 14 blocos do jogador (as neutras, como o enderman, só se vierem caçar), antes
  que ela ataque. Vencida a luta, volta ao recado.
- **O chamado de volta.** Um estudante cujo período acabou há um dia ou mais e que está a mais de 256 blocos de toda
  universidade é procurado de novo, com a mesma pequena chance, no máximo a cada cinco dias. A mensagem diz o que a casa
  espera: o novo exame, ou a mensalidade do período (quantas esmeraldas). A carta é outra, curta.
- **Ir embora.** Terminado o recado, ele se afasta uns 30 blocos e some num sopro de luz. Some também se o jogador sai
  do mundo, morre longe ou vai para outra dimensão por um minuto.
- **Para testar:** `/mago emissario` (o convite) e `/mago emissario retorno` mandam um agora. Num mundo sem
  universidade (um mundo plano, por exemplo), ele não vem.
- O código: `university/Emissary` (o recado: procurar, esperar, levar, ir embora) e `university/Emissaries` (quando
  ele vem, a carta, o mapa, o engaste). A tag `elderlexicon:university` acha a casa mais próxima.

## 6. Perguntas abertas

- **O Mago como aldeão:** um `AbstractVillager` próprio (proposta), ou uma profissão nova de aldeão (dorme, trabalha
  num bloco, mas a IA de aldeão de vanilla é difícil de casar com o transe)?

## 7. Decisões tomadas

1. As estruturas são geradas por código, não montadas à mão. (12/10/2026)
2. O sistema das universidades segue o que o autor tem em mente (exame, mensalidade pelo desempenho, graus, Arquivo,
   oficina com comissão, enfermaria, conselho), sem nenhuma referência direta à obra que o inspirou. (12/10/2026)
3. O Mago negocia (varinhas, pergaminhos, grimórios, guias); os aldeões ligados à magia também passam a oferecer
   coisas da língua. (12/10/2026)
4. O Mago ataca quem fere um aldeão; os aldeões da magia se defendem com magia. (12/10/2026)
5. Os graus têm nomes na Língua Antiga: Auditor, Scriba, Lector, Magister; o Rector preside. A mensalidade é em
   esmeraldas, por período de sete dias. O exame é uma tela de diálogo como as dos jogos de RPG. (12/10/2026)
