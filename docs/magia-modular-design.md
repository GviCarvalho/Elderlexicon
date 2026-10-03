# Magia modular: um léxico pequeno, feitiços pela lógica

> **Nota (docs/particulas-design.md, etapa 3, 30/09/2026):** todas as runas fundidas saíram da língua: as fontes
> (`vita`, `fusus`, `caligo`, `lutum`, `pulvis`, `nebula`, `fulmen`), as formas (`hasta`, `murus`...) e os verbos fundidos
> (`transiectio`, `aversio`...). Saíram com elas a classe "forma", a origem "fusão", os componentes e os atalhos que se
> desdobram, e o léxico recusa uma fusão que um addon traga. Onde este documento fala delas, vale só como história.

Este documento descreve como o sistema de magia lê e executa um feitiço depois do rework modular.
O princípio vem do grimório: **um dicionário de runas não tão extenso, mas com possibilidades infinitas de feitiços somente
através da lógica.**

- O motor não conhece o nome de nenhuma runa.
  - Ele conhece **classes de palavra** (fonte, verbo, filtro, forma) e **papéis** (o que um verbo faz com a energia, o que
    ele toma depois de si, onde junta o que gasta).
  - O que cada runa é fica em dados: `src/main/resources/data/elderlexicon/lexicon/runes.json`.
- Um feitiço vale o que suas palavras compõem.
  - Tudo o que o mago descreve com precisão e consegue pagar, o espírito executa.
  - O que ele não entende, ele diz o porquê (runa desconhecida, verbo sem alvo, fusão sem composição...).

O vocabulário de força e matéria (o `tenet`, o sinal que inverte um verbo, o sujeito vindo do verbo anterior, a escada de
estados, os códigos das coisas naturais e a mistura) está em `plano-materia-e-forca.md`; a matéria por emergência
(qualidades pela composição, reações dos opostos, nada de receita) em `plano-materia-emergente.md`.

Os casos de cada verbo continuam nos documentos próprios:
- `exsugat-vertere-design.md`: captura e conversão (histórico: o `exsugat` saiu, e o `tenet` faz a captura).
- `condensacao-design.md`: intensidade.
- `marcas-como-runas-design.md`: marcas, sujeito, `ubis` (o `transvocatio` de lá saiu).
- `surgit-visao-design.md`: visão.
- `ligabis-design.md`: vínculos.

## 1. Camadas

| Camada | Onde | O que sabe |
|---|---|---|
| **Léxico** (dados) | `magic/lexicon`, `runes.json`, `foci.json` | O que cada palavra é: classe, glifo, essência, traços, operação, custo, textos. |
| **Gramática** | `magic/grammar/SpellGrammar` | Como palavras viram ações, só por classe e quadro de objeto. |
| **Fluxo** | `magic/flow/FlowInterpreter` | Por onde a energia passa: gastar, capturar, converter. Não conhece Minecraft. |
| **Matéria** | `magic/matter`, `materials.json` | Composição dos quatro, estados, UMU, qualidades, os códigos das coisas naturais e as leis L1 a L5. Não conhece Minecraft. |
| **Ponte da matéria** | `spell/matter/WorldMatter`, `FormlessMatterBlock` | Lê o mundo como matéria e põe matéria nele, misturando o que é fluido. |
| **Mundo** | `spell/action/SpellActionExecutor`, `spell/function/*` | Como cada **operação** acontece no jogo. |
| **Textos** | `SpellReading`, `SpellDescription`, `Parser` | A leitura do grimório e a transcrição, vindas dos `texts` e `notes` do léxico. |

- **Léxico.**
  - `LexiconReader` lê o JSON.
  - `LexiconBuilder` monta e **valida** o léxico:
    - glifo repetido, ou glifo que se leria como número;
    - componente ou expansão que não existe;
    - ciclo de abreviações;
    - fonte padrão e repertório inválidos.
  - `Lexicon` responde às perguntas.
  - `Lexicons.get()` é o léxico em vigor: o do mod mais as extensões de addons.
- **Gramática.** `SpellGrammar` faz o trabalho do antigo `SpellActionEngine`, que agora só delega.
  - Cada verificação que antes era `"vertere".equals(rune)` virou uma pergunta ao léxico, como "este verbo converte?" ou
    "toma uma marca como alvo?".
