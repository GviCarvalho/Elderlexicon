# O Feiticeiro: um inimigo que escreve magias

> "Uma runa jamais mente. O homem, sim." (Grimório de EVL, cap. 4)

**Status:** primeira fase implementada (12/10/2026): etapas 1 a 7 (seção 9). Os vexes (seção 7.3) ficam para depois.

O mod não ganha um inimigo inventado do zero: ele pega o que o Minecraft já tem e faz dele algo maior. Os Illagers já
têm um conjurador, o evocador. O **Feiticeiro** (*Sorcerer*) é a versão dele que **conhece a Língua Antiga**: carrega um grimório, compõe
feitiços com as runas que conhece e os lança pelas mesmas regras que o jogador. O nome foi decidido em 12/10/2026
(decisão 9).

A ideia central: **o evocador joga com as regras do jogador.** Ele não tem ataques próprios. Tudo o que faz é uma frase
da língua, lida pelo `SpellGrammar` e paga pelo `FlowInterpreter`. Se uma runa nova entra no léxico (do mod ou de um
addon), ele pode usá-la sem uma linha de código a mais.

## 1. Quem ele é, e o que o livro prende

**Os Illagers no mod** (decidido em 12/10/2026). O mod e o livro são versões levemente diferentes do mesmo mundo: no
livro não há villagers nem Illagers, e no mod não há elfos, gnomos nem goblins. O livro não é lido ao pé da letra. No
mod, a Língua Antiga sempre existiu, e todos os seres lidam com a magia como no livro. Os Illagers são os mesmos do
Minecraft; a diferença é que **alguns deles usam magia**, como alguns jogadores usarão e outros não. O feiticeiro
é um desses.

As regras da magia do livro valem para ele como para qualquer ser vivo:

- **A Âncora dos Cem (3.2).** Todo ser vivo tem 100 UMU de Vis, "até mesmo os cães que ladram nas ruas". O evocador
  também: o que o faz mago é abrir a fresta, não ter mais reserva. Ele regenera devagar, como o corpo do livro.
- **O tributo de sangue (3.2.1).** Um feitiço que custa mais que a Vis cobra o resto da carne. O evocador pode pedir
  isso de propósito, e paga em vida.
- **A janela de dois segundos (cap. 5).** Sem nada escrito, só cabem duas ou três palavras no transe. Por isso ele
  carrega um **grimório** e conjura do jeito do livro: transe, olhar para a página, `surgit`.
- **Ressignificar (`reframe`).** Uma frase longa pode receber um nome no grimório e ser dita numa palavra, como o
  "fireball" do livro.
- **Ler não ensina (docs/aprendizado-runas-design.md).** Uma runa só se aprende entendendo-a no corpo e escapando do
  sonho. O espírito **pula as runas que o conjurador não conhece**, como se não estivessem escritas. Isso vale para o
  evocador e para o jogador que ler o grimório dele (seções 4 e 5).

## 2. Como ele conjura

### 2.1 Dois tempos: preparar e lançar

- **Preparar (fora de combate).** Parado na mansão, num acampamento ou com a raid ainda longe, o evocador **escreve**.
  É aqui que a mente dele trabalha (seção 3): ela compõe frases, testa e passa a limpo as melhores no grimório. O
  trabalho pesado fica fora da luta e é dividido em pedaços pequenos ao longo de muitos ticks.
- **Lançar (em combate).** Ele escolhe uma página pronta, entra em transe e diz `surgit`. A escolha é rápida porque as
  frases já existem: falta só ajustar os números e os alvos à cena.

### 2.2 O que se vê

- Antes de lançar, **o transe fica visível**: ele para, ergue o grimório, e as runas da frase aparecem no ar, como nas
  recitações do jogador (`RecitationRenderer`).
- Quem conhece as runas **lê o que vem** e pode reagir. Quem não conhece vê glifos que não entende.
- Esse aviso é a alma do combate: o evocador é perigoso, mas sempre se anuncia.

### 2.3 Fluxo

Os mais experientes sustentam o **estado de fluxo** (docs/fluxo-design.md) e deixam linhas de condição no grimório:
`patitur aura impediunt` (ao ser ferido, repele o ar ao redor) ou `languet ...` (fraco, faz algo desesperado). O fluxo
custa como torneira aberta, então ele não fica nele a luta toda: entra quando a luta aperta e sai quando a Vis baixa.

### 2.4 Energia

- **100 UMU de Vis**, que regeneram aos poucos. A taxa vai para a configuração.
- **Fontes do mundo primeiro.** Com `tenet`, ele tira a fonte do ambiente em vez de pagar do corpo. Perto de lava, fogo
  sai barato; perto de água, água. Um evocador numa sala de pedra seca é mais fraco que um num pântano. O jogador
  aprende a escolher onde lutar.
- **Tributo.** Sem Vis, um evocador desesperado (personalidade e vida baixa, seção 3.4) pode lançar mesmo assim. O
  espírito cobra da carne: ele perde vida e fica fraco. Pode ser a última jogada dele.

## 3. A mente: como ele compõe

Nada de modelo de linguagem dentro do jogo: seria lento, caro, dependeria de internet e não seria previsível. A mente é
um **planejador por objetivos**, o tipo de IA comum em jogos, apoiado no que o mod já tem.

### 3.1 Intenções

Primeiro ele decide **o que quer**, pela cena:

| Intenção | Quando pesa mais |
|---|---|
| Ferir | o alvo está ao alcance e ele tem Vis |
| Defender | está sendo atingido, ou o alvo recita algo perigoso |
| Afastar | o alvo está perto demais (o evocador não luta corpo a corpo) |
| Prender ou puxar | o alvo foge, ou está perto de um precipício ou da lava |
| Mudar o terreno | há fonte boa por perto, ou o terreno favorece o alvo |
| Recuperar | Vis baixa e nenhuma ameaça imediata |
| Chamar aliados | outros Illagers por perto, longe do alvo (seção 7.2) |

### 3.2 Composição: o catálogo de frases

O motor não conhece runa pelo nome, e a mente também não deve conhecer. Então ela aprende **o que cada frase faz**
lendo e simulando:

1. **Gerar.** Pelas classes de palavra e pelos frames do léxico (fonte, filtro com número, verbo, alvo, condição),
   monta frases curtas, de 2 a 7 palavras, só com runas que o evocador conhece. No lugar do alvo vão `ille` ou `ego`.
2. **Validar.** `SpellGrammar.read` lê cada frase, e qualquer frase com problemas cai fora.
3. **Simular.** Cada frase passa por um `FlowWorld` imaginado. O livro já prevê o feitiço "lido sem ninguém lá", que
   só faz as contas (`FlowWorld.hasCaster`). O mundo imaginado anota **o efeito**: dano possível, empurrão (para
   onde), barreira, conversão do cerne do alvo, fonte capturada, e o custo.
4. **Catalogar.** O resultado é uma assinatura por frase, com efeito, custo e risco. O catálogo depende só do léxico,
   então é calculado **uma vez por mundo** (ou quando o léxico muda) e guardado.

Com o catálogo, cada intenção vira uma busca: quais frases têm a assinatura que essa intenção quer. Exemplos de frases
que o catálogo acharia (ilustrativas; a gramática valida):

- ferir: `vis vertere igni ille ubis vocant` (fogo onde o alvo está)
- ferir com fonte do mundo: `igni tenet ille iactare`
- afastar: `ille quantum 10 iactare`
- puxar: `ille quantum -10 iactare`
- defender: `aura impediunt`
- mudar o alvo: `aqua quantum 16 vertere ille`

### 3.3 Avaliação

Para cada frase candidata da intenção escolhida:

```
nota = peso da intenção × efeito previsto
     − peso do custo × UMU que não vem do mundo
     − risco (ferir a si mesmo, cobrar tributo, atingir aliados)
     + novidade (não repetir a mesma frase sem parar)
```

Os números da frase (o `quantum`, o `chronos`, o `ubis`) são ajustados na hora: quanto custa o bastante para matar,
quanto o alcance pede. A melhor frase vence, com um pouco de sorte no meio para ele não ser previsível.

### 3.4 Personalidade

Cada evocador nasce com pesos próprios:

- **Agressivo:** pesa ferir, aceita tributo cedo.
- **Cauteloso:** pesa defender e afastar, nunca paga tributo.
- **Trapaceiro:** pesa mudar o terreno e o alvo (converter, puxar para a lava, barreiras no caminho).

### 3.5 Adaptação dentro da luta

Ele guarda uma **memória curta do oponente**: o que acertou, o que foi bloqueado e o que o jogador costuma fazer.
Ajusta os pesos e **varia as frases que funcionaram**: troca a fonte, muda o número, acrescenta `chronos`, põe uma
condição na frente. O jogador sente que ele aprende a luta.

### 3.6 Onde um modelo de linguagem ajuda

Fora do jogo, como ferramenta de quem faz o mod: gerar e revisar bancos de frases de exemplo, para dar a cada tipo de
evocador um grimório inicial com cara de autor. Esse banco entra como dado (JSON), passa pela mesma validação e não é
consultado durante o jogo.

### 3.7 Fonte e alcance (pedido do autor, 12/10/2026)

- **Sem fonte não fere.** Um `iactare` sozinho é Vis sem aspecto: um feixe de luz que não machuca ninguém (o efeito
  de pulso de `BALANCED` não faz nada). A mente agora sabe disso: um arremesso sem fonte não serve a golpe nenhum, e
  só o Vis condensado (`chronos 0`, que solta os quatro aspectos onde cai) conta como golpe.
- **O que não chega não faz nada.** Cada ato tem um alcance (`mind/Reach`): o arremesso vai 20 blocos (o do
  `iactare`), o que se faz onde se mira 12 (o do `vocant`), um corpo se bebe de 16. Uma distância escrita antes do
  verbo (`igni 40 ubis iactare`) leva o ato até lá; o `ubis` com distância continua sendo a mira, só mais longe (o
  `Act` guarda o `reach`). Fora do alcance, a opção não vale nada na avaliação.
- **Quem sabe escrever a distância, escreve.** Se o alvo está além do que a opção alcança e o conjurador conhece o
  `ubis`, a mente reescreve a frase com a distância (arredondada para cima, de 8 em 8, com folga de 4 blocos, porque
  o alvo anda durante o transe): `igni iactare` vira `igni 32 ubis iactare`. A gramática lê cada tentativa; até 12
  opções reescritas por escolha, guardadas para a próxima.
