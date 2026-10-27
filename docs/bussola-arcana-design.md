# Bússola arcana: um mostrador para o espírito

Decidido com o autor entre 15 e 16/10/2026. Substitui a Bússola da Vida (`LifeCompassItem`), que lia o corpo direto no
código e mostrava números exatos sem física nenhuma.

A ideia: **o aparelho não tem modos; o feitiço é que decide o que ele mede.** A bússola é um mostrador, as pepitas
marcadas encaixadas nela são as âncoras, e um pergaminho é o programa. O espírito mede (como sempre mediu) e mostra o
que mediu numa pepita.

## O aparelho

- **Receita:** pepitas em losango na bancada, com uma pepita no meio (`_P_ / P P P / _P_`, o meio é a pepita da
  âncora). O losango, que é a caixa, é de um metal só; a pepita do meio pode ser de qualquer um (16/10/2026).
- **As pepitas marcadas.** A pepita do meio é a primeira âncora. Outras podem ser encaixadas depois, **até 5**. Uma pepita
  ganha a marca **renomeada na bigorna** antes de encaixar (a regra que já existe: o nome vira a marca, docs/marks.md).
- **As pepitas ficam dentro do aparelho.** O lugar de cada uma é o próprio mostrador: o que o espírito põe nela aparece
  pequeno, dentro da bússola.
- O mostrador é redondo, como um relógio: cada pepita tem a sua casa no anel.

## Ferro ou ouro

O que muda de um metal para o outro é o que mudaria no mundo real. Para as ilusões, nada: uma ilusão é luz e não pesa.
Para o que **empurra** a pepita (a agulha, o velocímetro), muda a massa, e o ferro tem o que o ouro não tem.

**Cada pepita é do seu metal, e não da caixa** (decidido com o autor em 16/10/2026). Um mostrador de ferro aceita pepitas
de ouro e o de ouro aceita de ferro, misturadas, até 5. Quem pesa, quica e é puxado pela magnetita é a pepita; a caixa é
só a caixa. O metal de cada pepita aparece: a pedrinha e o arco do velocímetro têm a cor dela (cinza claro o ferro,
amarelo o ouro), e o tooltip diz o metal de cada uma. Misturar metais é também o jeito de distinguir duas pedrinhas
no mesmo vidro.

| | Ferro | Ouro |
|---|---|---|
| **Massa** (densidade 7,9 contra 19,3: o ouro pesa 2,4 vezes mais) | leve: reage a pouca força, mas treme | pesado: estável, reage devagar |
| **Agulha** (localizador) | aponta logo, oscila antes de assentar | demora a virar, mas não oscila |
| **Velocímetro** | sensível a pouca velocidade, e chega ao fim da escala cedo | mede velocidades maiores antes de chegar ao fim da escala |
| **Magnetismo** | é atraído pela magnetita (`lodestone`): perto de uma, a agulha de ferro se desvia para ela | não é magnético: nada desvia |
| **Corrosão** | enferruja | não se altera |

- **A massa** é a regra principal: a mesma força move a pepita de ferro 2,4 vezes mais que a de ouro. Mais leve é mais
  sensível e menos estável; mais pesado é o contrário. Nenhum é melhor: são instrumentos para coisas diferentes.
- **O magnetismo** é uma consequência fiel ao mundo real e à magnetita do jogo: quem quiser uma agulha que não se
  engana perto de magnetita usa ouro.
- **A ferrugem** fica de fora por enquanto: no mundo real ela é lenta (anos), e no jogo seria só incômodo.

## O que se mede, e com que feitiço

### O corpo (as quatro fontes)

O espírito revela quanto de uma fonte o sujeito tem, e a ilusão dessa quantidade é posta na pepita:

```
igni quantum ego surgit p1 ubis vocant
aqua quantum ego surgit p2 ubis vocant
aura quantum ego surgit p3 ubis vocant
firmo quantum ego surgit p4 ubis vocant
leitura chronos 1 surgit
```

- Num pergaminho marcado `leitura`. A última linha relê o pergaminho a cada segundo: a leitura fica viva, e cada volta
  paga as suas ilusões (uma torneira aberta).
- Fogo, água, ar e terra crescem e diminuem dentro do mostrador, como um termômetro redondo.
- `ego` é quem conjura; uma marca no lugar dele lê outro corpo (um paciente, um companheiro).
- Esquecer o `surgit` leva todo o fogo do corpo até a pepita: o mago morre de hipotermia. O espírito é literal.

