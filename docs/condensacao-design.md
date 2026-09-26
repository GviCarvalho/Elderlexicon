# Condensação: a intensidade dos elementos

**Status:** desenho em discussão (26/09/2026). Nada disto está implementado ainda. A seção "O que já existe" lista as
peças do mod que este desenho aproveita.

## A lei

A lei vem do livro, cap. 3: *"a energia nunca pode ser criada e nunca pode ser destruída; ela apenas muda de forma"*.
Toda UMU manifestada precisa vir de algum lugar.

Toda energia de um elemento é **intensidade × extensão**, como na física, onde se multiplica uma grandeza intensiva
por uma extensiva:

| Elemento | Intensidade (a qualidade de cada unidade) | Extensão (quantas unidades) | Na física |
|---|---|---|---|
| firmo | densidade: quantos kg cabem num bloco | blocos | massa = densidade × volume |
| igni | calor: quão quente é a chama | chamas ou golpes | calor = temperatura × massa |
| aura | pressão: quão forte é o vento | volume de ar | energia = pressão × volume |
| aqua | pressão: quantos litros estão num ponto | pontos ou blocos | energia = pressão × volume |

**UMU = intensidade × extensão**, mais o custo das runas.

## A regra: a intensidade é conquistada, não escrita

- **O `quantum` é sempre extensão.** Ele diz *quantas unidades*, nunca *quão intensas*. **Não existe `quantum` antes
  da fonte para dar intensidade.** Se bastasse escrever para ter um fogo fortíssimo, a técnica de juntar o elemento não
  teria propósito.
  - O `quantum` antes da fonte continua existindo **na revelação** (`quantum 2 firmo surgit`), mas lá ele *mede* o que
    se procura e não cria nada.
- **Sem captura, a intensidade é 1, o elemento comum:** chama comum, terra solta, água parada, brisa. A energia vem do
  corpo. `igni quantum 10 iactare` = 10 unidades × 1 = 10 UMU, como no livro (4.3.2).
- **Com captura, a intensidade é a energia capturada ÷ extensão:**
  - `igni exsugat quantum iactare`: tudo o que foi puxado, na extensão natural. O anel de 44 fogos vira 44 golpes comuns.
  - `igni exsugat quantum 1 iactare`: tudo o que foi puxado **num ponto só**. 44 UMU numa unidade = **intensidade 44**.
  - `igni exsugat quantum 4 iactare`: 44 UMU em 4 unidades = intensidade 11 em cada uma.
- **O que é capturado já traz a intensidade que tinha.** Uma poça de lava tem pouco volume e muito calor, então, puxada,
  dá um fogo intenso já na extensão natural. "O que se tira é exatamente o que se dá" (4.3.2).

### Exemplo: o anel condensado

```
página 1:  igni eu ubis quantum 40 chronos 30 vocant
           igni eu ubis quantum 40 chronos 30 impediunt
página 2:  igni exsugat quantum 1 iactare
```

O mago acende o fogo à sua volta e o empurra para formar o anel. Depois puxa o anel inteiro, condensa tudo num ponto e
lança. É um fogo muito mais quente que qualquer iactare escrito direto, e ele só existe porque o mago juntou esse fogo antes.

## Condensar vai contra a natureza

Na realidade, o calor quer se espalhar e a pressão quer escapar. Então:

1. **Condensar exige trabalho do espírito.** Pode ter um custo próprio, além da energia capturada (em aberto).
2. **O que foi condensado é instável.** O fogo esfria e a pressão vaza. O mago usa logo, na mesma frase, ou paga para
   segurar com `chronos`, como uma torneira aberta. Exceções estáveis: o que a condensação transforma em outra matéria
   (a rocha densa, o gelo de pressão, o diamante).

## Cada elemento condensado

As faixas abaixo são **rascunho**, com números para discutir. Em cada faixa: o que aconteceria de verdade, e uma ideia
de como aparece no Minecraft.

### Fogo: juntar o calor de uma área num ponto