- **Quem não sabe, chega perto.** Um objetivo novo (`approachGoal`) leva o conjurador até onde suas magias chegam
  (um pouco aquém do alcance) quando o alvo está longe demais ou fora de vista, e para lá; não chega mais perto do que
  precisa. Quem conhece o `ubis` mira até 32 blocos.
- O Feiticeiro enxerga 24 blocos (o evocador vê 12, menos do que suas magias alcançam).
- Testes: `ReachTest` (unitários) e os GameTests `aSorcererWalksUpToWhatItCannotReach` e
  `aSorcererWhoKnowsHowWritesTheDistance`.

### 3.8 Errar menos, e cuidar de si (pedido do autor, 12/10/2026)

O autor viu que eles insistiam no que não fazia efeito, e que só atacavam.

- **A mira durante a canalização.** O `iactare` dura 2 segundos, em pulsos, e cada pulso cai onde o conjurador olha
  naquele instante. A mente só mirava no instante da leitura; depois o mob virava a cabeça e os pulsos se perdiam. Agora
  ele segura a mira até as torneiras que abriu fecharem (2 s do arremesso, ou o `chronos` escrito). É provavelmente o
  que fazia magias com fonte "não darem dano nem nada". O GameTest `aSorcererHurtsWhatItAimsAt` confirma o acerto.
- **Folga de alcance.** Ele só lê o que alcança o alvo com 1,5 bloco de folga, porque o alvo anda durante o transe.
- **Desistir do que não funciona.** Depois de cada leitura ele confere o que aconteceu. A que não fez nada (menos de
  0,25 de vida tirada) passa a valer no máximo 30% contra aquele alvo; duas vezes seguidas, ele desiste dela contra
  aquele alvo. Se falhou de longe (mais da metade do alcance), ele aprende também que ela não chega tão longe: não a
  lê mais daquela distância contra ninguém, e o objetivo de se aproximar passa a contar com esse alcance menor. Uma
  leitura que funciona apaga a falha. Quando algo não faz nada, ele solta fumaça e resmunga.
- **Só um golpe quebra o transe.** Fogo, afogamento ou queda não quebram mais (senão quem pega fogo nunca consegue ler a
  água que o apaga). O golpe precisa vir de alguém (um atacante ou um projétil).

**Novas intenções**, todas com magias que o sistema já tem, e cada uma com a sua mira (`mind/Aim`):

| Intenção | Frase típica | Mira | Quando serve |
|---|---|---|---|
| curar (`HEAL`) | `aura quantum -10 vocant` | o céu (nenhum corpo) | ferido (menos de 90%): puxa do mundo o elemento que falta |
| proteger (`WARD`) | `vis impediunt` | qualquer | um inimigo dentro do raio da zona (3 blocos com 10 UMU) |
| fugir (`FLEE`) | `ego iactare` | para longe do inimigo, um pouco para cima | inimigo a menos de 4,5 blocos, sobretudo ferido |
| apagar o fogo (`DOUSE`) | `aqua vocant` | os próprios pés | pegando fogo |

- A mesma frase faz coisas diferentes conforme a mira: `aqua quantum -10 vocant` mirando um corpo bebe dele (`DRAW`);
  mirando o céu, puxa água do mundo para o próprio corpo (`HEAL`). A mente pesa cada opção em cada mira e escolhe.
- **A cura respeita o Vita.** A vida segue o elemento que mais falta, então um elemento sozinho só cura o que só ele
  tirou (uma queimadura, uma queda). Por isso a preparação escreve a página "cura", com todos os elementos que ele sabe
  puxar, um por passo, e também uma "cura de X" para cada elemento. A previsão é exata (`VitaSystem.lifeGivenBy`), e
  ele só puxa o que vê ao redor: ar sempre, terra onde há chão, água e fogo se houver por perto (6 blocos).
- **Sem inimigo** ele também lê para si: ferido ou pegando fogo, cura-se ou se apaga depois da luta.
- A preparação escreve, além dos golpes: "escudo", "fuga", "apagar o fogo" e "cura". Pesa num terceiro cenário,
  "pressionado" (inimigo a 2,5 blocos, ferido e pegando fogo).
- A personalidade tem um peso novo, "cuidar de si": agressivo 0,7, trapaceiro 1,1, cauteloso 1,5 (apagar o fogo pesa
  1,5 para todos).
- **O que ficou de fora:** curar os outros (o Mago curando aldeões). O sistema ainda não tem como pôr um elemento no
  corpo de outro ser (`docs/vita-design.md`, "cura com diagnóstico", nos próximos passos). Também não há escudo contra
  flechas: o `impediunt` empurra criaturas e itens, não projéteis.
- Testes: `KeepingWholeTest` (unitários) e os GameTests `aSorcererHurtsWhatItAimsAt`, `aWoundedWizardHealsItself`,
  `aBurningWizardPutsItOut` e `aCasterWardsAFoeAtHand`.

### 3.9 Onde ficar, e o grau na cabeça (pedido do autor, 13/10/2026)

Um zumbi vencia um ancião. O mago abria o transe com o zumbi colado, o golpe quebrava o transe, nada saía, e ele abria
de novo. Depois de duas quebras, passava a só abrir de longe, mas não tinha como se afastar: ficava parado apanhando.
Além disso, o grau só mudava as runas que ele sabe, não o jeito de pensar.

- **Tempo até o golpe (`Threat`).** Ele mede a distância, o alcance do golpe do inimigo (2 blocos; uma pessoa, 3) e a
  velocidade com que o inimigo vem: a que ele viu nos últimos ticks, ou a que ele chuta para alguém daquele tipo (0,5
  bloco por tick para cada ponto de velocidade; uma pessoa correndo, 0,28), a maior das duas. Só conta quem vem bater:
  uma pessoa, ou uma criatura que o caça e tem golpe. Quem atira ou lê fica onde está, e a presa foge.
- **Só abre o transe se o golpe não chegar antes do fim.** O mestre quer que o transe acabe antes do golpe (1×), o
  ancião com folga (1,25×). O aprendiz não vê o inimigo chegando: continua aprendendo só depois de levar o golpe, como
  antes (`Memory.safeDistance`).
- **Manter distância (`keepDistanceGoal`).** Entre uma leitura e outra, ele recua de quem está mais perto do que ele
  quer, mas nunca para tão longe que o que ele lê deixe de alcançar. Se recuar sem ganhar distância por 1,5 s, está
  **encurralado**: lê onde está por 3 s (a mente já prefere barreira e fuga com o inimigo em cima). O Mago usa o mesmo
  objetivo, com um piso de 5 blocos.
- **O grau pesa na escolha.** Escolhe entre as 3, 2 ou 1 melhores (aprendiz, mestre, ancião), com sorte 0,3, 0,15 ou
  0,05. Cansa de repetir: cada vez seguida que lê a mesma coisa contra o mesmo inimigo, ela vale 15% (mestre) ou 25%
  (ancião) menos, até no mínimo 30%. O aprendiz não cansa, e repete.
- Testes: `ThreatTest` (unitários) e o GameTest `aMasterKeepsAHuskOff`, em que um mestre só com pedra e fogo vence um
  husk que começa a 3 blocos. Com o código anterior, o husk ganha.

### 3.10 A arena e as doutrinas (pedido do autor, 13/10/2026)

Os anciãos aprendem a lutar **fora do jogo**, numa arena, e o que aprendem fica guardado num arquivo que o mod carrega.

- **A arena (`src/arena`).** É um simulador tique a tique: chão plano murado, um conjurador e um inimigo
  que anda até ele e bate. Ela reproduz o transe de 2 s que um golpe quebra, as linhas soltas depois dele (o
  conjurador fica parado, mirando: um arremesso segura 2 s), o descanso, a Vis gasta e recuperada (0,5 por segundo).
  Ela **usa a própria mente do mod**: o catálogo de frases, `Mind.evaluate`, `Threat`. Assim, o que se treina é o
  mesmo que roda no jogo, e não uma cópia que se perderia com o tempo.
- **Calibrada pelo mundo.** A GameTest `theArenaIsSetByTheWorld` mede a velocidade do husk (0,114 bloco por tick)
  e a de quem recua (Feiticeiro 0,345, Mago 0,264), e a arena usa esses números (`Calibration`). Quando o mundo
  mudar, mede-se de novo. A medição às vezes sai torta, quando algo de outro teste atravessa o caminho: vale olhar
  duas rodadas.
- **O que se aprende é a postura, não a frase.** Em cada momento livre, o ancião escolhe entre ferir, tirar,
  proteger, fugir, curar, sumir, recuar, aproximar e esperar. A frase continua sendo escolhida pela mente. A situação
  é vista em degraus: distância, quantos transes até o golpe, a própria vida, a Vis, a vida do inimigo e se o
  descanso já acabou (1512 situações). Sem esse último dado, mais treino piorava a doutrina: recuar e ferir se
  confundiam. O aprendizado é Q-learning em tabela: cada luta ajusta um pouco quanto vale cada postura em cada
  situação.
- **A doutrina** é um JSON em `data/elderlexicon/doctrines/<inimigo>.json`. Ele guarda os degraus, quanto cada
  postura vale em cada situação vista pelo menos 30 vezes, e como foi treinado (lutas, sementes, velocidades,
  vitórias). Dá para abrir e ler o que o ancião "pensa".
- **No jogo.** Só o **ancião** segue doutrina, e só contra um inimigo para o qual há uma (zumbis e parentes,
  aranhas e aranhas de caverna, esqueletos e errantes com arco: seção 3.11). A cada meio segundo livre ele pergunta à doutrina o que fazer. Se for ler, a mente
  escolhe a melhor frase para aquela intenção. Se for recuar ou aproximar, os objetivos de movimento obedecem. Numa
  situação que a doutrina nunca viu, ele luta como na 3.9.
- **Treinar:** `gradlew trainDoctrines --args="--foe zombie --episodes 1000000 --open"`. Sem `--open`, treina também em salas (3.12), o que ainda não deve ser instalado. Sem `--install`, o
  arquivo vai para `build/doctrines`. `--show` lista as frases que a mente escolheria para cada intenção. Mudou a
  potência, o custo ou o alcance das runas: treina-se de novo.