- **Fluxo.** O interpretador trabalha só por duas interfaces:
  - `SpellLedger`, a conta do feitiço, implementada por `SpellContext`;
  - `FlowWorld`, o que o mundo faz, implementado dentro do `SpellActionExecutor`.
  - Por isso ele é testável sem o jogo (`FlowInterpreterTest`).
- **Mundo.** Cada verbo nomeia no JSON a **operação** que o mundo executa. `SpellFunctionHandlerRegistry` liga cada
  operação a um handler:

  | Operação | Handler |
  |---|---|
  | `project` | `IactareFunctionHandler` |
  | `manifest` | `VocantFunctionHandler` |
  | `repel` | `ImpediuntFunctionHandler` |
  | `name` | `ReframeFunctionHandler` |
  | `bind` | `LigabisFunctionHandler` |
  | `perceive` | `SurgitFunctionHandler` |
  | `convert` | `MarkVertereFunctionHandler` |

  - Dois verbos podem compartilhar uma operação. Um verbo de addon que diga `"operation": "project"` é lançado como o
    `iactare`.
  - `SourceLooks` transforma os traços de uma fonte em blocos e partículas do jogo, pelos ids dos registros do Forge.

## 2. Como um feitiço é lido

### Fontes

- Uma fonte é uma **mistura dos quatro aspectos** (`essence`).
  - `igni` é fogo puro.
  - `fusus` é metade fogo, metade terra.
  - `vis` tem os quatro em equilíbrio.
- Ela segue as leis do aspecto que declara em `element`. Sem `element`, segue o aspecto de que tem mais.
- Os **traços** (`traits`) dizem como ela se mostra no mundo: se fica (água, terra, lama, magma) ou age e vai embora,
  que bloco deixa, se sopra, se acende fogo, se cai como raio, que partícula mostra, quanto tempo acende uma fornalha...
  - O código do mundo pergunta pelos traços, nunca pelo nome.
  - Uma fonte nova (uma fusão, ou uma de addon) se comporta pelo que declara.
- `bond` diz o que um `ligabis` liga quando a fonte é escrita como aspecto do vínculo:
  - `firmo`: integridade;
  - `igni`: calor;
  - `aqua`: respiração;
  - `aura`: movimento;
  - `vis`: leitura.

### Verbos

Cada verbo declara:

- **`operation`**: o que o mundo executa.
- **`flow`**: o que ele faz com a energia no fluxo. É isso que o interpretador lê.
  - `spend` (padrão): gasta a energia da fonte. Lançar, invocar, repelir...
  - `convert`: muda a fonte em outra. É o `vertere`. A fonte escrita depois dele é o alvo, não uma fonte a gastar.
- **`cost`**: o custo base em UMU.
- **`reversible`**: uma quantidade negativa o vira ao contrário (`iactare` puxa, `impediunt` atrai, `vocant` traz até
  quem conjura). Nos outros verbos, o espírito recusa o sinal.
- **`transfers`**: ele move matéria em vez de gastar energia. Com `tenet`, carrega o que tira do mundo como é, em vez de o
  fluxo capturar como energia. É o `vocant`.
- **`gathering`**: onde se junta o que ele gasta.
  - `hand`: diante da mão, para o que é lançado.
  - `destination`: no ponto onde vai surgir.
  - `body`: no corpo.
- **`object`**: o que ele toma depois de si.
  - `takes`: `source`, `mark` e/ou `name`.
  - `as`: `target` (alvo), `subject` (sujeito) ou `name` (nome).
  - `immediate`: a palavra tem que vir logo em seguida.
  - `markNeedsMarkedSubject`: só toma uma marca se o sujeito também for marca (`m1 vertere m2`).
  - `optionalWithSubject`: pode faltar quando o sujeito é marca.
  - `measure`: o que um `quantum` escrito depois do sujeito mede (visibilidade, no `surgit`).
  - `refusal`: o que o espírito diz quando se escreve uma fonte onde só cabe marca.
- **`beforeVerb: view`**: escrito logo antes de outro verbo, só muda com o que aquele verbo trabalha. É o `surgit` antes
  de um `vocant`: invoca a imagem.
