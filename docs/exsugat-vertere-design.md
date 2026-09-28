# Exsugat e vertere de fonte

> **Nota (docs/plano-materia-e-forca.md, etapa 6):** `exsugat` e `transvocatio` saíram da língua. Onde este documento
> os usa, leia: tirar uma fonte do mundo é `tenet` antes do verbo (`igni tenet iactare`); absorver é um `vocant` com
> quantidade negativa (`igni quantum -10 vocant`); puxar uma coisa marcada é `m1 quantum -10 iactare`; trocar de lugar
> não existe mais. A tabela completa está na seção 4 do plano.

Este documento reescreve a captura e a conversão de fontes seguindo o grimório, cap. VIII (8.1, 8.2 e 8.2.1).
Os casos com marca estão em `marcas-como-runas-design.md` e os de imagem em `surgit-visao-design.md`.

## O que o livro diz

- **Vertere direto é interno.** `igni vertere aqua` converte o fogo *do corpo*. O livro não recomenda esse uso.
- **Exsugat é externo.** Ele puxa uma fonte de fora, "o fogo de uma fogueira próxima, a água de um lago próximo".
- **Escrito sozinho, o exsugat leva a fonte para dentro do corpo.** Exemplo: `igni exsugat` quando se sente frio.
- **Seguido de outras funções, o exsugat captura a fonte.** As funções gastam a fonte capturada, não o Vita.
  - `igni exsugat vertere aqua` converte o fogo que está sendo puxado.

## Como funciona agora

### Exsugat

**Captura.** Quando há outra função depois do exsugat, ele só puxa depois que o feitiço inteiro rodou. Só então se sabe
quanto as funções seguintes gastaram, e ele puxa exatamente isso.
- `igni exsugat quantum 20 iactare` captura o custo do lançamento de 20.
- Antes, ele puxava cedo demais e pegava só o custo base, uns 3 UMU.

**Fontes inteiras, das mais próximas da mão.** A mão do mago é que absorve: a busca começa logo à frente do corpo,
na altura do peito, e não depende da mira.
- **Com uma quantidade** (o custo das funções, ou o `quantum N` depois do exsugat, que diz quanto tirar, livro 9.2):
  o espírito procura do mais perto ao mais longe até juntar a quantidade, alcançando até 16 blocos se precisar
  (8 para cima e para baixo).
- **Com o `quantum` sem número** (tudo): pega o que está ao alcance de costume, 5 blocos.
- **Com um `ubis` antes do exsugat** (`igni eu ubis exsugat …`, `igni m1 ubis exsugat …`, `igni 10 ubis exsugat …`),
  a busca é em volta da marca ou do lugar, e não da mão.
- **Dentro de uma zona de impediunt** do mesmo elemento, o alcance cobre a zona inteira e o anel dela.
- Um bloco nunca é tirado pela metade.
- O que o último bloco traz além do custo vai para o corpo: "não há desperdício, não há sobra".

**Com firmo,** o chão debaixo dos pés do mago nunca é puxado.

**Sem dupla contagem.** Antes, a energia capturada pagava o feitiço *e* ainda voltava ao Vita. Agora:
- o que paga o feitiço é gasto;
- só o que foi absorvido para o corpo entra no Vita.

**Exsugat sozinho** (`igni exsugat`, `aqua quantum 5 exsugat`) absorve para o corpo 10 UMU, ou o `quantum` escrito.

**Mensagens.** A mensagem do feitiço mostra "Ambiente" (o que pagou) e "Absorvido" (o que entrou no corpo). Se não
houver fonte por perto, o mago é avisado e o corpo paga.

### O que cada fonte dá e o que deixa

| Fonte | UMU | O que fica |
|---|---|---|
| fogo | 1 | nada |
| tocha | 1 | um graveto |
| lanterna | 1 | 8 pepitas de ferro |
| abóbora iluminada | 1 | abóbora esculpida |
| vela acesa | 0,5 | vela apagada |
| fogueira acesa | 3 | fogueira apagada |
| fornalha acesa | 2 | fornalha apagada, sem o combustível que queimava |
| bloco de magma | 2 | netherrack |
| lava (fonte) | 10 | obsidiana |
| água (fonte) | 3 | ar; se era um bloco alagado, ele deixa de ser alagado |
| caldeirão com água | 1 por nível | caldeirão vazio |
| esponja molhada | 3 | esponja |
| lama | 1 | terra |
| terra arada úmida | 1 | terra arada seca |
| terra (blocos de terra da revelação) | a dureza, igual à revelação | nada, sem drop |

Blocos com conteúdo (baú, fornalha) nunca são desfeitos pela terra que contêm.

### Vertere

**Com o exsugat, a fonte externa é o alvo das funções que vêm depois** (8.2.1). `igni exsugat igni iactare` lança o
fogo que está em volta do mago. Repetir a fonte (`firmo exsugat firmo vertere igni`) é o mesmo que não repetir.

- **Com uma função depois do vertere** (`firmo exsugat vertere igni iactare`), a terra capturada vira fogo e é lançada.
  O Vita não é tocado.
