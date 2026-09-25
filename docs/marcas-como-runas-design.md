# Marcas como runas — desenho (v1)

**Status:** desenho fechado e **primeira versão implementada** (22/09/2026): parser, `ubis`, `vocant`, `iactare`, `exsugat`, `impediunt`, `transvocatio` e `vertere` com marcas. Ainda não foi testado dentro do jogo; ver seção 11. O que falta decidir está na seção 9.
**Relaciona-se com:** `ligabis-design.md` (vínculos por marca), `marks.md` (como gravar marcas), `alpha-backlog.md` (filtros `ubis`, `chronos`, `quantum`).

## 1. Fontes

- O livro (*Grimório de EVL*): cap. 4.3.1 (Ligabis), cap. 4.3.2 (filtros: `ubis`, `chronos`, `quantum`), cap. 8 (funções essenciais) e 9.4 (achar um objeto perdido).
- A ideia do autor nesta rodada: **a marca é um código sobre uma entidade ou bloco, e esse código é uma runa**. Ela pode ser usada em qualquer feitiço, e não só no Ligabis.

## 2. A ideia central

Hoje a marca só existe para o Ligabis e para ativar pergaminhos. Em qualquer outro feitiço, uma palavra como `m1` vira "lexema desconhecido" e é descartada.

A proposta é que **a marca ocupe o lugar de uma fonte**. Uma fonte diz *qual energia* o feitiço usa (`igni`, `aqua`...). A marca diz *qual objeto* o feitiço usa. A função que vem depois age sobre o objeto marcado, como agiria sobre a energia:

| Feitiço | Com fonte | Com marca |
|---|---|---|
| `X vocant` | faz fogo surgir no ponto mirado | faz **o objeto m1** surgir no ponto mirado |
| `X iactare` | lança fogo do mago na direção mirada | lança **o objeto m1** na direção do ponto mirado |
| `X ubis ...` | (o `ubis` captura a posição de uma fonte absorvida) | o `ubis` captura **a posição de m1** |

Uma consequência direta: **marca + `vocant` = teletransporte**, sem precisar de runa nova.

## 3. Os dois papéis da marca

A mesma palavra tem papéis diferentes conforme a posição:

1. **Objeto (sujeito):** é a marca que alimenta a função. Em `m1 vocant`, o que é invocado é o que tem a marca m1.
2. **Lugar:** é a marca consumida pelo `ubis`. Em `m1 ubis igni vocant`, m1 só informa *onde*; o que é invocado é o fogo.

O Ligabis continua com a gramática própria (`firmo m1 ligabis m2`). Lá a marca é o nome do grupo.

## 4. Gramática do `ubis` (do livro)

O `ubis` consome **o operando imediatamente antes dele** e o transforma num lugar. O resto do feitiço é lido normalmente, e a função age nesse lugar.

| Forma | Operando do `ubis` | Resultado |
|---|---|---|
| `igni 10 ubis vocant` | um número | fogo a 10 m do mago, na direção mirada |
| `igni 10 43 25 ubis vocant` | três números | fogo nas coordenadas x=10, y=43, z=25 |
| `igni exsugat ubis aqua vocant` | uma fonte absorvida | água no ponto de onde o fogo foi absorvido |
| `marca ubis aqua vocant` | uma marca | água na posição de quem tem a marca |
| `chave ubis vis vocant` | uma marca | centelha de Vis na posição da chave perdida (9.4) |

**Formas do `ubis` (decididas com o autor em 24/09/2026):** tudo o que vem colado antes do `ubis` (números e marcas) é o lugar.

| Antes do `ubis` | Lugar |
|---|---|
| 1 número (`20 ubis`) | 20 blocos à frente, na direção da mira |
| 2 números (`20 5 ubis`) | 20 blocos à frente pelo chão e 5 acima dos pés do mago (altura **relativa**) |
| 3 valores (`10 64 -30 ubis`) | coordenadas exatas; **qualquer eixo pode ser uma marca** e pega aquele eixo de onde a marca está: `10 m1 -30 ubis` usa o y de m1 |
| 1 marca (`m1 ubis`) | onde m1 está, **nos pés**; com vários membros, a **média** das posições |

