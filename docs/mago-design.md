# O Mago, as torres e as universidades

> "Tudo o que é medido pode ser transformado." (Grimório de EVL, cap. 3)

**Status:** passos 1 e 2 feitos (13/10/2026): a mente comum, o Mago com as trocas dele (5a a 5c) e o bibliotecário
(5d). Segue docs/feiticeiro-design.md (seção 10a).

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
3. A **torre do feiticeiro** e a **torre do mago** (peças por código).
4. A **casa do mago** nas vilas.
5. A **universidade**: primeiro o lugar e quem mora; depois o exame, a mensalidade e os graus; depois o Arquivo e a
   oficina; por fim a disciplina da casa.
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
