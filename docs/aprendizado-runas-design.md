# Aprender uma runa: entender e sonhar

> "Nahl" é a palavra antiga para "ideia" […] "vá" é a evolução da palavra "veil", que significa "espaço", "dimensão",
> "plano". (Grimório de EVL, 2.1)
>
> A linguagem antiga é um idioma que nasceu em Nahvá, no mundo adormecido, onde vivem as ideias. (4)

**Status:** o ciclo completo do `igni` está implementado (06/10/2026), numa primeira versão do desafio. Não testado
dentro do jogo (a dimensão carrega sem erro no servidor de testes).

## 1. O conceito

Saber que `igni` quer dizer "fogo" não ensina a runa. Escrevê-la mil vezes não ensina, nem a pronúncia perfeita. Para
aprender `igni`, o mago precisa **entender o calor**: não a palavra, nem o conceito, mas o que é de fato estar quente e
estar frio, queimar e congelar.

1. **Entender:** o mago vive, no corpo, as experiências que a runa significa.
2. **Sonhar:** entendida a runa, ele passa por um **sonho vívido**: o seu espírito vai, em Nahvá, a uma **dimensão
   que é a ideia daquela runa**.
3. **Escapar ou morrer:** só se acorda desse sonho escapando da dimensão ou morrendo nela.
   - **Escapou:** passou no desafio, e a runa é aprendida (`RuneUnlocks.unlock`).
   - **Morreu:** simplesmente acorda, sem ter aprendido a runa.
4. **O mesmo vale para todas as runas**, cada uma com o seu entendimento e a sua dimensão.

## 2. O que já existe e se encaixa

- **Os estados do Vita** (a Bússola Vita os mostra): o entendimento dos elementos pode ser vivê-los.
  | Runa | Estados do corpo |
  |---|---|
  | `igni` | Temperatura: Frio, Hipotermia, Quente, Fervendo |
  | `aqua` | Hidratação: Baixa, Crítica, Alta, Transbordando |
  | `aura` | Circulação: Baixa, Crítica, Alta, Sobrecarregada |
  | `firmo` | Minerais: Baixos, Deficientes, Elevados, Rígidos |
- **Os acontecimentos do fluxo** (`Happenings`): golpear, ser ferido, matar, pular, cair, quebrar, usar, agachar,
  correr, pouca vida, em chamas, debaixo d'água. São o entendimento natural dos **Fatos** (`ferit`, `cadit`…).
- **O registro de runas desbloqueadas** e o aviso de runa aprendida (docs/repertorio-design.md).

## 3. Decisões (06/10/2026)

1. **O sonho vem com uma chance**, a cada noite dormindo, para uma runa entendida e ainda não aprendida (35%).
2. **No sonho:** voo criativo e UMU infinita (nada é cobrado, nada passa pelo corpo), mas **nenhum item** no inventário.
   Itens podem ser conseguidos lá, de maneira fácil.
3. **Acordar é sempre ruim**, tendo escapado ou morrido: náusea (10 s), fraqueza (1 min), lentidão (30 s) e cansaço
   (1 min). O mago acorda na cama com tudo o que tinha: inventário, vida e fome de antes do sonho.
4. **Uma só dimensão, Nahvá, com várias regiões.** Cada região é como um planeta regido pelo kern: uma região não pode
   ver outra. Cada jogador sonha **sozinho**, numa instância só dele (há uma exceção, a definir).
5. **O entendimento das Funções, dos Filtros e dos Fatos** vem de ações que os representam: soltar uma flecha é
   aplicar força em algo (`iactare`); largar um item é trazer algo de dentro do corpo (`vocant`). A pensar runa a runa.
6. **O mago começa sem saber nenhuma runa:** todas, até as fontes, se aprendem assim.
7. **No repertório**, uma runa entendida que espera o sonho mostra o nome no **texto secreto** do Minecraft (o
   embaralhado), embaixo do bloquinho escuro.

## 4. O ciclo do `igni` (implementado)

- **Entender:** viver os quatro estados de temperatura do Vita (Frio, Hipotermia, Quente, Fervendo), **pegar fogo** e
  **congelar** (na neve fofa). Cada experiência nova dá um aviso discreto ("Você sente o frio de verdade."); com todas,
  "Você entende o calor por completo [...] Talvez sonhe com isso." As experiências de cada runa são dados
  (`lexicon/understanding.json`); o que cada experiência é fica em `nahva/Understanding`.