- **Toda marca antes do `ubis` faz parte do lugar.** O que é invocado vem depois dele, como no livro (`casa ubis m1 vocant`). `m1 10 ubis vocant` é um lugar inválido (dois valores precisam ser dois números).
- **Números depois do `ubis`** (`ubis 20`) continuam aceitos, por conveniência.
- **Por que o `ubis` pega o que vem antes e o `quantum` e o `chronos` o que vem depois:** o `ubis` transforma em lugar o que o precede ("o Ubis captura a posição da primeira fonte"), e a fonte capturada e a marca só existem antes dele; o `quantum` e o `chronos` recebem um valor, "o número posicionado entre o filtro e a função".
- **Invocar nos pés de uma marca:** o feitiço acontece embaixo de quem tem a marca. O ar sopra para cima (e, com `chronos` e força suficiente, mantém a pessoa no ar), o fogo queima os pés, a água aparece nos pés e a matéria sólida (terra, lama, magma) aparece embaixo dos pés, se houver ar ali.
- **Nos pés, sem golpe:** invocado nos pés de uma marca, o elemento não ataca quem está em cima (a terra não dá o golpe de terra, o ar não dá o golpe de ar); só a matéria ou o vento aparecem. O fogo continua queimando os pés.
- **Vento com `chronos` é uma força com efeito solo (`WindLift`, decidido em 25/09/2026):** o vento empurra com aceleração = K × potência (UMU por tick) / massa × e^(−distância / 2 blocos), com a distância medida até o chão embaixo (vento para cima) ou até a origem (vento de lado). Um vento para cima segura a coisa na altura em que o empurrão iguala o peso: 2 × ln(potência / potência de sustentação). Calibragem: 1 UMU por segundo segura um jogador de 20 de vida rente ao chão. Para um jogador: ~1,2 UMU/s flutua sem atrito, uns 0,4 bloco acima do chão (`eu ubis aura quantum 12 chronos 10 vocant`); ~2,7 UMU/s paira a 2 blocos; ~7,4 UMU/s a 4 blocos. Cada bloco a mais multiplica a potência por e^(1/2). O vento para cima também freia 40% da velocidade vertical por tick (amortecimento crítico, 2 × raiz(g / H)), para a coisa assentar na altura sem quicar, e zera a distância de queda enquanto segura pelo menos metade do peso (sem dano de queda enquanto flutua). A rajada sem `chronos` continua sendo um empurrão só. Uma pressão atmosférica por altitude, no estilo do Create Aeronautics, fica para quando o mod tiver voo de verdade (talvez com o Sable).
- **Andar no vento:** um jogador sustentado por um vento para cima anda com as teclas de movimento na velocidade de andar (1,3× correndo), em vez do controle fraco que o Minecraft dá no ar (usa a entrada que o cliente já manda, `PlayerMotionInputPacket`). O vento mostra nuvenzinhas saindo da superfície na direção em que sopra.
- **Com `chronos`, a invocação segue a marca:** a cada pulso o lugar é recalculado, então o vento continua embaixo de quem anda, a terra vai se formando embaixo dos pés (como uma ponte) e o fogo acompanha. Tudo o que foi posto some quando o tempo acaba.

Regras que saem disso:

- Com um número (ou três), a fonte que veio **antes** continua sendo o sujeito (`igni 10 ubis vocant`).
- Com marca ou fonte absorvida, o operando é gasto como lugar, e o sujeito é o que vem **depois** (`marca ubis aqua vocant`).
- Então `igni m1 ubis vocant` e `m1 ubis igni vocant` querem dizer a mesma coisa.
- O `ubis` diz **onde**. A função diz **o que fazer lá**. `igni vocant ubis m1` (o `ubis` depois da função) é inválido.

## 5. Marca + funções

### Vocant — invocar (teletransporte)

