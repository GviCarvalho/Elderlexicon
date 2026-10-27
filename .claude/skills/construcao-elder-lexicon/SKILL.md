---
name: construcao-elder-lexicon
description: Princípios de construção em Minecraft e o estilo do Elder Lexicon (universidade, campus, torres de mago, vilas). Use SEMPRE antes de desenhar, mudar ou revisar qualquer estrutura do mod (Masonry, CampusBuildings, UniversityPiece, CampusPiece, TowerPiece, templates .nbt) ou de discutir a arquitetura de um prédio. Traz o fluxo de rascunho e preview com a minecraft-builder-skill, a paleta da casa e o checklist de revisão.
---

# Construção no Elder Lexicon

O objetivo é que cada prédio pareça **feito por alguém**: tenha profundidade, um telhado de verdade, uma paleta coerente
e um lugar no terreno. Uma caixa de um bloco só com telhado chapado é o erro a evitar. Esta skill é o "gosto"; a
`minecraft-builder-skill` (ao lado, em `.claude/skills/minecraft-builder-skill`) é o "olho".

## 1. Fluxo de trabalho

1. **Ler antes de desenhar.** Leia o design doc do prédio (`docs/universidade-arquitetura-design.md`,
   `docs/mago-design.md`, etc.) e, se houver lore envolvida, o livro (`livro/*.md`). Lembre: o livro não é literal
   para o mod (no mod não há elfos; no livro não há Illagers).
2. **Esboçar a planta em texto**: as dimensões, os volumes (corpo principal, alas, torres), a altura de cada andar, o
   tipo de telhado e a paleta (seção 3). Decida por que o prédio tem essa forma: o que se faz dentro dele.
3. **Rascunhar em JS e ver.** Escreva uma receita no scratchpad usando `masonry.mjs` (deste diretório), o gêmeo do
   `Masonry` do mod: as mesmas coordenadas (offset do meio, `y = 0` é a grama) e as mesmas funções (`set`, `fill`,
   `stone`, `chance`, `door`, `chandelier`, `stairs`). Então:

   ```bash
   cd .claude/skills/minecraft-builder-skill
   node scripts/run-build.mjs <scratchpad>/recipes/predio.mjs --out-dir <scratchpad>/builds/predio
   node scripts/preview.mjs --nbt <scratchpad>/builds/predio/predio.nbt --no-open --port 5174   # em background
   ```

   Abra a URL que ele imprime no navegador do app (`preview_start` com `url`, depois `navigate`), espere uns 7 s e
   tire screenshots. **Não gire com o mouse**: no painel do app a imagem se desconfigura depois de arrastar ou rolar.
   Ponha a câmera na URL (patch local no visualizador): `&yaw=<graus>&pitch=<graus>&dist=<blocos>` e, se quiser,
   `&cx=&cy=&cz=` (o alvo, em coordenadas da estrutura, a partir do canto). Recarregue para cada vista. Veja pelo menos
   3 ângulos: frente (`pitch=6`), um canto a 45° (`pitch=25–35`) e de cima (`pitch=70`). Se o screenshot der timeout,
   repita com `zoom` na região `[0,0,800,600]`, que costuma passar. Use `&lighting=night` para conferir a iluminação.
   A grama não aparece no visualizador (só a borda do chão) e a pedra sai rosada: é o renderizador. **Um sino
   (`minecraft:bell`) trava o carregamento** ("Loading build..." para sempre): deixe-o fora do rascunho
   (`if (!process.env.NOBELL)`) e ponha só no Java. Mantenha um prédio
   por preview: construções de 100 mil blocos não carregam. Para várias partes, um servidor por porta.
4. **Criticar com o checklist** (seção 5), corrigir a receita, regerar, ver de novo. Pelo menos uma volta de crítica
   antes de mostrar.
5. **Portar para Java** no estilo do arquivo (`CampusBuildings`, `UniversityPiece`...): cada `m.set`/`m.fill` vira o
   mesmo chamado em `Masonry`, com `BlockState`s em vez de strings. Os comentários no estilo do código do mod (frases
   curtas, em inglês, falando do que o prédio é).
6. **Só compilar** (`gradlew compileJava`), e **antes ver se o jogo está aberto** (processo `java` do cliente dev):
   recompilar com o jogo aberto derruba o cliente. Quem testa no jogo é o autor; depois leia os logs.

Os rascunhos ficam no scratchpad, não no repositório. Se um prédio virar template `.nbt` de verdade (em
`src/main/resources/data/elderlexicon/structure/`), gere com o `DataVersion` padrão (3465, a 1.20.1 do mod).

## 2. Princípios

**Volume e silhueta**
- Nada de caixa única. Quebre o volume: corpo principal + ala(s), um torreão, um alpendre, um anexo mais baixo. A
  silhueta contra o céu deve ser reconhecível de longe (uma agulha, uma cúpula, um campanário).
- Proporções: andares de 4–5 blocos (pé-direito de 3–4 + piso); salões nobres de 7–12. Larguras e comprimentos
  **ímpares** quando há eixo de simetria (porta, nave, janela central no meio de verdade).
