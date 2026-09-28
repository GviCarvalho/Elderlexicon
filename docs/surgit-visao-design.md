# Surgit: a visão do espírito

> **Nota (docs/plano-materia-e-forca.md, etapa 6):** `exsugat` e `transvocatio` saíram da língua. Onde este documento
> os usa, leia: tirar uma fonte do mundo é `tenet` antes do verbo (`igni tenet iactare`); absorver é um `vocant` com
> quantidade negativa (`igni quantum -10 vocant`); puxar uma coisa marcada é `m1 quantum -10 iactare`; trocar de lugar
> não existe mais. A tabela completa está na seção 4 do plano.

> "Ao ouvir o comando Surgit pelo canal de áudio, o espírito instantaneamente assume os seus nervos ópticos e olha
> através dos seus olhos." (Grimório de EVL, 5.1.1)

Hoje o Surgit só lê pergaminhos. Lido ao pé da letra, porém, ele é o **espírito vendo**. Ler é só o primeiro uso,
porque ver um pergaminho é o mesmo que lê-lo. Este documento estende a runa para a revelação, o vínculo de visão
(wargar), a projeção astral, a ilusão e a visibilidade, mantendo a lógica do livro:

- **A magia é o sonho do kern vazando para Harlvilt** (2.1.2). Uma ilusão é um vazamento que chega só aos olhos, sem
  matéria.
- **O espírito é literal.** Ele vê exatamente o que foi pedido, e nada além disso.
- As marcas levam a magia "para onde jamais alcançaria com os olhos" (Ubis, 4.3.2).

## Regra de posição

O lugar do Surgit na frase decide o papel dele:

| Posição | Papel | Exemplo |
|---|---|---|
| **depois** do sujeito, no fim | **função**: o espírito olha para o sujeito | `igni surgit`, `m1 surgit`, `vis surgit` |
| **depois** do sujeito e antes de outra função | **filtro**: a função age só sobre a imagem | `igni surgit vocant` |
| **antes** do sujeito | **aspecto** "a visão de": no vínculo ou na visibilidade | `surgit m1 ligabis`, `surgit m1 quantum 0` |
| com **ubis** | o olho sai do corpo e vai até o lugar | `m1 ubis surgit`, `10 ubis surgit` |

O aspecto segue a gramática do Ligabis que já existe (`vis eu ligabis r1`: aspecto, marca, ligabis). O Surgit passa a
ser mais um aspecto.

## 1. Revelação: `fonte surgit` / `marca surgit`

O mundo fica **preto** para o mago. Só aparece o que carrega o que foi pedido, com contorno e através das paredes:

- `igni surgit`: fogo, lava, fornalhas acesas, criaturas quentes (blazes, magma), quem está pegando fogo.
- `aqua surgit`: água, criaturas aquáticas, quem está molhado.
- `firmo surgit`: a terra: solo (terra, grama, areia, cascalho, argila, lama), rocha (pedras de toda dimensão,
  arenito, terracota) e minérios; golems, blocos caindo e traças.
  **A terra é vista pela UMU que carrega** (cap. III: "uma pedra pesada ... possui um valor exato em UMU expresso
  em matéria de Terra"). A UMU de terra de um bloco é a sua dureza: terra 0,5, pedra 1,5, minérios de 3 a 4,5,
  obsidiana 50; a bedrock é incomensurável. Quanto mais densa, mais forte o espírito a vê, numa escala logarítmica.
  O solo solto aparece quase apagado, a pedra média, os minérios se destacam e a obsidiana brilha.
- `aura surgit`: criaturas voadoras, jogadores voando ou planando, e o que o vento está levantando. Não mostra
  blocos, porque o ar está em toda parte.
- `m1 surgit`: tudo o que tem a marca m1 (blocos, entidades, jogadores), com o nome da marca.
- `vis surgit`: **tudo o que tem magia**: marcas (com o nome), vínculos (linhas entre os membros), pergaminhos
  marcados, feitiços ativos da cena, ilusões (tremulando) e projeções astrais. Nesta versão aparecem as marcas,
  os pergaminhos, os golems e os raios da cena; as linhas dos vínculos, as ilusões e as projeções chegam depois.

Filtros:

- `quantum` **antes do surgit**: a potência do olhar, em UMU. A visão é paga por bloco de alcance por segundo, então
  a UMU compra alcance: `firmo quantum 20 surgit` alcança 100 blocos em 2 s. Sem ele, **16 blocos**; no máximo 128.
  Blocos só são procurados até 32 blocos; além disso, só criaturas.
- `quantum` **antes da fonte**: a UMU procurada em cada coisa, comparada em UMU inteira, porque a Língua Antiga só
  escreve números inteiros. `quantum 2 firmo surgit` mostra só a terra de 2 UMU: pedra (1,5), granito, pedregulho (2).
  `quantum 1` mostra o solo solto, `quantum 3` a ardósia e os minérios comuns, `quantum 50` a obsidiana. Por
  enquanto, só a terra tem medida; com esse filtro as criaturas não aparecem.
- `chronos`: duração, com padrão de 2 s (a janela do transe, cap. V).
- `ubis`: de onde o olho olha (junto com a projeção astral).

O custo é de visão, não de matéria: **0,1 UMU por bloco de raio por segundo** (16 blocos por 2 s custam 3,2 UMU).

**Implementado.** As criaturas reveladas brilham com contorno, visto através das paredes. Os blocos aparecem como
caixas na cor do elemento, e as marcas vêm escritas acima de cada coisa. As criaturas são procuradas de novo a cada
0,5 s; os blocos, uma vez no começo. De uma massa de um mesmo elemento só aparece a superfície: um bloco cercado
dos seis lados pelo mesmo elemento não seria visto de lugar nenhum. Aparecem no máximo os 4096 blocos mais próximos. Um olhar novo substitui o anterior. Nesta versão o `ubis` ainda não move o olho:
isso chega com a projeção astral.

`r2 surgit` revela o que tem a marca r2 **e** lê os pergaminhos r2 (decidido: ver um pergaminho é lê-lo, o que dá um
efeito bonito nos rituais). O `chronos` é a duração do olhar e também o instante da leitura, como antes.

### Regra geral do quantum

Um filtro age sobre o que vem escrito **depois** dele (como o `chronos` em `igni chronos 5 iactare`, que muda o
tempo da função). O quantum é sempre UMU (4.3.2), e o que ele mede depende de onde está:

| Posição | Mede | Exemplo |
|---|---|---|
| antes da fonte | o valor da fonte | `quantum 2 firmo surgit`: terra de 2 UMU |
| antes da função | a potência da função | `igni quantum 20 iactare`: lança 20 UMU (livro) |

As outras funções ainda não distinguem as duas posições: por enquanto leem o mesmo número de qualquer uma delas. A
potência das fontes nessas funções, como "cada pulso de 20 UMU" em `quantum 20 igni iactare`, fica para depois.

## 2. Vínculo de visão (wargar): `surgit m1 ligabis`

É um vínculo de aspecto Surgit. **A tua visão passa a ser a da coisa marcada.** Tu vês o que ela vê, mas **não
controlas a câmera dela**: a câmera acompanha a cabeça do alvo. O teu corpo fica onde estava, cego para o próprio
entorno, e isso é o preço.

- **Decidido:** não há alternância. A visão do alvo dura o feitiço, com 2 s de padrão ou o que o `chronos` der, e
  depois a visão normal volta.
- Se o alvo morrer ou sair da dimensão, o vínculo se rompe.
- **Implementado.** A câmera vai para os olhos do alvo, só no cliente: o jeito do servidor (a câmera do espectador)
  arrastaria o corpo até lá. Enquanto dura, o corpo não anda, não pula e não agacha. Custa 0,5 UMU por segundo,
  pago de uma vez. O alvo precisa estar perto o bastante para o cliente do mago conhecê-lo (a distância em que o jogo
  mostra aquele tipo de entidade); mais longe, o feitiço avisa e não cobra. Com várias coisas com a marca, vale a
  mais próxima.

## 3. Projeção astral: `m1 ubis surgit` / `10 ubis surgit`

O espírito sai do corpo e olha a partir do lugar indicado pelo `ubis`. **Neste modo tu controlas a câmera**, ao
contrário do vínculo.

- O corpo fica em transe e indefeso. Se levar dano, a projeção se desfaz.
- O espírito projetado é **invisível**, a não ser para quem estiver com `vis surgit` ativo.
- O `chronos` dá a duração, com padrão de 2 s. O custo é distância × tempo, como nas marcas.
- **Implementado.** O espírito surge no lugar do `ubis` (`10 ubis surgit`: 10 blocos à frente; `m1 ubis surgit`:
  junto de m1; coordenadas também). O mago gira a vista com o mouse e flutua com as teclas de andar, pulo e agachar,
  atravessando tudo, até 16 blocos de onde surgiu. O corpo fica parado; se levar dano, o espírito volta na hora.
  Custa 0,05 UMU por bloco (distância até o espírito mais os 16 que ele pode flutuar, no mínimo 4) por segundo, pago
  de uma vez. Quem estiver com `vis surgit` ativo vê um fantasma do mago onde o espírito está. O lugar precisa estar
  carregado no cliente do mago (dentro da distância de renderização).

## 4. Ilusão: `fonte surgit função`

Com o surgit logo antes de uma função, ele é um filtro: tira da fonte só a **luz**, e a função trabalha apenas com a
**imagem** dela. Vale para qualquer função. Tudo o que se faz assim é **intangível**, dura **2 s** ou o que o `chronos`
disser, e custa um décimo da matéria que mostra. A marca, o lugar e os filtros escritos antes do surgit passam para a
função seguinte (`m1 chronos 30 surgit vocant`). O `vis surgit` vê através das ilusões.

- **`vocant`**: a imagem do que o vocant traria, nos mesmos lugares. `firmo surgit vocant`: uma parede que se
  atravessa; `aqua surgit vocant`: água em que não se nada; `igni surgit vocant`: fogo que não queima. O que não tem
  matéria mostra só a luz: `aura surgit vocant` é vento de partículas, sem empurrão, e o raio é um raio que não
  atinge nada. **`m1 surgit vocant`**: uma imagem de m1 no lugar do `ubis` (ou da mira), que se mexe como m1 se mexe.
- **`iactare`**: um jato que se vê, seguindo a mira, sem queimar nem empurrar. **`m1 surgit iactare`**: uma imagem de m1
  sai de onde ele está e voa até a mira (ou o `ubis`) na velocidade de um arremesso de verdade, e fica lá até o tempo
  acabar; o m1 verdadeiro não se mexe. `m1 surgit exsugat` traz a imagem até o mago; `m1 surgit impediunt` a empurra
  para longe dele.
- **`exsugat`**: absorve só a luz da fonte próxima (5 blocos): o fogo continua lá, quente, mas invisível. Imagens
  dessa fonte ali são dissipadas.
- **`impediunt`**: afasta a imagem da fonte próxima: ela continua no mesmo lugar, invisível; se for só ilusão, se
  dissipa.
- **`vertere`**: `igni surgit vertere aqua` disfarça o fogo próximo de água; ele continua sendo fogo.

## 5. Visibilidade: `surgit m1 quantum N`

Aqui o `quantum` vai de **0 (invisível) a 10 (completamente visível)** e é linear: 5 é meio translúcido.

- `surgit m1 quantum 0` deixa invisível tudo o que tem a marca m1. `surgit m1 quantum 10` restaura a visibilidade.
- **Dura 2 s**, como todo feitiço de visão, ou o que o `chronos` disser; depois a visibilidade volta sozinha.
- Com `vis surgit`, o invisível aparece.
- **Vale para tudo o que tem a marca: criaturas, outras entidades e blocos.**
  - **Criaturas:** em 0, nada delas é desenhado: corpo, armadura, item na mão e nome. De 1 a 9, tudo aparece
    translúcido junto. O próprio corpo do mago, visto de fora (F5), nunca some além de um fantasma, para ele saber
    onde está. A sombra no chão ainda aparece.
  - **Outras entidades** (itens no chão, molduras, pergaminhos, blocos caindo, raios): o renderizador daquele tipo
    é envolvido enquanto houver algo dele escondido; a sombra some junto.
  - **Blocos:** o bloco continua sendo o que é (quebra, faz o som dele, tem colisão e dá luz como sempre); só o
    desenho some. Todo modelo de bloco é envolvido, e quando o construtor de chunks pergunta por aquela posição, ele
    responde que não tem nada a desenhar. De 1 a 9, o bloco é desenhado de novo, translúcido. Blocos com renderizador
    próprio (baú, placa, estandarte) são envolvidos como as entidades. Água e lava não são desenhadas por modelo e
    ainda aparecem. Um bloco totalmente escondido não mostra contorno ao ser mirado.
- **Pela mira:** `surgit quantum N` sem marca age no que o mago mira, no alcance do toque; uma entidade na frente
  de um bloco tem preferência.
- `vis surgit` também mostra os blocos escondidos por feitiço, com ou sem marca.
- Esta é a base para invocar coisas invisíveis (fogo, água, terra, vento sem partículas), a desenhar depois.
- **Custo:** esconder é curvar a luz em volta de um corpo, então custa 0,05 UMU por unidade de massa por segundo,
  proporcional ao quanto está escondido, pago de uma vez no lançamento pelo tempo todo. Um jogador (massa 20)
  totalmente invisível por 30 s custa 30 UMU; em 5, metade.
- `surgit m1 quantum 10` devolve a visibilidade na hora. `surgit m1` sozinho continua lendo os pergaminhos m1.

## Ordem de implementação sugerida

1. Revelação, com o mundo preto e só a fonte à vista. Isso já ajuda a depurar marcas e vínculos.
2. Visibilidade, com entidades em 0 e 10 primeiro.
3. Vínculo de visão e projeção astral, que compartilham a câmera e o corpo em transe.
4. Ilusão.
5. Translucidez intermediária e blocos invisíveis.
