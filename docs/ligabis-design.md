# Ligabis — desenho (v2)

**Status:** desenho acordado em conversa (21/09/2026). O **núcleo puro dos quatro aspectos** (firmo, igni, aqua e aura, em espelho e hierarquia) está implementado em `com.elderlexicon.mod.ligabis` e testado. Falta tudo o que toca o Minecraft.
**Substitui:** `ligabis-immortality-plan.md`. O trecho de molduras de `ligabis-linked-frames.md` continua válido (ver seção 8).

## 1. Fontes

- O livro: capítulo 4.3.1 (Ligabis: ligação espelhada e hierárquica, correntes) e capítulo 9.4 (marca sobre um objeto).
- As regras definidas pelo autor nesta rodada (seções 3 a 7).
- O que já existe: UMU e custo (`ReadmeUMU.md`), a cena e leis emergentes (`spell/scene`), o foco arcano que poupa o Vita.

## 2. Vocabulário

| Termo | Significado |
|---|---|
| **Marca** | Palavra livre gravada num objeto. É **secreta**: quem conhece a palavra pode usá-la. |
| **Membro** | Qualquer objeto que carrega a marca. |
| **Aspecto** | O que se vincula: `firmo` (integridade), `igni` (calor), `aqua` (respiração), `aura` (movimento). |
| **Espelho** | Todos os membros do grupo se refletem entre si. |
| **Hierarquia** | O pai reflete no filho; o filho não reflete no pai. |
| **Corrente** | Hierarquias encadeadas: m1 → m2 → m3. |
| **Dono** | Quem criou o vínculo. É quem paga. |

## 3. Gramática

O aspecto é uma runa de fonte escrita antes das marcas; as marcas são palavras livres.

| Forma | Significado |
|---|---|
| `firmo marca1 ligabis` | Espelho entre **todos** que carregam `marca1` |
| `firmo marca1 ligabis marca2` | Hierarquia: os de `marca1` são pais, os de `marca2` são filhos |
| `firmo m1 ligabis m2 ligabis m3` | Corrente: m1 → m2 → m3 |

- **O feitiço marca o que você mira.** O alvo recebe a **última marca** citada (o destino). Na forma espelho é a única.
- **Sem alvo, o feitiço marca o próprio mago.** Mirar pro vazio (nada na frente, nem entidade nem bloco) não falha mais: vira o alvo você mesmo. O Pincel Elder tem o mesmo atalho: agachado, um clique sem mirar em nada marca você.
- O parser precisa reconhecer palavras desconhecidas ao lado de `ligabis` como marcas, e não como "lexema desconhecido".

## 4. Ciclo de vida do vínculo

- **Persistente.** Sobrevive a reinícios (`SavedData` por mundo com dono, aspecto, forma e marcas). **Não há duração.**
- **Membros são as marcas que existem no mundo**, não uma lista fixa: quem for marcado depois entra sozinho.
- **Quando o vínculo acaba:**
  1. o custo não pode mais ser pago (ver seção 6);
  2. as marcas são removidas.
- **A marca some quando o membro morre ou é destruído.** O vínculo continua entre os restantes.
- **Dono offline: o vínculo fica suspenso.** Nada é refletido nem cobrado. Quando o dono volta, retoma sem "recuperar" o que passou.
- **Segredo e interferência.** Não há dono por marca. Quem descobre a palavra pode marcar algo e entrar no vínculo, e o dono continua pagando. Um intruso pode romper o vínculo removendo marcas dos membros ou sobrecarregando o dono até o custo ficar impagável.

## 5. Aspectos

### Firmo: integridade

- **Gatilhos:** dano (depois do cálculo de armadura) e morte ou destruição.
- **Espelho:** o dano que um membro sofreu é reproduzido nos outros com o **mesmo valor final**. Quem recebe o reflexo **ignora a própria armadura**: se o original estava sem armadura, os outros levam como se estivessem sem; se estava com, os outros levam como se tivessem a mesma. Se um membro morre ou é destruído, **todos** morrem ou são destruídos.
- **Hierarquia:** o dano que o **filho** sofre é **cancelado nele e transferido ao pai**. Um jogador ligado a um porco escondido nunca sofre o dano: quem sofre é o porco. Quando o pai **morre ou é destruído, o filho morre junto**, mesmo sem ter levado dano. O dano que o pai sofre por conta própria não passa ao filho, e um filho que morre não afeta o pai.
  - **Corrente: o dano sobe até o topo.** Num m1 → m2 → m3, o dano de m3 vai para m2, e m2, que também é filho, o passa a m1. Quem está no meio não o sofre; só o topo. O dano do próprio m2 também sobe. Cada salto é um disparo e é pago à parte.
  - **Sem eco:** o dano que chegou ao topo nunca volta a descer.
  - **Vários pais:** o dano é dividido em partes iguais; a morte de **qualquer** pai mata o filho (confirmado).
  - **Que dano é transferido:** todo dano, **exceto o que um Totem da Imortalidade também não evita** (o que ignora invulnerabilidade: vazio, `/kill`). É um filtro do adaptador; o núcleo só vê o dano que lhe é entregue.
  - **Morte:** a morte do topo desce em cascata por todos os níveis.
  - **Se um salto não puder ser pago**, o dano fica onde chegou (no membro do meio) e aquele vínculo quebra.
- **Objetos (blocos, itens) têm capacidade.** Um bloco não tem vida, então o motor acumula o dano que ele absorve e o destrói quando passa da capacidade. É isso que separa um pai de obsidiana de um pai de terra: a obsidiana aguenta muitos golpes, a terra quebra no primeiro e leva o filho junto. A capacidade vem do adaptador, da resistência a explosão do bloco: a **raiz quadrada** da resistência, com mínimo de 1 HP (terra 1, pedra ≈ 2,4, obsidiana ≈ 35). É um primeiro chute a calibrar.
- **Não há imortalidade.** O firmo é destino compartilhado. O código de totem (`LigabisEvents`) sai.
- **Custo:** vida movida × 5 UMU (taxa de vida do `ReadmeUMU`); morte propagada custa a vida restante do alvo × 5. Para blocos vale a mesma conta sobre a capacidade restante (equivale a resistência ÷ 10 UMU).