- Simetria para o que é oficial (o Salão, a fachada do Prédio das Aulas); assimetria controlada para o que é de uso e
  para as casas da vila.

**Profundidade nas paredes** (a regra que mais muda o resultado)
- Uma parede nunca é um plano só. Estrutura (pilares, vigas, contrafortes) fica **1 bloco à frente** do enchimento.
- Pilares nos cantos e a cada 3–5 blocos; uma viga horizontal entre andares; janelas **recuadas** 1 bloco com peitoril
  de escada/laje embaixo e verga em cima.
- Use escadas e lajes (inclusive de ponta-cabeça), muros, trapdoors e botões como relevo: frisos, mísulas sob beirais,
  molduras de janela.
- Base mais pesada e escura que o resto (embasamento de pedra de 1–2 blocos, às vezes alargado como rodapé).

**Telhados**
- Sempre com **beiral**: o telhado sai 1 bloco além da parede, com escadas de ponta-cabeça por baixo da borda.
- Inclinação: escadas inteiras (45°) para o comum; alterne escada + laje para telhados mais baixos; escadas empilhadas
  para torres e agulhas íngremes. Telhado de duas águas com **empena** fechada (preenchida com outro material ou
  enxaimel), ou de quatro águas com cantos `shape=outer_*`/`inner_*` certos.
- Cumeeira com acabamento (lajes, muros ou blocos de outra cor). Água-furtada/lucarna em telhados grandes.
- Telhado de material diferente da parede (ardósia: deepslate tiles/escuros; madeira escura; cobre que verdeja).

**Paleta**
- 3 a 5 blocos por prédio: um principal, um de estrutura, um de detalhe, um de telhado, um de acento.
- Gradiente e textura: misture variações do mesmo material por `chance()` (como `Masonry.stone`: musgo e rachado de
  vez em quando), mais musgo/desgaste perto do chão, mais limpo em cima.
- Contraste de valor: estrutura mais escura que o enchimento (ou o contrário), telhado contrastando com a parede.

**Terreno e entorno**
- O prédio senta no chão: fundação descendo até o sólido (`ground()` em `CampusBuildings`), escadaria ou rampa até a
  porta, sem blocos flutuando nem buracos embaixo.
- Entorno: caminho até a porta, poste de luz, um canteiro, uma árvore, um banco, barris e caixotes junto à porta de
  serviço. Isso é o que faz o lugar parecer vivo.

**Interior**
- Cada cômodo tem uma função e móveis que a mostram (a mesa, a estante, a bancada). Nada de sala vazia.
- Luz suficiente para não nascer monstro (nível ≥ 8 em todo piso, na 1.20.1 monstros nascem no 0): lanternas
  penduradas (`chandelier`), luminárias em paredes, velas nas mesas. Esconda luz sob tapetes/em pisos quando couber.
- Circulação: portas de 2 de altura, escadas com 3 de altura livre, corredores de 2–3 de largura nos prédios públicos.

**Detalhe e vida**
- Os mesmos detalhes repetidos ritmam a fachada; detalhes únicos marcam o importante (o portal, o brasão).
- Ruído em escala certa: trepadeiras, musgo e rachaduras pontuais, não uniformes.
- Tudo determinístico pela posição (`chance()`), nunca `Random` solto: os chunks têm de concordar.

## 3. O estilo da casa

O campus tem **cara de castelo-escola de magia** (`docs/universidade-arquitetura-design.md`): torres, agulhas,
claustros, pontes; muralha só no núcleo. A vila em volta é **uma vila do jogo, do bioma**.

| Uso | Principal | Estrutura | Detalhe | Telhado | Acento |
|---|---|---|---|---|---|
| Núcleo (Salão, Biblioteca, muralha) | stone bricks (+musgo/rachado) | polished andesite / stone brick wall | stone brick stairs/slabs | deepslate tiles | vitral (stained glass), lanternas |
| Prédios de uso do campus | stone bricks no térreo, enxaimel em cima | dark oak / spruce log | spruce trapdoors e stairs | deepslate tiles ou dark oak | lanternas, bandeiras de lã do curso |
| Laboratório, estufa | stone bricks | cobre (oxidando) | vidro | cobre cortado | caldeirões, frascos |
| Observatório, torres de mago | stone bricks / deepslate bricks | polished deepslate | escadas de deepslate | agulha ou cúpula | ametista, vidro roxo (o sonho), velas |
| Dormitório do sonho | — | — | — | — | **vidro roxo** no andar silencioso |

Ao inventar fora da tabela, mantenha a família: pedra cinza com musgo, madeira escura, telhado escuro, luz quente.
Glifos e runas usam só as formas canônicas (SGA e os glifos do mod), nunca formas novas.

## 4. Do rascunho ao Java

- **Nunca mude a cara de uma peça que já gera em mundos existentes** (`UniversityPiece` e o seu `Hall`, as torres):
  os chunks gerados depois não casariam com os de antes. Faça uma classe nova para o que é novo, como `CampusHouse`
  para o campus.

