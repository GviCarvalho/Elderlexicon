#import "@preview/droplet:0.3.1": dropcap
// O modelo do livro: Fundamentos de Siglística, de Ummia Imhexis.
// Um códice escrito à mão, em tinta preta e vermelha, com a ordem de um livro de estudo.

#let papel = rgb("#f1e8d2")
#let tinta = rgb("#22170f")
#let rubro = rgb("#8c2218")
#let sepia = rgb("#a08a6a")
#let sombra = rgb("#e6dbbf")

#let corrente = "Siglistica Corrente"
#let bastarda = "Siglistica Bastarda"
#let runica = "Siglistica Manuscrita"

// uma página é um verso em branco quando o texto anterior acabou antes dela e um capítulo começa depois
#let em-branco(p) = {
  let inicios = query(<inicio>).map(m => m.location().page())
  let fins = query(<fim>).map(m => m.location().page())
  (p + 1) in inicios and not (p in fins) and not (p in inicios)
}

#let titulo-livro = [Fundamentos de Siglística]
#let capitulo-atual = state("capitulo", none)
#let inicio-capitulo = state("inicio", ())

// ---------------------------------------------------------------- as páginas de feitiço

#let palavra-de-runa(p) = {
  if p == "·" { text(fill: sepia)[·] }
  else if p.match(regex("^-?\d+$")) != none { text(fill: tinta, p) }
  else { text(fill: rubro, p) }
}

#let pagina-de-feitico(fonte) = {
  set text(font: corrente, size: 13pt)
  set par(first-line-indent: 0pt)
  let linhas = fonte.split(regex("\r?\n")).filter(l => l.trim() != "")
  // um esquema (com colchetes ou setas) não é uma página: vai como nota centrada
  if linhas.any(l => l.contains("[") or l.contains("→") or l.contains("...")) {
    return align(center, block(above: 1em, below: 1em, {
      for l in linhas { text(fill: rubro, l); linebreak() }
    }))
  }
  let grade = linhas.map(l => l.split(regex("\s+")).filter(p => p != ""))
  let colunas = calc.max(..grade.map(l => l.len()))
  let fio = 0.4pt + sepia.transparentize(40%)
  align(center, block(above: 1.1em, below: 1.2em, breakable: false,
    table(
      columns: colunas,
      inset: (x: 8pt, y: 5pt),
      align: center,
      stroke: (x, y) => (left: if x > 0 { fio }, bottom: if y > 0 { fio }),
      // o número de cada coluna, em cima, como no relógio da página
      ..range(colunas).map(c => text(font: bastarda, size: 8.5pt, fill: sepia, str(c))),
      ..grade.map(l => range(colunas).map(c => if c < l.len() { palavra-de-runa(l.at(c)) } else [])).flatten()
    )))
}

// ---------------------------------------------------------------- a página

