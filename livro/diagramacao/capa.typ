// A capa escolhida (o caderno de estudo): capa, lombada e contracapa.
// Página 1: a capa aberta para a gráfica (contracapa | lombada | capa), com sangria.
// Páginas 2 e 3: a capa e a contracapa sozinhas, para ver.
#import "@preview/cetz:0.3.4"
#import "modelo.typ": *
#import "capas.typ": emblema

#let fundo = rgb("#f4eedf")
#let largura = 17cm
#let altura = 24cm
// A lombada depende do papel e da gráfica: ~280 páginas em papel de 90 g dão por volta de 1,5 cm. Confirmar antes de
// imprimir e mudar aqui.
#let lombada = 1.5cm
#let sangria = 0.3cm
#let faixa = 1.6cm      // a faixa vermelha junto à lombada, na capa e na contracapa

#let frente() = box(width: largura, height: altura, fill: fundo, clip: true, {
  place(left + top, rect(width: faixa, height: altura, fill: rubro))
  place(left + top, dx: faixa, rect(width: 0.12cm, height: altura, fill: tinta))
  place(left + top, dx: 3.4cm, dy: 3cm, block(width: 12cm)[
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
  place(center + horizon, dx: 2.2cm, dy: 4.4cm, emblema(rubro, papel, raio: 2.7,
    frase: "o espírito faz exatamente o que você escreve · "))
  place(left + bottom, dx: 3.4cm, dy: -2.2cm, block[
    #text(font: bastarda, size: 22pt, fill: tinta)[Ummia Imhexis]
    #linebreak()
    #text(font: corrente, size: 12pt, fill: sepia)[Edubba de Rostrand]
  ])
})

#let costas() = box(width: largura, height: altura, fill: fundo, clip: true, {
  // a faixa do lado da lombada, espelhando a da capa
  place(right + top, rect(width: faixa, height: altura, fill: rubro))
  place(right + top, dx: -faixa, rect(width: 0.12cm, height: altura, fill: tinta))
  place(left + top, dx: 2.2cm, dy: 3cm, block(width: 11cm)[
    #set par(leading: 0.55em, spacing: 1em, justify: false)
    #text(font: bastarda, size: 20pt, fill: rubro)[Todo ser vivo carrega a mesma quantidade de vis: cem.]
    #v(0.5cm)
    #set text(font: corrente, size: 13pt, fill: tinta)
    A magia não dá mais poder a quem tem mais força. Dá a quem entende melhor.

    Este é o livro do primeiro ano da Edubba de Rostrand, a casa das tabuletas mais antiga do mundo. Nele você aprende
    de onde vem a magia e por que ela cobra o que cobra; as quatro famílias da Língua Antiga e a ordem em que o espírito
    as lê; o relógio de uma página e o tempo de um círculo; as marcas, os vínculos e os olhos do espírito; e o que esta
    casa ainda não sabe, dito com a mesma franqueza do que ela sabe.

    Cada feitiço vem com o seu preço. Cada capítulo, com os seus exercícios. E cada erro que eu cometi na juventude, com
    a rachadura dourada que ele me deixou, para que não precise acontecer com você.

    #v(0.3cm)
    #align(right, text(font: bastarda, size: 15pt, fill: rubro)[Ummia Imhexis])
    #align(right, text(size: 11pt, fill: sepia)[mestre de siglística da Edubba de Rostrand])
  ])
  place(left + bottom, dx: 2.2cm, dy: -2.4cm, block(width: 11cm)[
    #line(length: 11cm, stroke: 0.5pt + rubro)
    #v(0.25cm)
    #text(font: corrente, size: 12pt, fill: tinta)[Estude devagar. Faça os exercícios. E nunca peça ao seu espírito algo
    que não pode pagar.]
  ])
})

#let costado() = box(width: lombada, height: altura, fill: rubro, {
  // o título corre de cima para baixo, como nas lombadas desta terra
  place(center + horizon, rotate(90deg, reflow: true, box(width: altura - 3cm, {
    set text(fill: fundo)
    text(font: bastarda, size: 21pt)[Fundamentos de Siglística]
    h(1fr)
    text(font: bastarda, size: 14pt)[Ummia Imhexis]
  })))
  place(center + bottom, dy: -0.9cm, text(font: runica, size: 16pt, fill: fundo, "vis"))
})

// ---------------------------------------------------------------- 1. a capa aberta, para a gráfica
#page(width: 2 * largura + lombada + 2 * sangria, height: altura + 2 * sangria, margin: 0pt, fill: rubro)[
  #place(left + top, dx: 0pt, dy: 0pt, rect(width: 100%, height: 100%, fill: fundo))
  // a sangria repete a borda de cada lado
  #place(left + top, dx: sangria + largura - faixa, rect(width: 2 * faixa + lombada, height: 100%, fill: rubro))
  #place(left + top, dx: sangria, dy: sangria, costas())
  #place(left + top, dx: sangria + largura + lombada, dy: sangria, frente())
  // a lombada por cima das duas, meio ponto para cada lado, para não ficar emenda clara
  #place(left + top, dx: sangria + largura - 0.5pt, dy: 0pt, rect(width: lombada + 1pt, height: 100%, fill: rubro))
  #place(left + top, dx: sangria + largura, dy: sangria, costado())
  // os fios pretos atravessam a folha inteira, sangria incluída
  #place(left + top, dx: sangria + largura - faixa - 0.12cm, rect(width: 0.12cm, height: 100%, fill: tinta))
  #place(left + top, dx: sangria + largura + lombada + faixa, rect(width: 0.12cm, height: 100%, fill: tinta))
]

// ---------------------------------------------------------------- 2 e 3. capa e contracapa, para ver
#page(width: largura, height: altura, margin: 0pt, frente())
#page(width: largura, height: altura, margin: 0pt, costas())
