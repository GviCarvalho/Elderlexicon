# O grimório próprio

**Status:** implementado (27/09/2026), falta testar no jogo. As páginas de círculo ainda serão discutidas.

## Por quê

O grimório era o livro e pena do Minecraft com outro nome: texto livre na textura do livro vanilla, com as letras
trocadas por glifos SGA. Isso não mostrava o que importa desde que **a coluna virou o tempo** (ver
`interacoes-design.md`): o leitor conta palavras, não posição na tela. Um número (`UQ`) ou uma marca (`pg2`) ocupa uma
coluna só, mas vários glifos de largura, então duas linhas que pareciam alinhadas podiam não estar.

## O livro

Na ordem em que se folheia:

1. **A capa**, feita a partir da do grimório de EVL: couro escuro, filete dourado duplo, o sigilo (losango inscrito em
   dois anéis) e o **nome do criador** em SGA, com "grimorio" embaixo. Clicar nela (ou PageDown, Enter, seta) abre o
   livro. Um grimório ainda em branco abre na capa.
2. **O glossário**, nas primeiras páginas: as runas que **o criador** escreveu no grimório, na ordem em que as
   escreveu pela primeira vez, e depois as **runas nomeadas** nele com `reframe`. Cada entrada tem o glifo grande, o
   nome e o que a runa é; a nomeada diz o feitiço que guarda e a página onde foi nomeada (clicar leva até lá). O
   glossário ocupa quantas páginas precisar (6 entradas por página).
3. **As páginas de feitiço**: cada página do grimório abre em duas. **À esquerda, o feitiço**; **à direita, a
   descrição** que o grimório escreve dele depois de lê-lo.

O criador é quem escreve primeiro no grimório (o servidor guarda `Author`/`AuthorId`). O glossário (`Glossary`) só
cresce com o que o criador escreve; alguém que escreva no grimório de outro não acrescenta runas a ele. As runas
nomeadas continuam valendo só com o grimório no inventário (o espírito as procura nos grimórios que o mago carrega).

Os cantos dobrados embaixo das páginas viram a página (para trás à esquerda, para a frente à direita); virar tem som e
uma folha que varre de um lado da lombada para o outro. O número da página de feitiço é escrito nos dígitos antigos
(glifos Q a Z).

## A página do feitiço (esquerda)

- **10 linhas pautadas**, como um caderno, e a linha de margem. Cada linha é um feitiço independente, e todas são
  lidas juntas.
- **Uma célula é uma coluna, e uma coluna é um passo de tempo** (0,25 s). O que está na mesma coluna de duas linhas
  acontece no mesmo instante.
- **A linha tem quantas colunas quiser.** Ela só quebra quando o mago aperta Enter. A página mostra 10 colunas e rola
  na horizontal (roda do mouse, ou seguindo o cursor).
- **Célula vazia é uma pausa**: um passo em que nada é lido. Começar uma linha mais à direita atrasa o feitiço.
- Um número de vários dígitos cabe numa célula (os glifos encolhem). Uma **marca** aparece em tinta vermelha, com
  moldura de selo. Uma **runa nomeada** aparece em tinta dourada.
- **Sem guias de coluna.** As células têm largura fixa, então o alinhamento é o mesmo, só não é desenhado. Passando o
  mouse, uma faixa leve desce pela coluna, **dourada** se duas ou mais linhas leem algo nesse instante.

## A descrição (direita)

A página da direita é a descrição que **o próprio grimório** escreve do feitiço, em texto (os pictogramas e a linha do
tempo foram testados e saíram: a página descritiva ficou mais bonita). Ela só aparece **depois que o espírito lê o
feitiço de verdade** (surgit com a página aberta): antes disso, o grimório diz que ainda não o conhece. A leitura fica
guardada pelo texto da página (`Readings`, com o custo), então **mudar a página** a faz voltar a ser desconhecida até
ser lida de novo.

- **O nome**: o grimório dá um nome ao feitiço (`SpellDescription`), pelo que ele faz: "Bola de fogo" (fogo condensado
  e lançado), "Rajada", "Meteoro", "Fonte", "Barreira de fogo"… e pelo que acontece onde as linhas se cruzam:
  "Tempestade" (água e ar soltos no mesmo instante), "Explosão de vapor" (fogo e água), "Redemoinho de fogo" (fogo e
  ar). **Clicar no nome** deixa o mago dar outro (`PageNames`, até 40 letras); apagar o nome dado devolve o do
  grimório.
- **O que faz**, frase por frase, agrupado pelo instante da soltura: "Ao mesmo tempo, em 0,75 s: • Converte 10 UMU de
  mana do corpo (a experiência) em ar e lança na direção mirada. • Converte 10 UMU de mana do corpo (a experiência) em
  água e lança na direção mirada."