### Igni: calor

- **Gatilho:** uma **mudança** de estado térmico (acendeu ou apagou), não o nível. O mundo informa o estado de cada membro uma vez por tick; quem mudou desde a observação anterior dispara o vínculo.
- **Linha de base:** a primeira vez que um membro é visto só registra o estado. Quem já estava queimando ao entrar no vínculo não sincroniza ninguém.
- **Espelho:** a **última ação** (acender ou apagar) reflete em todos os outros. Se no mesmo tick uns acendem e outros apagam, **acender vence**.
- **Hierarquia:** só pai → filho, inclusive apagar. O filho mudar não afeta o pai, e um filho apagado com o pai queimando não é reacendido.
- **Corrente:** uma mudança aplicada por um vínculo passa aos membros abaixo dele, nunca para cima. O calor **desce** a corrente; o dano do firmo **sobe** (cada um percorre a corrente na sua direção).
- **Sem eco:** o estado que o vínculo aplica fica lembrado; ver esse estado de novo no tick seguinte não é uma ação nova.
- **Quando o vínculo quebra, ninguém volta ao estado anterior.** Cada membro fica no último estado que tinha. Uma lava apagada vira obsidiana, pedregulho, pedra ou basalto pela lógica vanilla e continua assim; o mesmo vale para abóbora, fornalha etc. Blocos assim não reacendem, então não há vai-e-vem.
- **Blocos:** só mexe em estados que já existem (aceso, fogo). Não coloca fogo em bloco que não tem estado aceso, e respeita `mobGriefing`.
- **Custo:** 1 UMU por membro cujo estado é alterado (placeholder; `ReadmeUMU`: 12 níveis de luz = 1 UMU). Membro que já estava no estado certo não custa.
- **Contrato com o adaptador:** informa o estado de cada membro por tick (`observeHeat`) e aplica os `SetHeat` devolvidos. Se não conseguir aplicar um (por exemplo, `mobGriefing` desligado), chama `syncHeat` com o estado real, para o motor não tomar aquilo por uma ação nova.

### Aqua: respiração

O aqua tem dois canais, um para cada tipo de membro:

**Fôlego (seres vivos)**
- **Gatilho:** uma **variação** do ar (em ticks). O mundo informa o ar de cada membro uma vez por tick; a diferença para a observação anterior é o que se compartilha. A primeira observação só registra a linha de base.
- **Espelho:** pulmões compartilhados. O que um perde ou ganha, os outros perdem ou ganham. Dois membros prendendo o fôlego ao mesmo tempo se afogam mais rápido: cada um leva a perda do outro além da sua.
- **Hierarquia:** só pai → filho. Um filho se afogando não afeta o pai.
- **Soma:** um membro que recebe variações de várias fontes recebe a soma. O resultado fica entre 0 e o ar máximo do próprio membro (`Members.maxAir`, 300 por padrão).
- **Corrente:** a variação desce a corrente.
- **Sem eco:** o ar que o vínculo aplica fica lembrado.
- **Custo:** 1 UMU por 100 ticks de ar movidos, somando todos os que recebem (placeholder).

**Encharcado (blocos)**
- Mesmo molde do igni: liga e desliga (bloco com água ou seco). A mudança de um se reflete nos outros; no mesmo tick, **molhar vence** secar; só o pai muda o filho na hierarquia.
- **Custo:** 1 UMU por bloco alterado (placeholder).
- **Só participa do canal quem já foi informado ao motor**, então um ser vivo nunca vira "encharcado" e um bloco nunca perde fôlego.

**Contrato com o adaptador:** informa o ar (`observeAir`) e o estado molhado (`observeWet`) por tick e aplica os `SetAir` e `SetWet` devolvidos. Se não conseguir aplicar algum, chama `syncAir` ou `syncWet` com o valor real.

### Aura: movimento

- **Como o motor enxerga o movimento.** O mundo informa a posição de cada membro uma vez por tick. O quanto o membro andou desde a observação anterior é o deslocamento **próprio** dele. O vínculo decide o quanto ele *deveria* ter andado, e o motor devolve a diferença (`Displace`) para o mundo aplicar. A primeira observação só registra a linha de base.
- **Hierarquia:** o deslocamento do filho é **forçado** a ser igual ao dos pais (a soma deles). Se o pai se move, o filho é movido igual, com custo do mago. Se o pai está parado, o que o filho tentar é **anulado**, também com custo do mago: fica imóvel como o pai. O pai nunca é movido pelo filho.
- **Corrente:** resolvida de cima para baixo. Um neto segue o que o pai *foi obrigado* a fazer, não o que o pai tentou. Se o do meio se afasta sozinho com o topo parado, ele é segurado, e o de baixo também.
- **Espelho:** o deslocamento de cada membro se **soma** ao dos outros. Dois membros andando para o mesmo lado andam o dobro; andando em sentidos opostos, se travam no lugar. É caótico por desenho.
- **Custo:** 1 UMU por bloco de correção que o motor precisa aplicar (placeholder). Um filho que já anda exatamente como o pai não custa nada. Se o dono não pode pagar, o vínculo quebra e ninguém é movido.
- **Sem eco:** a posição que o vínculo pede fica lembrada, então vê-la depois não conta como movimento novo. Movimentos abaixo de 1 mm são ruído e ignorados.
- **Contrato com o adaptador:** informa as posições (`observeMotion`) e aplica os `Displace` devolvidos, de preferência como impulso de velocidade e não como teletransporte por tick. Se um membro não puder ser movido, chama `syncMotion` com a posição real, para o motor não ler o desvio como movimento próprio.
- **Membros:** qualquer coisa que tenha posição e possa ser movida (seres vivos, itens no chão). Blocos ficam para depois.
- **Limitação conhecida:** um filho com dois grupos de pais diferentes (duas marcas de pai em vínculos distintos) segue só o último vínculo, na ordem estável. Vários pais na *mesma* marca somam normalmente.

