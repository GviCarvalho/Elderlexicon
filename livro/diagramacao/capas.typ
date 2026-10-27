// Opções de capa para Fundamentos de Siglística (uma por página).
#import "@preview/cetz:0.3.4"
#import "modelo.typ": *
#import "pranchas.typ": arco

#let ouro = rgb("#c9a45c")
#let couro = rgb("#3a1e16")
#let barro = rgb("#cfae7c")
#let barro-escuro = rgb("#5a3a22")

#set page(width: 17cm, height: 24cm, margin: 0pt)
#set text(lang: "pt")

// Um emblema: as cinco Fontes num anel, vis no meio, e uma frase correndo em volta
#let emblema(cor, fundo, frase: "a gramática do mundo adormecido · para o primeiro ano · ", raio: 3.2) = context {
  let u = 0.8cm
  cetz.canvas(length: u, {
    import cetz.draw: *
    circle((0, 0), radius: raio, stroke: 1.1pt + cor)
    circle((0, 0), radius: raio - 0.12, stroke: 0.4pt + cor)
    circle((0, 0), radius: raio - 0.95, stroke: 0.5pt + cor)
    arco(frase, raio - 0.72, 90, tam: 8.5pt, cor: cor, unidade: u, volta: true)
    for (i, r) in ("igni", "aqua", "aura", "firmo").enumerate() {
      let a = 90 - i * 90
      content((a * 1deg, 1.45), text(font: runica, size: 22pt, fill: cor, r))
    }
    content((0, 0), text(font: runica, size: 30pt, fill: cor, "vis"))
  })
}

// ---------------------------------------------------------------- 1. o códice de couro
#page(fill: couro)[
  #place(center + horizon, rect(width: 15.4cm, height: 22.4cm, stroke: 1.4pt + ouro))
  #place(center + horizon, rect(width: 15cm, height: 22cm, stroke: 0.5pt + ouro))
  #for (x, y) in ((1.35cm, 1.35cm), (15.65cm, 1.35cm), (1.35cm, 22.65cm), (15.65cm, 22.65cm)) {
    place(top + left, dx: x - 0.3cm, dy: y - 0.3cm, box(width: 0.6cm, height: 0.6cm, align(center + horizon,
      text(fill: ouro, size: 14pt)[✦])))
  }
  #align(center)[
    #v(3.2cm)
    #text(font: bastarda, size: 46pt, fill: ouro)[Fundamentos \ de Siglística]
    #v(1.1cm)
    #emblema(ouro, couro, raio: 3.7)
    #v(1.1cm)
    #text(font: bastarda, size: 20pt, fill: ouro)[Ummia Imhexis]
    #v(0.2cm)
    #text(font: corrente, size: 12pt, fill: ouro.transparentize(20%))[Edubba de Rostrand]
  ]
]

// ---------------------------------------------------------------- 2. o pergaminho rubricado
#page(fill: papel)[
  #let faixa = {
    set text(font: runica, size: 15pt, fill: tinta)
    runas.slice(0, 20).join(" ")
  }
  #place(top + center, dy: 1.6cm, block(width: 14cm, clip: true, height: 1cm, align(center, faixa)))
  #place(bottom + center, dy: -1.6cm, block(width: 14cm, clip: true, height: 1cm, align(center, faixa)))
  #place(top + center, dy: 2.8cm, line(length: 14cm, stroke: 0.8pt + rubro))
  #place(bottom + center, dy: -2.8cm, line(length: 14cm, stroke: 0.8pt + rubro))
  #align(center)[
    #v(5.4cm)
    #text(font: bastarda, size: 80pt, fill: rubro)[F]#h(-0.12em)#text(font: bastarda, size: 44pt, fill: tinta)[undamentos]
    #v(-0.6cm)
    #text(font: bastarda, size: 44pt, fill: tinta)[de Siglística]
    #v(0.6cm)
    #text(font: corrente, size: 17pt, fill: tinta)[a gramática do mundo adormecido, \ para o primeiro ano]
    #v(1.4cm)
    #text(fill: rubro, size: 12pt)[✦ #h(1em) ✦ #h(1em) ✦]
    #v(1.4cm)
    #text(font: bastarda, size: 22pt, fill: rubro)[Ummia Imhexis]
    #v(0.1cm)
    #text(font: corrente, size: 13pt, fill: tinta)[mestre de siglística da Edubba de Rostrand]
  ]
]