- **`sense`**: o sentido que ele liga num vínculo. É o `surgit m1 ligabis`: a visão.
- **`binds`**: ele liga. Lê um aspecto e as marcas em volta.

### Filtros

- Cada filtro ajusta um **parâmetro** do verbo seguinte:
  - `quantity`, a quantidade;
  - `time`, o tempo;
  - `place`, o lugar;
  - `origin`, de onde vem a fonte: é o `tenet`. Sem ele, a fonte vem do corpo; com ele, do mundo ao alcance, ou de perto
    das marcas e números escritos antes dele (`firmo m1 tenet`).
- O **argumento** pode ser:
  - `value`: um número. Para quantidade, diferente de zero (negativo só num verbo `reversible`); para tempo, não
    negativo;
  - `operands`: números e marcas antes dele, como `m1 ubis` ou `10 ubis`.
- `bare: all` quer dizer que, escrito sem número, o filtro pede tudo o que há: `firmo tenet quantum iactare` (toda a terra ao alcance).

### O sujeito continua

Uma marca escrita antes de um verbo é o **sujeito** dele e dos verbos seguintes, até outra fonte ou marca tomar seu lugar.
Sem marca, um verbo escrito depois de outro age sobre o que aquele produziu ou moveu (`igni vocant iactare`: o fogo que
surge é o que se lança).

- `m1 vocant iactare`: traz o que tem a marca m1 e depois o lança.
- `m1 vertere m2 vocant`: o que tinha m1 passa a ter m2, e é m2 que se invoca.
- `m1 vertere aqua iactare`: convertido em água, é água que se lança.

### Fusões

- **Fontes fundidas** são misturas.
  - `fusus` (igni + firmo), `lutum` (aqua + firmo), `fulmen` (aura + igni)...
  - Elas seguem as leis do aspecto declarado e os próprios traços.
- **Fusões de verbos são abreviações.** A fusão se escreve no lugar das runas que funde, e `@` marca onde entra a palavra
  seguinte:

  | Abreviação | Equivale a |
  |---|---|
  | `transiectio` | `vertere @ iactare` |
  | `aversio` | `vertere @ impediunt` |

  - `igni transiectio aqua` é exatamente `igni vertere aqua iactare`, com o mesmo custo.
  - O foco da varinha também favorece a abreviação como as runas que ela representa.
  - Uma abreviação pode conter outra, até 8 níveis. Um ciclo é recusado quando o léxico é montado.
- **Fusões ainda sem composição** respondem que a fusão ainda não tem composição no léxico e pedem que se escrevam as
  runas que ela funde: `cohaesio`, `deflectio`, `vinculatio`, `evocatio`, `compeditio` e `coniuratio`.
  - Dar sentido a uma delas é só escrever o `expands` dela no JSON.
- **Palavras aposentadas** (`exsugat`, `transvocatio`, `orbis`, `exhaustio`, `exsuctio`, `extractio`, `exinanitio`)
  ficam no `retired` do léxico: o espírito as reconhece e diz o que escrever no lugar.
- **Qual fusão duas runas fazem** (`Fusions.fuse`, usado pelo `reframe`) vem dos `components`.
  - A ordem não importa: `igni firmo` e `firmo igni` fazem `fusus`.
  - `source` quer dizer qualquer fonte, como em `hasta` = `iactare` + qualquer fonte.
  - Quando duas fusões têm as mesmas partes (`hasta` e `cuspis`), vale a primeira do léxico.

### Custo

| Parte do feitiço | Custo |
|---|---|
| Fonte escrita | 1 UMU. |
| Verbo | O `cost` dele no léxico: `iactare` 2, `vocant` 1, `vertere` 1, `ligabis` 0,1. |
| Energia do efeito (`quantum N`) | Soma-se ao custo e é paga como o resto (XP, saturação, HP), a menos que o `tenet` a tire do mundo. |
| Conversão | Além do custo do `vertere`, o trabalho do espírito tira 5% da energia convertida por degrau da escada de estados (no máximo 90%). |
| Matéria movida (`tenet vocant`) | O mundo dá a matéria; o mago paga só o trabalho do verbo. |