`m1 vocant`: o que tem a marca m1 **aparece** no ponto de invocação. Não percorre o caminho: some de onde estava e surge lá.

| Feitiço | Efeito |
|---|---|
| `m1 vocant` | traz m1 para o ponto mirado (chamar o cavalo, puxar um baú, resgatar um aliado) |
| `casa ubis m1 vocant` | leva m1 até onde está a marca `casa`. Se m1 é o próprio mago, é o teletransporte para casa |
| `20 ubis m1 vocant` | m1 aparece a 20 m do mago, na direção mirada ("piscar") |
| `10 64 -30 ubis m1 vocant` | m1 aparece nessas coordenadas |

- **Ponto de chegada seguro:** o mundo procura espaço livre para a caixa de colisão do objeto perto do ponto (em cima do bloco atingido, e não dentro dele). Sem espaço, o feitiço falha e não cobra a parte do deslocamento.
- **Ponto padrão (decidido):** o ponto que o mago mira, até **12 blocos** (o raycast que o código já usa, e o "ponto que você apontar" do livro 8.4). O `ubis` quebra esse limite (`20 ubis m1 vocant`, coordenadas, outra marca).
- **Blocos (decidido):** blocos marcados também podem ser invocados. Um baú (ou qualquer bloco com bloco-entidade) leva o conteúdo e a própria marca junto.

### Iactare — lançar

`m1 iactare`: o objeto m1 é **arremessado** a partir de onde está, na direção do ponto que o mago mira. É um impulso de velocidade, e não um teletransporte. O objeto faz um arco com gravidade e atrito e **não chega exatamente no ponto**. Pode ficar no meio do caminho, bater em algo ou passar do ponto.

| Feitiço | Efeito |
|---|---|
| `m1 iactare` | lança m1 em direção ao ponto mirado com a força padrão (10 UMU, cap. 4.3.2) |
| `m1 quantum 30 iactare` | a mesma coisa, com mais força |
| `alvo ubis m1 iactare` | lança m1 na direção da marca `alvo`, em vez do ponto mirado |

- A velocidade sai da energia dividida pela massa. Um objeto pesado vai mais devagar com a mesma energia.
- Se m1 é o próprio mago, isso é um salto mágico ou um dash.
- Se m1 está longe, ele é lançado de lá mesmo, em direção ao ponto mirado. O mago não precisa ver m1.
- **Blocos (decidido):** um bloco marcado é solto e voa como bloco que cai (`FallingBlockEntity`), levando o conteúdo (baú cheio) e a marca. Ao pousar, volta a ser bloco no lugar onde parou.

### Exsugat — puxar

O livro define `exsugat` como "puxar um elemento externo para si". Com marca: `m1 exsugat` **arrasta** m1 na direção do mago, como um ímã (é movimento, não teletransporte). É o contrário do `iactare`.

- Mantém o papel do livro de "capturar" uma fonte externa: `m1 exsugat ubis aqua vocant` é igual a `m1 ubis aqua vocant`, porque a marca já tem posição.

### Impediunt — repelir

`m1 impediunt`: empurra m1 para longe do mago. `alvo ubis m1 impediunt` empurra m1 para longe do ponto `alvo`.

### Vertere — converter a matéria (decidido)

`m1 vertere <fonte>` converte **a matéria do objeto** na fonte indicada, conservando a quantidade de UMU, como `igni vertere aqua` faz com energia.

**Seres vivos: o Vita vira a fonte.** O Vita do alvo é a vida dele (`ReadmeUMU`: 1 HP = 5 UMU). `m1 vertere aqua` transforma **todo o Vita** do alvo em aqua: a vida vai a zero, e a água equivalente (vida restante × 5 UMU) surge na posição dele. Um jogador de 20 HP vira 100 UMU de água.

| Feitiço | Efeito |
|---|---|
| `m1 vertere aqua` | todo o Vita de m1 vira água: m1 morre e a água surge onde ele estava |
| `m1 vertere igni` | o mesmo, virando fogo |
| `m1 quantum 20 vertere aqua` | converte só 20 UMU de Vita: m1 perde 4 HP e surgem 20 UMU de água |

