"""Prancha das fontes latinas: o texto passa pelo HarfBuzz (com os recursos da fonte) e é desenhado em PNG."""
import sys
import uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen
from PIL import Image, ImageDraw, ImageChops

PAPER, INK, RED = (241, 232, 210), (34, 23, 16), (140, 34, 24)


class Face:
    def __init__(self, path):
        self.tt = TTFont(path)
        self.gs = self.tt.getGlyphSet()
        self.order = self.tt.getGlyphOrder()
        blob = hb.Blob.from_file_path(path)
        self.hb = hb.Font(hb.Face(blob))
        self.cache = {}

    def contours(self, gid):
        if gid not in self.cache:
            pen = RecordingPen()
            self.gs[self.order[gid]].draw(pen)
            out, cur = [], []
            for op, args in pen.value:
                if op == "moveTo":
                    cur = [args[0]]
                elif op in ("lineTo", "qCurveTo", "curveTo"):
                    cur.append(args[-1])
                elif op in ("closePath", "endPath"):
                    out.append(cur)
            self.cache[gid] = out
        return self.cache[gid]

    def shape(self, text, features):
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hb, buf, features)
        return [(i.codepoint, p.x_advance) for i, p in zip(buf.glyph_infos, buf.glyph_positions)]

    def draw(self, im, runs, x, y, size, color, width=None, leading=None):
        """Escreve os trechos [(texto, recursos)], quebrando as linhas nas palavras se houver largura."""
        k = size / 1000
        mask = Image.new("L", im.size, 0)
        x_start = x
        words = []
        for text, feats in runs:
            for w in text.split(" "):
                if w:
                    words.append((w, feats))
        space = self.shape(" ", {})[0][1] * k
        for w, feats in words:
            glyphs = self.shape(w, feats)
            wlen = sum(a for _, a in glyphs) * k
            if width and x + wlen > x_start + width:
                x, y = x_start, y + leading
            for gid, adv in glyphs:
                cs = [[(x + px * k, y - py * k) for px, py in c] for c in self.contours(gid) if len(c) >= 3]
                if cs:
                    # cada glifo em par-ou-ímpar (os furos do o, do a), e os glifos somados entre si
                    bx0 = int(min(p[0] for c in cs for p in c)) - 2
                    by0 = int(min(p[1] for c in cs for p in c)) - 2
                    bw = int(max(p[0] for c in cs for p in c)) - bx0 + 3
                    bh = int(max(p[1] for c in cs for p in c)) - by0 + 3
                    gm = Image.new("1", (bw, bh), 0)
                    for c in cs:
                        layer = Image.new("1", (bw, bh), 0)
                        ImageDraw.Draw(layer).polygon([(px - bx0, py - by0) for px, py in c], fill=1)
                        gm = ImageChops.logical_xor(gm, layer)
                    region = mask.crop((bx0, by0, bx0 + bw, by0 + bh))
                    mask.paste(ImageChops.lighter(region, gm.convert("L")), (bx0, by0))
                x += adv * k
            x += space
        im.paste(Image.new("RGB", im.size, color), (0, 0), mask)
        return x, y


if __name__ == "__main__":
    corr, bast = Face("SiglisticaCorrente.ttf"), Face("SiglisticaBastarda.ttf")
    im = Image.new("RGB", (1700, 1540), PAPER)
    bast.draw(im, [("Carta ao aluno", {})], 80, 130, 110, RED)
    on = {"dlig": True}
    corr.draw(im, [("Seja bem-vindo à Edubba. Se este livro está nas suas mãos, você atravessou o deserto, passou pelos "
                    "portões e foi aceito na casa das tabuletas mais antiga do mundo. Nesta sala, todos começam do mesmo "
                    "lugar, e eu quero que você entenda isso antes de qualquer outra coisa: todo ser vivo carrega a mesma "
                    "quantidade de vis, cem. A magia não dá mais poder a quem tem mais força. Dá a quem entende melhor. "
                    "Escreva com afinco, estude o efeito de cada palavra, e lembre:", on),
                   ("igni vocant iactare", {"ss01": True}),
                   ("é a lança de fogo, e o espírito é literal.", on)], 80, 290, 72, INK, width=1540, leading=108)
    bast.draw(im, [("Sem os recursos", {})], 80, 1110, 52, RED)
    corr.draw(im, [("efficaz fiel flor estudo acto — que vis igni", {"liga": False, "calt": False})], 80, 1210, 72, INK)
    bast.draw(im, [("Com ligaduras, revezamento, fios, abreviação e runas", {})], 80, 1330, 52, RED)
    corr.draw(im, [("efficaz fiel flor estudo acto — que", on), ("vis igni", {"ss01": True})], 80, 1430, 72, INK)
    im.save("prancha_latina.png")
    print("ok")