- **Sonhar:** dormindo na cama, a cada noite, 35% de chance. O espírito vai à região do `igni`.
- **A região do `igni`** (`nahva/IgniRealm`), primeira versão: um mar de lava sob uma cadeia de ilhas de netherrack em
  chamas, com névoa de brasa. **O ar queima:** a cada segundo, fora d'água e sem nada frio na mão, o sonhador pega fogo
  e se fere. **O frio é o caminho:** um baú ao lado de onde o sonhador aparece tem gelo azul, baldes de água, neve fofa e
  bolas de neve; **segurar algo frio apaga o fogo** e impede o ar de queimar. **A fenda** é um anel de obsidiana chorona no fim da cadeia, a
  270 blocos; atravessá-la é escapar.
- **O voo** é religado depois da viagem a Nahvá (a troca de dimensão o desligava no sobrevivência) e mantido a cada
  tick enquanto o sonho dura.
- **Acordar:** escapando, aprende `igni` ("Você aprendeu a runa igni."); morrendo (no fogo, na lava, no vazio),
  acorda sem aprender. Sair do jogo no meio do sonho acorda o mago antes de salvar.

**Por dentro:** `nahva/Nahva` (a dimensão e as instâncias), `RealmLayout` (onde fica cada região), `Realms` (o que cada
região faz), `Dreams` (dormir, sonhar, acordar), `client/NahvaFog` (a névoa); `data/elderlexicon/dimension/nahva.json`
(um vazio plano, céu do End, sempre noite). O entendimento fica no repertório (`experienced`, `understood`).

**Testar:** `/runas entender igni` (como se tivesse vivido tudo) e `/runas sonhar igni` (o sonho na hora). Num mundo
antigo, `/runas bloquear igni` antes, se o mago já o tinha nos slots.

## 4b. O sonho febril, no kern (decidido com o autor em 18/10/2026)

Substitui a região do `igni` em Nahvá. O livro já dizia (cap. 1.3): **quem dorme e sonha está no próprio kern**, e
quando o corpo entende uma coisa por inteiro, **o kern se transforma** em algo que a representa. O sonho de aprender
uma runa passa a acontecer lá, e é um sonho de verdade: **REM, febril**, louco.

- **O palco é o kern do jogador**, a ilha para onde ele vai quando morre. O sonho **veste** o kern por cima: cada bloco
  que o sonho muda tem o original guardado (com o mundo, não com o jogador, para sobreviver a uma queda do servidor), e
  ao acordar tudo volta como estava. O que o jogador construiu no kern não se perde.
- **Memórias reais.** Quando o jogador vive uma experiência de uma runa pela primeira vez (pega fogo, congela, sente
  frio extremo), o mod guarda um pedaço do mundo em volta dele naquele instante (uns 11×8×11 blocos). No sonho, essas
  memórias voltam, **distorcidas**: soltas no ar em volta da ilha, de cabeça para baixo, viradas, com o quente e o frio
  trocados (a neve onde ele congelou vira fogo, a lava onde queimou vira gelo).
- **Um espaço liminar** (o autor, 19/10/2026): em cada fase o kern vira **um castelo de salas que não fazem sentido**,
  construído por cima da ilha, mas do mundo do jogo, medieval: pedra, madeira, luz fraca. Uma grade de 5×5 salas
  (9×9 por fora, 6 de pé-direito) ligadas por portas estreitas, como um labirinto (todas se alcançam). O que há nelas:
  salas que se repetem (um mesmo tipo volta aqui e ali), **uma cadeira só virada para a parede**, **uma mesa posta para
  ninguém**, estantes até o teto, **uma escada que termina no teto**, **uma cama sozinha** no meio, **um poço sem
  fundo**, **um batente de porta numa parede**, salas de pilares, salas vazias. E **portas que levam a outro lugar**:
  duas portas nas paredes de fora, que deviam dar para fora, levam a outra porta, do outro lado do castelo.
- **Em fases, sem aviso.** O castelo é construído de novo a cada fase (uns 25 a 45 s), com a fenda em outra sala longe
  do meio; o sonhador fica no meio da sala em que estava. Para o `igni`:
  1. **O calor:** salas de tijolo do Nether, chão de brasa e pedra negra, lanternas penduradas; **o ar queima**, com
     chamas no ar, e só segurar algo frio protege; coisas frias jogadas pelas salas.
  2. **As memórias:** salas de pedra velha e madeira, à luz de velas; **as memórias do corpo dentro de algumas salas**,
     viradas, com o quente e o frio trocados. A fenda está numa delas.
  3. **O frio:** salas de gelo e neve; o ar congela, e só algo quente na mão protege (tocha, vara de blaze, magma);
     coisas quentes jogadas pelas salas.
  As fases giram até ele escapar ou morrer.