- **O `quantum` limita a conversão.** Sem ele, converte tudo. Com ele, vira um dano à distância proporcional à quantidade.
- **Custo:** o custo base do `vertere` + a quantidade de UMU convertida. Matar um jogador cheio custa uns 100 UMU (1000 de XP), na mesma escala da morte propagada do firmo.
- **Dano:** um tipo próprio (`elderlexicon:vertere_vita`) que ignora armadura e encantamentos (a conversão é por dentro) e é atribuído a quem lançou. As regras de PvP do servidor valem.
- **Totem da Imortalidade (decidido):** salva o alvo, como numa morte comum. É a defesa de quem teve a marca descoberta. (Diferente do `elderlexicon:ligabis_death`, que passa por cima do totem.)
- **Mortos-vivos (decidido):** também têm Vita. Na v1, o Vita deles é simplesmente a vida.
- **O elemento liberado é real:** a água pode apagar fogo e encher buracos, o fogo queima em volta. Isso passa pela cena (`EmissionRecorder`), como qualquer outra emissão.

**Blocos: o bloco vira o elemento.** Uma tabela de bloco → elemento, com a quantidade de UMU igual à massa do bloco. Pedra com `aqua` vira água; um baú vira o elemento e solta o conteúdo.

**Itens no chão:** mesma regra dos blocos (a pilha some e o elemento surge).

**`m1 vertere m2` (marca → marca):** todos os membros de m1 passam a ter a marca m2. Serve para renomear, juntar grupos ou tirar alguém de um vínculo do Ligabis. Custo base só.

**`igni vertere m1` (fonte → marca):** inválido na v1.

### Transvocatio — trocar de lugar (decidido)

`transvocatio` (fusão de `vertere` + `vocant`) é a **troca**: duas coisas trocam de posição no mesmo instante. Já está no dicionário com `requiresTarget`, então exige o que vem depois.

| Feitiço | Efeito |
|---|---|
| `m1 transvocatio m2` | m1 vai para onde m2 estava, e m2 vai para onde m1 estava |
| `eu transvocatio inimigo` | o mago (marcado `eu`) troca de lugar com o inimigo marcado |
| `bau1 transvocatio bau2` | dois baús trocam de lugar, cada um com o seu conteúdo |

- Vale para seres vivos, blocos (com conteúdo) e itens no chão, com as mesmas regras do `vocant`.
- **Custo:** base + massa × distância **dos dois lados**.
- **Grupos (decidido):** se m1 ou m2 têm vários membros, eles são pareados do mais próximo ao mais distante, e cada par troca. Quem sobra sem par fica onde está.
- **Sem alvo (decidido):** `m1 transvocatio` troca m1 com o próprio mago, como o Ligabis, que marca o mago quando não há alvo.

## 6. Como o parser reconhece uma marca

Ordem de leitura de cada palavra:

1. **Runa do dicionário** (`ParserList.json`).
2. **Símbolo de `reframe`** registrado pelo mago (ex.: `fireball`). O reframe também cria palavras livres, então ele vem antes da marca.
3. **Número** (só algarismos, com sinal opcional).
4. **Marca:** qualquer outra palavra com **duas letras ou mais e ao menos uma letra**. Uma letra solta continua sendo glifo de runa no leitor de páginas (limite já conhecido no `ligabis-design.md`). `10` é número; `m1` é marca.

Se a marca não tem nenhum membro no mundo (nem em chunk descarregado), o feitiço **falha com aviso** ("A marca m1 não responde") e cobra só o custo base. Assim um erro de digitação não passa em silêncio.

## 7. Membros, alcance e custo