#let livro(corpo) = {
  set document(title: "Fundamentos de Siglística", author: "Ummia Imhexis")
  set page(
    width: 17cm, height: 24cm, fill: papel,
    margin: (inside: 2.2cm, outside: 3.4cm, top: 2.6cm, bottom: 2.6cm),
    header: context {
      let p = here().page()
      if query(<inicio>).any(m => m.location().page() == p) or em-branco(p) or p <= 2 { return }
      let cap = capitulo-atual.get()
      set text(font: bastarda, size: 10pt, fill: sepia)
      if calc.even(p) [#titulo-livro #h(1fr)] else [#h(1fr) #cap]
    },
    footer: context {
      let p = here().page()
      if p <= 2 or em-branco(p) { return }
      set text(font: bastarda, size: 11pt, fill: rubro)
      if calc.even(p) [#counter(page).display() #h(1fr)] else [#h(1fr) #counter(page).display()]
    },
  )
  set text(font: corrente, size: 13.5pt, fill: tinta, lang: "pt", region: "br", hyphenate: false,
           features: ("dlig",))
  set par(leading: 0.58em, spacing: 0.9em, first-line-indent: (amount: 1.4em, all: false), justify: false)

  // o negrito de um manuscrito é a tinta vermelha; o itálico, um sublinhado de pena
  show strong: set text(fill: rubro)
  show emph: it => underline(stroke: 0.5pt + tinta.transparentize(30%), offset: 2.5pt, evade: false, it.body)

  // o título de nível 1 é desenhado por capitulo(); o heading serve ao sumário e aos marcadores do PDF
  show heading.where(level: 1): it => none
  // as seções
  show heading.where(level: 2): it => {
    v(0.9em, weak: true)
    block(sticky: true, below: 0.7em, text(font: bastarda, size: 17pt, fill: rubro, it.body))
  }
  show heading.where(level: 3): it => {
    v(0.6em, weak: true)
    block(sticky: true, below: 0.5em, text(font: bastarda, size: 14pt, fill: tinta, it.body))
  }

  // as runas no meio da prosa: a grafia comum, em vermelho
  show raw: set text(font: corrente, size: 1.25em)
  show raw.where(block: false): it => text(fill: rubro, it.text)
  show raw.where(block: true): it => pagina-de-feitico(it.text)

  set list(marker: text(fill: rubro, size: 9pt, baseline: -2pt)[✦], indent: 0.4em, body-indent: 0.6em, spacing: 0.5em)
  set enum(numbering: n => text(font: bastarda, fill: rubro)[#n.], indent: 0.2em, spacing: 0.6em)

  set table(stroke: (x, y) => (bottom: 0.4pt + sepia), inset: (x: 6pt, y: 5pt))
  show table: set text(size: 11pt)
  show table: set par(first-line-indent: 0pt)
  show figure.where(kind: table): it => {
    show table.cell.where(y: 0): set text(font: bastarda, fill: rubro, size: 11.5pt)
    it
  }
  show figure.where(kind: table): set block(breakable: true)
  show figure: set block(breakable: true)

  corpo
}

#let horizontalrule = align(center, block(above: 1em, below: 1em, text(fill: rubro, size: 9pt)[✦ #h(1.2em) ✦ #h(1.2em) ✦]))

// ---------------------------------------------------------------- as peças grandes

#let rosto() = {
  set page(header: none, footer: none)
  align(center + horizon)[
    #text(font: bastarda, size: 40pt, fill: rubro)[Fundamentos \ de Siglística]
    #v(1.2em)
    #text(size: 15pt)[a gramática do mundo adormecido, \ para o primeiro ano]
    #v(3em)
    #text(fill: rubro, size: 10pt)[✦]
    #v(3em)
    #text(font: bastarda, size: 17pt)[Ummia Imhexis]
    #v(0.3em)
    #text(size: 12pt)[da Edubba de Rostrand]
  ]
  pagebreak()
  pagebreak()
  // o sumário
  [#metadata("inicio") <inicio>]
  v(2.2cm)
  block(below: 1.4em, text(font: bastarda, size: 30pt, fill: rubro)[Sumário])
  {
    set par(first-line-indent: 0pt, leading: 0.5em)
    set text(size: 12.5pt, features: (dlig: 0))   // no sumário, "que" por extenso
    outline(title: none, depth: 1, indent: 0pt)
  }
}

#let marca-inicio() = [#metadata("inicio") <inicio>]

#let parte(numero, titulo) = {
  [#metadata("fim") <fim>]
  pagebreak(to: "odd", weak: true)
  marca-inicio()
  capitulo-atual.update(titulo)
  heading(level: 1, outlined: true, bookmarked: true, numbering: none)[Parte #numero: #titulo]
  align(center + horizon)[
    #text(font: bastarda, size: 15pt, fill: sepia)[Parte #numero]
    #v(0.6em)
    #text(font: bastarda, size: 32pt, fill: rubro, titulo)
    #v(1.2em)
    #text(fill: rubro, size: 9pt)[✦]
  ]
}

#let capitulo(rotulo, titulo) = {
  [#metadata("fim") <fim>]
  pagebreak(to: "odd", weak: true)
  marca-inicio()
  capitulo-atual.update(titulo)
  v(2.2cm)
  if rotulo != none { block(below: 0.4em, text(font: bastarda, size: 14pt, fill: sepia, rotulo)) }
  block(below: 0.9em, text(font: bastarda, size: 30pt, fill: rubro, titulo))
  heading(level: 1, outlined: true, bookmarked: true, numbering: none,
          if rotulo != none [#rotulo: #titulo] else [#titulo]) // invisível: só para o sumário
}


#let epigrafe(corpo) = {
  set par(first-line-indent: 0pt)
  align(right, block(width: 78%, above: 0.6em, below: 1.6em,
    align(left, text(font: bastarda, size: 12.5pt, fill: tinta.transparentize(15%), corpo))))
}

#let aprender(lista) = block(width: 100%, inset: (left: 1em, rest: 0.9em), stroke: (left: 1.4pt + rubro),
  fill: sombra, below: 1.6em, {
    set par(first-line-indent: 0pt)
    text(font: bastarda, size: 13pt, fill: rubro)[O que você vai aprender]
    v(0.2em)
    set text(size: 12pt)
    lista
  })

#let caixa(tipo, corpo) = {
  set par(first-line-indent: 0pt)
  if tipo == "advertencia" {
    block(width: 100%, inset: 0.9em, stroke: 0.8pt + rubro, radius: 1pt, fill: sombra, above: 1.2em, below: 1.2em,
      text(size: 12.5pt, corpo))
  } else if tipo == "pratica" {
    block(width: 100%, inset: (left: 1em, y: 0.5em), stroke: (left: 1.4pt + rubro), above: 1.2em, below: 1.2em,
      text(size: 12.5pt, corpo))
  } else {
    block(width: 100%, inset: (y: 0.7em, x: 1.2em), stroke: (top: 0.6pt + rubro, bottom: 0.6pt + rubro),
      above: 1.2em, below: 1.2em, align(center, text(size: 13.5pt, corpo)))
  }
}

#let capitular(corpo) = {
  set par(first-line-indent: 0pt)
  dropcap(height: 3, gap: 4pt, hanging-indent: 0pt, transform: l => text(font: bastarda, fill: rubro, l), corpo)
}