| Calor | O que acontece | No jogo |
|---|---|---|
| 1 | chama comum | o fogo de hoje |
| ~3 | chama branca e azulada | aparência de fogo de alma; derrete neve e gelo; cozinha itens no chão; areia vira vidro |
| ~10 | **derrete a pedra** | onde cai, a pedra vira lava: a lava é o chão derretido, não o fogo; a água em volta vira vapor de uma vez |
| ~30+ | **plasma**, a matéria do raio e das estrelas; o livro fala do "calor do plasma" nos círculos (10.1.1) | uma bola que explode ao tocar, com dano elétrico e uma cratera de pedra derretida |

O dano e a duração da queimadura nas criaturas crescem com o calor.

### Terra: juntar massa num volume

Aqui a intensidade **é o material**, porque densidade é matéria. A escala é a mesma que a revelação já usa
(UMU por bloco = dureza):

| Densidade | Material |
|---|---|
| 0,5 | terra solta, areia |
| ~1,5 | pedra |
| ~3 | ardósia (rocha metamórfica) |
| ~50 | obsidiana |
| carbono condensado | **carvão vira diamante** (ideia: condensar carvão muito acima de um limite) |
| além da obsidiana | a massa começa a **puxar as coisas em volta**, um mini poço de gravidade (ideia) |

Um bloco denso é mais difícil de quebrar e, lançado, cai como um meteoro. O dano cresce com a massa.

### Água: litros num ponto são pressão

| Pressão | O que acontece | No jogo |
|---|---|---|
| 1 | água parada | uma fonte |
| ~3 | jato | empurra criaturas, apaga fogo |
| ~10 | jato que **corta** | leva embora terra, areia, cascalho e plantações |
| ~30+ | **gelo VII**: gelo formado só pela pressão, sólido até em temperatura alta (existe preso dentro de diamantes) | um "gelo de pressão" que não derrete; ao ser quebrado, **solta toda a água comprimida de uma vez**: uma inundação saindo de um bloco (a conservação) |

### Ar: pressão, calor e frio

O ar comprimido guarda energia de três jeitos:

| Pressão | O que acontece | No jogo |
|---|---|---|
| 1 | brisa | empurra itens e partículas (o vento de hoje) |
| ~3 | rajada | empurra criaturas |
| ~10 | ventania | apaga chamas pequenas; quebra folhas, flores e vidro fino |
| ~30+ | **bomba de pressão** | solto de uma vez, vira onda de choque: uma explosão sem fogo |

E as duas faces térmicas, para as interações:
- **Comprimido rápido, o ar esquenta.** É assim que o motor a diesel acende sem faísca. Ar condensado + fogo = um maçarico
  mais quente que o fogo sozinho.
- **Expandindo de repente, o ar gela.** É assim que funcionam a geladeira e o ar-condicionado. Ar condensado solto
  perto da água congela a água.

## O que já existe no mod

- **A captura** (`docs/exsugat-vertere-design.md`): o exsugat puxa fontes inteiras, com o valor de cada uma, e as
  funções seguintes gastam esse valor.
- **O `quantum` sem número depois do exsugat** puxa tudo ao alcance e entrega à função seguinte.
- **O anel** (`docs/impediunt-design.md`): o vocant alimentando e o impediunt empurrando formam o anel na hora, e o
  exsugat alcança o anel inteiro.
- **A escala da terra** (UMU por bloco = dureza) já é usada pela revelação e pelo exsugat.

O que falta é justamente a intensidade: hoje, todo fogo capturado volta a ser fogo comum.

## Decisões em aberto

1. **Custo de condensar:** só a energia capturada, ou também um custo pelo trabalho do espírito?
2. **Instabilidade:** quanto tempo o condensado dura sem `chronos`? É só o instante da própria frase?
3. **Os limites das faixas:** os números acima (3, 10, 30) são rascunho.
4. **Custo das funções:** hoje um iactare de 10 UMU custa ~3 ao corpo, com custos base fixos por runa. Pela lei do
   cap. 3 seria 1:1 (10 UMU de efeito = 10 UMU pagos). Isso muda muito o peso da captura e precisa ser decidido
   junto com isto.
5. **Condensar sem lançar:** `igni exsugat quantum 1 vocant` cria o elemento condensado parado num ponto (uma chama
   de calor 44 no chão, um bloco de gelo de pressão). Deve valer?
6. **As interações entre elementos condensados:** o ar comprimido com fogo, e o ar expandindo com água.