- **Onde procurar:** no índice de marcas que o `LigabisManager` já mantém (entidades pelos eventos de entrada e saída do mundo, blocos pelo `LigabisData`). Não há varredura por área.
- **Chunks descarregados (decidido):** o membro responde mesmo longe. O feitiço **carrega o chunk** onde ele está (um ticket temporário, só pelo tempo da ação) e depois o libera.
  - Para isso, o índice precisa guardar a **última posição conhecida** de cada entidade marcada (dimensão + posição), atualizada quando ela sai do mundo ou o chunk descarrega. Os blocos já estão no `LigabisData`; as entidades passam a estar também.
  - Jogador **offline** não está no mundo e não responde.
  - Membro em **outra dimensão** também não responde na v1 (seção 8).
- **Vários membros (decidido):** a função age em **todos**, e o custo é somado por membro. Para afetar um só, basta ter uma marca única.
- **Custo (decidido):** base da função + **massa × distância**. Não há alcance máximo; o custo já limita.
  - Massa: a vida máxima para seres vivos (como o firmo usa a vida) e a capacidade do bloco (raiz quadrada da resistência) para blocos.
  - Distância: de onde o objeto está até onde chega (`vocant`) ou o impulso aplicado (`iactare`, `exsugat`, `impediunt`).
  - Os valores são placeholders a calibrar com o `ReadmeUMU`. O que importa é que mover um jogador 1000 blocos custe muito mais que uma faísca.
- **Quem paga:** quem lança, pelo `SpellCostModule` (XP → saturação → vida, com o foco arcano poupando o Vita), como no Ligabis.
- **Outros jogadores (decidido):** podem ser invocados, lançados, puxados e empurrados livremente por quem sabe a marca deles. Não há consentimento nem resistência. É o preço do segredo: a marca é a única proteção. As regras de PvP do servidor continuam valendo.

## 8. Fora do escopo da primeira versão

- `ligabis` com marca como sujeito e as outras fusões (`evocatio`, `extractio`...) aplicadas a marcas. O `transvocatio` entra (seção 5).
- Teletransporte entre dimensões.
- `chronos` com marcas (ex.: `m1 chronos 5 vocant` com atraso).

## 9. Decisões pendentes

1. **Detalhes do `vertere` no Vita:**
   - **Composição do Vita:** o `ReadmeUMU` diz que o Vita é 56% aqua, 38% aura, 2% igni e 4% firmo. O custo poderia cobrar só a parte que *não* é daquele elemento: virar aqua cobraria 44% (mais fácil), e virar igni 98% (quase tudo). Proposta: deixar para a calibração, começando com o custo cheio.
   - **Converter o elemento sem destruir o objeto** (lava vira obsidiana, fogo se apaga) fica como candidato para `m1 exsugat vertere aqua` ("capture o elemento de m1 e converta"), num desenho à parte.

### Decididas (22/09/2026)

- Custo: massa × distância, sem alcance máximo.
- Jogadores: podem ser invocados, lançados, puxados e empurrados livremente.
- Blocos: podem ser invocados e lançados. Baús levam o conteúdo.
- Ponto do `vocant`: o ponto mirado, até 12 blocos. O `ubis` quebra o limite.
- `vertere` com marca: converte a matéria. Em seres vivos, o Vita vira a fonte (total, ou parcial com `quantum`). `m1 vertere m2` troca a marca. O totem salva do `vertere`, e mortos-vivos também têm Vita.
- Vários membros: a função age em todos.
- Chunks descarregados: o feitiço carrega o chunk do membro.
- `transvocatio`: troca de lugar duas coisas (`m1 transvocatio m2`). Grupos formam pares do mais próximo ao mais distante; sem alvo, troca com o mago.

## 10. Como ficou no código

**Parte pura (testada em `MarkSpellGrammarTest` e `SpellWordsTest`)**