- `masonry.mjs` não gira o campus nem corta por chunk: o rascunho é um prédio, sem rotação. No Java, `Masonry` cuida
  disso; desenhe nas facings "sem rotação" que o `set` gira tudo.
- `chance()` do rascunho usa a fórmula do `Mth.getSeed`, mas sobre o offset e não sobre a posição do mundo: o
  desgaste no jogo cai em outros lugares, com a mesma densidade.
- Cercas, painéis, grades e muros: no rascunho o runner já liga os vizinhos; no Java, chame `m.join()` no fim.
- Blocos com entidade (baú com loot, atril com livro) vêm do mod (`m.chest`, o atril): no rascunho, ponha o bloco
  simples para ver o lugar.

## 5. Checklist de revisão (rodar em cada screenshot)

- [ ] **Simetria:** janelas, contrafortes e lucarnas contados a partir do meio de cada parede (não do canto), a porta
      e o portal no eixo; nada de peitoril, contraforte ou janela caindo na frente de uma porta.
- [ ] A silhueta tem algo reconhecível? O volume está quebrado (nada de caixa única)?
- [ ] As paredes têm profundidade (pilares/vigas à frente, janelas recuadas)? Algum plano liso grande demais?
- [ ] O telhado tem beiral, cantos certos, cumeeira acabada e empenas fechadas? Nenhum buraco visto de cima?
- [ ] Paleta de 3–5 blocos, com contraste e um gradiente de desgaste?
- [ ] O prédio senta no chão (fundação, escada até a porta)? Nada flutuando?
- [ ] Portas de 2 de altura, escadas transitáveis, circulação clara? **Rode o caminhador** (`walk.mjs` deste
      diretório) sobre o rascunho com os interiores portados: da entrada, cada sala, porta interna e patamar de escada
      tem de ser alcançado. Os erros clássicos: estante ou peitoril na frente de uma porta, porta que abre para baixo
      de um lance de escada (o enchimento sob os degraus tampa a passagem), porta que abre sobre o vão da escada.
      Confira também que **corredores e passagens estão livres de parede a parede** na altura do corpo (uma laje de
      cima ou uma escada de ponta-cabeça bloqueia; só a de baixo é degrau).
- [ ] **Paredes compartilhadas:** onde um volume encosta em outro (ala no bloco central), a parede não ganha janela,
      peitoril, contraforte nem mísula para dentro do vizinho (`CampusHouse.draw(m, vizinhos...)`).
- [ ] **Divisórias até o teto** de cada andar (blocos de alturas diferentes têm tetos diferentes), e toda lanterna
      pendurada com algo em cima (`chandelier` desde o teto, nunca `hanging` solto no ar).
- [ ] **Nada que dependa de ler o mundo** para decidir onde pôr mobília: o `Masonry` só vê o chunk atual. Portas e
      vãos vêm de listas fixas.
- [ ] **Avenidas encontram a porta no nível dela:** a ponta da rua que chega a um prédio tem a altura do chão dele,
      e o ramal para antes dos postes da entrada (a rua limpa 4 blocos acima de si).
- [ ] Sino sobre bloco inteiro (numa laje ele flutua).
- [ ] **Na geração do mundo, nada que avise o mundo:** os blocos-entidade ainda não têm `level` (placa com
      `setText`, qualquer `setChanged`/`markUpdated` derruba o jogo). Escreva os dados como um bloco salvo é lido
      (`saveWithoutMetadata` → mexe no NBT → `load`), e entidades só no chunk onde ficam.
- [ ] **Parede redonda:** nada de peitoril ou verga para fora (fica laje torta e solta); a janela se emoldura na
      própria parede. Escadas cabem como lances retos em vaivém dentro do semicírculo.
- [ ] **Camas:** `FACING` aponta do pé para a cabeceira; se não bater com as metades, o jogo desmancha a cama no
      primeiro update ao lado.
- [ ] **Fogo tem chaminé ligada a ele** (lareira → cano por dentro de uma parede → topo acima do telhado), e todo
      espaço fechado tem porta ou é maciço (torre sem porta é caixa escura onde nasce monstro).
- [ ] **Salas com mobília em fileiras** (bancos, mesas): um corredor livre da porta até a frente, sem passar por cima
      de banco; o atril fora da linha da porta.
- [ ] **Escadas com folga:** 2 de largura nos prédios públicos, um vão para passar ao lado, uma lanterna em cada
      patamar (o vão de escada fechado fica escuro).
- [ ] Iluminação suficiente dentro (confira com `--lighting night`)?
- [ ] Facings certos (escadas, portas, lanternas penduradas, troncos com `axis` certo)?
- [ ] Entorno: caminho, luz, um detalhe de vida?
- [ ] Combina com o resto do campus e com o design doc?

Ao mostrar ao autor, mande os screenshots do preview junto, diga o que mudou e o que ainda fica para ver no jogo.