### Onde está (o localizador)

O `iactare` empurra a pepita na direção de outra marca; presa no centro, ela vira uma **agulha** que aponta para o que
foi marcado. É literalmente uma bússola, em que o norte é o que se quer.

```
p1 alvo ubis iactare
```

O empurrão se apaga, então a agulha precisa do mesmo loop para continuar apontando. (O autor tem outra ideia para o
localizador, para depois.)

### A que velocidade (o velocímetro)

`aura alvo ligabis p1`: o vínculo de movimento força a pepita a andar como o alvo. Presa no aparelho, ela não sai do
lugar, e a força com que tenta é a velocidade. O `aura` paga por bloco corrigido, então **medir algo rápido custa
mais**.

## As mudanças na língua

Duas, pequenas, e nenhuma runa nova:

1. **No `surgit`, uma fonte com `quantum` sem número e um sujeito revela quanto daquela fonte o sujeito tem.** Fica
   restrita ao `surgit`: em outro verbo, uma marca depois do `quantum` continua sendo o que o verbo move (`firmo quantum
   m1 iactare` empurra m1 com toda a terra do corpo).
2. **O `surgit` vira ilusão mesmo com um lugar entre ele e o verbo.** Hoje ele só é ilusão com o verbo logo depois;
   `igni quantum ego surgit p1 ubis vocant` põe o lugar colado no verbo, como o livro (`igni 10 ubis vocant`). Uma marca
   seguida de `ubis` já é sempre um lugar, então isso não confunde o `surgit r2` ("leia r2").

Uma terceira, para o localizador: **duas marcas antes de um lugar são o que o verbo move e para onde**
(`p1 alvo ubis iactare`). Antes, duas marcas antes do `ubis` eram um erro; uma, três ou números continuam como eram.

## Implementado (16/10/2026)

- **O item** (`item/ArcaneCompassItem`): `iron_arcane_compass` e `gold_arcane_compass`. As marcas das pepitas ficam no
  NBT (`Nuggets`), e o que cada pepita mostra também (`Readings`: o tipo, o elemento, a quantidade, a direção, a
  velocidade e até quando), e o metal de cada pepita (`NuggetMetals`; um mostrador de antes, sem essa lista, tem
  todas do metal da caixa). Mudar o que ele mostra não refaz a animação do item na mão.
- **A receita** (`item/ArcaneCompassRecipe`): o losango de pepitas de um metal e a do meio de qualquer um; se a do meio foi renomeada,
  a marca dela é a primeira âncora (uma do meio sem nome dá um mostrador vazio).
- **Encaixar pepitas**, na bigorna (`item/ArcaneCompassAnvil`: o mostrador à esquerda e uma pepita marcada de ferro
  ou de ouro à direita, por 1 nível) **ou na bancada** (o mostrador e a pepita marcada, em qualquer casa). A mesma marca não
  entra duas vezes. Um nome digitado na bigorna renomeia o mostrador; o nome que ele já tem, não (daria a ele uma marca).
- **A marca aparece no tooltip** de qualquer item (`item/MarkTooltips`); um item renomeado cujo nome não virou marca
  (uma runa, uma letra só, só números) diz isso. Antes não havia como saber, e uma pepita renomeada "igni" não entrava em
  mostrador nenhum, sem explicação.
- **As pepitas como lugares** (`spell/function/Dials`): a pepita é achada no inventário (mãos e mochila) de qualquer
  jogador.
  - **A medida** (`Dials.measure`, chamada pelo `vocant` de imagem): lê do corpo do sujeito sem escrever nada, e põe a
    imagem na pepita. Sem sujeito escrito, o corpo é o de quem conjura, como toda fonte sem `tenet`.
    **Uma imagem não tira nada do corpo** (`FlowInterpreter`): antes, um `quantum` sem número tirava toda a fonte do corpo
    mesmo numa ilusão, e a medida do fogo matava o mago de hipotermia. **Custa a ilusão de 1/81 do que mostra**: uma pepita é um nono de
    lingote, um lingote um nono de bloco, e a imagem é desse tamanho. Fora de uma pepita, a imagem tem o tamanho do que
    mostra, e custa como tal.
  - **A agulha** (`Dials.push`, chamada pelo `iactare` de uma marca): a direção da pepita até o lugar, no plano; custa o
    arremesso da massa da pepita. Uma pepita de ferro a até 6 blocos de uma magnetita é puxada por ela, mais quanto mais
    perto.
  - **O velocímetro** (`Dials.pull`, a cada tick do item no inventário): os vínculos `aura` hierárquicos com a pepita como
    filha. Segurar a pepita é movê-la: o dono paga a massa da pepita carregada pela distância que o pai andou
    (`MarkCost.movement`), uma vez por segundo; sem pagar, o vínculo quebra. Um `ligabis` cuja última marca é de uma
    pepita não marca o que o mago mira.