| Peça | Papel |
|---|---|
| `spell.mark.SpellWords` | Diz o que é cada palavra: runa, número, marca ou desconhecida (seção 6) |
| `spell.mark.SpellPlace` | O lugar escrito com `ubis`: distância, coordenadas ou marca |
| `spell.mark.MarkCost` | Custo massa × distância (`MOVE_RATE` = 0,01 UMU por massa por bloco), velocidade e custo dos lançamentos, Vita em UMU |
| `SpellActionEngine` | Lê marcas e números, resolve o `ubis` e grava no `SpellAction` a marca-sujeito (`subjectMark`), a marca-alvo (`targetMark`) e o lugar (`place`). Feitiços com `ligabis` continuam lendo as próprias marcas, e a palavra depois de `reframe` é o nome gravado |
| `Parser` | Descreve o feitiço com as marcas: `Summon 'm1' at 'casa'`, `Swap 'm1' with 'm2'` |

**Parte do jogo (`spell.function`)**

| Peça | Papel |
|---|---|
| `MarkTargets` | Acha quem tem a marca: entidades carregadas e blocos pelo grafo do Ligabis, e entidades de chunk descarregado pela última posição salva. Carrega os chunks com um ticket temporário (`elderlexicon_mark`, 100 ticks) e espera até 40 ticks as entidades aparecerem |
| `MarkMotion` | Teletransporta e lança entidades, e tira e põe blocos inteiros com o conteúdo e a marca. Procura espaço livre até 2 blocos para os lados e 3 para cima |
| `MarkSpells` | O que cada função faz com a marca: `summon`, `push` (lançar, puxar, empurrar), `swap`, `rename`, `convert` |
| `TransvocatioFunctionHandler` | Novo. Troca de lugar |
| `MarkVertereFunctionHandler` | Novo, registrado como `vertere`. O executor só manda para ele o `vertere` com marca; entre fontes continua convertendo o Vita do mago |
| `LigabisData` / `LigabisManager` | Guardam a última posição de cada entidade marcada quando o chunk dela descarrega (`StoredEntity`). Um bloco lançado que pousa recebe a marca de volta |