## 6. Custo e pagamento

- **Cobrança por evento**, cada vez que o vínculo dispara. **Não há custo por tempo.**
- **Quem paga:** o dono, na ordem XP → saturação → vida (`SpellCostModule`). Com um foco arcano em mãos, a conta passa pelo foco e **não toca no Vita**.
- **Até as últimas consequências (livro, cap. 3.2.1):** se não houver o que pagar, o espírito cobra em carne, e o mago pode morrer pagando. Sem recursos, ou morto, o vínculo quebra.
- **Num espelho com N membros**, um evento gera N−1 reflexos, e o custo cresce junto. Isso equilibra o sistema.
- **Refatoração necessária:** o pagamento hoje vive dentro de `SpellContext`. Precisa virar um serviço que cobre de um jogador sem depender de um cast.

## 7. Regras de propagação

- **Anti-eco:** o reflexo carrega uma etiqueta ("vindo de vínculo") e não é refletido de novo. Sem isso, A fere B, B fere A, e assim para sempre.
- **Snapshot por tick e ordem determinística:** eventos do tick são processados no fim, em ordem estável. O resultado não depende da ordem dos membros.
- **Ciclos** (m1 → m2 → m1): rejeitados na criação ou limitados por profundidade.
- **Dano refletido** usa um tipo próprio (`elderlexicon:ligabis_reflection`), marcado como ignorando armadura e encantamentos, e é atribuído ao **dono**. As regras de PvP do servidor valem.

## 8. Arquitetura

- **Núcleo puro e testável** (sem Minecraft): grafo e registro de vínculos, leis por aspecto, livro anti-eco, política de custo.
- **Adaptadores de ponta** por tipo (ser vivo, item, bloco, moldura) expondo integridade, calor, fôlego e movimento; o que o alvo não suporta é operação nula.
- **Persistência:** `SavedData` por mundo para os vínculos e um índice de marcas por mundo (entidades pelos eventos de entrada e saída do mundo, blocos com entidade pelo NBT, blocos comuns por um registro de posições).
- **Blocos comuns:** o registro guarda o tipo do bloco ao marcar; ao disparar, se o tipo mudou, o bloco foi destruído e a marca some (validação preguiçosa).
- **Molduras e pergaminhos:** `ServerSpellingController.collectLinkedScrolls` hoje consulta `LigabisLinkManager` e, sem elo, varre 64 blocos atrás da mesma marca. Deve passar a consultar o índice de marcas, **mantendo o comportamento visível** do `surgit` em cadeia.
- **Sai:** o mapa estático `LINKS`, o `LigabisLinkManager` atual, o `LigabisEvents` (totem) e os blocos fantasma.
- **Fica:** o `MarkHelper`, o `SetMarkPacket` e o Pincel Elder.

## 9. Fora do escopo da primeira versão

- Aura em blocos.
- Marcas em pilhas de itens dentro de inventários.
- Mais de uma marca por objeto (hoje é uma só; a hierarquia relaciona marcas, então uma corrente já funciona com uma).
- Recuperar eventos ocorridos durante a suspensão.

## 10. Pendências

1. **Marcar sem vincular.** Como marcar um pai sem criar um espelho sobre a marca dele? Proposta: `marca1 ligabis` **sem aspecto** só marca.
2. **Qual marca o alvo recebe** na hierarquia. Proposta: a última citada (destino).
3. **O Pincel Elder revela a marca?** Se revelar, o segredo vaza. Decidir quem pode ler.
4. **Membro em chunk descarregado.** A morte ou destruição de um membro fica pendente e é aplicada quando o outro carregar?
5. **Empate no igni.** Acender vence (placeholder).
6. **Constantes de custo.** Todas as marcadas como placeholder precisam de calibração em jogo.
7. **Capacidade dos blocos.** A raiz quadrada da resistência é um chute; a terra fica com 1 HP de capacidade e a obsidiana com cerca de 35.
8. **Aqua: constantes.** 1 UMU por 100 ticks de ar e 1 UMU por bloco encharcado são chutes; calibrar em jogo.
9. **Aqua entre tipos diferentes.** Um ser vivo ligado a um bloco encharcado não troca nada (canais separados). Fica como está por enquanto (ver "Ideias" abaixo).
10. **Aura e gravidade.** Se o pai está parado, a queda do filho conta como movimento e é anulada: o filho fica parado no ar. Decidir se o eixo vertical fica isento.
11. **Aura: constante de custo.** 1 UMU por bloco corrigido é um chute; um espelho de vários membros andando fica caro muito rápido, o que talvez seja o desejado.

## 11. Ideias (não implementadas)

- **Entidades encharcadas.** Hoje só blocos têm o estado "encharcado". Fica registrado que seres vivos também poderiam ficar encharcados, o que abre outras mecânicas (por exemplo, interação com o raio e com o calor). Por ora é só uma ideia; o aqua dos seres vivos continua sendo o fôlego.

## 12. Estado da implementação

Núcleo puro em `src/main/java/com/elderlexicon/mod/ligabis`, sem nenhuma dependência do Minecraft:

| Classe | Papel |
|---|---|
| `Link` | Vínculo declarado: espelho (1 marca) ou hierarquia (2 marcas); `chain` expande correntes |
| `LinkGraph` | Vínculos e membros por marca, em ordem estável; rejeita ciclos de hierarquia |
| `LinkEngine` | Lê `LinkEvent` (dano, morte) e devolve `Effect` (cancelar dano, dar dano, matar, cobrar, quebrar vínculo) |
| `Members`, `Owners` | Interfaces que o mundo implementa: perfil e vida dos membros; dono online e pagamento |
| `CostPolicy` | Custo por efeito (5 UMU por HP; 1 UMU por membro com calor ou encharcado alterado; 0,01 UMU por tick de ar; 1 UMU por bloco de movimento corrigido) |
| `SwitchRules` | Aspectos liga/desliga: calor no igni, encharcado no aqua. Mudança por tick, empate, cascata em corrente, memória do estado aplicado |
| `AirRules` | Aqua para seres vivos: variação de ar compartilhada, soma, limites, custo por tick de ar |
| `MotionRules` | Aura: deslocamento próprio contra o exigido pelo vínculo, de cima para baixo na hierarquia, soma no espelho |
| `Vec` | Vetor de posição e deslocamento, para o núcleo não depender do vetor do jogo |

Os quatro adaptadores do Minecraft já existem (seções 13 a 16). O que falta é a limpeza da seção 8 (remover o código antigo, que ficou morto).

## 13. Adaptador do firmo (implementado)

O firmo já roda no jogo, junto com os outros três (seções 14 a 16). O código antigo do `LigabisFunctionHandler` não é mais alcançado por nenhum aspecto, mas ainda não foi removido (ver seção 8 e a nota no fim desta seção).

**Peças** (`com.elderlexicon.mod.ligabis.world`, compartilhadas com igni, aqua e aura — ver seções 14 a 16)

| Classe | Papel |
|---|---|
| `LigabisManager` | Um por servidor. Mantém o grafo, lê as marcas das entidades carregadas, alimenta o motor com dano, morte e calor, e executa o que ele responde |
| `LigabisWorldEvents` | Eventos do Forge: início e fim do servidor, dano (`LivingDamageEvent`, depois da armadura), morte, entrada e saída de entidades, calor a cada tick e um tick a cada 5 para conferir blocos marcados |
| `LigabisData` | `SavedData` no overworld: vínculos, marcas de blocos e desgaste dos blocos |
| `LigabisGuard` | Marca o trecho em que o próprio motor fere ou mata, para o dano dele ser tratado como vindo de vínculo (sem eco) |
| `MemberKeys` | Nome estável de cada membro: `e:<uuid>` para entidades, `b:<dimensão>\|x,y,z` para blocos |

Desde esta rodada, `castFirmo` virou **`castLink`**: um único método que lê a gramática, pega o aspecto dela mesma (não fixo em firmo) e cria o vínculo certo. É o que o igni reaproveita.

**O que ele faz**
- **Feitiço `firmo m1 ligabis [m2 ...]`:** lê a gramática, cria os vínculos (ou entra num que já existe, cujo dono continua pagando) e grava a **última marca** citada no alvo mirado (ser vivo ou bloco).
- **Dano:** o motor recebe o dano final do membro. Se ele for filho, o dano é cancelado e um `DealDamage` é aplicado no pai com o tipo `elderlexicon:ligabis_reflection`, que ignora armadura, encantamentos, escudo e intervalo de invulnerabilidade, sem empurrão.
- **Morte:** a morte de um membro chega ao motor, que mata os outros com o tipo `elderlexicon:ligabis_death`, que ignora invulnerabilidade e, por isso, o Totem da Imortalidade (como o `/kill`).
- **Blocos:** não têm vida, então o desgaste é acumulado pelo motor e salvo. Quando passa da capacidade, o bloco é destruído **sem drops**. Um bloco marcado que some por qualquer outro motivo (água, pistão, fogo, o jogador) é percebido pela conferência periódica e conta como morte.
- **Pagamento:** o dono paga por `SpellCostModule.payOutsideCast` (XP, depois comida, depois vida), com o foco arcano poupando o Vita. Sem recursos, tudo é tomado (o mago pode morrer) e o vínculo quebra. Jogadores em criativo não pagam nada.
- **Marcas:** ao morrer, o membro perde a marca. O Pincel Elder também marca blocos comuns agora, gravando no `LigabisData`.

**Limites da primeira versão**
- Só seres vivos e blocos são membros do firmo. Itens no chão e molduras ainda não.
- Marcas de uma letra só não funcionam: o leitor de páginas trata uma letra solta como glifo de runa. Use marcas de duas letras ou mais.
- `firmo marca ligabis` cria também um espelho sobre `marca`. Enquanto só houver um membro ele é inerte, mas passa a valer assim que outro for marcado com a mesma palavra (ver pendência 1).
- Ao trocar de dimensão ou descarregar o chunk, o membro sai do grafo até voltar; nada é refletido para ele nesse tempo.

**Como testar no jogo** (em criativo, para não pagar; em sobrevivência cada ponto de dano movido custa 5 UMU, ou seja, 50 de XP)
1. Escreva numa página do grimório `firmo pai ligabis` e lance mirando num porco, ou marque o porco com o Pincel Elder como `pai`.
2. Numa página escreva `firmo pai ligabis filho` e lance mirando numa vaca: a vaca recebe a marca `filho`, e nasce o vínculo `pai -> filho`.
3. Bata na vaca: ela não deve perder vida, e o porco deve perder o que a vaca perderia.
4. Mate o porco: a vaca deve morrer junto, e as marcas somem.
5. Troque o porco por um bloco de obsidiana (mire nele no passo 1) e depois por um de terra: a obsidiana aguenta bem mais golpes na vaca antes de quebrar e matá-la.