- **A textura** é a da bússola do jogo sem a agulha: o disco visto um pouco de cima, o vidro uma elipse escura. O ferro
  tem as cores dela; o ouro, a paleta do relógio do jogo. 16x16.
- **O mostrador** (`client/ArcaneCompassRenderer`) desenha nos pixels do próprio vidro, como a bússola do jogo desenha a
  agulha:
  - **A roda.** As imagens de todas as pepitas fazem **uma roda só**, dividida entre os elementos na proporção do que
    cada um tem: quanto mais abundante, mais da roda ele toma (decidido com o autor em 16/10/2026). Um corpo inteiro é
    mais da metade água, um terço ar e um fio de fogo. Cada elemento presente tem ao menos um pixel; o fogo tremula.
    Não há escala: a roda mostra a proporção, não a quantidade.
  - **A pedrinha** (16/10/2026, pedido do autor: a agulha parecia a de uma bússola qualquer). A pepita empurrada é um
    pixel solto dentro do vidro, da cor do metal, com uma sombra embaixo. O empurrão a aperta contra a borda do vidro, do
    lado do alvo; ela bate, quica e assenta. Ela fica parada no mundo, como uma bolinha numa tigela: girar com o
    mostrador na mão não a leva junto, e ela escorrega pela borda até o lado do alvo de novo. A de ferro é leve: dispara
    e quica. A de ouro é pesada: rola devagar e fica onde cai. Sem empurrão, ela rola até parar onde está. Cada pepita
    empurrada é uma pedrinha, até 5 no mesmo vidro, e elas **se chocam**: se afastam, e a de ouro, mais pesada, tira a de
    ferro do caminho quase sem se mexer. O vidro tem 12×6 pixels, então duas pedrinhas para o mesmo lado ainda podem cair
    em pixels vizinhos.
  - **O velocímetro** acende a borda do vidro a partir do alto, no sentido do relógio, até o fim da escala (10 blocos/s no
    ferro, 25 no ouro); o de ferro treme. Com várias pepitas puxadas, o arco mais longo vem por baixo e o mais curto
    por cima, cada um na cor do seu metal.
- **Um vínculo de dono ausente fica suspenso** também no velocímetro: não puxa nem é cobrado (docs/ligabis-design.md, 4).
- **O programa pela marca do mostrador** (16/10/2026, pedido do autor depois de testar no jogo): o mostrador renomeado com
  a marca do pergaminho que o programa. Com ele na mão, um `surgit` lê esse pergaminho (docs/rituais-design.md), esteja
  ele numa moldura ao toque ou carregado. **Em fluxo**, o mostrador na mão é lido de novo **a cada segundo**
  (`FlowState`, `ServerSpellingController.readHeldDial`): a imagem dura 2 s, então ele não apaga enquanto é segurado.
  Cada leitura paga a sua ilusão, uma torneira aberta como o próprio fluxo. Só o mostrador é lido assim: qualquer outra
  coisa marcada na mão continua precisando de uma condição para despertar em fluxo (docs/fluxo-design.md), senão uma
  espada com `igni iactare` lançaria fogo a cada segundo.
  Antes, o velocímetro cobrava o dono offline, não conseguia e quebrava o vínculo.
- A Bússola da Vida antiga continua no jogo por enquanto.
- Testes: `DialGrammarTest` (a gramática) e `DialGameTests` (a pepita renomeada e encaixada na bigorna, a bancada, a
  medida que não tira nada do corpo, a agulha, a magnetita e o velocímetro).
- **Não verificado jogando:** o desenho do mostrador na mão, no inventário e na moldura.

## Em aberto

- A outra ideia do autor para o localizador.
- O custo do loop de leitura: quatro ilusões pequenas por segundo precisam ser baratas o bastante para se manter ligadas
  numa viagem.