- **As paredes importam.** Numa arena de 24 × 24, o ancião recuava até o canto e perdia 82% das lutas, com ou sem
  doutrina. Em campo aberto (80 × 80), como as primeiras doutrinas foram treinadas:

  | 3000 lutas, mesmos começos | vence (sorteadas) | vence (frescas) | transes quebrados |
  |---|---|---|---|
  | como antes da 3.9 | 3% | 0% | 3,1 |
  | ancião, só a 3.9 | 95% | 93% | 0,2 |
  | ancião, doutrina | 96% | 95% | 0,5 |

  A doutrina é mais agressiva que a regra da 3.9: perde menos lutas, mas termina com menos vida. Ela não sabe onde
  ficam as paredes: num lugar fechado, ainda se encurrala.
- **Limites.** A arena não tem quinas, buracos, água nem empurrão. O resultado de cada frase é a previsão da mente
  (`Mind.potency`, `Mind.person`), e não o efeito de verdade. Por isso o que ela aprende precisa ser conferido no
  jogo (GameTest `anElderFollowsItsDoctrine`).

### 3.11 Aranhas e esqueletos (pedido do autor, 13/10/2026)

Cada inimigo pede da arena uma coisa diferente.

- **Aranha.** Corpo a corpo, como o zumbi, porém mais rápida (0,18 bloco por tick, medido), larga (o golpe alcança
  cerca de 2,9 blocos, contra 1,4 do zumbi) e salta quando está de 2 a 4 blocos. O jogo agora calcula o alcance do
  golpe pela largura da criatura, como o próprio vanilla (`blowOf`). Para o zumbi, nada muda.
- **Esqueleto.** O perigo é a flecha, não ele chegar. O arco tem um ciclo: espera 40 ticks depois de uma flecha (20 no
  difícil), puxa 20 e solta. A flecha voa 1,6 bloco por tick. O mago vê o esqueleto puxar o arco
  (`isUsingItem`) e percebe quando ele solta, e daí calcula quando vem a próxima flecha (`Threat.ticksToArrow`). Isso
  entra na situação da doutrina no mesmo lugar do "tempo até o golpe".
- **A regra da 3.9 não espera flechas.** Na arena, esperar uma janela entre flechas deixava o ancião sem doutrina
  *pior* que antes (vencia 65%, contra 77%): ele tomava flechadas enquanto esperava. Por isso, sem doutrina, o
  tempo até o golpe só vale contra quem vem bater.
- **Como a flecha acerta** era um chute: 90% a 5 blocos, 60% a 15, e 70% disso contra quem anda. Medido depois
  (3.15). O dano é 3,5.

Resultados na arena (80 × 80, 1 milhão de lutas de treino; 3000 lutas, mesmos começos):

| | vence (sorteadas) | vence (frescas) |
|---|---|---|
| aranha, como antes | 9% | 0% |
| aranha, ancião só com a 3.9 | 82% | 73% |
| aranha, ancião com doutrina | 87% | 83% |
| esqueleto, como antes (igual à 3.9) | 81% | 77% |
| esqueleto, ancião com doutrina | 99,9% | 99% |

- **O que a doutrina do esqueleto aprendeu:** lê (`tirar` ou `ferir`) logo depois de uma flecha, quando faltam de
  1 a 1,5 transe para a próxima, e se move quando a flecha está para vir. Os 99% dependem do chute de que a flecha
  erra mais quem anda: se no jogo não for assim, o número cai.
- **No jogo:** as GameTests `anElderFightsASpiderByItsDoctrine` e `anElderFightsASkeletonByItsDoctrine` passam,
  junto com a do husk. Em duas rodadas, o ancião venceu as três lutas em poucos segundos, perdendo no máximo 4 de
  24 de vida: com o idioma quase todo, ele tira a vida do corpo do inimigo de longe. Essas lutas são curtas demais para pôr à prova o ritmo da doutrina. A
  GameTest `theArenaIsSetByTheWorldForSpidersAndSkeletons` mede a aranha e o arco: 6 flechas em 360 ticks, cerca de
  23 ticks puxando cada uma.
- **Treinar:** `gradlew trainDoctrines --args="--foe spider --episodes 1000000 --install"` (e
  `--foe skeleton`).

### 3.12 O sentido de parede (pedido do autor, 13/10/2026)

Até aqui, o mago recuava sempre em linha reta para longe do inimigo, e a doutrina não sabia onde ficavam as paredes.
Em lugar fechado, ele recuava até o canto e morria lá.

- **Quanto espaço há (`Room`).** O mago olha 5 direções a partir da que se afasta do inimigo (reto, ±45° e ±90°) e
  mede quantos blocos poderia andar em cada uma, até 12. No jogo, um bloco é andável se não há nada no pé nem na
  cabeça, nenhum líquido nem fogo, e há chão embaixo (ou um degrau abaixo); ele também sobe um degrau. Na arena,
  conta só a parede da sala. O código é o mesmo nos dois lados.
- **Recuar pelo melhor caminho.** Ele vai pela direção com mais espaço; reto para trás vale 2 blocos a mais que de
  lado, porque ganha mais distância. Se nenhuma direção tem 1,5 bloco livre, ele se sabe **encurralado** na hora, em
  vez de empurrar a parede por 1,5 s antes de perceber. Isso vale para todo conjurador, com doutrina ou sem.
- **A doutrina pode ver o espaço:** encurralado (menos de 3 blocos), apertado (3 a 8) ou aberto. As doutrinas que
  não aprenderam com esse dado continuam sendo lidas, sem ele.
- **A arena treina em salas.** Cada luta de treino acontece em campo aberto (40% das vezes) ou numa sala de lados
  sorteados entre 5 e 32 blocos, o que inclui corredores (`Hall`). A luta começa com o mago descansando (20 ticks),
  como no jogo. `--open` treina só em campo aberto, `--hurt` diz quanto pesa a vida perdida (padrão 2), e
  `--trace N` imprime as decisões de N lutas numa sala.

**O que a arena mostrou** (3000 lutas frescas por cenário; ancião só com a 3.9 → ancião com doutrina treinada em
salas):

| | campo aberto | sala 16 × 16 | sala 10 × 10 | corredor 5 × 40 |
|---|---|---|---|---|
| zumbi | 99% → 100% | 4% → 89% | 0% → 32% | 28% → 78% |
| aranha | 99% → 100% | 0% → 0% | 0% → 0% | 4% → 28% |
| esqueleto | 77% → 100% | 63% → 100% | 47% → 100% | 65% → 100% |

**Mas essas doutrinas não foram instaladas.** No jogo, elas se saíram pior que as anteriores:

- **Pagar com a carne de propósito.** O rastreamento (`--trace`) mostrou que, em sala, a doutrina deixa o zumbi
  bater até a vida cair abaixo de 45%. Assim o mago agressivo fica desesperado e pode pagar com a carne o `tirar`
  letal, que custa uns 100 UMU. Na arena funciona; no jogo, o ancião passava do ponto e morria. Pesar em dobro a
  vida perdida (`--hurt 2`) acaba com isso em campo aberto (ele termina com 22 de vida em vez de 9), mas não em sala,
  onde é a única saída que a arena enxerga.
- **A diferença está no dano.** No jogo, o ancião mata um husk com um ou dois `ferir`; a arena supõe 8 de dano por
  arremesso (`Mind.potency`, um chute "a ajustar jogando"). Com o dano subestimado, as lutas da arena ficam longas, as
  paredes pesam muito mais e a doutrina aprende saídas desesperadas que no jogo só atrapalham. **Antes de treinar
  doutrinas para salas, é preciso medir o dano de verdade** (uma GameTest que compare o que cada frase faz com o que a
  mente previu) e calibrar a arena com isso.
- **O que ficou no jogo:** o recuo pelo melhor caminho e as doutrinas anteriores (só campo aberto, 3.10 e 3.11). Com
  eles, o ancião venceu as quatro lutas de teste, **inclusive a da sala de 16 × 16**, nas duas rodadas, quase sempre
  sem perder vida.
- **Aranha em lugar fechado é perdida na arena:** a 8 blocos, ela chega em cerca de 28 ticks, menos que os 40 do
  transe, e seu golpe alcança quase 3 blocos. Se isso se confirmar no jogo, a resposta não está na mente, e sim em ler
  mais depressa (varinhas gravadas, `docs/varinhas-design.md`) ou em ter algo preparado antes da luta.
- **Escapar do canto passando pelo inimigo** (de lado, a mais de 90°) ainda não está entre as direções olhadas.
- **GameTests:** `anElderFightsAHuskInAHall` (sala de 16 × 16, **não obrigatória** enquanto nenhuma doutrina de sala
  estiver instalada) e a da aranha, agora num campo de 40 × 40 com teto: sem o teto, a aranha arremessada pelo golpe
  escalava a parede e sumia de vista. As lutas de doutrina registram no log, a cada segundo, o que o ancião vê e
  decide (`describeFight`). Testes de unidade: `RoomTest`.

### 3.13 O dano medido (pedido do autor, 13/10/2026)