**Nota (21/09/2026):** com o aura implementado (seção 16), os quatro aspectos passam a marcar via `castLink`. O código antigo dentro de `LigabisFunctionHandler` (mapa estático `LINKS`, `LigabisLink`, `LinkTarget` e as subclasses) nunca mais é alcançado por nenhum feitiço — confirmado, não é suposição. O mesmo vale para `LigabisEvents` (o "totem" que a seção 8 já mandava tirar), que hoje só existe porque `LigabisLinkManager` nunca mais é alimentado. A única ponta viva é `ServerSpellingController.collectLinkedScrolls`, que ainda consulta `LigabisLinkManager` antes de cair no fallback de varrer 64 blocos atrás da mesma marca — sem vínculos novos sendo registrados, ele sempre cai no fallback, então o recurso de pergaminhos ligados **continua funcionando**, só que sem o atalho. A seção 8 já previa a solução (trocar essa consulta pelo índice de marcas novo) — é a limpeza que falta, não uma correção de bug.

## 14. Adaptador do igni (implementado)

Reaproveita as peças do firmo (seção 13): mesmo `LigabisManager`, mesma gramática, mesmo `castLink`. O que muda é o gatilho e o efeito.

**O que ele faz**
- **Feitiço `igni m1 ligabis [m2 ...]`:** igual ao firmo — cria o vínculo (ou entra num existente) e grava a última marca no alvo mirado.
- **Calor a cada tick:** `LigabisWorldEvents` chama `LigabisManager.tickIgni` uma vez por tick, por nível carregado. Ele reúne o estado de calor de **todo** membro marcado (qualquer aspecto) que esteja naquele nível — seres vivos pelo tempo restante de fogo, blocos por `isHot` — e entrega ao motor via `observeHeat`. Só quem mudou desde a última vez dispara algo.
- **Seres vivos:** acender aplica 8 segundos de fogo (`setSecondsOnFire`); apagar chama `clearFire`.
- **Blocos:** só mexe em blocos que já têm a propriedade `lit` (fornalha, campfire, etc.) ou que já são fogo/fogo-de-alma — alterna o estado ou remove o bloco de fogo. **Nunca coloca fogo num bloco que não tinha esse estado**, e nada acontece se `mobGriefing` estiver desligado (o motor é avisado do estado real via `syncHeat`, para não achar que a ação deu certo).
- **Sem eco e sem reversão:** o motor já cuida disso (seção 5); o adaptador só relata o estado observado e aplica o que ele manda.

**Limites da primeira versão**
- Vale a mesma limitação de marcas de uma letra e de mirar-pro-vazio do firmo (seção 13).
- O calor não reconhece a *duração* do fogo, só se está pegando fogo ou não — um jogador com Fogo IV e outro recém-aceso contam igual.
- Um bloco de lava que vira obsidiana ao apagar simplesmente deixa de estar "quente" na próxima observação; nada volta atrás.

**Como testar no jogo**
1. Marque uma vaca como `fonte` (Pincel Elder ou `igni fonte ligabis`).
2. Escreva `igni fonte ligabis brasa` e lance mirando num carneiro: nasce o vínculo `fonte -> brasa`.
3. Toque fogo na vaca (isqueiro, lava): o carneiro deve pegar fogo pouco depois, sem você ter feito nada a ele.
4. Deixe a vaca apagar (ou apague com água): o carneiro apaga também.
5. Repita mirando num bloco de campfire apagado no lugar do carneiro: ele deve acender (`lit=true`) quando a vaca pega fogo.

## 15. Adaptador do aqua (implementado)

Também reaproveita o `LigabisManager` e o `castLink` (seção 13). Dois canais, cada um alimentado por um `tickAqua` só, chamado uma vez por tick por nível — igual ao igni.

**O que ele faz**
- **Feitiço `aqua m1 ligabis [m2 ...]`:** igual ao firmo e ao igni.
- **Fôlego (seres vivos):** a cada tick, reporta o ar (`getAirSupply`) de todo ser vivo marcado ao `observeAir`, e aplica os `SetAir` devolvidos com `setAirSupply`. O ar máximo vem da própria entidade (`getMaxAirSupply`), não de um valor fixo — então um golfinho, por exemplo, não é limitado a 300.
- **Encharcado (blocos):** a cada tick, reporta se o bloco marcado está molhado (`isWet`: fonte de água ou propriedade `waterlogged` ligada) ao `observeWet`, e aplica os `SetWet` devolvidos. Mesmo molde do igni: só alterna a propriedade `waterlogged` de quem já tem ela, ou remove uma fonte de água já existente — **nunca coloca água num bloco que não tinha esse estado** — e respeita `mobGriefing`.
- **Os dois canais nunca se misturam:** um ser vivo nunca é avaliado por `isWet`, e um bloco nunca reporta ar. Isso é automático: cada `Map` do `tickAqua` só recebe o que faz sentido pro tipo do membro.

**Limites da primeira versão**
- Mesmas limitações de marca de uma letra e mirar-pro-vazio das seções 13 e 14.
- O canal de fôlego só synca quem está marcado — um jogador sem marca nunca compartilha pulmão, mesmo perto de outro marcado.
- Um bloco de água normal (não fonte) ou um bloco a jusante de uma fonte não conta como "molhado" para o canal — só a fonte em si ou a propriedade `waterlogged`.

**Como testar no jogo**
1. Marque dois jogadores (ou um jogador e um afogável qualquer) com `aqua respira ligabis` num, e `aqua respira ligabis parceiro`... — mais simples: marque os dois com a mesma palavra via `aqua ar ligabis` (espelho).
2. Os dois debaixo d'água ao mesmo tempo devem perder fôlego mais rápido que um sozinho (a perda de um soma na do outro).
3. Marque um bloco de escada (tem propriedade `waterlogged`) como `seco`, e outro como `seco` também, com `aqua seco ligabis` (espelho).
4. Coloque um balde de água numa delas (waterlogged): a outra deve ficar `waterlogged` também pouco depois.
5. Tire a água de uma: a outra seca também.

## 16. Adaptador do aura (implementado)

Fecha os quatro aspectos. Reaproveita `LigabisManager`/`castLink`, mas é o único que ganhou membros novos (itens no chão) e um jeito próprio de mirar neles.

