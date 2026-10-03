# Vita: criar vida

**Status:** a lei da animação está implementada (28/09/2026), falta testar no jogo. Dar alma, reviver e a cura com
diagnóstico são os próximos passos.

> **Nota (docs/particulas-design.md, etapa 2, 30/09/2026):**
> - Os seres e as proporções abaixo estão agora na tabela única (`materials.json`, seção `beings`), e a lei os lê de
>   lá. Um addon traz seres no mesmo formato.
> - O corpo é pesado em partículas (256 fazem uma UMU). A matéria trazida do mundo com `tenet … vocant` no mesmo
>   instante e lugar também entra nele: são os ingredientes.
> - Um GameTest faz um homúnculo com 25 carnes do chão, 36 de ar, 40 de água e a âncora.
> - Um corpo exatamente entre dois seres vira o que a tabela lista primeiro, e não o que o arredondamento escolheria.

## A ideia

A vida não é um elemento: é uma **mistura desequilibrada dos quatro** (`ReadmeVITA.md`), numa proporção que cada tipo
de ser tem, e **ancorada** numa reserva de **100 UMU de vis**, a mesma em todo ser vivo (livro 3.2, a Âncora dos
Cem). Então, se as energias forem dadas **na proporção exata**, com a âncora, **elas se ligam num ser**.

O que o livro diz ("a magia pode tirar vidas, mas nunca devolvê-las") é a visão de um mago daquele mundo, não uma
lei. Pela lógica do sistema, tudo é possível com precisão suficiente, inclusive reviver.

## A lei da animação

Tudo que um `vocant` invoca espera até o fim do tick (`Quickening`), e as energias soltas **no mesmo instante** e **no
mesmo lugar** (a até 2,5 blocos umas das outras) são pesadas juntas (`spell/life/Animation`):

- Se entre elas há **vis ≥ 100** (a âncora) e um **corpo** (água, ar, fogo e terra) de pelo menos 5 UMU, **elas se
  ligam num ser** e não viram água, rajada, fogo, pedra nem luz. A vis além da âncora sai como luz.
- Se não, cada uma toma a forma que tomaria, como antes.

"O mesmo instante" é o que já existe: linhas na mesma coluna de uma página, ou peças do mesmo anel de um círculo.

## Quem nasce

Cada tipo de ser tem a sua proporção (fração do corpo em água / ar / fogo / terra):

| Ser | Água | Ar | Fogo | Terra |
|---|---|---|---|---|
| homúnculo (a proporção de uma pessoa) | 0,55 | 0,38 | 0,02 | 0,05 |
| vaca | 0,60 | 0,30 | 0,03 | 0,07 |
| porco | 0,58 | 0,32 | 0,04 | 0,06 |
| ovelha | 0,54 | 0,36 | 0,03 | 0,07 |
| galinha | 0,48 | 0,46 | 0,03 | 0,03 |
| lula | 0,82 | 0,10 | 0,01 | 0,07 |
| morcego | 0,35 | 0,60 | 0,03 | 0,02 |
| slime | 0,70 | 0,05 | 0,01 | 0,24 |
| cubo de magma | 0,10 | 0,05 | 0,45 | 0,40 |
| blaze | 0,02 | 0,38 | 0,58 | 0,02 |
| golem de neve | 0,72 | 0,24 | 0,00 | 0,04 |
| golem de ferro | 0,05 | 0,10 | 0,05 | 0,80 |

- Nasce o ser de proporção **mais próxima** da dada (a menor soma das diferenças).
- A **vida** dele é o **corpo ÷ 5** (100 UMU de corpo = 20 HP), qualquer que seja o tipo: uma vaca de 200 UMU tem 40 HP.
- O **homúnculo** é criatura do mod: uma figura de pessoa, de carne pálida e inacabada, sem rosto (só as órbitas).
  Seus traços vêm do corpo que recebeu: mais ar, mais rápido; mais terra, mais armadura e mais firme; fogo a partir de
  20% do corpo, imune a fogo.