- **Dano:** `elderlexicon:vertere_vita` ignora armadura, encantamentos, escudo e o intervalo de invulnerabilidade, e não empurra. O totem funciona.
- **Números:** a Língua Antiga escreve um glifo por algarismo (`0` = `Q` ... `9` = `Z`), lado a lado (livro 4.3.2). O editor do grimório converte cada algarismo digitado (`20` vira `SQ`, mesmo digitado um de cada vez), e o servidor lê de volta (`spell.mark.NumberGlyphs`). Antes, `20` virava `S0` e era lido como a marca `s0`. Glifos são maiúsculos: `sq` em minúsculas continua sendo uma marca. O sinal de menos é aceito (`-TQ` = -30).
- **`quantum`:** pega o número logo depois dele (`igni quantum 20 iactare`) ou, se não houver, o que veio logo antes (`20 quantum`). **O efeito é linear**, como no livro (cap. 4.3.2: "o dobro do fogo, o dobro do perigo, mas também o dobro do efeito"): potência = UMU / 10. No `iactare` e no `vocant` de fonte, o dano, o tempo de fogo e o empurrão são multiplicados pela potência, e a matéria posta no mundo (fogo, terra) ou tocada (água) cobre cerca de *potência* blocos em volta do impacto (até 40 blocos, a partir de 400 UMU, por desempenho). O feixe fica mais denso. O que passar dos 10 UMU padrão é cobrado 1 para 1. Com marca, é a energia do lançamento (abaixo). No `vertere` com marca, limita a conversão: `m1 quantum 20 vertere aqua` tira 4 de vida; um bloco ou item só é convertido se o quantum cobrir o valor inteiro dele.
- **`chronos`:** pega o número como o `quantum`. O livro diz que ele muda "a duração padrão dos efeitos ou o instante de sua manifestação" (cap. 4.3.2). Nas funções que duram, é a duração: `igni chronos 5 iactare` estica a evocação de 2 para 5 segundos, com a mesma energia espalhada (mais lento, mais controlado), e `chronos 0` solta tudo de uma vez; no `vocant` de fonte, é quanto tempo a matéria invocada fica na cena. No `iactare`, `exsugat` e `impediunt` com marca, é a duração do empurrão: `eu chronos 10 iactare` empurra durante 10 segundos, em velocidade constante, com a mesma energia espalhada (velocidade = 2 × raiz(energia / (massa × ticks))); `eu quantum 1000 chronos 10 iactare` dá uns 200 blocos de voo. Só nas funções sem nada para esticar (teletransporte, troca, troca de marca) é o instante em que acontecem; a conversão do `vertere` também, por enquanto. Negativo é recusado com aviso ("matéria para outro volume"). Máximo de 5 minutos. Ainda não afeta `exsugat` e `impediunt` de fonte.
- **`ubis` no `iactare` de fonte:** a distância vira o alcance da mira (`igni 200 ubis iactare` acerta até 200 blocos, em vez de 20), e coordenadas ou marca fixam onde o fogo cai. Se nada estiver ao alcance, o feitiço avisa que "se perdeu". O `ubis` também aceita os números depois dele (`ubis 200`), como o `quantum` e o `chronos`.
- **O feitiço segue a mira enquanto dura:** o `iactare` de fonte divide o efeito entre os pulsos (um a cada 5 ticks), e cada pulso cai onde o mago mira naquele instante; varrendo a mira, o efeito se espalha, e com a mira parada o total é o mesmo de um impacto só (o fogo acumulado e o dano somam, e o intervalo de invulnerabilidade não engole os pulsos). Nos lançamentos com marca e `chronos`, o empuxo recalcula a direção a cada tick: para a mira atual (quando quem voa é o próprio mago, para onde ele olha), para o lugar do `ubis` (vira perseguição) ou, no `exsugat` e `impediunt`, a partir de onde o mago está agora.
- **Elementos permanentes e efêmeros no `vocant` (`ElementPersistence`, `Invocation`):** decidido com o autor em 24/09/2026 e conferido no livro (9.5: `firmo quantum 15 vocant` ergue uma barreira; 4.3.2: com `chronos`, a barreira "permanece ativa e se regenera"; 9.4: a centelha de Vis é "um brilho fraco que você pode seguir com os olhos").
  - **Permanentes** (aqua, firmo, lutum, fusus e a matéria marcada): surgem e ficam. Água vira bloco de água, terra vira terra, lama vira lama, magma vira bloco de magma, cerca de *potência* blocos. Com `chronos`, ficam só durante a janela, se levantam de novo se quebrados (a cada segundo) e somem no fim; a matéria marcada volta para onde estava.
  - **Efêmeros** (igni, aura, vis, fulmen, caligo, nebula, pulvis): surgem e se vão. Sem `chronos`, o efeito de sempre, uma vez (e o `vis` brilha, o `fulmen` cai como raio). Com `chronos`, continuam agindo naquele ponto durante a janela, com a energia espalhada: o fogo fica aceso e depois se apaga, o ar vira um vento constante, a cada tick, que sopra para fora da superfície onde foi invocado (do chão para cima, de uma parede para fora; numa criatura ou no ar, na direção em que o mago olhava) e mantém tudo o que está solto na área (criaturas, o próprio mago e itens no chão) andando nessa direção pelo menos na velocidade que a energia espalhada no tempo permite (a mesma conta do voo com `chronos`; sem `chronos`, uma rajada forte e única em todos da área), o raio cai uma vez por segundo, a névoa e a poeira empurram, o vapor escalda, a Vis brilha.
  - **Mudança de comportamento:** antes, o `aqua vocant` não punha água, só apagava fogo e enchia caldeirão; agora põe água (ainda enche o caldeirão e apaga o fogo se acertar um).
  - As fusões foram classificadas pelo Claude: lutum e fusus permanentes; caligo, nebula e pulvis efêmeros.
