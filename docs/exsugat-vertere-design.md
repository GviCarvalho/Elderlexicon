# Exsugat e vertere de fonte

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

**Fontes inteiras, das mais próximas.** A procura começa no bloco mirado, dentro do alcance de toque. Sem mira, começa no mago. O raio é de 5 blocos.
Dentro de uma zona de impediunt do mesmo elemento, o alcance passa a cobrir a zona inteira e a borda dela (o anel).
- Um bloco nunca é tirado pela metade.
- O que o último bloco traz além do custo vai para o corpo: "não há desperdício, não há sobra".

**Mira no vazio com firmo.** O chão debaixo dos pés não é puxado.

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
- **Sem função depois** (`firmo exsugat vertere igni`), aquela porção vira o outro elemento **no próprio mundo**.
  Os blocos são tomados dos mais próximos da mira até somar 10 UMU da fonte, ou o `quantum` escrito antes do exsugat.
  Onde cada bloco estava:

  | Vira | O que aparece |
  |---|---|
  | igni | fogo onde ele pode queimar; onde ficaria no ar, um bloco de magma |
  | aqua | água; num bloco que pode ser alagado (fogueira, vela), ele fica alagado |
  | firmo | terra; de água, lama |
  | aura | nada, só uma lufada de nuvem |

  Hoje a troca é de um bloco por um bloco. A equivalência em UMU ainda não entra na conta.

**Direto** (`igni vertere aqua`), o vertere continua convertendo o Vita do mago, o uso perigoso. Converte 1 UMU, ou o `quantum` escrito antes:
`igni quantum 5 vertere aqua`.

**Exsugat sozinho** vai para o corpo. As partículas vão da fonte até o mago, e uma mensagem na barra de ação diz quanto foi
absorvido.

## O que ainda falta

- **`aura exsugat`:** ainda não puxa nada, porque o ar está em todo lugar. É preciso decidir se é energia infinita, fraca ou limitada.
- **Filtros sem número depois do exsugat** (cap. 4.3):
  - `igni exsugat ubis aqua vocant`: a posição da fonte absorvida;
  - `igni exsugat chronos firmo vocant`: o tempo que a fonte ainda duraria;
  - ~~`igni exsugat quantum iactare`: todo o valor da fonte.~~ **Feito:** o `quantum` sem número depois de um exsugat puxa
    tudo o que está ao alcance e a função seguinte gasta tudo de uma vez. Sem um exsugat antes, ele continua recusado.
- **`chronos` no exsugat de fonte:** ainda não é uma torneira aberta.