// ---------------------------------------------------------------- 3. a tabuleta da Edubba
#page(fill: barro)[
  // a moldura geométrica, de losangos, como nas portas das casas de tabuletas
  #let losangos(n) = for k in range(n) { text(fill: barro-escuro, size: 9pt)[◆ ] }
  #place(top + center, dy: 1.1cm, losangos(22))
  #place(bottom + center, dy: -1.1cm, losangos(22))
  #place(center + horizon, rect(width: 14.6cm, height: 21cm, stroke: 2.4pt + barro-escuro, radius: 4pt))
  #place(center + horizon, rect(width: 14cm, height: 20.4cm, stroke: 0.6pt + barro-escuro, radius: 3pt))
  // uma coluna de glifos descendo pela esquerda, como uma inscrição
  #place(left + top, dx: 2.4cm, dy: 3.4cm, stack(spacing: 0.55cm,
    ..("igni", "aqua", "aura", "firmo", "vis", "vocant", "iactare", "vertere", "surgit", "ligabis").map(r =>
      text(font: runica, size: 20pt, fill: barro-escuro.transparentize(15%), r))))
  #place(left + top, dx: 3.6cm, dy: 3.2cm, line(angle: 90deg, length: 17.4cm, stroke: 0.6pt + barro-escuro))
  #place(left + top, dx: 4.4cm, dy: 5.5cm, block(width: 10cm)[
    #set par(leading: 0.4em)
    #text(font: bastarda, size: 15pt, fill: barro-escuro)[Edubba de Rostrand · primeiro ano]
    #v(0.8cm)
    #text(font: bastarda, size: 50pt, fill: rgb("#2c1a10"))[Fundamentos de Siglística]
    #v(0.8cm)
    #text(font: corrente, size: 16pt, fill: rgb("#2c1a10"))[a gramática do mundo adormecido]
    #v(3.6cm)
    #text(font: bastarda, size: 22pt, fill: rubro)[Ummia Imhexis]
  ])
]

// ---------------------------------------------------------------- 4. o caderno de estudo
#page(fill: rgb("#f4eedf"))[
  #place(left + top, dx: 0pt, dy: 0pt, rect(width: 1.6cm, height: 24cm, fill: rubro))
  #place(left + top, dx: 1.6cm, dy: 0pt, rect(width: 0.12cm, height: 24cm, fill: tinta))
  #place(left + top, dx: 3.4cm, dy: 3cm, block(width: 12cm)[
    #text(font: corrente, size: 13pt, fill: sepia)[para o primeiro ano]
    #v(0.3cm)
    #text(font: bastarda, size: 54pt, fill: rubro)[Fundamentos]
    #v(-0.9cm)
    #text(font: bastarda, size: 54pt, fill: tinta)[de Siglística]
    #v(0.2cm)
    #line(length: 9cm, stroke: 0.7pt + rubro)
    #v(0.2cm)
    #text(font: corrente, size: 17pt, fill: tinta)[a gramática do mundo adormecido]
  ])
  #place(center + horizon, dx: 2.2cm, dy: 4.4cm, emblema(rubro, papel, raio: 2.7,
    frase: "o espírito faz exatamente o que você escreve · "))
  #place(left + bottom, dx: 3.4cm, dy: -2.2cm, block[
    #text(font: bastarda, size: 22pt, fill: tinta)[Ummia Imhexis]
    #linebreak()
    #text(font: corrente, size: 12pt, fill: sepia)[Edubba de Rostrand]
  ])
]