**O que ele faz**
- **Feitiço `aura m1 ligabis [m2 ...]`:** igual aos outros três. **Sem blocos por enquanto** — mirar num bloco com aura falha com mensagem própria (o desenho original já previa isso: "Blocos ficam para depois").
- **Membros:** seres vivos (jogadores, mobs) e **itens largados no chão**. Um item largado não é "pickable" pro motor de mira (`SpellEffects.findImpact` já exige isso pra não confundir feitiços com itens no caminho), então o cast de aura tem um raio próprio que só procura itens, usado como último recurso antes de cair no auto-alvo.
- **Movimento a cada tick:** `tickAura` reporta a posição (x,y,z) de todo membro marcado ao motor via `observeMotion`. O motor compara com a posição anterior, decide o quanto cada um *deveria* ter andado (soma no espelho, forçado pelo pai na hierarquia) e devolve a diferença.
- **Aplicação:** a diferença vira um **empurrão de velocidade** (`setDeltaMovement` somado ao que já tinha), não um teletransporte. Se o empurrão não for totalmente realizado num tick (colisão, gravidade), a observação do tick seguinte pega a diferença e corrige de novo — converge sozinho em poucos ticks.

**Limites da primeira versão**
- **Sem blocos.** Ligar uma criatura a um bloco esperando o bloco seguir o movimento dela **não funciona ainda** — é o próximo passo natural, mas exige mover blocos via uma entidade "fantasma" (como o código antigo fazia com `FallingBlockEntity`), que é bem mais trabalho que os outros três aspectos.
- Mesmas limitações de marca de uma letra dos outros três.
- Correção não é instantânea: um filho "puxado" por um pai que acelera de repente leva 1-2 ticks pra alcançar, por desenho (evita teleporte brusco).
- Um filho com pais em duas marcas diferentes (dois vínculos de hierarquia distintos) só segue o último, na ordem estável do grafo — várias origens na *mesma* marca somam normalmente.

**Como testar no jogo**
1. Marque dois mobs com a mesma palavra via `aura vento ligabis` (espelho).
2. Ande com um: o outro deve se mover na mesma direção e ganhar velocidade dobrada se os dois andarem juntos, ou travar se andarem em sentidos opostos (é caótico por desenho).
3. Para hierarquia: `aura pai ligabis filho`, mirando um mob como pai e outro como filho.
4. Mova o pai: o filho segue exatamente o deslocamento dele. Pare o pai: se o filho tentar andar sozinho, ele é seguro no lugar.
5. Repita mirando um item largado no chão como filho (em vez de um segundo mob): ele deve flutuar seguindo o pai.

## 17. Ritual de golem (etapa 1 — mecânica, implementado; aparência final pendente)

Ideia do autor (21/09/2026): uma estrutura no formato do ritual de golem de ferro, mas feita de **qualquer bloco único**, vira uma entidade viva quando um vínculo `aura` hierárquico a liga a um ser vivo como pai. O golem passa a espelhar o movimento do pai (semântica normal do aura) e, quando o vínculo se rompe, volta a virar os blocos originais, parados onde o golem estava.

Combinado em conversa: a aparência final deve usar o **modelo real da espécie do pai** (porco vira porco, galinha vira galinha), sempre texturizado com o bloco usado. Como isso exige um modelo por espécie, o trabalho foi dividido em duas etapas — esta primeira valida a mecânica com uma aparência provisória (sempre o modelo do Golem de Ferro), e a segunda troca pelo modelo certo por espécie.

**Forma da estrutura** (`GolemStructure`): a mesma silhueta do golem de ferro vanilla — uma coluna de 2 blocos, uma fileira de 3 no topo dela (podendo se estender no eixo X ou Z) e um bloco de "cabeça" no centro, acima da fileira. Todos os 6 blocos precisam ser do **mesmo tipo**, qualquer um. Ao contrário do golem de ferro vanilla, **não há varredura passiva do mundo**: a forma só é checada no instante em que o mago lança `aura <marca> ligabis <marca2>` mirando no bloco do topo (a "cabeça").

**Peças** (`com.elderlexicon.mod.ligabis.world.golem`)

| Classe | Papel |
|---|---|
| `GolemStructure` | Detecção pura da forma (dado o bloco do topo, tenta os dois eixos) e os deslocamentos relativos de cada posição |
| `GolemEntity` | Estende `IronGolem` para herdar hitbox/modelo/animação; sem IA própria (`setNoAi(true)`) — quem move é só o vínculo aura; guarda o bloco de origem como dado sincronizado |
| `GolemRenderer` (cliente) | Estende `IronGolemRenderer`; só troca a textura por uma gerada a partir do bloco |
| `GolemTextures` (cliente) | Gera e cacheia uma textura 128x128 ladrilhando a textura do bloco (16x16) — sem tentativa de "vestir" o UV do modelo, por isso funciona melhor em blocos simples (terra, pedra, lã) que em blocos com padrão direcional (toras, estantes) |
| `LigabisClientInput` (cliente) + `PlayerMotionInputPacket`/`LigabisNetwork` | Reportam o `xxa`/`zza` do próprio jogador ao servidor sempre que muda — é a única forma de um golem saber que o jogador-pai está tentando andar quando o Minecraft não conta isso sozinho (ver correção abaixo) |

**O que acontece em `LigabisManager`**
- `castLink`, ao mirar um bloco com `aura`, tenta `GolemStructure.find`; se bater, destrói os 6 blocos e nasce um `GolemEntity` nos pés da estrutura, com a última marca do feitiço.
- A cada `tickAura`, além do movimento normal, `checkGolemsWithoutAParent` verifica se a marca do pai ainda tem algum portador vivo; se não tiver, o golem se desfaz.
- Quando um vínculo aura se rompe (dono sem UMU), `revertGolemOf` desfaz qualquer golem cujo vínculo era esse.
- Desfazer sempre recria os blocos originais na posição **atual** do golem (não na posição onde foi criado).