- Focos e madeiras de varinha descontam o que favorecem. Isso está em
  `src/main/resources/data/elderlexicon/lexicon/foci.json`.
  - `discounts`: a parte do custo de cada runa que o foco carrega.
  - `conversions`: a madeira carrega o custo de toda conversão para aquelas fontes. É o caso de `crimson` (igni e aura)
    e `warped` (firmo e aqua).

## 3. O arquivo do léxico

Toda chave, fora a `class` de cada runa, é opcional. As chaves do antigo `ParserList.json` (`type`, `fusionOf`,
`origin: original`) ainda são lidas.

```json
{
  "defaultSource": "vis",
  "repertoire": ["igni", "aqua", "aura", "firmo", "impediunt", "vertere", "vocant", "murus", "iactare"],
  "notes": {"note.conversion": "..."},
  "meetings": [{"elements": ["aqua", "aura"], "name": "Tempestade", "note": "..."}],
  "remove": ["runa-que-um-addon-tira"],
  "runes": {
    "igni": {"class": "source", "glyph": "C", "essence": {"igni": 1}, "bond": "igni",
             "traits": {"kindles": true, "image": "minecraft:fire", "reveals": "igni"}},
    "iactare": {"class": "verb", "glyph": "I", "operation": "project", "cost": 2, "gathering": "hand"},
    "vertere": {"class": "verb", "glyph": "H", "operation": "convert", "flow": "convert", "cost": 1,
                "object": {"takes": ["source", "mark"], "as": "target", "immediate": true,
                           "markNeedsMarkedSubject": true}},
    "quantum": {"class": "filter", "glyph": "N", "parameter": "quantity", "argument": "value", "bare": "all"},
    "hasta": {"class": "form", "origin": "fusion", "components": ["iactare", "source"], "form": "spear"},
    "transiectio": {"class": "verb", "origin": "fusion", "components": ["vertere", "iactare"],
                    "expands": ["vertere", "@", "iactare"]}
  }
}
```

- **Todas as runas.**
  - `glyph`: letra do alfabeto SGA. Só as runas primordiais têm glifo. As páginas já escritas continuam lendo igual.
  - `translation`, `name`, `noun`, `lore`.
  - `origin`: `primordial` ou `fusion`.
  - `components`: as partes de uma fusão.
  - `expands`: a abreviação.
  - `texts`: frases da leitura do grimório e da descrição, com `{variavel}`, `{Variavel}` (com maiúscula) e
    `{a|b}` (usa `b` se `a` faltar).
- **Fontes.**
  - `element`, `essence`, `bond`.
  - `traits`:
    - `persistent`, `matter`, `image`;
    - `wind`, `windStrikes`, `kindles`, `touches`, `strikes`, `quenches`;
    - `particle` (aceita `block:<id>`), `glow`, `burnTicks`;
    - `reveals`.
- **Verbos.** `operation`, `flow`, `cost`, `gathering`, `object`, `beforeVerb`, `sense`, `binds`, `phrase`, `joiner`.
- **Filtros.** `parameter`, `argument`, `bare`.
- **Formas.** `form`.
- **Nível do arquivo.**
  - `defaultSource`: a fonte de um feitiço que não escreve nenhuma.
  - `repertoire`: as runas com que um mago começa.
  - `notes`: textos gerais.
  - `meetings`: o que acontece quando dois aspectos se encontram.

`docs/Fusionslist.json` fica como referência histórica. Quem manda é o `runes.json`.

## 4. Como um addon estende a magia

1. **Palavras novas por dados**, com o mesmo formato do arquivo do mod:
   ```java
   Lexicons.extend(words -> words.read(reader));
   ```
   - Uma fonte, verbo, filtro ou abreviação nova passa a ser lida, explicada no grimório, custeada e executada pelas
     mesmas regras das runas do livro.
   - O `CompositionTest` mostra isso com um gelo (`glacies`), um verbo (`proicere`), um filtro (`tempus`) e abreviações.
2. **Palavras novas em código:**
   ```java
   Lexicons.extend(words -> words.rune(Rune.builder("umbra", WordClass.SOURCE).source(spec).build()));
   ```
   - Uma extensão que quebre o léxico (glifo repetido, componente inexistente, abreviação circular) é **recusada na
     hora** e não deixa rastro.