## Precisão: o que sai impreciso nasce defeituoso

A diferença do corpo dado para o do seu tipo, elemento por elemento, é guardada no ser. A cada 2 s ele sofre o que
cada excesso ou falta causa (`ReadmeVITA` 5), mais forte quanto mais longe:

| Elemento | Excesso | Falta |
|---|---|---|
| água | lentidão | fraqueza; muito longe, seca por dentro |
| ar | fraqueza | sufoca (dano de afogamento) |
| fogo | pega fogo | congela |
| terra | lentidão | definha (dano de fome) |

Faixas: menos de 3 pontos percentuais de diferença é são; até 8, leve; até 20, grave; além, mortal. Um corpo muito
fora da proporção de um tipo já é, na prática, de outro tipo (uma pessoa com fogo no lugar do ar fica mais perto de um
porco).

## Sem kern

O ser nasce **sem kern** (o eu espiritual, em Nahvá): vive, respira, sangra e pode morrer, mas **não age**. Ele não
tem vontade, e continua assim depois de recarregar o mundo. Ganhar um kern é a próxima etapa.

## Incorporação: o teu kern num corpo sem kern

Um corpo sem kern não tem vontade, e nada nele resiste a um kern que entre: **o mago vive nele como no próprio**. Um
ser com kern (qualquer criatura comum) tem vontade própria e recusa; os vínculos com ele só veem por ele ou o puxam.

- **Duas portas** (`Incorporations`):
  - **`m1 surgit`**: se a marca m1 está num corpo sem kern, o kern do mago entra nele (se não, o `m1 surgit` segue
    como antes: revelação e leitura dos pergaminhos com a marca);
  - **aura + surgit**: o vínculo de visão (`surgit m1 ligabis`) num corpo sem kern com o qual o mago já tem um vínculo
    de **aura** (`aura m1 ligabis`, movimento) leva o kern para dentro dele.
- **Dentro dele**: a câmera está nos olhos do corpo e gira com o mouse; as teclas de andar, o pulo (e nadar para cima)
  e o botão de atacar vão para o corpo; ele bate com as mãos e com **o que ele segura** (só os itens dele). O corpo do
  mago fica parado, em transe.
- **Duração**: o `chronos` (2 s sem ele), paga de uma vez a **1 UMU por segundo**.
- **Saída**: acaba o tempo; o corpo do mago leva dano (o kern volta na hora); ou **o corpo emprestado morre**: o kern
  volta **num choque** (náusea, lentidão e fraqueza por alguns segundos).
- Ainda não dá para usar itens (clique direito) nem quebrar blocos com o corpo emprestado.

## Exemplo: um homúnculo

Uma página com as cinco linhas soltas na mesma coluna, mirando o mesmo ponto:

```
A F N VV J        aqua exsugat quantum 55 vocant
B F N TY J        aura exsugat quantum 38 vocant
C F N S J         igni exsugat quantum 2 vocant
D F N V J         firmo exsugat quantum 5 vocant
. E N RQQ J       vis quantum 100 vocant   (a pausa alinha a soltura na mesma coluna)
```

A água, o ar, o fogo e a terra vêm do mundo ao redor da mão; a vis (a âncora) vem da experiência do mago (1000
pontos de XP).

## Próximos passos

- **Dar um kern próprio**: a etapa que dá alma (vontade) permanente a um ser criado. A desenhar. (Emprestar o kern do
  mago por um tempo já existe: a incorporação.)
- **Reviver**: recriar o corpo exato de quem morreu (a mesma proporção e o mesmo tamanho) e reencontrar o seu kern.
  Pede o registro do corpo e do espírito de quem morre. A desenhar.
- **Cura com diagnóstico**: ler a vita de um ferido (quanto lhe falta de cada elemento) e injetar exatamente o que
  falta. Um "raio curativo" genérico não existe.
- As composições de mais criaturas, e ajustar as que existem jogando.