**Refinamento (22/09/2026):** além do deslocamento (regra normal do aura), o golem agora também copia a mira (`yRot`/`yBodyRot`/`yHeadRot`/`xRot`) e o gesto de ataque (`swing`) do pai, a cada tick. Isso é **específico do golem**, não uma mudança na regra geral do aura — um vínculo aura comum entre duas criaturas continua só deslocando, sem forçar a mira de uma na outra. A aparência provisória (modelo do Golem de Ferro) não tem uma animação de ataque própria ligada ao `swing` genérico, então esse gesto só fica visualmente completo a partir da etapa 2 (modelo real da espécie).

**Correção (22/09/2026), primeira tentativa (revertida):** o primeiro teste mostrou o golem completamente imóvel. Suspeita inicial: o atrito interno anulava o empurrão de velocidade num mob sem IA. "Corrigi" fazendo `applyDisplace` mover a posição do golem diretamente (`setPos`), o que resolveu a imobilidade, mas trocou o problema por outro: o golem passou a atravessar paredes e flutuar, porque teletransportar ignora colisão e gravidade — exatamente o oposto do desenho original ("de preferência como impulso de velocidade e não como teletransporte", seção 5).

**Causa real (confirmada lendo o bytecode do próprio Minecraft):** `Mob.isEffectiveAi()` retorna `false` sempre que `setNoAi(true)` está ativo, **mesmo no servidor**. O jogo usa esse retorno em `isControlledByLocalInstance()`, que por sua vez decide, dentro de `LivingEntity.travel()`, se a entidade recebe o processamento normal de física (gravidade, colisão, atrito) ou é tratada como "controlada remotamente" (e por isso ignorada). Ao desligar a IA do golem para ele não agir sozinho, a física dele também foi desligada sem querer.

**Correção da física:** `GolemEntity` agora sobrescreve `isControlledByLocalInstance()` para sempre retornar `true`, restaurando a física normal mesmo sem IA. `applyDisplace` voltou a ser só o empurrão de velocidade (`setDeltaMovement`), igual a qualquer outro membro do aura.

**Segunda rodada (22/09/2026): golem não é um membro comum do aura, é um boneco.** Depois da correção de física, o autor testou de um jeito mais exigente: vincular-se a um golem, ficar preso num cercado e tentar andar contra a cerca — o objetivo era o golem se mover mesmo com o próprio mago colidindo e não indo a lugar nenhum. Isso expôs a diferença entre dois modelos:
- **O que o aura comum faz** (e continua fazendo para vínculos normais): observa a posição resultante do pai a cada tick e força o filho a repetir o **deslocamento realizado**. Se o pai não anda (travado numa cerca), o deslocamento dele é zero, e o filho também não recebe nada.
- **O que o autor descreveu querer para o golem:** o filho não replica o resultado do movimento do pai, replica a **intenção** — "se o pai move a perna, o filho também" — e deixa a própria física do filho decidir se ele realmente anda.

Conferido no bytecode do próprio jogo: `LivingEntity.travel()` monta o vetor de movimento a partir dos campos públicos `xxa`/`yya`/`zza` (o quanto a entidade está tentando acelerar naquele tick, em relação à própria orientação — o mesmo campo que o teclado do jogador ou a IA de um mob preenchem), e só depois disso resolve colisão. É exatamente o sinal de "intenção" que faltava.

**Redesenho:** o golem saiu do cálculo de deslocamento do motor puro do aura (`observeMotion`/`Displace`) — ele nunca mais entra nessa lista de observação. Em vez disso, `puppetGolems` (que já cuidava da mira e do golpe) agora também copia `xxa`/`yya`/`zza` do pai a cada tick, direto. O resultado: o filho tenta o mesmo movimento que o pai tenta, e a colisão de cada um é resolvida pelo próprio corpo — o cercado do mago não afeta o golem, e um obstáculo na frente só do golem o para, mesmo com o pai livre.

**Efeito colateral aceito por ora:** como o golem não passa mais pelo `MotionRules`, o movimento dele deixou de cobrar UMU (o custo do aura vive dentro do cálculo de correção que ele não usa mais). Fica como pendência: decidir se o golem deve ter algum custo de manutenção próprio.

**Limitação nova:** pular não é copiado (o campo correspondente do jogo, `jumping`, não é público) — o golem sobe qualquer coisa que sua própria física deixe, mas não pula por vontade própria imitando o pai.

**Correção (22/09/2026): o golem virava, mas não andava.** Copiar `xxa`/`yya`/`zza` deixou a rotação funcionar, mas o movimento continuava travado. Causa, confirmada no bytecode: `LivingEntity.travel()` multiplica o vetor de movimento por `getSpeed()`, e esse valor vem do campo `speed`, que só é alterado por quem chama `setSpeed(...)` — normalmente a IA do mob, a cada tick, em função do próprio ritmo de andar. Sem IA, o golem nunca tinha `speed` ajustado, então ficava em 0 pra sempre: direção certa, potência zero. `puppetGolems` agora também copia `golem.setSpeed(parent.getSpeed())` a cada tick. Para um jogador, `getSpeed()` é o atributo de velocidade de movimento (sempre presente, não some quando ele para); para um mob comum, é o ritmo que a própria IA dele está definindo naquele instante (zero quando parado, maior andando) — os dois casos fazem sentido para o golem herdar.

**Correção (22/09/2026): mesmo com velocidade certa, o golem de um jogador continuava parado.** A causa era outra, mais funda: `xxa`/`zza` só são preenchidos no servidor pelo jogador quando ele está pilotando um veículo (`ServerPlayer.setPlayerInput`, chamado a partir do pacote de controle de barco/cavalo/carrinho). **Andando a pé, o cliente nunca manda essa intenção pro servidor — só manda a posição resultante.** Ou seja, `parent.xxa`/`parent.zza` de um jogador a pé são sempre 0; eu estava copiando uma fonte que nunca existiu. Isso não afeta um pai que é uma criatura comum (a IA dela preenche `xxa`/`zza` de verdade, via `MoveControl`).