3. **Uma operação nova**, para quando o que se quer não é lançar, invocar, converter, repelir, ligar, ver ou nomear:
   ```java
   SpellFunctionHandlerRegistry.register("minha_operacao", handler);
   ```
   - O verbo do addon declara `"operation": "minha_operacao"`.
4. **Uma lei de intensidade:**
   ```java
   IntensityLaws.register(VitaElement.X, lei);
   ```
   - Ela diz quão intensa fica a energia de um aspecto quando é solta de uma vez.
5. **Focos:** as entradas de `foci.json`. Os itens de varinha leem os descontos por id.
6. **Coisas naturais novas**, com código (a composição), estado natural, unidade e formas, no formato do `materials.json`:
   ```java
   Materials.extend(table -> table.read(reader));
   ```
   - Uma extensão que quebre a matéria (código perto demais de outro, bloco lido como duas coisas, falta de uma
     primordial) é recusada.

## 5. O que continua em código

São as **leis da natureza** dos quatro aspectos. Elas valem para toda fonte que segue aquele aspecto, e nenhuma runa é
nomeada nelas:

- o que cada elemento faz quando é solto condensado (`IactareFunctionHandler`, `VocantFunctionHandler`): o golpe de
  calor, a rajada de ar, o jato de água ou o gelo, o bloco de terra com peso (ou o buraco negro), a vis mostrando os
  quatro de uma vez;
- as penalidades de vazamento por elemento (`UmuLeakHandler`);
- que blocos e coisas do mundo contam como fonte de cada aspecto quando o `tenet` captura energia, e quanto cada um
  rende (`WorldSources`);
- a partícula padrão de cada elemento (`SpellEffects`);
- os quatro aspectos e a vis (`VitaElement`);
- o que cada **operação** faz no jogo (os handlers). Um verbo novo que reuse uma operação não precisa de código. Um
  **tipo** de efeito que ainda não existe precisa de um handler.

## 6. Compatibilidade

- Os glifos são os mesmos, então páginas, pergaminhos e círculos já escritos leem igual.
- Os 159 feitiços que já funcionavam antes, tirados do `GRIMOIRE_REFERENCE.md`, dos documentos e dos testes, foram lidos
  pelo motor antigo e pelo novo.
  - Todos dão a mesma leitura.
  - A única exceção é o texto da reclamação de um vínculo de sentido sem marca, que agora serve para qualquer verbo de
    sentido.
  - O catálogo fica em `src/test/resources/spell-catalogue.txt`, com as mudanças intencionais no fim, e é verificado pelo
    `SpellCatalogueTest`.
- **Mudanças intencionais:**
  - o sujeito persiste entre verbos;
  - as fusões de verbos com composição passaram a funcionar.
- O antigo `ParserList.json` saiu. `ParserDictionary` virou uma vista do léxico.
- Saíram também:
  - o código morto do Ligabis antigo (`LigabisLinkManager`, `LigabisEvents` e o caminho do mapa `LINKS` no
    `LigabisFunctionHandler`), como o `ligabis-design.md` (seção 8) previa;
  - os mapas de custo, glifo e desconto espalhados pelos itens e handlers.

## 7. Testes

- O núcleo não depende do Minecraft. Ele é testado pelos testes de `src/test/java/com/elderlexicon/mod/magic`:
  - `LexiconTest`, `CompositionTest`, `FlowInterpreterTest`, `SpellCatalogueTest`, `FociTest`, `FusionsTest`;
  - os testes que já existiam, da gramática, da leitura do grimório e do custo.
- A matéria tem os seus (`magic/matter`: `MaterialTableTest`, `MatterLawsTest`, `PlacementTest`).
- Os testes do lado Minecraft (`SpellActionExecutorTest`, `SpellContextTest`, `SpellFunctionHandlerRegistryTest`...)
  rodam no `./gradlew test`.
- Os GameTests (`gametest/MatterGameTests`, `gametest/SpellGameTests`) rodam num servidor de verdade com
  `./gradlew runGameTestServer`, e lançam feitiços com um mago falso.
