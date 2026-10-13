# As habilidades do mago

**Status:** o transe, a ilha, a torre infinita e a voz de Phantur estão implementados (07/10/2026); as salas da torre
ainda são as quatro provisórias.
Não testado dentro do jogo (Nahvá carrega sem erro no servidor de testes).

Antes de tudo, é **apenas o Minecraft normal**: nenhuma tecla de magia faz nada, nenhuma runa se entende, nenhum sonho
vem. O mago nasce em duas etapas.

## 1. O transe

- O jogador se torna mago depois de **atingir 100 pontos de experiência**: a **primeira noite dormida com 100 XP ou
  mais** acorda o transe.
- Ele **acorda com náuseas** ("Você acorda com náuseas. Por um instante, no fundo do sono, algo te ouviu. Você pode entrar
  em transe.").
- Com o transe, ele pode: entrar em transe e dizer runas (a tecla do spelling), abrir o repertório e **aprender runas**
  (entendê-las e sonhar com elas, docs/aprendizado-runas-design.md). Ele ainda não sabe nenhuma runa.
- Nessa noite não vem nenhum sonho de runa: o despertar é o sonho dela.

## 2. O fluxo

- Para ativar o fluxo, o mago precisa **já ter o transe** e **morrer**.
- Ao morrer, em vez de morrer, seu espírito desperta em **Nahvá**, na **sua própria ilha** (o seu kern), para descansar
  para sempre. Se quiser voltar, precisa encontrar e vencer a **torre infinita**.
- **Vencendo todos os desafios**, ele acorda com náuseas, **não morre**, e o fluxo é liberado.
- **Morrendo em Nahvá** (na ilha ou na torre), ele morre de verdade: no modo sobrevivência, renasce no Overworld como
  numa morte normal; no hardcore, é o fim. Na ilha, sem morrer, ele fica para sempre.

## 3. A ilha (kern)

- **Toda morte** de um mago com o transe e sem o fluxo leva o espírito à sua ilha em Nahvá (grimório 2.1.1: cada
  espírito tem um kern, um planeta pessoal). Uma ilha verde flutuando no escuro, numa névoa cor de crepúsculo: uma
  cerejeira, uma bétula, um carvalho, um lago, flores, silêncio. Ela é **dele**: é feita uma vez, e o que ele constrói
  fica. Lá ele voa (é o seu kern), a vita não conta, e Phantur aparece pouco, contente.
- Na beira da ilha há um **portão para o caminho**: a longa jornada até Lig'dar. **Por enquanto o caminho é curto**: o
  portão leva direto à beira do vulcão. O que ele tinha na ilha fica guardado e volta quando ele volta.
- Sair do jogo na ilha: o espírito fica lá.

## 4. A torre infinita

- Nahvá é a dimensão dos espíritos; a torre foi erguida por **Phantur**, o guardião dos portões.
- **Ele chega sem nada**, na beira do vulcão Lig'dar, diante da torre; mas em Nahvá, que é um sonho lúcido, não é
  difícil conseguir recursos. Tem UMU infinita; não voa (subir é o desafio). Em Nahvá todo espírito pode entrar em
  transe.
- **Por fora** (a visão de Lig'dar): um vulcão de rocha escura com um lago de lava na cratera, e no centro a torre, de
  pedra negra (blackstone) com **veios de magma** subindo pelas paredes, **centenas de janelas** irregulares (escuras ou
  acesas de vermelho), larga na base e se estreitando em degraus, **pináculos inclinados** em ângulos impossíveis,
  **escadarias externas** que contornam a torre e terminam no vazio, **portas soltas no ar** acesas por dentro, **ilhas
  flutuantes** e **planetas** ao longe (os espíritos). Sobe até 636 (Nahvá tem 640) e some na névoa vermelha muito antes
  do topo. O espírito chega na beira do vulcão e desce até o portão por uma ponte em rampa suave, sobre a lava.
- **Por dentro:** andares de 40 blocos (uma sala de 16 e, acima dela, pedra maciça) ligados por **escadas largas que
  parecem nunca terminar**: degraus de 2 blocos de largura em volta de um pilar, subindo meio bloco por vez (sem pular),
  cinco voltas de 8 blocos num poço fechado; o chão da sala de cima se abre sobre a metade final da última volta, para se
  sair de pé. Ao chegar na quarta volta, o escalador é posto de volta na terceira, no
  mesmo degrau; depois de 4 vezes, a escada o deixa sair no andar seguinte, por uma abertura no chão. O laço é
  imperceptível: de nenhum dos dois pontos se vê a entrada nem a saída (a volta acima e as duas abaixo são escada
  comum), e o teleporte é relativo, então o cliente não perde o passo, o balanço nem para onde olha.
- **A torre não cede:** o jogador não quebra os blocos dela (só o que ele mesmo pode ter posto: pedregulho e tochas), então
  não dá para furar paredes e pular desafios.
- **O corpo de vis:** quem entra em Nahvá por sonho ou por morte é só vis, então a **vita fica suspensa** lá (não muda,
  não pesa, não adoece) e volta como estava. Quem um dia entrar por um portal, de corpo, levará a vita junto.
- **O objetivo do jogador é vencer a torre; o da torre é fazê-lo desistir.** O desgaste é psicológico, mas a torre
  também mata. Uma subida vencida deve levar **uma hora ou mais** (o fluxo vale isso).
- **As salas são sorteadas** a cada subida (`nahva/TowerRoom`). Fisicamente a torre tem só a antessala (andar 0), dois
  andares de salas (1 e 2) e o topo (3): as salas se revezam nos andares 1 e 2, refeitas fora da vista (enquanto o
  escalador está na escada); a escada que sai do andar 2 o leva, sem que ele perceba, para a escada que sai da antessala
  (que é igual), e daí ao andar 1, já refeito com a sala seguinte. Assim a torre tem quantas salas a subida precisar. Cada
  sala pode selar a escada de saída (obsidiana chorona) até o desafio ser vencido.
- **A porta de descanso**, em todo andar, junto da entrada: um batente de cerejeira, luz quente, grama e flores. É a oferta
  de Phantur, sempre à vista: atravessá-la é **desistir**, e o espírito volta à sua ilha (o que ele pegou na torre fica
  na torre).
- **Phantur fala** (`nahva/Phantur`, falas em `docs/phantur-falas.md`): na primeira chegada, ao voltar, ao entrar numa
  sala, na escada, quando o mago fica parado, perto da porta, com pouca vida, de tempos em tempos, no topo, ao desistir, ao
  morrer e ao vencer. Às vezes uma palavra, às vezes um devaneio longo; ele não se repete até ter dito tudo e lembra das
  subidas anteriores, mesmo depois da morte.
- **As salas de agora:**
  - **A escada que desce** (`DESCENT`): uma capela escura, só de velas, bancos e um tapete até a escada de saída, que está
    selada para sempre. O único caminho é voltar pela escada por onde se chegou: ela desce em laços (6, mais que os 4 da
    subida) e entrega o mago na sala seguinte, refeita embaixo, entrando pela própria escada dela (que se fecha atrás
    dele, se a sala for selada). E o contador volta: depois da "Sala 4", vem a "Sala 2". Phantur tem falas próprias para
    a descida. Nunca é a última sala.
  - **A memória** (`MEMORY`, `nahva/MemoryRoom`): a torre copia o lugar onde o mago renasce (a cama) ou, sem cama, onde
    morreu (31×16×31 blocos; se for soterrado, como uma caverna, o ponto de nascimento do mundo). A cópia fica vazia, só
    com a luz que o lugar tinha, os baús vazios, sem fluidos, fogo, portais nem geradores. **Uma coisa está errada**: um
    bloco trocado por um "irmão" (outra madeira, outra cor, uma variante: grama por podzol, tocha por tocha de alma) ou
    virado ao contrário; se nada puder mudar, algo que não estava lá (uma lanterna, um caldeirão, um crânio). **Golpear** a
    coisa errada a desfaz e abre o selo; golpear qualquer outra coisa dói (1,5 coração) e o erro muda de lugar. Phantur
    fala ao chegar e às vezes ao errar. Camas em Nahvá explodem; explosões não quebram a torre.
  - **O espelho** (`MIRROR`, `nahva/MirrorRoom`, `nahva/Reflection`): um salão cortado na diagonal por um espelho
    invisível (barreiras sobre uma linha de quartzo, que brilha de vez em quando). Do outro lado, onde fica a escada de
    saída, está o **reflexo** do mago: a pele dele, o que ele veste e segura (com as mãos trocadas, como num espelho),
    fazendo o que ele fez um momento antes. A passagem no meio do espelho só abre enquanto o reflexo pisa a placa do lado
    dele: o mago tem que pisar a imagem da placa do seu lado e correr até a passagem antes que o reflexo saia de cima. O
    atraso começa em 5 s e encolhe; quando chega a 2 s, o reflexo o alcança (2 corações) e o atraso volta a 5 s. Bater no
    reflexo machuca quem bate. Atravessar quebra o espelho, e o reflexo some.
  - **A fala** (`SPEECH`, `nahva/Loans`): um braseiro apagado (fogueira) sobre um altar, e diante dele uma placa em
    **latim escrito no alfabeto do mod** (a fonte `elderlexicon:sga`): duas traduções, das letras e da língua. Quatro
    enigmas sorteados, todos pedindo fogo na lareira ("IACTA IGNEM IN FOCUM", "FOCUS FRIGET / IACTA IGNEM", "IGNIS
    DORMIT IN CINERE / IACTA EUM IN FOCUM", "SINE IGNE NULLA VIA / IACTA IGNEM IN FOCUM"), apontando para `igni` e
    `iactare`. A torre **empresta** essas runas ao chegar (desbloqueia e põe num espaço vazio) até o fim da subida
    (vencendo, desistindo ou morrendo, devolve: esquece o que não se sabia e esvazia os espaços que ela encheu; o que já
    se sabia não é tocado). O braseiro acende com o calor do feitiço (docs/particulas-design.md §8.10), ou, se o fogo
    não chegar, a torre ouve a palavra: um feitiço com `igni` dito olhando para o braseiro o acende. Aceso, o selo abre.
  - As quatro primeiras, provisórias (combate, lava, alavancas, runas), a serem trocadas pelas novas.
- **Falhar** reseta tudo: na próxima vez, a torre é refeita do zero, com novas salas.
- **Vencendo** (atravessando a fenda): por ora, ele volta à vida **onde morreu** (ou no lugar firme mais perto, se ali
  for lava, água ou o vazio), em **situação precária, cruel mas não impossível**: 3 corações, fome em 6 (baixa, sem
  regenerar, mas longe de matar de fome), sem saturação, náusea, cegueira breve, e por um tempo fraqueza, lentidão e
  cansaço. Ganha o fluxo. (Será mudado: ele volta em espírito saindo de um vulcão do mundo desperto;
  ver as perguntas abertas.)
- **Saindo do jogo na torre**, o espírito fica parado lá, e a subida continua de onde parou quando ele voltar.
- **Morrendo na torre**: a morte acontece de verdade, onde aconteceu da primeira vez, com tudo
  o que ele tinha; no hardcore, é o fim.

## 5. O que existe no código

- `spelling/data/SpellingRepertoire`: `trance` e `flow`, guardados com as runas e enviados ao cliente.
- `spelling/data/MageAbilities`: a noite que acorda o transe (`PlayerWakeUpEvent`), as perguntas "tem transe? tem
  fluxo?" (no criativo, sempre sim) e dar/tirar as habilidades.
- **Travas:** no cliente, as teclas do spelling, do repertório e do fluxo só agem com a habilidade; no servidor, o spelling
  (`ServerSpellingController.handleSpellCast`) não ouve quem não tem o transe, e o `FlowTogglePacket` não liga o fluxo de
  quem não o tem; o entendimento de runas e os sonhos só acontecem com o transe.
- `nahva/TowerRealm`: a torre (construção, escadas sem fim, as salas se revezando, a porta de descanso);
  `nahva/TowerRoom`: as salas; `nahva/TowerGuard`: a torre não se quebra; `nahva/KernRealm`: a ilha;
  `nahva/Phantur`: a voz; `nahva/Dreams`: a morte que leva à ilha, o caminho, desistir, a volta à vida e a morte de
  verdade.
- **As falas de Phantur** ficam em `docs/phantur-falas.md`; depois de editar, `python tools/phantur_lines.py` gera as
  traduções e o índice.
- **Teste:** `/mago transe`, `/mago fluxo`, `/mago nenhuma`, `/mago ilha`, `/mago torre`, `/mago sala <sala>` (a próxima subida começa por essa sala) (operador). Quando o sorteio tem uma sala a mais, sai sempre uma das provisórias.

## 6. O que vem depois (não implementado)

- **A jornada:** da ilha até a torre, uma jornada muito longa por um mundo onírico, uma galáxia inteira, até um único
  ponto daquele universo, Lig'dar. Hoje o portão da ilha leva direto.
- **Mais salas**: todas no tema do mod (pedra antiga, velas, capelas), estranhas e
  sem saída aparente; nada moderno.
- **Chefes:** espíritos que tentaram escapar de Nahvá e estão presos na torre há eras.
- **A volta:** vencendo a torre, ele volta **em espírito, saindo de um vulcão** do mundo desperto: ali fica a porta entre
  Nahvá e o Overworld.

## 7. Perguntas abertas

1. **A primeira construção** da torre de cada jogador congela o servidor por ~11 s (as seguintes, ~0,3 s). Construir aos
   poucos, ao longo de vários ticks, resolve.

## 8. Decisões tomadas

1. Antes dos 100 XP, apenas o Minecraft normal; a primeira noite dormida com 100 XP ou mais acorda o transe, com
   náuseas. (06/10/2026)
2. O fluxo exige o transe e uma morte: o mago vai à torre infinita de Nahvá; vencendo, acorda com náuseas, vivo e com o
   fluxo; morrendo lá, morre de verdade (no hardcore, de vez). (06/10/2026)
3. A torre: escada sem fim com o truque do teleporte, colossal por fora; todo tipo de desafio, que reseta a cada falha;
   chega-se sem nada, mas Nahvá é um sonho lúcido e os recursos são fáceis; vencendo, volta onde morreu, em situação
   precária; toda morte leva até lá. (07/10/2026)
4. A torre fica no vulcão Lig'dar, como na descrição e na imagem do usuário; as escadas internas são largas e sem pulo.
   Em Nahvá todo espírito pode entrar em transe. (07/10/2026)
5. A morte leva o espírito à sua própria ilha em Nahvá; a torre precisa ser encontrada numa longa jornada; vencendo, ele
   volta saindo de um vulcão do Overworld. (07/10/2026; a ilha está feita; a jornada e o vulcão, ainda não)
6. O laço da escada deve ser imperceptível; a torre não pode ser quebrada; em Nahvá, por sonho ou morte, a vita não
   conta (por portal, contará); a volta é cruel mas sobrevivível. (07/10/2026)
7. Ao sair do jogo na torre (ou na ilha), o espírito fica lá. (07/10/2026)
8. Phantur, guardião dos portões, quer manter a ordem: um espírito que volta pode destruir o mundo físico. Ele sempre
   oferece a saída fácil (a porta de descanso, para a ilha) e fala com o desafiante (frases curtas, médias e devaneios
   longos, vivo). A ilha é para sempre; dela sempre se pode partir de novo para a torre. Na sobrevivência, morrer em
   Nahvá é renascer no Overworld; no hardcore, só morrer de verdade em Nahvá acaba o jogo. A subida leva uma hora ou
   mais; a torre pode matar, o desgaste é psicológico, desistir é um jeito de perder; salas sorteadas. (07/10/2026)