**Solução:** um pacote de rede novo, cliente → servidor (`com.elderlexicon.mod.ligabis.network`), que o cliente manda toda vez que seu próprio `xxa`/`zza` muda — o mesmo valor que o jogo já calcula localmente pro seu próprio jogador, antes de qualquer colisão ser resolvida, então continua não-zero mesmo travado numa cerca. `LigabisManager.setPlayerInput` guarda o último valor reportado por jogador, e `puppetGolems` usa esse valor no lugar do `xxa`/`zza` do jogador quando o pai é um `ServerPlayer` (mantendo o valor direto do mob para pais que não são jogadores).

**Redesenho (22/09/2026): parar de coexistir com o sistema de movimento de mob e substituí-lo inteiro.** Depois de três rodadas de correções (`isControlledByLocalInstance`, velocidade zerada, entrada às vezes não convertendo em movimento visível) e ainda sem uma explicação sólida pro "anda um passo e trava" mesmo com sinal de entrada correto e contínuo, paramos pra repensar o método em vez de seguir corrigindo sintoma por sintoma.

A referência veio de como mods como o Valkyrien Skies e as contraptions do Create resolvem esse mesmo problema: eles não tentam fazer o sistema de IA de movimento do Minecraft fazer o que querem — eles o substituem inteiro por um laço de física próprio, e só usam o jogo para colisão de baixo nível e para desenhar o resultado. Fizemos o mesmo aqui:

- `GolemEntity.travel(Vec3)` agora é **totalmente reescrito**, sem nenhuma dependência de `xxa`/`zza`/velocidade/atrito do sistema de IA do jogo. Ele guarda um vetor de "intenção de movimento" (`moveX`/`moveZ`, velocidade final já pronta, no espaço do mundo) definido de fora, aplica uma gravidade simples e manual, e entrega tudo pronto pro `Entity.move()` — o método de baixo nível do próprio jogo que resolve colisão e sobe degraus automaticamente. É a mesma peça que o Minecraft usa por baixo dos panos; só cortamos fora a camada de conversão de entrada que vinha causando os problemas.
- `LigabisManager.puppetGolems` agora calcula a velocidade final no mundo **com a própria mão**, usando a mesma fórmula de rotação que o jogo usa internamente (`xxa`/`zza` locais, girados pela mira do pai), e entrega isso pronto via `GolemEntity.setMoveIntent`. Não sobra nenhuma dependência do sistema de IA de mob — nem `setSpeed`, nem os campos `xxa`/`zza`/`yya` do golem.

Isso deve ser bem mais previsível: não há mais nenhum comportamento escondido do jogo para descobrir, porque o único código rodando é o nosso.

**Histórico das tentativas anteriores (mantido para referência):**

**Correção (22/09/2026): segurando a tecla, o golem dava só um passo e parava.** O pacote só era reenviado quando o `xxa`/`zza` do jogador *mudava* — economiza tráfego, mas dependia de o valor ficar realmente estável enquanto a tecla continua pressionada, algo que não consegui confirmar com certeza sem testar num cliente de verdade. Removida essa otimização: `LigabisClientInput` agora manda o pacote a cada tick do cliente, sempre, incondicionalmente (poucos bytes, 20x/segundo — tráfego irrelevante). Isso garante que o servidor nunca fique com um valor parado por mais de 1/20 de segundo, seja qual for a causa exata do problema anterior.

**Limites desta etapa**
- **Aparência provisória:** todo golem usa o modelo do Golem de Ferro, não o do pai. É a etapa 2.
- **Não sobrevive a reinício do servidor:** o registro de qual golem pertence a qual vínculo vive só em memória. Um golem que existir quando o servidor reiniciar continua no mundo como entidade, mas para de se mover e nunca mais se desfaz sozinho — fica órfão. Não é um problema para testar agora, mas precisa de persistência antes de ir para produção.
- Só testei o lado servidor (o boot, a destruição dos blocos, o registro do tipo de entidade). A textura gerada e a renderização em si só um cliente gráfico pode confirmar.
- Vínculos em espelho (`aura marca ligabis`, uma marca só) também golemizam, mas sem "pai" — o golem nunca se desfaz sozinho por falta de pai, só quando o vínculo quebra por falta de pagamento.

**Como testar no jogo**
1. Construa a estrutura com qualquer bloco (ex.: terra): coluna de 2 blocos, fileira de 3 em cima (ao longo do eixo X ou Z), e mais um bloco de terra no centro, acima da fileira (a "cabeça").
2. Marque um porco como `pai` (Pincel Elder).
3. Escreva `aura pai ligabis filho` numa página e lance mirando na cabeça da estrutura.
4. A estrutura deve sumir e um golem (visualmente um Golem de Ferro, por enquanto) aparecer na base dela.
5. Ande com o porco: o golem deve seguir o deslocamento dele.
6. Mate o porco (ou deixe o vínculo quebrar por falta de UMU): o golem deve desmontar de volta em blocos de terra, parado onde estava.

## 18. Plano de testes (núcleo puro)

- Espelho e hierarquia propagam na direção certa; o filho nunca afeta o pai.
- Corrente m1 → m2 → m3 propaga em dois saltos.
- Sem eco: um reflexo não gera outro reflexo.
- Firmo: dano refletido com o valor final do original e sem armadura no destino; morte propaga a todos no espelho; a hierarquia só desce.
- Igni: a última ação reflete; empate acende.
- Custo: N−1 reflexos num espelho de N; vínculo quebra quando o custo não pode ser pago; suspende com o dono offline.
- Marca some com a morte ou destruição do membro; os restantes continuam.
- Ordem dos membros não muda o resultado.
