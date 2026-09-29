# Plano: a rosa dos elementos

Continuação de `plano-materia-emergente.md` e de `estudo-referencias.md`. Aqui a rosa (o plano cartesiano dos quatro
elementos) e a tensão viram a base de tudo o que a magia faz no mundo: criar matéria, soltar fenômenos e bater.

## 1. Decisões

| Pergunta | Decisão |
|---|---|
| A escada de estados e a rosa | **Separadas.** O estado é como a matéria está (sólida, líquida, gás, plasma); a rosa é o que ela é. |
| Quanto o impacto pode destruir | **Configuração** do servidor: quebra blocos sempre, nunca, ou segue a regra `mobGriefing` do mundo. |
| Reações com chance ou sempre iguais | **Sempre iguais.** O ambiente (temperatura do bioma, umidade, altitude) entra depois como entrada das leis, sem sorteio. |

## 2. A rosa e a altura

```
                aura (↑ leveza)
                     │
     água + ar       │      fogo + ar
                     │
aqua ◄──── frio ─────●───── calor ────► igni
                    vis
     água + terra    │      fogo + terra
                     │
                firmo (↓ peso)
```

Cada porção, seja matéria ou energia, tem:

- **Posição na rosa:** o caráter. x vai do frio ao calor (fogo menos água); y vai do peso à leveza (ar menos terra).
- **Tensão:** a altura acima da rosa. Ela vem de duas fontes:
  - **latente:** opostos juntos (o menor de fogo e água, e o menor de terra e ar);
  - **agitação:** energia colocada na porção, por UMU. Essa energia vem da concentração (`chronos 0`), da velocidade (o
    empurrão do `iactare`) e do calor.
- **UMU** e **estado**, como hoje.

A altura decide o que a porção é:

- **Tensão baixa é matéria.** Ela para, tem forma e se assenta no código de uma coisa natural (terra, pedra, madeira,
  osso, carne, ferro) ou fica informe. É o lado da criação.
- **Tensão alta é fenômeno.** É energia que não para e se descarrega: relâmpago, vapor, rajada, estilhaço, erupção. É o
  lado da destruição.
- **Um fenômeno descarrega e o que sobra volta a ser matéria.** O relâmpago deixa a água cair como chuva; o vapor esfria
  e volta a ser água; o redemoinho perde força e larga o que carregava.

## 3. As leis

As leis falam só de propriedades, nunca de nomes (lição do Breath of the Wild). As propriedades saem da composição e da
agitação:

| Propriedade | De onde vem |
|---|---|
| calor | a parte de fogo, mais a agitação térmica |
| umidade | a parte de água |
| leveza | a parte de ar |
| massa | a parte de terra, vezes o UMU |
| movimento | a agitação cinética (velocidade) |

As `ElementProperties` do sistema de cenas e as `Qualities` da matéria viram uma coisa só.

| Lei | O que acumula | O que solta ao passar do limite | O que sobra |
|---|---|---|---|
| **Fricção** | carga, de umidade vezes a diferença de movimento | **descarga:** um relâmpago | água, que cai |
| **Térmica** | pressão de vapor, de calor vezes umidade | **estouro de vapor** | água, ou nada se o fogo venceu |
| **Pressão** | expansão, de calor vezes leveza | **rajada**, que empurra e acende | ar quente, que sobe |
| **Estilhaço** | tensão mecânica, de leveza contra massa | **estilhaços:** a terra arremessada | pó, que cai |
| **Impacto** | energia cinética, metade da massa vezes a velocidade ao quadrado | **golpe:** dano e blocos quebrados pela dureza | a matéria, que pousa |

- As quatro primeiras já existem em parte: a fricção e a térmica estão no sistema de cenas (`SceneLaws`). O plano estende
  essas leis para a matéria e acrescenta as outras três.
- As formas extremas que já existem (plasma, poço de gravidade, buraco negro, gelo VII, bomba barotérmica, explosão de
  vis) continuam como o que acontece em cada região da rosa quando a tensão é muito alta.
- A vis puxa para o centro. Somada, ela neutraliza a mistura. Solta de uma vez, é tensão pura.
- **Sempre iguais:** as mesmas entradas dão o mesmo resultado, com energia conservada. O que é solto nunca vale mais do
  que o que foi pago.

## 4. Entrega e efeito

Lição do Ars Nouveau: o verbo de força **entrega** e o que é entregue **age onde bate**.

- **O lançamento condensado.** O `vocant` que condensa passa a esfera ao verbo seguinte. O `iactare` a lança, e ela se
  solta **no impacto**: calor, rajada, gelo ou terra densa, conforme o que ela é.
