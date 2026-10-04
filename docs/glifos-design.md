# Glifos: como o alfabeto do mod cresce

**Status:** implementado (04/10/2026). Glifos do mod até agora: `languet` (U+E010), `mergitur` (U+E011), `aut`
(U+E012) e `non` (U+E013).

O alfabeto SGA tem 26 letras, e elas acabaram: A–P são as runas do livro, Q–Z as condições. `languet` e `mergitur`
já nasceram sem glifo, e mais runas virão. Este plano diz onde os glifos novos moram, como se desenham, como entram no
jogo e como um addon traz os seus.

## 1. O que já existe

- A fonte `elderlexicon:sga` (`assets/elderlexicon/font/sga.json`) junta as letras do jogo (`minecraft:alt`) e os
  glifos próprios do mod (`textures/font/sga_glyphs.png`: os dígitos em U+E000 a U+E009, e as runas a partir de
  U+E010).
- Uma runa diz seu glifo no léxico (`"glyph"`), e o motor só exige que ele seja **um caractere** (`Glyphs`). Um
  caractere da Área de Uso Privado do Unicode (U+E000 a U+F8FF) serve tão bem quanto uma letra.
- Os pergaminhos destacados pintam as runas num mapa (`SpellMapHelper`) pelos bitmaps de `GlyphBitmaps`: as letras
  copiadas pixel a pixel da textura do jogo, e os glifos do mod lidos da folha que vai no `.jar`.
- O Minecraft **soma** as definições de uma mesma fonte vindas de todos os mods e pacotes de recursos
  (`FontManager.listMatchingResourceStacks`). Um addon que traga o seu `assets/elderlexicon/font/sga.json` acrescenta
  glifos à fonte do mod sem substituí-la.

## 2. Onde os glifos moram

Cada glifo novo é um caractere da Área de Uso Privado, desenhado só na fonte `elderlexicon:sga` (nenhum outro texto
do jogo usa esses caracteres, então não há conflito com outros mods).

| Faixa | Para quê | Folha |
|---|---|---|
| U+E000–E00F | dígitos (0–9) e reserva | `sga_glyphs.png`, 1ª linha |
| U+E010–E0FF | runas do mod (240 lugares) | `sga_glyphs.png`, 128×128, grade 16×16 de células 8×8 |
| U+E100–E7FF | runas do mod, folhas seguintes, quando a primeira encher | `sga_glyphs_2.png`… |
| U+E800–EFFF | **addons**, cada um com sua folha e seu `sga.json` | do addon |

- As letras A–Z continuam sendo das runas que já as têm: nada já escrito muda.
- Um lugar dado a uma runa **nunca é reaproveitado**, nem se a runa sair da língua: páginas antigas ainda guardam o
  caractere, e ele deve continuar lendo a mesma coisa. A única exceção é uma runa nova que **toma o lugar** da antiga
  e lê as páginas antigas do mesmo jeito, como `tenet` herdou o `F` de `exsugat`.

## 3. Como se desenha um glifo

Os glifos novos têm de parecer letras da mesma língua. As regras saem do próprio SGA do jogo:

1. **Caixa 5×7**, a mesma das letras: até 5 de largura, 7 de altura, a linha de base embaixo.
2. **Traço de 1 pixel.** Nada de blocos 2×2. Curvas e diagonais são degraus de 1 pixel, como o arco do `A`.
3. **O vocabulário do SGA:** hastes verticais, barras horizontais, pontos soltos e diagonais em degrau. Os dígitos
   acrescentaram o arco de canto. Nada de formas que o SGA não use, como letras latinas ou desenhos.
4. **Distância das outras:** cada glifo novo deve diferir de todos os outros em pelo menos **4 pixels**, e nunca ser
   o espelho de outro. Senão, em tamanho de jogo, os dois se confundem.
5. **Largura variável vale.** O jogo mede a largura pelo pixel mais à direita (o `I` tem 1 de largura).

### Famílias

Decidido (04/10/2026): os glifos **novos** levam uma marca da sua classe. As letras A–Z ficam como estão, e a marca
precisa manter a cara do SGA do jogo.

A marca (aprovada em 04/10/2026) é **um ponto isolado num canto**. É vocabulário do próprio SGA: o `E` tem um
em cima à direita, o `T` embaixo à direita, o `C` em cima à esquerda e o `R` embaixo à esquerda.

| Classe | Canto do ponto |
|---|---|
| Fonte | embaixo, à direita (4,6) |
| Verbo | em cima, à direita (4,0) |
| Filtro | em cima, à esquerda (0,0) |
| Condição | embaixo, à esquerda (0,6) |

