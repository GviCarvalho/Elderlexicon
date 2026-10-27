// As pranchas do livro: círculos e rituais desenhados à mão, em tinta preta e vermelha.
#import "@preview/cetz:0.3.4"
#import "modelo.typ": *

// ---------------------------------------------------------------- ferramentas de desenho

// Escreve um texto ao longo de um arco (sentido horário a partir do ângulo a0, em graus), letra por letra.
// Precisa estar dentro de `context`, para medir as letras.
#let arco(texto, r, a0, tam: 7pt, cor: rubro, fonte: corrente, unidade: 1cm, centro: false, volta: false) = {
  import cetz.draw: *
  let folga = 0.035   // um respiro entre as letras, em unidades do desenho
  let graus = t => (measure(text(font: fonte, size: tam, t)).width / unidade + folga * t.clusters().len()) / r * 180 / calc.pi
  if volta {
    // repete o texto até dar a volta inteira
    let n = calc.max(1, calc.floor(360 / graus(texto)))
    texto = texto * n
  }
  let a = if centro { a0 + graus(texto) / 2 } else { a0 }
  for c in texto.clusters() {
    let w = measure(text(font: fonte, size: tam, c)).width / unidade + folga
    let meio = a - (w / 2) / r * 180 / calc.pi
    content((meio * 1deg, r), text(font: fonte, size: tam, fill: cor, c), angle: (meio - 90) * 1deg, anchor: "south")
    a = a - w / r * 180 / calc.pi
  }
}

// Uma peça do círculo: um pergaminho preso no chão, com a sua letra e o seu apelido.
#let peca(pos, letra, apelido, marca: none) = {
  import cetz.draw: *
  let (x, y) = pos
  rect((x - 0.95, y - 0.55), (x + 0.95, y + 0.55), fill: sombra, stroke: 0.6pt + tinta, radius: 0.04)
  // as linhas da grade do pergaminho, bem leves
  for k in range(1, 5) {
    line((x - 0.85, y - 0.55 + k * 0.22), (x + 0.85, y - 0.55 + k * 0.22), stroke: 0.25pt + sepia.transparentize(50%))
  }
  content((x, y + 0.1), text(font: bastarda, size: 12pt, fill: rubro, letra))
  content((x, y - 0.32), text(font: corrente, size: 7pt, fill: tinta, apelido))
  if marca != none {
    content((x + 0.72, y + 0.34), text(font: corrente, size: 7pt, fill: rubro, marca))
  }
}

#let seta(a, b, rotulo: none, cor: rubro, curva: 0) = {
  import cetz.draw: *
  let meio = ((a.at(0) + b.at(0)) / 2 + curva * (b.at(1) - a.at(1)), (a.at(1) + b.at(1)) / 2 - curva * (b.at(0) - a.at(0)))
  bezier(a, b, meio, stroke: 0.7pt + cor, mark: (end: ">", fill: cor, scale: 0.6))
  if rotulo != none { content(meio, box(fill: papel, inset: 2pt, text(font: corrente, size: 7.5pt, fill: cor, rotulo))) }
}

#let titulo-prancha(numero, nome) = {
  block(below: 0.3em, text(font: bastarda, size: 11pt, fill: sepia)[Prancha #numero])
  block(below: 0.8em, text(font: bastarda, size: 22pt, fill: rubro, nome))
}

// A moldura das pranchas: fio duplo, como nos códices
#let moldura(corpo) = block(width: 100%, stroke: 0.9pt + rubro, inset: 3pt,
  block(width: 100%, stroke: 0.4pt + rubro, inset: 10pt, corpo))

// ---------------------------------------------------------------- Prancha I: a arrumação

