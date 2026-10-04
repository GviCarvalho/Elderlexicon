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
- **Tela:** um campo para as runas (em SGA enquanto se digita, com a leitura embaixo e a contagem `n/10`), um slot para
  o **buril** e um slot para **o que gravar**, e o botão **Gravar** (ou Enter).
- Pôr na mesa um item já gravado mostra o que está gravado nele. Gravar de novo **grava por cima**.
- Uma palavra que o espírito não lê, ou mais de 10 runas, é recusada.
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
- Outras técnicas de ativação podem vir depois.

## 5. Raspar

- **No rebolo:** um item gravado sozinho no rebolo sai sem a gravação, e nada mais muda. Se ele também for
  encantado, os encantamentos saem numa segunda passada, como sempre.
- Depois de raspado, ou mesmo sem raspar, ele pode ser **regravado na mesa**.

## 6. O que existe no código

- `galdraria/Engravings`: lê, grava e raspa; as regras de palavra, linha e coluna.
- `galdraria/GaldrariaTableBlock`, `GaldrariaMenu`, `EngravePacket`, `client/GaldrariaScreen`: a mesa.
- `galdraria/BurinItem`: o buril.
- `galdraria/GrindstoneScraping`: o rebolo.
- `ServerSpellingController.processEngravedItem` e `readMarkedEngravings`: a leitura.

## 7. Perguntas abertas

1. Quais feitiços gravados **mudam o comportamento** do item (por exemplo, numa varinha)?
2. Outras técnicas de ativação além do `surgit` e da marca.
3. A gravação deve aparecer no sprite do item (um brilho, glifos)?

## 8. Decisões tomadas

1. Qualquer coisa pode ser gravada; uma linha de até 10 runas por item. (04/10/2026)
2. A gravação exige uma ferramenta que se gasta aos poucos: o buril, 1 por runa. (04/10/2026)
3. Ativação pelo `surgit` segurando o item, ou chamando pela marca, como os pergaminhos. (04/10/2026)
4. Raspa-se no rebolo; regrava-se na mesa. (04/10/2026)
5. Renomear um item na bigorna dá a ele a marca do nome. (04/10/2026)