- **As outras fontes** (19/10/2026), no mesmo molde: seis experiências (os quatro extremos do elemento no Vita e duas
  vividas no mundo, guardadas como memória) e o castelo em três fases (um extremo, as memórias, o outro extremo). Em
  cada extremo o ar faz algo, e segurar a coisa certa protege; ela aparece jogada pelas salas.
  | Runa | Entender (além dos 4 extremos do Vita) | 1º extremo | Memórias | 2º extremo |
  |---|---|---|---|---|
  | `aqua` (fluxo, dissolução) | afogar-se; ser levado pela correnteza | **A cheia:** prismarinho, salas afundadas em **poços fundos** (6 blocos; a fenda fica no fundo de um); o fôlego acaba; **esponja** o mantém | a água vira areia, a areia vira vidro azul (água parada) | **A seca:** arenito, terracota, arbustos secos; o ar resseca e fere; **garrafa d'água** |
  | `aura` (pressão, fôlego, som) | cair 20+ blocos; o sopro de uma explosão | **A ventania:** pedra clara, ventos que empurram de sala em sala, a pressão fere os ouvidos, portas que batem e ecoam; **pena** | o cheio vira vidro | **O vácuo:** ardósia, teias, **nenhum som**; o fôlego acaba como debaixo d'água; **garrafa vazia** (o ar guardado nela) |
  | `firmo` (massa, solidez) | sufocar dentro de um bloco; descer abaixo do nível 0 | **O peso:** salas baixas (3 de altura); o corpo não voa e anda devagar; **pena ou papel** | a pedra vira minério | **O soterramento:** terra e pedra; as paredes de todas as salas avançam uma fileira a cada 6 s (até 3); a **picareta** abre caminho |
  No sonho, o que o sonhador quebra some sem soltar nada, e o que ele põe é tirado ao acordar (o kern volta como
  estava). A água da cheia é de verdade, mas a superfície dos poços fica no nível do chão e as portas acima dela: não
  escapa. (A primeira versão enchia as salas até o teto; a água escorria pelas portas e derrubava camas e o que mais
  encontrasse.) O sonho põe os seus blocos **sem avisar os vizinhos**: senão o jogo quebra o que ficou sem apoio por
  um instante (meia cama na troca de fase, um arbusto seco sobre pedra) e o item cai no chão do sonho.
- **`vis`** (19/10/2026): só se entende depois de aprender as quatro fontes. Entender: **ficar sem mana nenhuma**, ter
  a **mana transbordando** (nível 30 de experiência) e ter **as quatro fontes em equilíbrio** no corpo. O sonho: cada
  extremo é **um dos oito extremos das quatro fontes, sorteado de novo a cada vez**; nas memórias, **tudo vira cristal**
  (ametista e calcita).
- **A febre na tela:** cada fase tem um efeito de tela do próprio jogo (ondulação, cores que se separam, rastro), a
  visão que pulsa e a névoa que muda de cor (brasa no calor, azul no frio).
- **Escapar ou morrer**, como antes: atravessar a fenda ensina a runa; morrer só acorda. Acordar continua ruim.
- **Lucidez só depois de dominar a runa** (o autor): num sonho de aprendizado o jogador não manda em nada. Depois,
  dormindo no próprio kern com a runa já dominada, terá momentos de "a vontade é lei" (a desenhar).
- **Sem memória ainda** (uma experiência vivida antes desta mudança): a fase das memórias usa pedaços do próprio kern.
- **Nada de fogo, lava ou água de verdade no kern:** escorreriam ou se espalhariam para o que o jogador construiu, e não
  voltariam ao acordar. Por isso as chamas são só partículas, e nas memórias a água vira gelo compactado e a areia e o
  cascalho viram pedra (não caem).
- **Por dentro:** `nahva/BodyMemories` (as memórias, guardadas com o mundo), `nahva/KernDressing` (o que o sonho pôs
  no kern e o que havia antes; tirado ao acordar, e também no login se o servidor caiu no meio do sonho),
  `nahva/FeverDream` (as fases), `nahva/FeverHalls` (o castelo) e `nahva/client/FeverClient` (a febre na tela). A região antiga (`IgniRealm`) saiu; o
  lugar dela no mapa de Nahvá fica vazio.
- **Testar:** `/runas entender igni` e `/runas sonhar igni`, como antes. As memórias: cada experiência guarda o lugar
  na primeira vez em que é vivida; uma vivida antes desta mudança guarda o lugar na próxima vez em que for vivida de novo
  (pegar fogo, congelar na neve fofa...), mesmo com a runa já entendida.

## 5. Perguntas abertas

1. **O desafio do `igni`:** a primeira versão é um rascunho. Como deve ser?
2. **O entendimento de cada outra runa** (ações que as representam).
3. **A exceção** ao sonho solitário.