#let arrumacao-pecas = (
  // letra, apelido, posição (em sar), as palavras da peça
  ("N", "o puxão", (0, 0), "m1 ubis vis quantum -40 chronos 4 impediunt"),
  ("A1", "o muro", (0, 2.4), "m1 ubis firmo quantum 40 impediunt"),
  ("A2", "a luz", (-2.08, -1.2), "m1 0 3 ubis igni vocant"),
  ("A3", "o que segura", (2.08, -1.2), "m1 ubis vis quantum -40 chronos 2 impediunt"),
  ("B1", "a água", (2.69, 2.69), "m1 ubis aqua quantum 40 vocant"),
  ("B2", "a água", (-2.69, 2.69), "m1 ubis aqua quantum 40 vocant"),
  ("B3", "a água", (-2.69, -2.69), "m1 ubis aqua quantum 40 vocant"),
  ("B4", "a água", (2.69, -2.69), "m1 ubis aqua quantum 40 vocant"),
)

#let arrumacao-desenho() = context {
  let u = 0.82cm
  cetz.canvas(length: u, {
    import cetz.draw: *
    // o muro que A1 ergue, a seis sar do centro, e a água que B1-B4 põem dentro dele
    circle((0, 0), radius: 6.0, stroke: (paint: tinta, thickness: 3.2pt, dash: "densely-dotted"))
    for r in (5.3, 4.75) {
      for k in range(0, 24) {
        let a = k * 15 + (if r < 5 { 7.5 } else { 0 })
        arc((a * 1deg, r), start: a * 1deg, delta: 6deg, radius: r, stroke: 0.4pt + sepia.transparentize(30%), anchor: "start")
      }
    }
    arco("aqui A1 ergue o muro, e dentro dele fica a água de B1 a B4", 6.3, 90, tam: 8.5pt, cor: tinta,
         unidade: u, centro: true)
    // a corda esticada em volta de todas as peças: o anel de fora
    rect((-3.85, -3.45), (3.85, 3.45), radius: 0.35, stroke: (paint: rubro, thickness: 0.6pt, dash: "dashed"))
    // a corda em volta do que sobrou: o primeiro anel
    line((0, 2.9), (-3.3, -1.9), (3.3, -1.9), close: true, stroke: (paint: rubro, thickness: 0.5pt, dash: "dashed"))
    // os nomes das camadas da cebola, e a ordem em que o espírito as lê
    arco("o anel de fora · lido em terceiro", 3.95, 90, tam: 7.5pt, unidade: u, centro: true)
    content((0, -2.2), text(font: corrente, size: 7.5pt, fill: rubro)[o primeiro anel · lido em segundo])
    content((0, -0.85), text(font: corrente, size: 7.5pt, fill: rubro)[o núcleo · primeiro])
    for (letra, apelido, pos, _) in arrumacao-pecas {
      peca(pos, letra, apelido, marca: if letra == "N" { "m1" } else { none })
    }
  })
}

// As palavras de uma peça, coluna por coluna, numa grade
#let palavras(fonte) = fonte.split(" ")

