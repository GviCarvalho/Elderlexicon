"""Monta o livro: Markdown -> Typst (Pandoc + filtro.lua) -> PDF (Typst, com as fontes do próprio livro).

    python montar.py            # o livro inteiro
    python montar.py 1 2 13     # só a abertura e estes capítulos (para provas rápidas)
"""
import glob, os, re, subprocess, sys
import typst

AQUI = os.path.dirname(os.path.abspath(__file__))
LIVRO = os.path.dirname(AQUI)
OBRA = os.path.join(AQUI, "obra")
FONTES = [os.path.join(LIVRO, "fontes", "latina"), os.path.join(LIVRO, "fontes", "sga")]


def capitulos(escolha):
    caps = sorted(glob.glob(os.path.join(LIVRO, "capitulo-*.md")))
    if escolha:
        caps = [c for c in caps if int(re.search(r"capitulo-(\d+)", c).group(1)) in escolha]
    apendices = [] if escolha else sorted(glob.glob(os.path.join(LIVRO, "apendice-[a-f]-*.md")))
    return caps + apendices


def abertura():
    """A abertura, sem o título, o subtítulo e o autor (a folha de rosto do modelo os desenha)."""
    texto = open(os.path.join(LIVRO, "abertura.md"), encoding="utf-8").read()
    corpo = texto[texto.index("## Carta ao aluno"):]
    return "# Abertura\n\n" + corpo


def converter(md_texto, destino):
    r = subprocess.run(["pandoc", "-f", "markdown-auto_identifiers", "-t", "typst",
                        "--lua-filter", os.path.join(AQUI, "filtro.lua"), "--wrap=none"],
                       input=md_texto.replace("\r\n", "\n").encode("utf-8"), capture_output=True)
    if r.returncode:
        sys.exit(r.stderr.decode("utf-8", "replace"))
    open(destino, "w", encoding="utf-8", newline="\n").write(r.stdout.decode("utf-8").replace("\r\n", "\n"))


def main():
    escolha = {int(a) for a in sys.argv[1:]}
    os.makedirs(OBRA, exist_ok=True)
    partes = []
    converter(abertura(), os.path.join(OBRA, "00-abertura.typ"))
    partes.append("obra/00-abertura.typ")
    for md in capitulos(escolha):
        nome = os.path.splitext(os.path.basename(md))[0] + ".typ"
        converter(open(md, encoding="utf-8").read(), os.path.join(OBRA, nome))
        partes.append("obra/" + nome)
    if not escolha:
        # o fim do livro: o retrato do autor, de frente para a página sobre ele
        sobre = open(os.path.join(LIVRO, "sobre-o-autor.md"), encoding="utf-8").read()
        converter("```{=typst}\n#retrato-do-autor()\n```\n\n" + sobre, os.path.join(OBRA, "zz-sobre-o-autor.typ"))
        partes.append("obra/zz-sobre-o-autor.typ")
    principal = ['#import "modelo.typ": *', '#import "capa.typ": frente, costas', "#show: livro",
                 # a capa, o verso dela em branco, e então a folha de rosto
                 "#page(margin: 0pt, header: none, footer: none, frente())",
                 "#page(header: none, footer: none)[]",
                 "#rosto()"]
    principal += [f'#include "{p}"' for p in partes]
    # a contracapa fecha o livro, numa página par
    principal += ['#pagebreak(to: "even", weak: true)', "#page(margin: 0pt, header: none, footer: none, costas())"]
    open(os.path.join(AQUI, "livro.typ"), "w", encoding="utf-8").write("\n".join(principal) + "\n")
    # cada capítulo importa o modelo e as pranchas, para enxergar as peças
    for p in partes:
        caminho = os.path.join(AQUI, p)
        corpo = open(caminho, encoding="utf-8").read()
        open(caminho, "w", encoding="utf-8").write(
            '#import "../modelo.typ": *\n#import "../pranchas.typ": prancha-arrumacao, prancha-ritual\n' + corpo)
    saida = os.path.join(AQUI, "livro.pdf")
    try:
        typst.compile(os.path.join(AQUI, "livro.typ"), output=saida, font_paths=FONTES, root=AQUI)
    except typst.TypstError as e:
        sys.exit(f"{e.message}\n  {e.trace}\n  {e.hints}")
    print("ok:", saida)


if __name__ == "__main__":
    main()