- **Sem função depois** (`firmo exsugat vertere igni`), aquela porção vira o outro elemento **no próprio mundo,
  guardando a UMU** (livro, cap. 3: a energia só muda de forma). As fontes são tomadas das mais próximas da mão até
  somar 10 UMU, ou o `quantum` escrito antes do exsugat. Soma-se a UMU delas, e o novo elemento aparece em quantas
  unidades essa UMU compra:

  | Vira | Cada unidade vale | O que aparece |
  |---|---|---|
  | igni | 1 UMU | um fogo, só onde ele pode queimar |
  | aqua | 3 UMU | uma fonte de água, ou alaga um bloco que aceite água (fogueira, vela) |
  | firmo | 0,5 UMU | um bloco de terra solta; de água, lama |
  | aura | — | nenhum bloco: a matéria vira ar e a UMU dela sai como **uma rajada** daquela pressão, no meio de onde estava |

  - **Onde aparece:** primeiro onde as fontes estavam; se sobrar, nos espaços livres vizinhos.
  - **O que sobra não se perde:** uma fração, ou o que não achou lugar, vai para o corpo do mago como o elemento novo.
  - **Exemplos:** 20 blocos de terra solta (10 UMU) viram 10 fogos. 10 fogos (10 UMU) viram 3 fontes de água, e o 1 UMU
    que sobra vai para o corpo.

**Converter é trabalho do espírito.** O livro (3.1) diz que a matéria é "desfeita pelo seu espírito e transformada em
qualquer outra coisa". O vertere não pinta a terra de fogo: **desfaz** um elemento e o **refaz** como outro. O espírito
faz esse trabalho **com a própria energia que tem nas mãos**: a parte do trabalho se perde dela, e o corpo não paga
nada.
- **Na captura**, o exsugat puxa um pouco a mais para cobrir o trabalho.
- **Na conversão no lugar**, sai um pouco menos do elemento novo.
- **Na condensação**, o que é solto já vem com o trabalho descontado. O quanto custa depende de quantas das duas qualidades clássicas mudam:

| | quente | frio |
|---|---|---|
| **seco** | fogo | terra |
| **úmido** | ar | água |

- **Vizinhos** (terra↔fogo, fogo↔ar, ar↔água, água↔terra) mudam uma qualidade: **5%** da UMU convertida.
- **Opostos** (fogo↔água, terra↔ar) mudam as duas: **10%**.
- Isso vale para todo vertere de fonte capturada: no feitiço comum, na conversão no lugar e na condensação. Às vezes
  compensa converter em dois passos, passando por um vizinho.

**Condensado com vertere** (`firmo exsugat vertere igni quantum chronos 0 iactare`) acontece em duas etapas, e cada uma
leva o seu tempo:

1. **Reunir:** carrega pelo total de UMU (`condensacao-design.md`), e a esfera cresce no elemento original: a pedra
   ficando mais densa.
2. **Converter:** 0,5 s + 0,02 s por UMU, até 5 s, **por qualidade mudada** (os opostos levam o dobro). Na primeira
   metade a matéria é desfeita: a esfera treme e solta faíscas do que vai virar. Na segunda ela é refeita como o
   elemento novo, que cresce até a intensidade final.

A UMU se conserva, e **a intensidade é a do elemento de destino**: 40 UMU de terra viram fogo de calor 40 (plasma).
Terra em água dá gelo de pressão, água em ar dá bomba de pressão.

**Direto** (`igni vertere aqua`), o vertere continua convertendo o Vita do mago, o uso perigoso. Converte 1 UMU, ou o `quantum` escrito antes:
`igni quantum 5 vertere aqua`.

**Exsugat sozinho** vai para o corpo. As partículas vão da fonte até o mago, e uma mensagem na barra de ação diz quanto foi
absorvido.

## Um fluxo só para todo feitiço

O executor trata todo feitiço como um **fluxo de energia**, sem casos especiais:

1. **Origem**
   - **o mundo**, com `exsugat`;
   - **o corpo**: um `quantum N` num vertere tira N do corpo; o `quantum` sem número (no vertere ou direto antes da
     função) tira tudo. A vis vem da mana (XP).
2. **Transformações:** zero ou mais `vertere`. Cada um muda o elemento da energia guardada e soma as qualidades que
   mudou. O trabalho (5% da UMU por qualidade) e o tempo de conversão saem da soma da cadeia inteira, então converter
   passando por um vizinho custa o mesmo que o caminho direto.
3. **Uso:** a função seguinte gasta a energia guardada, e o `chronos 0` a condensa (reunir e depois converter).

**A vis nas conversões:**
- **vis → qualquer elemento:** não muda qualidade nenhuma, porque é a matéria-prima (livro 3.1). Não tem trabalho extra
  e leva só o tempo mínimo.
- **elemento → vis:** é o refazer mais caro, porque exige equilibrar os quatro. Conta como os opostos (2 qualidades).

**Exemplos que agora funcionam pelo mesmo caminho:**
- `vis quantum vertere igni chronos 0 iactare`: toda a mana vira fogo e é condensada num ponto, um plasma de calor = XP/10.
- `aqua quantum 20 vertere aura chronos 0 vocant`: 20 UMU da água do corpo viram ar, condensados no ponto.
- `firmo exsugat vertere aqua vertere aura quantum chronos 0 iactare`: a terra em volta vira água e depois ar, e sai condensada.

**Sem nenhuma função depois:**
- O vertere interno sem `quantum` (`igni vertere aqua`) continua mudando só o Vita do mago, como antes.
- A energia tirada do corpo e convertida que nenhuma função gastou volta para o corpo, já como o elemento novo.

## O que ainda falta

- **`aura exsugat`:** ainda não puxa nada, porque o ar está em todo lugar. É preciso decidir se é energia infinita, fraca ou limitada.
- **Filtros sem número depois do exsugat** (cap. 4.3):
  - `igni exsugat ubis aqua vocant`: a posição da fonte absorvida;
  - `igni exsugat chronos firmo vocant`: o tempo que a fonte ainda duraria;
  - ~~`igni exsugat quantum iactare`: todo o valor da fonte.~~ **Feito:** o `quantum` sem número depois de um exsugat puxa
    tudo o que está ao alcance e a função seguinte gasta tudo de uma vez. Sem um exsugat antes, ele continua recusado.
- **`chronos` no exsugat de fonte:** ainda não é uma torneira aberta.