- **O custo**: o que a página custou na última leitura, em UMU, somando os feitiços que saem depois.
- **Notas**: o que os elementos soltos no mesmo instante podem fazer um ao outro, pelos aspectos da energia (água e ar:
  gotículas em atrito, carga, relâmpago; fogo e água: vapor que explode; fogo e ar; fogo e terra; água e terra; ar e
  terra), e o que vale notar de cada feitiço (o vácuo de tirar o ar, o trabalho de condensar, o custo de converter, os
  quatro aspectos da vis). São só palavras: o que acontece de fato é a física.

## A tinta diz se o feitiço lê

- Uma linha que o espírito **lê** ganha um pequeno **selo dourado** na margem.
- Uma linha que ele **não lê** fica com a tinta **cor de ferrugem**, e um **borrão** na margem; o tooltip do borrão
  diz o motivo.

## Ler adiante (Tab)

Tab faz uma luz dourada percorrer as colunas **no tempo real** (0,25 s por coluna), rolando a página se preciso.
Quando ela chega à coluna em que um feitiço é solto, soa um sino (ou um baixo abafado, se a linha não lê) e o selo
dessa linha acende. Serve para ver quando cada feitiço sai e onde eles se cruzam.

## O tooltip de cada palavra

O glifo, o nome (e o elemento), o que a runa é, **o que ela faz naquele feitiço** e em que coluna/tempo ela está.
Exemplos em `E N UQ H A O Q I`:

- `E`: "A fonte do iactare: a mana do corpo (a experiência)."
- `H`: "Converte vis em água (sem custo)."
- `O` seguido de `Q`: "Solta tudo num único instante: condensa, e a intensidade vira a energia toda."
- `I`: "Lança a água na direção mirada, condensado numa esfera que se junta antes (diante da mão)."

A leitura é feita por `spell/block/SpellReading`, a partir do mesmo vocabulário do motor.

## Marcadores: as runas nomeadas

Uma runa nomeada com o `reframe` (`… reframe nome`, o encapsulador de feitiços) ganha uma **fita de marcador** saindo
da borda direita do livro, com o nome escrito em SGA. A fita fica na página onde a runa foi nomeada (a primeira
página em que o nome aparece; a runa só existe enquanto o nome estiver escrito em alguma página). A fita da página
aberta sai mais para fora. Clicar vira até ela; o tooltip dá o nome, o feitiço que ela guarda (em glifos e por
extenso) e a página. A margem valida a linha já com a runa aberta no feitiço que ela guarda, como o espírito faz.

## Como se escreve

- **Teclado**, como antes: letras e dígitos vão para a célula do cursor, com o som da pena. Ao sair da célula, o nome
  de uma runa vira o glifo (`igni` → `C`) e os dígitos viram glifos (`40` → `UQ`).
- **Espaço**: próxima célula; no fim da linha, cria uma pausa. **Shift+Espaço** ou **Insert**: insere uma pausa.
- **Enter**: quebra a linha no cursor; o que vem depois vai para uma linha nova abaixo.
- **Backspace** apaga letra a letra; numa célula vazia, remove a célula; numa linha vazia, remove a linha.
  **Delete** remove a célula do cursor. Setas, Home e End movem o cursor; PageUp/PageDown viram a página.
- **Ctrl+C** copia a página como texto; **Ctrl+V** cola palavras na linha do cursor.
- O botão **Destacar página** e o limite de 1024 caracteres por página continuam como antes.

## Por baixo, nada muda

A página é salva como o mesmo texto de antes (`GrimoirePage`): uma linha por feitiço, palavras separadas por espaço,
célula vazia escrita como `.`. O servidor lê isso pelo mesmo `RuneTokens.normalize` que a tela usa. Então `surgit`, os
pergaminhos destacados, os rituais e as marcas funcionam como antes, e as páginas antigas abrem no livro.

## Arte (nossa)

- `textures/item/grimoire.png`: o livro deitado e inclinado, como os livros do jogo, com a capa do grimório de EVL:
  couro quase preto, filete dourado e o sigilo no centro.
- `textures/gui/grimoire_book.png`: o livro aberto, couro marrom de cantos arredondados, duas páginas creme que
  escurecem perto da lombada, a pilha de folhas nas bordas, a lombada com uma costura dourada e os cantos dobrados
  com as setas. As pautas são desenhadas pela tela, só nas páginas de feitiço.
- `textures/gui/grimoire_cover.png`: a capa, a partir da do grimório de EVL.

Geradas por script (pixel art), sem arquivos da Mojang. A fonte continua sendo o SGA do Minecraft (`minecraft:alt`).

## Próximo

- **Páginas de círculo**: anéis concêntricos, onde o anel é o tempo, lido de dentro para fora. Ainda em discussão.
- **Escrita à mão** (glifos levemente tortos, tinta aparecendo aos poucos): ideia guardada.