Os três pixels vizinhos do ponto ficam vazios, para que ele se leia como ponto e não como parte do traço. O corpo do
glifo ocupa o resto da caixa.

## 4. Uma fonte de verdade só, e um gerador

Hoje os dígitos estão em três lugares: a textura, a tabela do `SpellMapHelper` e o rascunho de onde saíram. Com mais
glifos, isso vira erro na certa. O plano:

Implementado (04/10/2026):

- **`tools/glyphs/glyphs.txt`**: cada glifo como uma grade de texto (`#` e `.`), com o caractere e a runa:

  ```
  E010 languet
  #....
  #.#..
  ...
  ```

- **`tools/glyphs/build.py`**: lê o arquivo e gera:
  - a folha (`sga_glyphs.png`);
  - as linhas `chars` do `sga.json`;
  - uma prancha ampliada (`tools/glyphs/preview.png`), com cada glifo ao lado das letras do jogo, para aprovar o desenho.
  - avisos quando um glifo novo fica a menos de 4 pixels de outro, ou é o espelho de outro.

  Com `--drafts arquivo.txt`, só desenha a prancha com os rascunhos ao lado do alfabeto, sem gravar nada.
- **O pergaminho lê a textura** (`GlyphBitmaps`), e não uma tabela no código. As folhas do mod vão no `.jar`, então o servidor as lê do
  classpath (`ImageIO`), e um glifo novo aparece no pergaminho sem mexer em Java. As letras A–Z continuam na tabela,
  porque a textura delas é do jogo, não do mod, e um servidor dedicado não a tem.

## 5. Testes que guardam o alfabeto

Implementados em `AlphabetTest` (04/10/2026):


- Toda runa do léxico tem glifo, e todo glifo é desenhável: uma letra A–Z, ou um caractere presente no `sga.json`.
- Nenhum glifo de runa é igual ao de outra; a distância mínima de 4 pixels vale para todos os pares de glifos novos.
- As faixas de §2 são respeitadas: dígitos em E000–E00F, runas do mod em E010–E7FF, nada do mod em E800–EFFF.
- As folhas têm o tamanho que o `sga.json` diz (16 colunas por linha de `chars`, células 8×8).

## 6. Como entra uma runa nova, passo a passo

1. Desenhar o glifo em `glyphs.txt`, no próximo lugar livre.
2. Rodar `python tools/glyphs/build.py` e conferir a prancha.
3. Escrever `"glyph": ""` na runa, no `runes.json`.
4. Rodar os testes.

O editor do grimório já converte o nome digitado (`languet`) no glifo, e o pergaminho, o transe e a mesa leem pela
mesma fonte. Nenhuma tela precisa mudar.

## 7. Para os addons

Um addon que traga runas:

1. Escolhe uma faixa dentro de U+E800–EFFF. Convém o mod manter, neste documento, uma lista de quem ficou com qual
   faixa.
2. Traz a sua folha e um `assets/elderlexicon/font/sga.json` só com o seu provedor `bitmap`. O jogo soma com o do mod.
3. Declara o glifo na runa (`Lexicons.extend`, `Rune.builder(...).glyph("")`).

O pergaminho de um addon só desenha o glifo novo se a folha estiver no classpath do servidor, o que vale para mods. Um
simples pacote de recursos não chega ao servidor; nesse caso o pergaminho mostra `?`.

## 8. Ordem do trabalho

1. **Gerador e fonte de verdade** (§4): mover os dígitos para `glyphs.txt` e fazer o pergaminho ler a textura.
2. **Testes do alfabeto** (§5).
3. **Primeiros glifos novos:** `languet` (U+E010) e `mergitur` (U+E011). Feito.
4. Daí em diante, cada runa nova segue §6.

## 9. Perguntas abertas

Nenhuma por enquanto.

## 10. Decisões tomadas

1. Os glifos novos ganham famílias por classe, sem mudar as letras A–Z e mantendo a cara do SGA do jogo. (04/10/2026)
2. A caixa é sempre 5×7. (04/10/2026)
3. Os glifos novos são propostos por Claude e aprovados pelo autor do mod. (04/10/2026)
4. A marca das famílias é um ponto isolado num canto: fonte embaixo à direita, verbo em cima à direita, filtro em cima
   à esquerda, condição embaixo à esquerda. (04/10/2026)
5. Primeiros glifos do mod: `languet` (U+E010, uma barra que se desfaz e cai) e `mergitur` (U+E011, a linha da água e
   uma bolha). (04/10/2026)
6. `aut` (U+E012, uma forquilha: dois ramos que se juntam, e a haste segue depois de uma pausa) e `non` (U+E013, uma
   barra cortada por uma diagonal), ambos com a marca de condição. (04/10/2026)