A arena usava como dano de cada frase a previsão da mente (`Mind.potency` para arremessos, um chute "a ajustar
jogando"; `Mind.person` para tirar, o corpo de uma pessoa). Agora ela usa o que foi medido no jogo.

- **A medição.** As GameTests `whatTheElderDoesToAHusk`, `...ToASpider` e `...ToASkeleton` montam uma lista: as
  frases que o ancião da arena lê para ferir e para tirar (perto e longe, inteiro, ferido e desesperado, com Vis cheia
  e pouca) e os arremessos simples de cada energia. Um Feiticeiro novo lê cada frase num alvo parado a 6 blocos, 3
  vezes, e fica a mediana. Tudo vai para `run/arena-calibration/damage-<inimigo>.json`, com o previsto ao lado.
  A distância não muda nada (6 e 12 blocos deram o mesmo). A medição leva minutos, então só roda com a variável de
  ambiente `ELDERLEXICON_CALIBRATE` definida; sem ela, os testes passam na hora.
- **A arena usa a tabela** (`src/arena/calibration`, `Damage`). Frase medida faz o que foi medido. Frase não medida
  faz o previsto vezes a mediana de medido/previsto das medidas da mesma intenção e energia. `--foreseen` volta ao
  previsto.

O que a medição mostrou (dano em pontos de vida; previsto pela arena → medido):

| frase | husk (20) | aranha (16) | esqueleto (20) |
|---|---|---|---|
| `firmo quantum 20 iactare` | 8 → 7,5 | 8 → 13,6 | 8 → 7,8 |
| `aqua quantum -10 vocant quantum 20 iactare` | 7,6 → 13,7 | 7,6 → 13,1 | 7,6 → mata |
| `firmo quantum -10 vocant quantum 20 iactare` | 28 → 14,3 | 28 → mata | 28 → 10,3 |
| `igni tenet quantum 20 iactare` (só desesperado) | 28 → mata | 28 → mata | 28 → mata |
| `igni iactare` / `aqua iactare` / `aura iactare` | 4 / 2 / 1 → 6,8 / 4 / 2,8 | → 7,1 / 4 / 3 | → 6 / 4 / 2,9 |

O arremesso de terra está bem previsto, menos contra a aranha. Tirar (com o arremesso que vem junto) faz quase o
dobro do previsto. Fogo, água e ar fazem de 1,7 a 2,8 vezes mais que o chute. **No jogo, `Mind.potency` erra do
mesmo jeito**, o que puxa os conjuradores para a terra; corrigi-lo muda o equilíbrio de todos, e fica para decidir.

- **A mente que treina é a cautelosa** (`--personality`). O rastreamento mostrou que a doutrina, treinada com um
  ancião agressivo, planejava apanhar até ficar desesperado e então pagar com a carne o golpe letal. O cauteloso
  nunca fica desesperado: ele seguiria esse plano até morrer. Treinada com a mente cautelosa, a doutrina não conta
  com o desespero e vence o mesmo tanto, terminando com mais vida.
- **`sumir` como no mod.** Quem foi despistado ainda nota um invisível a 7% do seu alcance de visão (zumbi: 2,5
  blocos; aranha e esqueleto: 1,1), e o reencontra. A arena tratava `sumir` como perder o mago de vez.
- **Esperar encostado.** Uma regra "com o inimigo a menos de 3 blocos e espaço para recuar, nunca esperar nem
  aproximar" (`--sensible`) piorou a doutrina na arena nas duas vezes em que foi testada. Fica como opção, desligada.

Resultados com o dano medido (3000 lutas frescas; ancião só com a 3.9 → com doutrina):

| | campo aberto | sala 16 × 16 | corredor 5 × 40 |
|---|---|---|---|
| zumbi | 99% → 99,9% (23 de vida no fim) | 16% → 79% | 32% → 75% |
| aranha | 99% → 99% | 0% → 0% | 4% → 2% |
| esqueleto | 79% → 100% | 67% → 100% | 69% → 100% |

**Instaladas depois da 3.14.** Na primeira tentativa, o ancião morreu queimado numa luta em campo aberto; a causa
estava na mente, não na calibração (3.14). Corrigida a mente, as doutrinas calibradas venceram as quatro lutas de
teste em quatro rodadas seguidas, sem queimaduras, terminando com 19 a 22 de vida de 24.

Quanto a flecha acerta quem anda foi medido depois (3.15).

### 3.14 O fogo que o mago lê contra si (pedido do autor, 14/10/2026)

Numa luta de teste, o ancião morreu queimado. Parecia que ele andava pelo fogo que suas frases deixavam no chão; não
era.

- **Ele não anda pelo fogo.** As GameTests `aCasterWalksAroundFire` e `aCasterDoesNotWalkThroughFire` põem uma faixa
  de fogo eterno entre um Feiticeiro e o alvo. Com uma passagem de lado, ele dá a volta e chega perto; sem passagem,
  fica do lado de fora. O caminho do Minecraft já evita fogo; nada precisou mudar ali.
- **Ele se queimava lendo.** As lutas de doutrina agora registram no log o momento em que o ancião pega fogo: onde
  estava, as chamas em volta (nenhuma), a última fonte de dano e a última frase lida (`BURNING ...`). Era sempre
  `igni quantum 20 24 ubis vocant`, "fogo levado a 24 blocos", lida contra um husk a 12 ou 20 blocos.
- **`igni ... vocant` não fere quem é mirado.** A GameTest `whatFireSetsTheCasterBurning` leu cada variante contra um
  husk a 6, 12 e 20 blocos: o husk não perdeu nada em nenhuma, e quem leu perdeu de 1 a 4 de vida. A mente contava
  "fogo trazido para onde se mira" como golpe (`Mind.intentOf`); deixou de contar. O motor de feitiços ficou como
  está: **por que o fogo trazido fere quem lê, e não o alvo, é uma pergunta para o motor** (`VocantFunctionHandler`),
  se não for o que se quer.
- **A sonda deixa chamas no mundo** (blocos de luz) onde os testes seguintes são montados, e derrubava 8 a 10 testes
  de fogo e matéria. Por isso ela só roda com `ELDERLEXICON_PROBE` (nem a calibração a liga).
- **Queimadura legítima:** um esqueleto em chamas atira flechas em chamas, e isso também incendeia o mago.

### 3.15 Quanto a flecha acerta (pedido do autor, 14/10/2026)

- **A medição.** A GameTest `howOftenAnArrowStrikes` (só com `ELDERLEXICON_CALIBRATE`) monta quatro raias lado a
  lado. Em cada uma, um esqueleto que não anda (velocidade zero, mas mira e atira) atira num Feiticeiro inerte, que é
  movido à mão a 0,345 bloco por tick, o passo de quem recua. O movimento começa quando o arco é puxado e vai até a
  flecha chegar, e a partida é escolhida para que, no disparo, ele esteja a 5, 10 ou 15 blocos. Uma raia por
  movimento: parado, recuando, avançando, de lado. Acertou é perdeu vida; ele é curado entre uma flecha e outra.
  Tudo vai para `arena-calibration/arrows-skeleton.json`, flecha por flecha, com a distância real no disparo.
- **O resultado** (três rodadas somadas, `src/arena/calibration/arrows-skeleton.json`):

  | movimento do mago | 5 blocos | 10 blocos | 15 blocos |
  |---|---|---|---|
  | parado | 93% | 50% | 27% |
  | recuando | 42% | 37% | 13% |
  | avançando | 90% | 42% | 28% |
  | de lado | 8% | 3% | 0% |

  O chute errou para os dois lados. Parado, o mago leva **bem menos** flechas de longe (27% a 15 blocos, não 60%).
  Avançar quase não protege. **Andar de lado é quase imune**: o esqueleto mira onde o mago está, não onde vai estar.
- **A arena** classifica o movimento do mago no instante do disparo, pela direção do passo em relação à flecha
  (`Arrows.Motion`): parado, recuando, avançando ou de lado. Entre as distâncias medidas, interpola.
- **O esqueleto da raia de lado morreu cedo** em duas das três rodadas, e não foi o sol (na terceira ele tinha
  resistência a fogo). Por isso essa raia tem menos flechas (48, 36 e 12). A causa ficou sem explicação.
- **Medir mexe no mundo.** As medições de dano leem frases de fogo e deixam chamas onde os testes seguintes são
  montados: numa rodada com `ELDERLEXICON_CALIBRATE`, os testes de chama podem falhar. A suíte normal, sem a
  variável, não roda nada disso.

Com isso, contra o esqueleto (3000 lutas frescas): como antes 98% em campo aberto, 84% na sala de 16 × 16, 62% na de
10 × 10; com a doutrina, 100% em todos os cenários, terminando com 21 a 23 de vida.

**O que isso sugeria:** uma postura própria de andar de lado, circulando o inimigo como um jogador faz (3.16).

### 3.16 Circular (pedido do autor, 14/10/2026)

Uma postura nova, `CIRCLE`: o mago anda em volta do inimigo, na perpendicular à linha entre os dois.

- **Para que lado.** Para o lado em que estava indo, enquanto houver espaço ali (1,5 bloco livre); senão, para o
  outro, se ele tiver mais (`Room.aside`, o mesmo olhar de espaço do recuo, 3.12). No jogo, o objetivo que recua
  também circula quando a doutrina manda, a passos de 3 blocos, refazendo o caminho a cada quarto de segundo. Na
  arena, cada passo dele conta como "de lado" para as flechas (3.15).
- **Só pela doutrina.** A regra da 3.9, sem doutrina, não circula. As doutrinas que não conhecem a postura nunca a
  escolhem (ela vale o mínimo nelas).

Resultados na arena (3000 lutas frescas; doutrina sem círculo → com círculo):

| | campo aberto | sala 16 × 16 | sala 10 × 10 | corredor 5 × 40 |
|---|---|---|---|---|
| zumbi | 99,9% → 100% | 79% → **97%** | 8% vence, 92% morre → 3% vence, 54% morre | 75% → 78% |
| aranha | 99,4% → 99,9% | 0% → 0% | 0% → 0% | 2% → 4% |
| esqueleto | 100% → 100% (vida no fim 21 → 23) | 100% → 100% (21 → 24) | 100% → 100% | 100% → 100% |

- **Contra o zumbi numa sala,** circular é o que faltava: ele anda em volta, o zumbi corta por dentro e nunca o
  alcança, e o mago lê quando abre distância. Na sala de 10 × 10, ainda não ganha, mas sobrevive em quase metade das
  lutas, que acabam empatadas.
- **Contra a aranha em lugar fechado,** nada muda: ela é rápida demais para isso.

### 3.17 Mago contra mago (pedido do autor, 14/10/2026)

- **O transe do outro é a ameaça.** Contra outro conjurador, o "tempo até o golpe" é quanto falta para o transe dele
  acabar (o transe aparece sobre a cabeça), zero enquanto ele solta as linhas, ou o descanso dele mais um transe
  inteiro (`Threat.ticksToReading`).
- **Dano medido contra um Feiticeiro** (`whatTheElderDoesToACaster`; `ELDERLEXICON_CALIBRATE=caster` mede só isso):
  ferir forte 6,6, tirar 9,3 a 9,5, e as frases de desespero matam (24).
- **A arena ganhou um oponente que lê.** Lê ferir, tirar ou curar com o mesmo transe, descanso e Vis, e anda até o
  mago quando nada do que lê o alcança. Um golpe quebra o transe dele como o dele quebra o do mago. Não usa barreira,
  fuga nem sumir, não recua e não põe fogo.
- **Quem é ferido reencontra quem o feriu, mesmo invisível** (o `HurtByTargetGoal` do jogo ignora a invisibilidade).
  A arena achava que `sumir` despistava até quem estava sendo ferido; corrigido para todos os inimigos. As doutrinas
  de zumbi, aranha e esqueleto quase não mudaram com isso.
- **Doutrina por temperamento.** Contra outro mago, o agressivo, ferido, fica desesperado e mata de uma vez; o
  cauteloso nunca. Por isso a doutrina contra magos é de um temperamento (`caster-<temperamento>.json`), treinada
  contra oponentes de temperamento sorteado (`--personality`, `--foe-personality`), e o ancião segue a do seu.

  | temperamento | sem doutrina | com doutrina (arena) |
  |---|---|---|
  | agressivo | 63–79% | 97–100% |
  | trapaceiro | 24–49% | 81–100% (termina com 4 de vida) |
  | cauteloso | 13–21% | 0–15%: pior |

- **Não instaladas.** No jogo, um ancião agressivo com a doutrina perdeu as quatro lutas contra um Mago mestre
  agressivo. O Mago de verdade recua e põe fogo no chão sob os pés do ancião, e ele queimava por segundos; nada
  disso existe no oponente da arena. A GameTest `anElderBeatsAnotherCaster` fica, não obrigatória, para medir a
  distância. Para instalar, o oponente da arena precisa recuar e incendiar como o do mundo.
- **Pegando fogo, o mago larga a doutrina.** Nenhuma postura dela é "apagar o fogo": com a doutrina, ele não se
  apagava. Agora, em chamas, a mente decide. Mesmo assim, na luta registrada, ela leu ferir em vez de sair do fogo;
  sair de um bloco em chamas é o próximo passo.

## 4. O que o evocador sabe

- **Repertório por grau.** Aprendiz (2 ou 3 runas), mestre (6 a 8), ancião (quase o léxico inteiro). Cada evocador
  nasce com um sorteio dentro do grau e da região (deserto puxa `igni` e `firmo`; pântano, `aqua`).
- **Grimório próprio e persistente.** O que ele escreveu fica salvo na entidade. Ele chega na luta com páginas prontas.
- **Ele não rouba runas.** Ler não ensina, nem a ele. O que ele faz é **copiar frases**: se vê o jogador recitar algo
  que funciona, anota a frase no grimório. As runas que ele não conhece o espírito pula, então a cópia sai pela
  metade, mas às vezes a metade ainda serve. *(Isto corrige a ideia da conversa anterior, de que ele aprenderia runas
  vendo o jogador: o livro e a decisão de 06/10 não deixam.)*

## 5. O que o jogador ganha

- **O grimório do evocador** como loot: páginas com frases e feitiços nomeados (`reframe`). O jogador pode usá-las com
  `surgit`, mas o espírito dele pula as runas que ele não conhece, como em qualquer leitura.
- **A notícia de runas que ele não tem.** Ver no grimório uma runa que não sabe ler mostra que ela existe e dá vontade
  de ir entendê-la. O loot não ensina; aponta o caminho.
- **Frases de autor.** O catálogo e a adaptação produzem combinações que o jogador talvez não pensasse. Derrotar um
  ancião é também uma aula de gramática.

## 6. Contrajogo

Tudo vem do livro ou das regras já existentes:

- **Quebrar o transe.** O livro diz que, se o mago hesitar, "a fresta se fechará". Um golpe forte durante o transe
  visível cancela a conjuração, e a Vis é gasta à toa (ou só metade; seção 10).
- **Tirar o grimório.** Sem a página, o evocador só tem os dois segundos: frases de duas ou três palavras, de cor.
  Desarmá-lo (derrubar o livro da mão, queimar, roubar) o deixa muito mais fraco.
- **Secar a Vis.** Forçá-lo a gastar (defender, se esconder) até ele ter que escolher entre recuar e pagar tributo.
- **Escolher o terreno.** Longe das fontes do mundo, tudo sai do corpo dele.
- **Ler e responder.** Quem conhece as runas vê o feitiço chegando e escolhe a resposta: barreira, distância, contra-
  feitiço.

## 7. No mundo

- Aparece **no lugar de parte dos evocadores comuns**: nas mansões e nas raids das ondas mais altas. Os comuns
  continuam existindo.
- Mansões com feiticeiro ganham uma **sala de estudo**: estante, mesa, o grimório aberto. Ele fica lá preparando
  até ser perturbado.

### 7.1 No lugar das presas e dos vexes (em discussão)

Os golpes de vanilla saem do feiticeiro e dão lugar a feitiços da língua, feitos só com o que existe hoje.

**Primeira rodada, testada pelo autor (12/10/2026):**

| Frase | Resultado | Por quê |
|---|---|---|
| `firmo ille tenet chronos 0 iactare` | tirou a terra do Vita de quem conjura | `ille` só aponta para alguém até um segundo depois de um acontecimento (golpear, ser ferido…); sem ele, ninguém tem a marca, e a terra veio do corpo |
| `firmo ille tenet ille ubis vocant` | falhou | o mesmo: `ille` sem acontecimento |
| `aura impediunt` | um círculo em volta de quem conjura, sem efeito que importe | — |
| `firmo <guarda> ligabis ego` | protegeu, até o primeiro guarda morrer, e então o conjurador morreu junto | o `firmo` é destino compartilhado: quando um pai morre, o filho morre (docs/ligabis-design.md, seção 5). Não serve de escudo |
| estátuas com `aura` | descartado | o golem ainda tem defeitos, e o vínculo de `aura` faz a estátua repetir os movimentos do evocador: ele teria de lutar como um golem para usá-la |

O que a rodada ensinou: **para mirar num alvo, o evocador usa a mira e os números, não `ille`.** Sem `ubis`, o
destino de um verbo é o ponto mirado (docs/plano-materia-e-forca.md, R3), e um mob sempre olha para o alvo. Os números
do lugar (a distância, ou as coordenadas) ele calcula na hora (seção 3.3). O `ille` fica para as linhas de reação, com
condição (`patitur …`), quando há mesmo um "aquele".

**Segunda rodada, testada pelo autor (12/10/2026):**

| Frase | Resultado |
|---|---|
| `firmo 8 tenet vocant` | prende, mas o alvo sai pulando: pouco efetivo |
| `firmo X Y-2 Z tenet X Y+2 Z ubis iactare` | complicado demais para reescrever a cada passo do alvo |
| `firmo tenet chronos 0 iactare` | funciona como um tiro de terra básico; a condensação da terra ainda precisa de ajustes |
| `aura quantum 20 impediunt` | o mesmo círculo, só visual |
| `<marca> vocant iactare` | traz o mob da marca e o empurra, sem impacto real no alvo; para só trazer um lacaio, `<marca> vocant` basta e sai mais barato |

**O que fica por enquanto:**

- **Presas:** a **mordida**, `aqua quantum -10 vocant` mirando o alvo: tirar a água do corpo dele. É como um mago
  inteligente deste mundo lutaria. O tiro de terra fica como ataque básico, quando a condensação estiver ajustada.
  - Testada pelo autor (12/10/2026): a água foi absorvida, mas **o alvo não sofreu nada**. Lendo o código, a água não
    veio dele: o `vocant` negativo (`WorldSources.absorb`) só drena **blocos** perto da mão ou do `ubis`, nunca um
    corpo. E um mob nem tem corpo a drenar: só o jogador tem Vita. Ver seção 7.4.
  - **Com `chronos`, vira dreno contínuo**, pago como torneira aberta.
  - **O preço para quem morde:** a água chega ao corpo do evocador. Ferido, ela cura o que falta. Com a água cheia, ela
    transborda: empurra os outros elementos para fora e traz os males do excesso (vita-design, "o excesso toma
    espaço"). A mente pesa isso (seção 3.3): morde quando está ferido, ou gasta a água tirada no próximo verbo em vez
    de guardá-la (seção 7.4).
- **Chamar um lacaio:** `<marca> vocant` (seção 7.2).
- **Vexes:** homúnculos famintos (seção 7.3).

### 7.2 Chamar aliados

Dois sentidos, e os dois ficam:

- **Chamar com a voz** (sem magia): os Illagers por perto seguem a ordem dele (cercar o alvo, protegê-lo).
- **Invocar pela marca.** Alguns Illagers **nascem com uma marca** (a do bando), e o evocador do bando a conhece. A
  língua já faz o resto: `<marca> vocant` traz até ele quem tem a marca (testado: funciona). Lançar o lacaio com
  `iactare` só o empurra, sem ferir o alvo, então não vale o custo.
- **A marca no loot.** O grimório do evocador traz a marca do bando escrita. Uma marca não é runa: o jogador que a lê
  pode usá-la como qualquer marca (`<marca> vocant`) e virar os bandos daquele evocador contra eles mesmos.

### 7.3 Os vexes: homúnculos famintos

Ideia do autor (12/10/2026). Em vez de chamar um lacaio, o evocador **faz** o vex, pela alquimia que o mod já tem
(docs/vita-design.md e docs/particulas-design.md, seção 6):

1. **O homúnculo.** Carne do chão, ar e água no mesmo instante e lugar, com a âncora de 100 de vis: nasce um homúnculo
   sem kern (a lei da animação).
2. **O vex.** Ele muda o cerne do homúnculo para o código do vex (`materials.json`: aura 79, igni 12, aqua 8,
   firmo 1), com quatro linhas `<fonte> quantum N vertere <marca>` na mesma coluna. Converter não cobra vis: o
   homúnculo vira vex com o mesmo total.
3. **A fome.** Um ser sem kern não tem vontade, mas o corpo dele quer continuar vivo. Ele **busca combustível**: vai até
   os seres vivos e tira deles o que lhe falta. Por isso ataca sem que ninguém mande.

O que isso dá ao combate:

- **A vida curta do vex de vanilla sai da fome.** Sem combustível, o corpo se gasta e morre. Um vex que acha vítimas
  dura mais; um que não acha, definha.
- **O jogador também pode fazer.** É alquimia comum; nada é exclusivo do evocador.

**O que o sistema precisa ganhar (antes de implementar):**

- **A fome dos seres sem kern.** Hoje um ser sem kern perde todos os objetivos (`Beings`, "vive, mas não age"). Ele
  passa a ter um só: alimentar-se. Falta decidir:
  - de que ele tem fome. Proposta: do elemento em que está **mais abaixo** da proporção do seu tipo, e, estando são,
    do que o corpo gasta com o tempo (o vex, quase todo ar, gasta ar);
  - como come. Proposta: o mesmo que a mordida (seção 7.1), o `vocant` negativo, tirando o elemento da vítima. Um vex
    tira o fôlego de quem ataca;
  - como se gasta. Proposta: sem kern não há homeostase (`vita-design`); o corpo perde um pouco por segundo e definha
    quando um elemento chega à faixa grave.
- **A quem ele não ataca.** Um ser faminto sem vontade ataca qualquer um, inclusive quem o fez. Proposta: a âncora
  dele é **a vis de quem o fez**, e ele não se alimenta da vis que o sustenta. Assim poupa o criador, seja o evocador
  ou o jogador. Os outros Illagers do bando não estão protegidos por isso.
- **A conta da âncora.** A âncora custa 100 de vis, toda a reserva de um ser vivo (livro 3.2). O evocador não pode
  fazer vexes no meio da luta sem pagar tributo em carne. Daí:
  - ele os faz **na preparação** (seção 2.1), com a reserva cheia, e os deixa à espera na sala de estudo;
  - ou, desesperado, faz um em combate e paga o tributo (seção 2.4).
  - Um vex já pronto pode esperar porque, perto do criador e sem vítima, o corpo dele se gasta devagar? (pergunta)
- **O ritual como página.** São várias linhas no mesmo instante: uma página fixa do grimório, que a mente não precisa
  compor, só decidir quando usar.

### 7.4 O que a mordida pede do sistema

Duas peças que não existem, e que o evocador precisaria de qualquer jeito:

1. **Todo ser vivo tem corpo (Vita).** Hoje só o jogador tem. O evocador precisa de um (a Âncora dos Cem, o tributo em
   carne, o transbordar), e a vítima da mordida também. Proposta:
   - todo `LivingEntity` ganha o cálice da Vita (decidido em 12/10/2026) (vita-design, "O corpo como cálice"): capacidade de 5 UMU por ponto de
     vida máxima, dividida pelo **cerne do seu tipo**, que a tabela já tem (`materials.json`, seção `beings`: os 12
     primeiros mais os 64 propostos na etapa 3);
   - o dano comum tira do elemento que atinge, como no jogador, e a vida do mob segue o elemento mais em falta;
   - para não pesar no servidor, o corpo de um mob só é guardado quando sai do cheio (o mundo em repouso fica fora da
     conta, como o `NatureField` faz com os blocos).
2. **Um corpo é uma fonte.** Quando a mira ou o lugar (`ubis`) caem num ser vivo, as fontes dele contam. **A mira
   basta** (decidido em 12/10/2026): não é preciso escrever um lugar.
   - `aqua quantum -10 vocant` tira a água do corpo mirado, e ela entra no corpo de quem conjura;
   - `aqua <lugar> tenet …` captura a água do corpo para pagar o verbo seguinte, sem passar pelo corpo do mago. É a
     mordida que não transborda: `aqua tenet iactare`, mirando o alvo, tira a água dele e a lança de volta;
   - o que sai do corpo vira falta nele, e a falta fere (a vida segue o elemento mais em falta).

São também a base da fome dos vexes (seção 7.3): comer é a mordida feita por um corpo sem kern.

**Implementado (12/10/2026)**, ainda sem teste dentro do jogo:

- `VitaData` e `VitaSystem` aceitam qualquer `LivingEntity`. O cerne de um mob vem de `Beings.coreOf` (a tabela, ou o
  que uma transformação lhe deu), relido a cada vez; sem entrada na tabela, a proporção de uma pessoa.
- O corpo de um mob só é escrito quando ele sai do inteiro (o primeiro dano, ou algo tirado dele) e é esquecido quando
  volta inteiro. `VitaEvents.onLivingTick` só trabalha nos que têm corpo escrito.
- O dano comum de um mob tira do elemento que atinge (`VitaDamageHandler`), como no jogador. O calor que o fogo põe e o
  ar que o raio põe continuam só no jogador (a janela de acumulação é dele).
- Os efeitos de desequilíbrio valem para mobs; a fome que eles causam, só para o jogador.
- Quando a vida de um mob cai porque um elemento falta, ele mostra o golpe (`broadcastDamageEvent`).
- `WorldSources.take`: sem lugar escrito, se a mira acha um ser vivo à vista (até 16 blocos, sem bloco na frente),
  as fontes saem do corpo dele (`VitaSystem.drain`). Vale para o `vocant` negativo e para o `tenet` que paga um verbo.
- GameTest `waterDrawnOutOfABodyHurtsIt`: `aqua quantum -10 vocant` mirando uma vaca a fere.
- Quem tira de um corpo fica com o golpe por 5 segundos: o mob se vira contra ele, e a morte pela falta é abate dele
  (`VitaSystem.drain(..., by)`, `indirectMagic`). GameTest `aBodyDrawnFromKnowsWhoDrewIt`.
- O `tenet … vocant` que move matéria tira de um corpo mirado (`Transfer.fromBody`): a água do sangue sai como água.
  GameTest `waterMovedOutOfABodyIsPutDown`.
- O calor do fogo e o ar do raio entram também no corpo dos mobs (sem a janela de acumulação, que é do jogador).
- Testado pelo autor no jogo (12/10/2026): funciona, e `aqua tenet iactare` mata com um golpe.

**Balanceamento (decidido e implementado em 12/10/2026: quem tira paga a vida que tira).** A vida segue o elemento mais em falta, então tirar de um elemento de parte
pequena é muito mais letal que tirar da mesma quantidade de outro:

| Feitiço contra um jogador (100 UMU de corpo) | Tira | Vida que sobra |
|---|---|---|
| `aqua quantum -10 vocant` | 10 dos 55 de água | 82% |
| `aqua tenet iactare` (o verbo paga 20) | 20 dos 55 de água, e ainda leva o tiro | 64%, menos o tiro |
| `igni quantum -1 vocant` | 1 dos 2 de fogo | 50% |
| `igni quantum -2 vocant` | todo o fogo | morte, por 2 UMU |

Uma vaca (10 de vida, 30 de água) cai a 33% só com o `aqua tenet iactare`. Proposta: **quem tira de um corpo vivo
paga a vida que tira**, 5 UMU por ponto, como o vínculo de `firmo` já cobra (ligabis-design, "vida movida × 5 UMU").
Tirar 2 de fogo de um jogador custaria 100 UMU (toda a reserva), e tirar 10 de água, 18. Mantém a anatomia e põe o
preço no dano, não no tanto que se move.

- `Bodies.draw` cobra de quem conjura 5 UMU por ponto de vida que a falta tira (`VitaSystem.Drawn.life`: a vida antes
  menos a vida depois), pago como um vínculo paga (`SpellCostModule.payOutsideCast`): experiência, comida, e por fim a
  própria vida. O espírito é literal: quem não pode pagar paga com a carne. O mago vê quanto tirou e quanto pagou.
- GameTest `drawingOutOfABodyCostsTheLifeItTakes`: 1 de fogo tirado de uma vaca leva dois terços da vida dela e custa
  cerca de 33 UMU.

## 8. Por dentro

- **O conjurador.** Hoje o lançamento é preso ao jogador: `SpellCastingService.cast(ServerPlayer, ...)`,
  `SpellContext.player()`, e `ServerPlayer` aparece em 42 arquivos do pacote `spell`. O primeiro trabalho é uma
  abstração de **conjurador** (`LivingEntity` com Vis própria, repertório e grimório), com o jogador como um caso
  dela. O `FlowWorld` já é uma interface; o resto do caminho vem atrás dele.
- **Feito (12/10/2026): o conjurador.** O lançamento aceita qualquer `LivingEntity` (`SpellCastingService.cast`,
  `SpellContext.player()`); o que é próprio de quem conjura está em `spell/Casters`:
  - **a Vis:** a de um jogador é a experiência; a de qualquer outro ser é a Âncora dos Cem (`Casters.ANCHOR_UMU`),
    que o corpo enche de volta a 0,5 UMU por segundo (cheia de novo em pouco mais de três minutos). O custo de uma
    criatura sai da âncora e depois da carne (o tributo); só o jogador paga com comida;
  - **o que o espírito diz:** só um jogador ouve (`Casters.tell`, `Casters.status`);
  - **o golpe:** o de um jogador, ou o de uma criatura (`Casters.strike`).
  - **Continua só do jogador**, e uma criatura que o tenta não faz nada: `reframe` (o grimório no inventário),
    `ligabis` (o vínculo cobra o dono pela lista de jogadores), a revelação, o vínculo de visão, a projeção e a
    incorporação (o que se mostra aos olhos de um jogador), o condutor e o engaste da varinha, e o `ille` (só o
    jogador guarda o que lhe acontece). A visibilidade (`surgit … quantum`) vale para criaturas: um evocador pode
    sumir.
  - GameTests `aCreatureCastsAndPaysFromItsAnchor` e `aCreatureDrawsOutOfABody`: um evocador sem IA lança terra e
    morde uma vaca, pagando da âncora.
- **A entidade.** Estende o evocador de vanilla (`SpellcasterIllager`), para herdar raid, alinhamento e animações. Os
  objetivos (`Goal`) novos são preparar, lançar e recuar para preparar.
- **A mente.** Um pacote próprio (`mob/evoker/mind`?): o gerador, o mundo imaginado, o catálogo, a avaliação, a
  personalidade e a memória. A mente nunca toca o mundo: devolve uma frase e o lançamento normal faz o resto.
- **Custo de processamento.** O catálogo é calculado uma vez por léxico, num pedaço por tick, e guardado. Em combate
  há só busca no catálogo e ajuste de números: barato.
- **Testes.** A mente pode ser testada sem jogo, com o `FlowWorld` que só faz contas (como já fazem os gametests de
  feitiço): para cada intenção, a frase escolhida tem a assinatura certa.

## 9. Etapas

1. ~~Abstração do conjurador (sem mudar nada para o jogador), e Vita para todo ser vivo, com o corpo como fonte (seção
   7.4).~~ Feito (12/10/2026).
2. ~~O feiticeiro com um grimório **fixo**, escrito à mão: prova que uma entidade conjura de verdade, com transe
   visível, Vis e tributo.~~ Feito (12/10/2026), ver seção 9.1.
3. ~~O catálogo de frases (gerar, validar, simular, guardar).~~ Feito (12/10/2026), ver seção 9.3.
4. ~~Intenções, avaliação e personalidade: ele passa a escolher sozinho.~~ Feito (12/10/2026), ver seção 9.4.
5. ~~Preparação fora de combate: ele passa a escrever.~~ Feito (12/10/2026), ver seção 9.5.
6. ~~Memória do oponente e cópia de frases.~~ Feito (12/10/2026), ver seção 9.6.
7. ~~Loot, sala de estudo, raids.~~ Feito (12/10/2026), ver seção 9.7.

### 9.1 Etapa 2, como ficou (12/10/2026)

- **A entidade** (`illager/Sorcerer`, `elderlexicon:sorcerer`, com ovo na aba do mod): estende o evocador de
  vanilla (raid, aliados, aparência e animações), sem as presas, os vexes e o wololo.
- **O grimório** (`data/elderlexicon/grimoires/sorcerer.json`, lido por `SorcererGrimoire`): páginas escritas à mão,
  cada uma com a distância ao alvo em que serve, um peso e se só serve ferido. Hoje:
  - lança de fogo, `igni vocant iactare` (3 a 16 blocos);
  - mordida, `aqua quantum -10 vocant` (até 14 blocos, só ferido: a água tirada cura o que lhe falta);
  - sangria, `aqua tenet iactare` (até 14 blocos).
  Um teste unitário garante que toda página é uma frase que a gramática lê sem problemas.
- **O transe:** com o alvo à vista e o descanso acabado, ele escolhe uma página que serve (por peso), para, ergue os
  braços, e as runas da página aparecem acima da cabeça, uma a uma, pelos dois segundos do transe (a mesma recitação
  do jogador, agora para qualquer ser vivo). No fim, mira o meio do alvo e lê a página. Descansa de 2,5 a 4 segundos.
- **A Vis e o tributo:** só lê uma página que a âncora paga (`SpellCastingService.estimateCost`, sem contar o preço de
  tirar de um corpo). Com menos de 30% da vida, desesperado, lê mesmo assim e paga com a carne.
- **Quebrar o transe:** um golpe durante o transe o quebra; a página não é lida e **nada é gasto** (provisório: ver
  perguntas abertas). Ele descansa 1,5 segundo.
- **Correção de passagem:** a mira de quem conjura (`pick`) lia a rotação do tick anterior; uma criatura, que se vira
  logo antes de lançar, errava o alvo. Agora lê a de agora.
- GameTests `theSorcererReadsAPageAgainstItsPrey` e `aBlowBreaksTheSorcerersTrance`.

### 9.2 Páginas de grimório e o grimório como loot (12/10/2026)

Pedido do autor: mais agressivo e mais inteligente, e com o grimório dele como loot, para o jogador aprender com ele.

- **Páginas de verdade.** Uma página do evocador é escrita como uma do jogador: uma linha por feitiço, cada coluna um
  passo de tempo (0,25 s), `.` é uma pausa (`GrimoirePage`, `SpellBlock`). Ele lê a página com o transe, e todas as
  linhas saem pelo mesmo caminho do `surgit` em página (`SpellCastingService.castBlock`), cada uma no seu passo.
  Enquanto as linhas atrasadas saem, ele continua mirando o alvo, mesmo andando. Linhas com condição ele não lê
  (o que acontece a uma criatura não é guardado).
- **O grimório de hoje:** lança de fogo; **salva de fogo** (três lanças, uma a cada meio segundo); mordida (ferido);
  sangria; **beber e devolver** (a mordida, e logo a água tirada volta como sangria); **véu** (ferido: some por 6
  segundos com `surgit ego quantum 0` e lança fogo de dentro do véu).
- **Mais agressivo:** o descanso depois de uma página caiu para 1 a 2 segundos, contados do fim da última linha.
- **O loot:** morto por um jogador, deixa o grimório com 25% de chance (mais 5% por nível de pilhagem). É um grimório
  comum: as páginas, os nomes delas, ele como autor e as runas no glossário. Lido com `surgit`, as runas que o leitor
  não conhece são puladas, como em qualquer leitura; o grimório mostra todas, e o que cada uma faz.
- GameTest `theSorcerersGrimoireIsAGrimoireLikeAnyOther`; teste unitário de que cada linha de cada página é lida
  sem problemas e de que a salva sai nos passos 0, 2 e 4.

### 9.3 Etapa 3, o catálogo (12/10/2026)

Pacote `mind`:

- **`PhraseCatalog`** escreve frases pelas classes de palavra do léxico, nunca pelo nome das runas: um sujeito (uma
  fonte, `ego` ou nada), a origem (`tenet`), um filtro com número (quantidades 10, 20 e -10; tempos 0 e 6), um verbo ou
  dois, e o que cada verbo leva depois (uma fonte, `ego`, `ego` com a medida 0 ou 10). Ficam de fora o `ille`, as
  condições, o `reframe` e o `ligabis`: pedem uma cena, um nome ou marcas que a mente não tem.
- A gramática lê cada frase; as que ela lê com algum problema caem fora.
- **`ImaginedFlow`** roda cada uma no `FlowInterpreter`, num mundo que só anota o que cada verbo faria. Cada coisa
  feita é um **`Act`**: a operação do verbo (`project`, `manifest`, `repel`, `convert`, `perceive`: o que o verbo faz,
  não qual runa o diz), a energia já convertida, para onde (a mira, o próprio conjurador, o que o verbo anterior fez,
  um lugar escrito), se é invertido, se vem do mundo, se é de uma vez, e os números. Os atos, em ordem, são a
  **assinatura** da frase.
- Fica **uma frase por assinatura**, a mais barata (a mais curta, no empate). Quem lê do catálogo fica só com as que
  pode ler inteiras (`readableBy`, pelas runas que conhece).
- Com o léxico do mod: **10.116 frases**, feitas em 3 a 4,5 segundos, numa thread à parte quando o servidor sobe.
  Ele acha sozinho, entre outras, `igni iactare` (lançar fogo na mira), `aqua quantum -10 vocant` (tirar a água do que
  se mira) e `surgit ego quantum 0` (sumir).
- **`/runas catalogo [busca]`**: sem nada, quantas frases e quantas começam por cada operação; com palavras, as frases
  cuja assinatura tem todas elas, das mais baratas (`/runas catalogo project:igni>aim`, `/runas catalogo rev aqua`,
  `/runas catalogo self v0`).
- O que ainda não está na conta: o preço de tirar de um corpo (a vida tirada) e o que a fonte do mundo paga. Muitas
  frases de dois verbos são composições estranhas (`igni iactare surgit`): o catálogo só diz o que elas fazem; quem
  escolhe é a mente (etapa 4).
- Testes unitários `PhraseCatalogTest`.

### 9.4 Etapa 4, a mente escolhe (12/10/2026)

Pacote `mind`, e o `Sorcerer` sobre ele:

- **O que ele sabe (`Degree`).** Nasce aprendiz (50%), mestre (35%) ou ancião (15%), com um sorteio de runas:
  sempre uma fonte e os verbos que lançam e trazem (`project`, `manifest`), para poder lutar; o mestre, mais cinco;
  o ancião, quase toda a língua. Lê só o que pode ler inteiro: as páginas do grimório escritas à mão e as frases do
  catálogo (seção 9.3). Guardado na entidade.
- **Como pesa (`Personality`).** Agressivo (quer ferir, pouco liga para o custo, paga com a carne abaixo de 45% da
  vida), cauteloso (liga para o custo, some quando apertado, nunca paga com a carne), trapaceiro (gosta de tirar de
  corpos e de sumir; carne abaixo de 30%).
- **O que quer (`Intent`).** Ferir (lançar na mira, ou fogo feito onde o alvo está), tirar de um corpo (o `vocant`
  invertido na mira; vale mais ferido, porque cura o que lhe falta) e sumir (o próprio conjurador sem ser visto; só
  ferido e com o alvo a menos de 8 blocos, o dobro abaixo de 40% da vida). O que um ato serve vem da operação do
  verbo, nunca da runa.
- **A nota (`Mind`).** Para cada opção: a soma, por intenção, do peso da personalidade × o quanto a cena pede × o que
  ela faria, menos o peso do custo × (o que os verbos gastam + a vida que tiraria de um corpo, 5 UMU por ponto), menos
  o risco (0,6 por ato que não serve a nada; uma frase que mexe no próprio corpo, fora sumir, nem entra), mais um
  pouco de sorte. Ele lê entre as três melhores, sorteando pela nota; sem nada que valha (ou que pague, fora do
  desespero), descansa e olha de novo.
- **O que ainda é chute:** o dano de um lançamento (`Mind.potency`: fogo e terra 4 pontos por 10 UMU, vis 3, água 2,
  ar 1; metade a mais condensado). O que tirar de um corpo leva é exato: vem da Vita do alvo.
- **O loot** traz, depois das páginas que ele lia, as frases que ele compôs e leu nesta vida (até oito), com o nome do
  que queria com elas ("ferir 1", "tirar 2", "sumir 1"). Um evocador que não lia página nenhuma e não compôs nada não
  deixa grimório.
- **`/runas mente`:** o grau, a personalidade, as runas, quantas opções ele pesa, a Vis e o que leu por último (e a
  nota) do feiticeiro mais próximo.
- Testes unitários `MindTest`; GameTest `aWoundedMindWantsToDrawMore`.

### 9.5 Etapa 5, a preparação (12/10/2026)

- **`mind/Preparation`**, um pouco por vez:
  1. lê o catálogo (400 frases por passo) e guarda as três melhores para cada intenção, só entre as que servem a
     **uma** intenção, pesadas contra um corpo de pessoa (20 de vida, 100 UMU na proporção de uma pessoa), sem cena
     (`Mind.person`);
  2. compõe rascunhos que o catálogo não tem: uma frase sozinha (golpe, mordida, véu), a **salva** (o mesmo golpe
     três vezes, meio segundo entre eles), **beber e ferir** (tira, e fere três passos depois) e **véu e golpe**;
  3. pesa um rascunho por passo, em duas cenas (inteiro a 8 blocos; ferido, a 35% da vida, a 5 blocos), e fica com o
     melhor de cada tipo. As páginas recebem nome pelo tipo e pela energia ("salva de fogo", "mordida de ar").
  Um ancião escreve as suas seis páginas em ~60 passos (~100 ms de trabalho no total).
- **O evocador escreve** quando não tem alvo, nem está lendo, nem comemorando uma raid, e o catálogo está pronto: fica
  parado, com as luzes da língua vindo até ele e o som de páginas, e dá um passo a cada 5 ticks (uns 15 segundos ao
  todo). Um alvo interrompe; ele continua de onde parou quando fica sozinho de novo.
- **Em combate**, com páginas escritas, ele pesa só o grimório (as páginas à mão que lê inteiras e as que escreveu):
  poucas opções. Sem nenhuma escrita (achou briga antes), pesa o catálogo inteiro, como na etapa 4.
- As páginas escritas são guardadas na entidade (nome e texto, como num grimório) e vão para o grimório que ele
  deixa, antes das frases soltas que compôs em combate.
- **Correções na nota** que as páginas revelaram: lançar de novo o que acabou de ser lançado (`igni iactare
  iactare`) não é um segundo golpe; sumir duas vezes é sumir uma; a visibilidade tem de ser de 0 a 1.
- **Teto de gasto (provisório):** fora do desespero, ninguém gasta mais de 60% da Vis que tem numa leitura só
  (`Mind.SPEND_SHARE`). Ver a pergunta sobre os elementos de parte pequena (seção 10).
- `/runas mente` mostra as páginas escritas, ou se ele está escrevendo.
- Testes unitários `PreparationTest`; GameTest `aSorcererAloneWritesItsPages`.

### 9.6 Etapa 6, memória e cópia (12/10/2026)

- **O que veio de cada leitura (`mind/Memory`).** A mente guarda, ao escolher, o dano que previa fazer
  (`Mind.Choice.harm`). Um segundo e meio depois da última linha, o evocador olha o quanto o alvo perdeu de vida e
  guarda, para aquele alvo, a razão entre o que veio e o que previa (uma média que anda; entre 0,4 e 1,8). Esse fator
  multiplica o quanto a opção vale contra aquele alvo dali em diante: o que nunca acerta vale menos, o que fere mais do
  que o previsto vale mais. A memória é desta vida, de até quatro oponentes; não é salva.
- **Varia o que funcionou (`mind/Variations`).** O que fez mais que o previsto (fator acima de 1,15) é variado uma
  vez: frases do catálogo que ele lê inteiras com a mesma forma (os mesmos verbos, do mesmo jeito, para o mesmo lugar)
  e outra energia ou outro número. Numa página, varia-se a primeira linha que serve a uma intenção, em todo lugar
  onde ela está escrita (a salva continua salva). Até duas variações; entram nas opções dele.
- **Quem quebra o transe.** Quem quebrou o transe dele duas vezes não chega mais perto: ele só abre o transe com essa
  pessoa a 7 blocos ou mais (fora do desespero), e enquanto isso o recuo do evocador de vanilla o afasta.
- **Cópia (`SpellWitnessedEvent`).** Todo feitiço lançado com sucesso é anunciado no barramento do Forge. Um feiticeiro a até 16 blocos, que vê o jogador que o lançou, anota a frase: as runas que ele não conhece saem, como o
  espírito dele as pularia (os números ficam), e a cópia só fica se o que sobrou ainda faz algo que vale ler. Até
  quatro cópias, salvas na entidade; vão para o grimório do loot como "copiada 1", "copiada 2"... Ver o jogador lançar
  `aqua tenet iactare`, sem conhecer `tenet`, faz um aprendiz anotar `aqua iactare`.
- `/runas mente` mostra quantas opções ele aprendeu e quantas copiou.
- Testes unitários `MemoryTest`; GameTest `aSorcererCopiesWhatItSeesCast`.

### 9.7 Etapa 7, no mundo (12/10/2026)

- **`illager/Sorcerers`.** Todo evocador de vanilla é olhado uma vez, quando entra no mundo (o de uma mansão quando
  o chunk carrega, o de uma raid quando chega a onda dele), e marcado para não ser olhado de novo. Com uma chance, um
  feiticeiro toma o lugar dele no tick seguinte (não durante o carregamento): onde ele estava, virado para onde
  olhava, com o nome, a persistência, e na mesma raid e onda (`Raid.joinRaid`).
- **As chances** estão na configuração comum (`elderlexicon-common.toml`):
  - `sorcerer.mansionChance` (0,5): o evocador persistente e fora de raid, que é o de uma mansão;
  - `sorcerer.raidChance` (0,1) mais `sorcerer.raidChancePerWave` (0,1) por onda depois da primeira: nas
    últimas ondas, até metade;
  - `sorcerer.otherChance` (0): qualquer outro (de um comando, de outro mod).
  Mansões já exploradas de um mundo antigo também entram: os evocadores delas são olhados na primeira vez que
  carregam depois desta versão.
- **A sala de estudo.** O feiticeiro de uma mansão ganha, ao lado de onde estava, um atril virado para ele e até
  duas estantes, só onde há ar sobre um chão firme. Sem o grimório no atril: o grimório só vem de derrotá-lo.
- GameTest `aMansionsEvokerMayBeARunicOne`. A troca numa raid não tem teste automático: testar com uma raid de verdade
  (Mau Presságio numa vila), com `sorcerer.raidChance` em 1.

### 9.8 O nome, a evolução e o bando (12/10/2026)

- **O nome** é Feiticeiro (*Sorcerer*): ele faz feitiços. Tudo foi renomeado: a entidade `elderlexicon:sorcerer`
  (ovo `sorcerer_spawn_egg`), as classes `illager/Sorcerer`, `Sorcerers`, `SorcererGrimoire`, o grimório
  `data/elderlexicon/grimoires/sorcerer.json`, a configuração `sorcerer.*` e este documento. Os evocadores rúnicos que
  já estavam salvos num mundo de teste somem (o id mudou).
- **A evolução.** Um aprendiz chega a ancião aprendendo, como um mago (docs/aprendizado-runas-design.md): vive a runa,
  entende, sonha. Ele ganha um ponto de entendimento de uma runa que não sabe quando a **vive** (queimar ou congelar
  ensina `igni`; cair ou ser esmagado, `firmo`; afogar, sufocar ou levar um raio, `aura`; sangrar, `aqua`) e quando
  **vê** alguém conjurar uma frase com ela. Com três pontos ele a entende, e na próxima calma (sem alvo) **sonha** e a
  sabe (luz de almas, um som de ametista). O grau segue o que ele sabe (até 4 runas aprendiz, até 10 mestre, depois
  ancião), e ele escreve o grimório de novo com o que sabe agora. Só se aprendem as runas com que a mente escreve
  (`Degree.learnable`).
- **O bando.** O feiticeiro é o líder: marca os Illagers do bando (na raid dele, ou perto quando não há raid; nunca
  outro feiticeiro, nem quem outro já marcou) como lhe convém, pela personalidade: o agressivo dá a todos uma marca só,
  para trazer todos de uma vez; o cauteloso, uma marca por função (lâminas, bestas, conjuradores); o trapaceiro, uma
  para cada, para trazê-los um a um. As marcas são palavras dele, de duas ou três sílabas ("kador", "vilnah"), nunca
  uma runa. Ele olha o bando a cada 5 segundos.
- **Chamar.** Uma intenção nova da mente (`Intent.CALL`): `<marca> vocant`, mirando o alvo, traz quem tem a marca até
  ele. Vale, para cada aliado daquela marca longe do alvo (mais de 6 blocos), 3 pontos de vida que ele tirará do alvo,
  menos para quem atira de longe ou conjura; com ninguém para trazer, não vale nada. O ato sabe a marca que carrega
  (`Act.mark`).
- **O dano previsto** continua um chute: o feiticeiro aprende com a experiência (seção 9.6), como um mago aprenderia.
- `/runas mente` mostra também o que ele está entendendo e as marcas do bando.
- Testes unitários em `MindTest`; GameTests `aSorcererLearnsARuneItLivedByDreamingIt` e `aSorcererMarksItsBandAsItLikes`.

## 10. Perguntas abertas

- **Vexes:** o homúnculo é necessário, ou o evocador pode juntar direto a proporção do vex (quase todo ar) com a
  âncora? A lei da animação já faria nascer um vex; o homúnculo é a etapa a mais.
- **A fome:** do que, como come e como se gasta (seção 7.3).

## 10a. A próxima fase (pedido do autor, 12/10/2026)

- **A torre do feiticeiro:** a torre clássica que todo mago tem. Estrutura própria do feiticeiro.
- **O Mago (*Wizard*):** a versão neutra do feiticeiro. Funciona igual (a mesma mente, grimório, aprendizado e
  catálogo), mas não é maligno: não caça ninguém, e se defende quando atacado. Tem estruturas próprias e vive dentro e
  fora das vilas.
- **As universidades:** estruturas onde vivem vários magos, junto de outros aldeões.
- Os vexes como homúnculos famintos (seção 7.3) ficam para mais adiante.
- O Mago e as universidades têm documento próprio: docs/mago-design.md.

## 11. Decisões tomadas

1. O livro não é lido ao pé da letra: no mod a Língua Antiga sempre existiu, os Illagers são os do Minecraft, e alguns
   deles usam magia, como alguns jogadores. (12/10/2026)
2. As presas e os vexes de vanilla saem do feiticeiro e dão lugar a feitiços da língua feitos com o que existe
   hoje. Quais, em discussão (seção 7.1). (12/10/2026)
3. Chamar aliados vale nos dois sentidos: a ordem dada com a voz, e a invocação de Illagers que nascem com marca.
   (12/10/2026)
4. Os vexes são homúnculos sem kern convertidos ao código do vex, que atacam por fome de combustível (seção 7.3).
   A guarda de sangue e as estátuas de golem foram descartadas. (12/10/2026)
5. No lugar das presas, a mordida: tirar a água do corpo do alvo, contínua com `chronos`, que transborda em quem
   conjura com a água cheia. Pede Vita nos mobs e o corpo como fonte (seção 7.4). (12/10/2026)
6. Todo ser vivo tem Vita, com o cerne do seu tipo pela tabela (`materials.json`, `beings`); e a mira basta para
   que o `vocant` negativo e o `tenet` tirem de um corpo vivo. (12/10/2026)
7. Quem tira de um corpo vivo paga a vida que tira, 5 UMU por ponto, como o vínculo de `firmo`. (12/10/2026)
8. Os elementos de parte pequena continuam letais: tirar o fogo (2%) ou os ossos (5%) de uma pessoa a mata, pelo preço da
   vida tirada (100 UMU, a âncora cheia). Sem "o corpo segura": um mago neste mundo deve ser visto como um ser
   perigoso. O teto de gasto da mente (60% da Vis fora do desespero) fica. (12/10/2026)
9. O nome é Feiticeiro (*Sorcerer*). (12/10/2026)
10. Um aprendiz evolui até ancião aprendendo runas como um mago: vivendo-as e vendo-as, e sonhando-as. (12/10/2026)
11. O feiticeiro lidera o bando e o marca como lhe convém (uma marca para todos, por função, ou uma para cada),
    segundo as intenções dele; `<marca> vocant` traz os marcados até o alvo. (12/10/2026)
12. O dano previsto de um lançamento não precisa ser exato: ele aprende com a experiência. (12/10/2026)
13. Quebrar o transe não custa nada: a página não é lida e nenhuma Vis se perde. Os vexes ficam para mais adiante.
    (12/10/2026)
