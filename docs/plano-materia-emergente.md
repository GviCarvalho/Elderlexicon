# Plano: matéria por emergência

Continuação de `plano-materia-e-forca.md`. Lá, uma mistura só era alguma coisa se batesse com uma receita da tabela;
fora disso era um amálgama que se desfazia em 20 segundos. Isso é uma consulta a uma lista, não emergência. Aqui toda
mistura é matéria de verdade, e o que ela faz sai do que ela é.

## 1. A matéria

- Uma porção de matéria continua sendo **composição** (as partes de terra, água, ar e fogo), **estado** (a escada:
  sólido, líquido, gás, plasma) e **UMU**.
- **Não há receita.** Nenhuma composição é inválida e nenhuma se desfaz por não estar numa lista.
- **O código das coisas naturais continua.** O `materials.json` diz de que é feita cada coisa natural do jogo (terra,
  pedra, ferro, madeira...), e é assim que o mundo é lido como matéria. Conhecer o código de uma coisa é saber misturá-la.

## 2. As propriedades

Cada aspecto dá uma qualidade à matéria, na proporção em que está nela:

| Aspecto | Qualidade | O que faz |
|---|---|---|
| `firmo` | peso | pesa, resiste, deixa um líquido espesso (quem entra nele anda devagar) |
| `aqua` | coesão | molha: apaga o fogo em que toca e em quem está dentro dele |
| `aura` | leveza | deixa a matéria leve; um gás se espalha no ar |
| `igni` | calor | queima quem toca, acende o que queima ao redor e brilha |

- As leis valem para qualquer composição. Uma mistura de 70% terra e 30% fogo é pesada e quente, então queima e deixa
  lento quem entra nela, sem que ninguém tenha escrito "magma".
- Uma qualidade só age acima de um limiar (um quarto da matéria): um pouco de fogo na pedra não a faz queimar.

## 3. As reações: aspectos opostos

- Os opostos são fogo e água, e terra e ar, na geometria dos aspectos que o mod já usa.
- Num fluido, onde as partes se movem, dois opostos que passam de um décimo da matéria reagem: a mesma quantidade de
  cada um se separa como um gás e vai embora.
  - Fogo com água ferve, e o vapor sobe.
  - Terra com ar se espalha como poeira.
  - O que sobra é o lado que venceu, com o resto da mistura.
- Nada se perde (L1): o gás que sai tem o UMU dos dois.
- Num sólido as partes estão presas, então não reagem: a madeira tem água e fogo e não ferve. Derretida, ferve.
- Isso substitui o amálgama que se desfazia por tempo.

## 4. A aparência

- Se a composição fica perto do código de uma coisa natural (5 pontos em cada parte) e essa coisa tem aparência naquele
  estado, a mistura aparece como ela: pedra derretida é lava, pedra é pedra. É assim que se descobre como fazer pedra.
  - Nesse caso ela assume o código exato, porque o bloco do jogo não guarda a diferença.
- Se não, é matéria informe, com a cor e as propriedades dela. Ela guarda a composição exata e não se desfaz.
- Um gás sem aparência própria aparece como partículas da cor dele e se espalha.
- O espírito descreve o resultado pelo que ele é: "A mistura ficou líquida, pesada e quente (terra 70%, fogo 30%)", e
  diz o nome quando ela vira uma coisa natural.

## 5. O que muda no que já existe

- Saem o tempo de vida do amálgama (`amalgam.seconds`), o bloco instável e o desfazer por tempo.
- A mistura (L4) continua igual. Depois dela, valem as reações e depois a aparência.
- As fusões ensinadas (lama, poeira, névoa, vapor, magma, relâmpago) continuam como códigos de coisas do jogo (as runas
  que os nomeavam saíram da língua depois, na etapa 3 de `particulas-design.md`), sem nada
  de especial.
- A condensação não muda.

## 6. Etapas

1. **Parte pura.**
   - As qualidades e a descrição;
   - a lei das reações;
   - a aparência pelo código mais próximo;
   - sai o amálgama por tempo.
2. **Mundo.**
   - A matéria informe age pelas propriedades: queima, acende, apaga, deixa lento, brilha;
   - o gás informe aparece como partículas coloridas;
   - o derramar aplica as reações;
   - o espírito descreve o resultado.
3. **Testes e documentos.**
   - GameTests:
     - pedra por mistura;
     - fogo numa poça de água que ferve;
     - mistura quente que queima;
     - mistura molhada que apaga o fogo.
   - Documentos: `GRIMOIRE_REFERENCE.md` e os planos.

## 7. Como ficou

- **Parte pura:**
  - `Qualities` dá peso, coesão, leveza e calor, o limiar de ação (um quarto) e a descrição que o espírito dá.
  - `MatterLaws.react` é a L5 nova: num líquido ou plasma, cada par de opostos acima de um décimo solta a mesma
    quantidade de cada um como gás.
  - `Placement` aplica as reações antes da aparência.
  - Saíram o `decay`, o `amalgam.seconds` e o conceito de receita. `Matter.unnamed` diz se a mistura está perto de
    nenhum código.
- **Mundo:**
  - O bloco informe brilha com o calor (a propriedade `glow`). Quente, ele queima quem entra ou pisa e acende o
    redor. Molhado, apaga o fogo. Pesado e líquido, deixa lento.
  - O gás sem aparência vira uma nuvem de partículas da cor dele.
  - Um bloco informe quebrado ou explodido se espalha como gás.
  - O espírito descreve o resultado ("A mistura ficou líquida, pesada e quente (terra 45%, fogo 45%, ar 10%).") e o
    que ferveu ("6 UMU se separaram e subiram como gás").
- **Testes no jogo:**
  - a pedra por mistura, que continua igual;
  - uma mistura sem nome que não se desfaz;
  - fogo e água num líquido que fervem;
  - uma mistura quente que queima um porco e uma molhada que apaga o fogo dele;
  - `igni quantum 6 ... ubis vocant` numa poça de três fontes, que ferve e sobra uma;
  - a condensação, que continua funcionando.
