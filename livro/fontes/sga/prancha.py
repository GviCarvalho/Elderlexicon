"""Gera a prancha de prova (prancha.html) com a fonte embutida."""
import base64
from glifos import GLYPHS, CODEPOINTS, pixels, check

font64 = base64.b64encode(open("SiglisticaManuscrita.ttf", "rb").read()).decode()


def px_svg(c, p=6):
    cells = "".join(f'<rect x="{x*p}" y="{y*p}" width="{p}" height="{p}"/>' for x, y in pixels(c))
    return f'<svg class="px" viewBox="0 0 {5*p} {7*p}" width="{5*p}" height="{7*p}">{cells}</svg>'


def card(c):
    rune = GLYPHS[c][0]
    ch = chr(CODEPOINTS[c][-1])
    label = f"<b>{c}</b> <code>{rune}</code>" if c != rune else f"<code>{rune}</code>"
    return f'<div class="card"><div class="g">{ch}</div><div class="m">{px_svg(c)}<span>{label}</span></div></div>'


groups = [
    ("As runas (A a P)", list("ABCDEFGHIJKLMNOP")),
    ("Os Fatos (Q a Z)", list("QRSTUVWXYZ")),
    ("As runas novas do mod", ["languet", "mergitur", "aut", "non", "ego", "ille"]),
    ("Os algarismos do mod", list("0123456789")),
]
sections = "".join(f"<h2>{t}</h2><div class='grid'>{''.join(card(c) for c in cs)}</div>" for t, cs in groups)
samples = ["igni vocant iactare", "aqua tenet quantum 120 vocant", "ferit vis vertere igni ille ubis vocant",
           "non quantum 5 firmo chronos vis quantum 1 vertere firmo", "casa ubis ego vocant"]
sample_html = "".join(f'<p class="s"><span class="sg">{t}</span><span class="tr">{t}</span></p>' for t in samples)
bad = check()
report = "todos os glifos seguem a forma canônica do SGA e do mod" if not bad else f"diferenças: {bad}"

html = f"""<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>SGA manuscrito</title>
<style>
@font-face {{ font-family: "SigMan"; src: url(data:font/ttf;base64,{font64}) format("truetype"); }}
:root {{ --paper:#f1e6cf; --ink:#3b2414; --rubric:#9b2a1a; --line:#d8c7a3; }}
body {{ background:var(--paper); color:var(--ink); font-family:Georgia, serif; margin:0; padding:24px 16px 64px; }}
main {{ max-width:1000px; margin:0 auto; }}
h1 {{ font-weight:normal; margin:0; }} h2 {{ color:var(--rubric); font-weight:normal; border-bottom:1px solid var(--line); margin-top:32px; }}
.note {{ background:#f6eedb; border-left:3px solid #b8892e; padding:10px 14px; margin:14px 0; }}
.grid {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(120px,1fr)); gap:12px; }}
.card {{ border:1px solid var(--line); border-radius:6px; background:#f6eedb; padding:8px; }}
.g {{ font-family:"SigMan"; font-size:84px; line-height:1.05; text-align:center; }}
.m {{ display:flex; align-items:center; gap:8px; font-size:.9em; }} .m code {{ color:var(--rubric); }}
.px rect {{ fill:#9a8668; }} .px {{ background:#efe2c6; border:1px solid var(--line); }}
.s {{ display:flex; align-items:baseline; gap:16px; flex-wrap:wrap; margin:8px 0; }}
.sg {{ font-family:"SigMan"; font-size:44px; word-spacing:.15em; font-feature-settings:"liga" 1; }} .tr {{ color:var(--rubric); font-family:monospace; }}
.sizes span {{ font-family:"SigMan"; display:block; margin:6px 0; }}
</style></head><body><main>
<h1>SGA manuscrito</h1>
<p>O Standard Galactic Alphabet e os glifos do mod, desenhados a estilete. Prancha de prova da fonte
<code>SiglisticaManuscrita.ttf</code>, versão 0.4.</p>
<div class="note">Cada glifo é a mesma forma que o jogo e o mod usam (o quadrinho em pixels ao lado), escrito com a mesma pena de ponta larga da bastarda do professor: o grosso e o fino saem do ângulo da pena, e as pontas afinam quando ela se levanta. Conferência automática: {report}.</div>
<h2>Linhas de exemplo</h2>
<p>Cada linha abaixo foi escrita com as palavras comuns, como no Markdown do livro: a fonte troca sozinha cada
palavra da Língua Antiga pelo glifo dela. Uma palavra que não é runa, como a marca <code>casa</code>, fica escrita letra
por letra no SGA.</p>{sample_html}
<h2>Em vários tamanhos</h2><div class="sizes">
<span style="font-size:18px">ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789</span>
<span style="font-size:28px">ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789</span>
<span style="font-size:44px">ABCDEFGHIJKLMNOPQRSTUVWXYZ</span></div>
{sections}
</main></body></html>"""
open("prancha.html", "w", encoding="utf-8").write(html)
print("prancha.html gerada")