- **Agendador (`SpellTicks`):** o mod agendava os atrasos com o `TickTask` do Minecraft, mas o servidor roda um `TickTask` assim que tem tempo livre (`MinecraftServer.shouldRun`), o que num servidor folgado é na hora. Todo atraso de feitiço era imediato: o impacto do `iactare` antes do feixe, o segundo do `vocant`, o `chronos`, o empuxo, a espera pelas entidades de chunk descarregado e os feitiços atrasados de uma página em bloco. Agora os atrasos contam ticks de verdade. Efeito colateral corrigido em 25/09/2026: com os pulsos do `iactare` espaçados de verdade, cada pulso vivia exatamente o intervalo até o próximo, e como o pulso é gravado no fim do tick (depois do passo da cena), a cena ficava vazia por um tick entre pulsos e zerava a carga e a pressão acumuladas (`WorldScenes` reseta as leis quando a cena esvazia). Sem isso, raio e vapor nunca nasciam. Cada pulso agora vive um tick a mais que o intervalo.
- **Varinha Criativa (`CreativeWandItem`):** só na aba criativa, sem receita. Inquebrável, canaliza qualquer custo e não dá náusea; o custo continua saindo do XP, para ser lido nos testes.
- **Lançamento (`MarkCost.throwWith`):** a velocidade é 2 × raiz(energia / massa), até 3,9 blocos por tick para entidades (o limite por eixo do pacote de velocidade do Minecraft) e 3 para blocos. A energia que passa do necessário para a velocidade máxima vira **empuxo**: a velocidade é mantida por mais ticks, como um foguete (até 100 ticks). O custo segue a regra massa × distância: a distância do empuxo mais um segundo de voo. Para você (20 de vida): `eu iactare` dá 1,4 bloco/tick e 1 tick de empuxo (~5,9 UMU); `eu quantum 1000 iactare` dá 3,9 blocos/tick por 13 ticks (~25 UMU); `eu quantum 3000 iactare` dá 39 ticks (~46 UMU). A queda pode matar.
- **Custo base:** `transvocatio` custa 1 UMU, como `vocant` e `vertere`.

## 11. Limites da primeira versão e como testar

**Limites**
- **O grimório não aceita `transvocatio`**, porque recusa runas de fusão ("Grimorio aceita apenas runas originais"). Hoje ele só pode ser lançado pelo comando `/spell`.
- **A conjuração rápida (runas do repertório) recusa marcas**, porque confere se cada palavra é uma runa atribuída. As marcas funcionam nas páginas do grimório e no `/spell`.
- **`igni exsugat ubis ...`** (o `ubis` pegar a posição de uma fonte absorvida) ainda não existe.
- **Entidades descarregadas no desligamento do servidor** podem não ter a posição salva, e só voltam a responder quando o chunk delas carregar.
- Só seres vivos e itens no chão são membros entre as entidades (a mesma regra do Ligabis).

**Como testar** (em criativo, para não pagar; comandos no chat)
1. Marque um porco como `p1` com o Pincel Elder e se afaste. `/spell p1 vocant` mirando no chão: o porco aparece ali depois de 1 segundo.
2. Marque um baú com itens como `bau` e rode `/spell bau vocant`: o baú vem com o conteúdo, e o Pincel ainda mostra a marca.
3. Marque um bloco longe como `casa` e você mesmo como `eu`. `/spell casa ubis eu vocant` te leva até lá.
4. `/spell 20 ubis eu vocant`: você "pisca" 20 blocos para a frente.
5. `/spell p1 iactare` lança o porco para onde você mira. `/spell eu quantum 1000 iactare`, mirando para cima, te lança longe. `/spell bau iactare` lança o baú como bloco que cai, e ele pousa com os itens e a marca.
6. `/spell p1 exsugat` puxa o porco; `/spell p1 impediunt` empurra para longe.
7. `/spell p1 transvocatio` troca você com o porco; `/spell p1 transvocatio bau` troca o porco com o baú.
8. `/spell p1 vertere m2` troca a marca do porco. `/spell m2 vertere aqua` mata o porco e solta água no lugar. Com um totem na mão de um jogador marcado, ele sobrevive.
9. Chunk descarregado: marque um porco, vá para longe (mais que a distância de visão) e rode `/spell p1 vocant`.