// A partitura do círculo: cada peça numa linha, cada palavra no passo (coluna) em que é lida
#let arrumacao-partitura() = {
  let linhas = (
    ("N", 0, palavras(arrumacao-pecas.at(0).at(3))),
    ("A1", none, palavras(arrumacao-pecas.at(1).at(3))),
    ("A2", none, palavras(arrumacao-pecas.at(2).at(3))),
    ("A3", none, palavras(arrumacao-pecas.at(3).at(3))),
    ("B1–B4", none, palavras(arrumacao-pecas.at(4).at(3))),
  )
  // o anel começa quando o de dentro é solto; a peça mais curta começa mais tarde, para todas acabarem juntas
  let fim-n = linhas.at(0).at(2).len() - 1                       // 7
  let anel1 = linhas.slice(1, 4)
  let longo1 = calc.max(..anel1.map(l => l.at(2).len()))       // 8
  let fim-a = fim-n + longo1                                      // 15
  let longo2 = linhas.at(4).at(2).len()
  let fim-b = fim-a + longo2                                      // 21
  let inicio = (l, fim) => fim - l.at(2).len() + 1
  let starts = (0,) + anel1.map(l => inicio(l, fim-a)) + (inicio(linhas.at(4), fim-b),)
  let total = fim-b + 1
  let largura = 0.53cm
  let celula(p, solta) = {
    let cor = if p.match(regex("^-?\d+$")) != none { tinta } else { rubro }
    box(width: largura, align(center + horizon,
      rotate(-90deg, reflow: true, text(font: corrente, size: 7.5pt, fill: cor,
        if solta { underline(stroke: 0.4pt + rubro, offset: 2pt, p) } else { p }))))
  }
  table(
    columns: (1.3cm,) + (largura,) * total,
    inset: (x: 0pt, y: 4pt),
    align: center + horizon,
    stroke: (x, y) => (left: if x > 1 { 0.25pt + sepia.transparentize(50%) }, bottom: 0.3pt + sepia.transparentize(30%)),
    [], ..range(total).map(c => text(font: bastarda, size: 7pt, fill: if c in (fim-n, fim-a, fim-b) { rubro } else { sepia }, str(c))),
    ..linhas.enumerate().map(((i, l)) => {
      let s = starts.at(i)
      let fim = s + l.at(2).len() - 1
      (text(font: bastarda, size: 9pt, fill: rubro, l.at(0)),) + range(total).map(c =>
        if c >= s and c <= fim { celula(l.at(2).at(c - s), c == fim) }
        else if c < s and c > (if i == 0 { -1 } else if i < 4 { fim-n } else { fim-a }) { text(fill: sepia, size: 8pt)[·] }
        else [])
    }).flatten()
  )
}

#let prancha-arrumacao() = {
  // página da esquerda: o desenho
  pagebreak(to: "even", weak: true)
  set par(first-line-indent: 0pt)
  titulo-prancha("I", [A arrumação: um círculo em três tempos])
  moldura(align(center, arrumacao-desenho()))
  v(0.6em)
  set text(size: 11.5pt)
  [O círculo que eu monto nas aulas, visto de cima. São oito peças no chão, todas a menos de três sar de outra.
  A corda de fora toca as quatro peças da água: elas são o *anel de fora*. Tiradas essas, a corda toca as três do
  meio: o *primeiro anel*. Sobra o *núcleo*, a pedra marcada `m1`. Um `surgit` olhando para qualquer uma lê as oito.]
  pagebreak()
  // página da direita: as peças e o relógio
  block(below: 0.5em, text(font: bastarda, size: 14pt, fill: rubro)[O que cada peça diz])
  set text(size: 11.5pt)
  grid(columns: (auto, 1fr), column-gutter: 0.8em, row-gutter: 0.55em,
    ..arrumacao-pecas.filter(p => p.at(0) != "B2" and p.at(0) != "B3" and p.at(0) != "B4").map(p => (
      text(font: bastarda, size: 12pt, fill: rubro, if p.at(0) == "B1" [B1–B4] else { p.at(0) }),
      [#raw(p.at(3)) #h(0.4em) #text(size: 10pt, fill: sepia)[(#p.at(1))]],
    )).flatten())
  v(0.9em)
  block(below: 0.4em, text(font: bastarda, size: 14pt, fill: rubro)[O relógio do círculo])
  [Cada coluna é um quarto de segundo. O anel só começa quando o de dentro é solto, e as peças de um anel acabam
  juntas: a mais curta começa mais tarde, e o ponto marca a espera.]
  v(0.4em)
  align(center, arrumacao-partitura())
  v(0.5em)
  [*Os três tempos.* No passo 7 o núcleo abre a zona que puxa tudo o que está solto para a pedra, e a segura por quatro
  segundos. No passo 15 o primeiro anel ergue o muro, acende a luz sobre a pedra e segura o que foi juntado. No passo
  21 as quatro peças de água soltam juntas, e a água enche o que o muro cercou. Cinco segundos e meio, um só `surgit`.]
  caixa("advertencia")[*Advertência.* O núcleo e A3 são torneiras abertas: pagam enquanto duram. Some as oito peças
  antes de ler o círculo, e lembre que é você quem paga todas. Os alunos chamam este círculo de "a arrumação"; eu o
  chamo de "a conta mais longa do primeiro ano".]
}