- **O impacto** vale para tudo o que voa: a esfera, a matéria empurrada, uma coisa marcada. Ele segue a lei do impacto:
  - dano;
  - quebra de blocos mais fracos que a energia, pela configuração;
  - empurrão;
  - som, com volume e tom pela energia.
  
  O caráter do que bate age junto: o quente queima, o molhado apaga.

## 5. Etapas

1. **Parte pura:**
   - a rosa (posição, tensão latente e agitação a partir da composição e da energia);
   - as propriedades unificadas;
   - a lei do impacto (energia cinética e o que ela quebra pela dureza);
   - os limites de tensão entre matéria e fenômeno.
2. **Entrega e impacto no mundo:**
   - um resolvedor único de impacto (dano, blocos, empurrão, som, caráter);
   - o lançamento condensado entregue ao verbo de força;
   - a opção de configuração da destruição.
3. **Fenômenos da matéria:**
   - a matéria agitada acumula tensão pelas leis da seção 3 e solta os fenômenos;
   - a reação dos opostos da L5 vira as leis térmica e de estilhaço;
   - o que sobra se assenta.
4. **Um sistema só:** as cenas (fluxos de energia) passam a usar as mesmas propriedades e o mesmo resolvedor que a
   matéria.
5. **Grimório:** o desenho da rosa (onde a mistura está e para onde cada runa puxa) e a leitura do ponto de uma coisa
   pelo `surgit`.
6. **Testes e documentos:** testes puros de cada lei, GameTests com feitiços reais para cada fenômeno e para o impacto,
   e a referência do grimório.

## 6. Como ficou

### Etapas 1 e 2

- **Parte pura** (`magic/matter`):
  - `Rose`: a posição (x do frio ao calor, y do peso à leveza) e a tensão latente de cada eixo. A vis conta como um
    quarto de cada aspecto, então ela é o centro com tensão máxima. O caráter (|x| + |y|) e a tensão latente sempre
    somam a porção inteira: é uma troca de coordenadas, nada se perde.
  - `Agitation`: a energia posta na porção, por UMU. É cinética (metade da massa vezes a velocidade ao quadrado),
    térmica (de nada no fogo comum a 1 no plasma) ou de concentração (de nada em 1 UMU a 1 no buraco negro).
  - `Tension`: a latente (presa num sólido, livre num fluido) mais a agitação. Acima de 0,2 (a mesma linha da
    reação dos opostos) a porção é fenômeno; abaixo, matéria. Terra, pedra, madeira, ferro, osso e carne são matéria.
  - `Qualities` ficou sendo as propriedades de matéria e de energia: vale para uma composição, para um aspecto ou
    para quantias com vis, e dá a densidade (a massa de um UMU).
  - `ImpactLaw`: a energia do golpe, o dano, a parte que quebra blocos (cada um custa a sua dureza), o raio, o
    empurrão, o volume e o tom, e quando o golpe vira explosão.
- **Mundo:**
  - `Impacts` é o resolvedor único: dano e empurrão em quem foi atingido; explosão com dano e empurrão em volta; os
    blocos mais próximos primeiro, cada um quebrando quando o que sobra da energia paga a sua dureza; o som do bloco
    atingido e o do golpe; e o caráter (o quente queima e acende, o molhado apaga).
  - O que o `iactare` empurra (`firmo vocant iactare`, `firmo tenet vocant iactare`) bate pela lei do impacto antes de
    fazer o que faz onde cai. A matéria tirada do mundo bate com a densidade que tem.
  - **O lançamento condensado.** O `vocant` que condensa sabe, antes de juntar, que o verbo seguinte vai levar o que
    ele faz. Então junta a esfera diante da mão (ou no lugar do `ubis`) e passa essa mesma esfera ao `iactare`, que a
    lança. Ela se solta onde bate: terra densa, gelo VII, rajada, vis, ou o fogo com o seu ponto quente.
  - A opção `magic.breaksBlocks` (`ALWAYS`, `NEVER`, `MOB_GRIEFING`, que é o padrão) vale para o impacto e para todas
    as explosões da magia.
- **Testes:**
  - puros: a rosa, a tensão e a lei do impacto;
  - no jogo: a terra condensada que voa como a própria esfera, quebra o vidro e vira um bloco denso; um golpe que
    quebra pedra, fere um porco e não quebra nada quando a configuração proíbe.
- **Fica para depois:** o impacto das coisas marcadas que são arremessadas (a massa delas usa outra medida) entra
  junto com as etapas 3 e 4.
