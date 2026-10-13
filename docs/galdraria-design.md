# Galdraria: gravar runas nas coisas

> "Foci (staves, wands, etched conduits) channel magical flow outside the nerves." (Grimório de EVL, 9.3)
>
> "A escrita é o pergaminho de instruções; o Transe é o canal; e o Surgit é o gatilho que faz o espírito ler." (5.1.1)

**Status:** primeira versão implementada (04/10/2026). Não testada dentro do jogo.

Galdraria é a arte de gravar runas em objetos para fazê-los funcionar como aparelhos tecnomágicos. Para o espírito, uma
gravação é uma página que não se gasta: ele a lê como lê um pergaminho.

## 1. O que se grava

- **Qualquer coisa pode ser gravada**: varinhas, ferramentas, armaduras, blocos em forma de item. Um item por vez.
- Cabe **uma linha de até 10 colunas**, ou seja, até 10 runas por item. A linha é lida como uma linha de página.
- A gravação fica no NBT do item (`Galdraria`), escrita como o grimório escreve (o nome de uma runa vira o glifo).
- A dica do item mostra a gravação em glifos e, embaixo, como o espírito a lê.

## 2. A mesa de Galdraria

- **Receita:** ametista, laje de pedra lisa, ametista sobre seis tábuas (`ASA / PPP / PPP`).
- **Bloco:** um tampo de laje escura com uma linha de casas gravadas e o buril em cima, sobre uma mesa de madeira
  escura com um buril e uma tabuleta de pedra pendurados (como as mesas de trabalho do jogo).
- **Tela "Gravar Runas", feita como a da bigorna:**
  - **No alto, a linha:** 10 casas rebaixadas como slots, uma palavra por casa, cada coluna um passo da leitura.
    Escreve-se como numa página do grimório: digitar escreve na casa, **Espaço** passa à próxima, uma casa vazia é uma
    **pausa**, Backspace/Delete apagam, as setas e o clique movem o cursor. Uma marca aparece emoldurada em violeta.
    Embaixo das casas, a linha nas letras comuns.
  - **Embaixo:** objeto **+** buril **→** objeto gravado, com o desenho fantasma do que vai em cada slot. Pegar o
    resultado consome o objeto e gasta o buril. Enquanto a linha não pode ser gravada (nada escrito, mais de 10
    colunas, uma palavra que o espírito não lê, buril gasto demais, ou a mesma gravação que o objeto já tem), a seta
    aparece riscada, como na bigorna.
  - **Passar o mouse numa casa:** o glifo, o que a runa é, o que ela faz nesta linha e quando é lida.
  - A linha vai para o servidor a cada mudança, como o nome na bigorna, e o resultado é refeito.
- Pôr na mesa um item já gravado mostra o que está gravado nele. Gravar de novo **grava por cima**.
- Uma palavra que o espírito não lê, ou mais de 10 colunas, é recusada. As pausas contam como coluna, mas não gastam
  o buril.
- Ao sair da mesa, os itens voltam para o jogador.

## 3. O buril

- **Receita:** um lingote de ferro e um graveto na diagonal.
- Cada runa gravada **gasta 1 de durabilidade**; um buril grava **100 runas**. Conserta-se na bigorna com ferro.
- Regravar o mesmo texto não gasta nada. No modo criativo, o buril não se gasta (mas é preciso tê-lo).

## 4. Como a gravação é ativada

- **`surgit` segurando o item:** o espírito lê a gravação do que está na mão (a principal primeiro). Um pergaminho ou
  uma inscrição para onde se olha vêm antes; o grimório e a página destacada na mão, depois.
- **Pela marca, como os pergaminhos:** `m1 surgit` lê também os itens gravados com a marca `m1`: os que o mago carrega
  e os que estão no chão ou em molduras ao alcance. Com o vínculo de leitura (`vis eu ligabis m1`), os de toda a
  dimensão carregada.
- Marcar um item: o Pincel Elder, ou **renomeá-lo na bigorna** (docs/marks.md).
- **Em fluxo** (docs/fluxo-design.md): as linhas com uma condição (`ferit igni iactare`) despertam sozinhas quando a
  condição acontece, nas coisas gravadas nas mãos e na armadura vestida (e em tudo o mais que o `surgit` lê).

## 5. Raspar

- **No rebolo:** um item gravado sozinho no rebolo sai sem a gravação, e nada mais muda. Se ele também for
  encantado, os encantamentos saem numa segunda passada, como sempre.
- Depois de raspado, ou mesmo sem raspar, ele pode ser **regravado na mesa**.

## 6. O que existe no código

- `galdraria/Engravings`: lê, grava e raspa; as regras de palavra, linha e coluna.
- `galdraria/GaldrariaTableBlock`, `GaldrariaMenu` (um `ItemCombinerMenu`, como a bigorna), `EngravePacket` (a linha
  escrita), `client/GaldrariaScreen` (um `ItemCombinerScreen`): a mesa.
- `galdraria/BurinItem`: o buril.
- `galdraria/GrindstoneScraping`: o rebolo.
- `ServerSpellingController.processEngravedItem` e `readMarkedEngravings`: a leitura.

## 7. Perguntas abertas

1. Quais feitiços gravados **mudam o comportamento** do item (por exemplo, numa varinha)?
2. ~~Outras técnicas de ativação além do `surgit` e da marca.~~ O estado de fluxo (docs/fluxo-design.md).
3. A gravação deve aparecer no sprite do item (um brilho, glifos)?

## 8. Decisões tomadas

1. Qualquer coisa pode ser gravada; uma linha de até 10 runas por item. (04/10/2026)
2. A gravação exige uma ferramenta que se gasta aos poucos: o buril, 1 por runa. (04/10/2026)
3. Ativação pelo `surgit` segurando o item, ou chamando pela marca, como os pergaminhos. (04/10/2026)
4. Raspa-se no rebolo; regrava-se na mesa. (04/10/2026)
5. Renomear um item na bigorna dá a ele a marca do nome. (04/10/2026)