// ---------------------------------------------------------------- Prancha II: o ritual em laço

#let rolo(pos, marca, linhas, destaque: false, quantos: 1) = {
  import cetz.draw: *
  let (x, y) = pos
  for k in range(quantos - 1, -1, step: -1) {
    let d = k * 0.18
    rect((x - 2.0 + d, y - 0.75 - d), (x + 2.0 + d, y + 0.75 - d), fill: if k == 0 { sombra } else { papel },
         stroke: (if destaque and k == 0 { 1.1pt } else { 0.6pt }) + (if destaque and k == 0 { rubro } else { tinta }), radius: 0.05)
  }
  content((x - 1.75, y + 0.48), text(font: bastarda, size: 10pt, fill: rubro, marca), anchor: "west")
  for (i, l) in linhas.enumerate() {
    content((x - 1.75, y + 0.05 - i * 0.42), text(font: corrente, size: 8.5pt, fill: rubro, l), anchor: "west")
  }
}

#let ritual-desenho() = context {
  let u = 0.82cm
  cetz.canvas(length: u, {
    import cetz.draw: *
    circle((0, 0), radius: 2.3, stroke: (paint: rubro, thickness: 0.5pt, dash: "dotted"))
    arco("um ritual é uma música que não sabe parar · ", 2.0, 90, tam: 7.5pt, unidade: u, volta: true)
    content((0, 0.15), text(font: bastarda, size: 12pt, fill: rubro)[o laço])
    content((0, -0.45), text(font: corrente, size: 7.5pt, fill: tinta)[uns 13 segundos por volta])
    rolo((0, 4.3), "r1", ("aqua vocant", "r2 chronos 3 surgit"))
    rolo((4.3, -3.1), "r2", ("igni iactare", "r3 surgit"), quantos: 3)
    rolo((-4.3, -3.1), "r3", ("r1 chronos 10 surgit",), destaque: true)
    seta((2.1, 3.9), (4.1, -2.1), rotulo: [3 segundos], curva: -0.18)
    seta((2.2, -3.3), (-2.2, -3.3), rotulo: [logo em seguida], curva: -0.18)
    seta((-4.1, -2.1), (-2.1, 3.9), rotulo: [10 segundos], curva: -0.18)
    content((-4.3, -4.45), text(font: corrente, size: 8pt, fill: rubro)[☞ o pergaminho de parar: tire a marca r3])
    content((4.6, -4.5), text(font: corrente, size: 7.5pt, fill: tinta)[vários pergaminhos marcados r2, lidos juntos])
  })
}

#let prancha-ritual() = {
  pagebreak(to: "odd", weak: true)
  set par(first-line-indent: 0pt)
  titulo-prancha("II", [O laço de três passos])
  moldura(align(center, ritual-desenho()))
  v(0.6em)
  set text(size: 11.5pt)
  [O ritual da seção 20.5, desenhado. Não há círculo aqui: os pergaminhos podem estar em qualquer lugar ao alcance da
  mão, porque é a marca, e não a forma, que leva a leitura de um ao outro. `r1` traz a água e chama os `r2` três
  segundos depois; os `r2`, todos juntos, lançam o fogo e chamam `r3`; `r3` espera dez segundos e chama `r1` de novo.]
  v(0.4em)
  table(columns: (auto, 1fr), stroke: none, inset: (x: 4pt, y: 4pt),
    text(font: bastarda, fill: rubro)[Para começar], [`r1 surgit`, ou o nome que você der: `r1 surgit reframe ritual`.],
    text(font: bastarda, fill: rubro)[Para parar], [tire a marca de `r3`. A próxima leitura não o encontra, e a corrente se quebra ali.],
    text(font: bastarda, fill: rubro)[Quem paga], [quem começou, a cada leitura, sem descanso. Quando a vis acaba, a carne.],
  )
  caixa("advertencia")[*Advertência.* Antes de ler `r1`, ponha a mão sobre `r3`. Os rituais desta casa são lidos em
  pares: um mestre lê, o outro guarda o pergaminho de parar.]
}
