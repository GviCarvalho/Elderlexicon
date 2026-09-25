# Rituais — cadência de pergaminhos (v1)

**Status:** decidido com o autor e implementado em 25/09/2026. Ainda não testado dentro do jogo.
**Relaciona-se com:** `marcas-como-runas-design.md` (marcas como runas, `chronos`), `ligabis-design.md` (vínculos), `ligabis-linked-frames.md` (pergaminhos com a mesma marca disparando juntos).

## 1. A ideia

Magia avançada, no Elder Lexicon, não é fusão: é **cadenciar feitiços**. O mago escreve pergaminhos que chamam outros pergaminhos, em sequências ditadas pelos próprios feitiços. É um ritual.

No livro, `surgit` é o "comando de leitura" (cap. 5.1): o espírito lê o pergaminho. Então `surgit` com uma marca como sujeito manda o espírito ler os pergaminhos que têm essa marca.

## 2. Gramática

| Escrito no pergaminho | O que faz |
|---|---|
| `r2 surgit` | lê os pergaminhos marcados `r2` (no tick seguinte) |
| `r2 chronos 3 surgit` | lê os `r2` 3 segundos depois |
| `surgit r2`, `chronos 3 surgit r2` | a mesma coisa, com a marca depois ("leia r2") |

- **Sequência:** marcas diferentes, uma chamando a outra, com `chronos` entre elas.
- **Simultaneidade:** vários pergaminhos com a mesma marca são lidos juntos.
- **Ramificação:** um pergaminho pode ter várias linhas `rX surgit`.
- **Loop:** o último passo chama o primeiro (`r1 chronos 10 surgit`). Sem `chronos`, a leitura espera 1 tick, então um pergaminho que chama a si mesmo vira um loop, e não uma recursão.
- **Nomear:** `r1 surgit reframe ritual`, e depois basta dizer `ritual`.

Exemplo de ritual em loop:

| Pergaminho | Texto |
|---|---|
| marcado `r1` | `aqua vocant` ⏎ `r2 chronos 3 surgit` |
| marcados `r2` (vários) | `igni iactare` ⏎ `r3 surgit` |
| marcado `r3` | `r1 chronos 10 surgit` |

## 3. Alcance e vínculo (decidido)

- **Sem vínculo:** o espírito só lê os pergaminhos **ao alcance do toque** do mago (o alcance de interação dele).
- **Com vínculo:** `vis eu ligabis r1` (mirando na moldura ou no pergaminho, que recebe a marca `r1`; o mago precisa carregar a marca `eu`) cria um **vínculo de leitura**, um aspecto novo do Ligabis (`Aspect.VIS`) sem efeito físico. Com ele, o espírito lê os `r1` **em qualquer lugar da dimensão**: os pergaminhos marcados ficam num índice salvo no mundo (`LigabisData.scrolls`), e os que estão em chunk descarregado têm o chunk carregado por um instante para serem lidos. **O loop do ritual não é um vínculo por si só:** sem `vis ... ligabis`, cada passo só alcança o toque, e se afastar quebra a corrente. O vínculo vale nos dois sentidos (`vis r1 ligabis eu` também serve).

## 4. Custo e perigo (decidido)

- Cada leitura é um feitiço normal, pago pelo mago que iniciou o ritual (XP → saciedade → vida). Quando o Vis acaba, o espírito cobra em carne (livro, cap. 3.2.1): um ritual em loop pode matar o mago. É proposital: magia é perigosa.
- Não há cooldown entre os passos de um ritual.

## 5. Como parar (decidido)

Tirando a marca de um pergaminho: a próxima leitura não o encontra e a corrente quebra ali.

## 6. No código

| Peça | Papel |
|---|---|
| `SurgitFunctionHandler` | `rX surgit`: agenda a leitura (1 tick, ou o `chronos`) |
| `ServerSpellingController.readMarkedScrolls` | acha os pergaminhos com a marca (no toque, ou na dimensão inteira com vínculo) e lança cada um, sem cooldown |
| `ligabis.Aspect.VIS`, `ligabis.ReadingBond` | o vínculo de leitura e a regra de quando o mago está vinculado |
| `LigabisManager.boundForReading`, `isScrollCarrier` | consulta o vínculo e aceita molduras e pergaminhos como alvo do `vis ... ligabis` |

## 7. Limites da v1

- **A marca fica no item:** marcar a moldura ou o pergaminho no chão também grava a marca no pergaminho; tirá-lo e colocá-lo de novo mantém a marca.
- Com vínculo, a busca percorre todas as entidades carregadas da dimensão a cada leitura; se rituais grandes pesarem, vale indexar as molduras marcadas.
- As mensagens de cada passo vão para o chat do mago ("Ritual: ...").
