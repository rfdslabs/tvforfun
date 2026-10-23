#!/usr/bin/env python3
"""Gera docs/banner.png — logo "TVFORFUN" em tiles neon com gradiente (estilo SLRFA)."""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---- fonte de blocos 5x7 ----
FONT = {
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    " ": ["00000"] * 7,
}

WORD = "TVFORFUN"
SUBTITLE = "FIRE TV  /  ANDROID TV      •      DEBLOAT  ·  STREAM  ·  PLAY"

# cores
BG = (11, 15, 28)            # azul-marinho escuro
BORDER = (56, 86, 130)
SUBCOL = (120, 196, 230)
STOPS = [(34, 211, 238), (79, 124, 255), (139, 92, 246), (236, 72, 153)]  # ciano->azul->roxo->magenta

TILE = 20          # lado do tile
GAP = 6            # espaço entre tiles
PAD = 64           # margem interna
LETTER_GAP = 1     # colunas vazias entre letras (em células)
CELL = TILE + GAP


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def grad(t):
    t = max(0.0, min(1.0, t))
    seg = t * (len(STOPS) - 1)
    i = min(int(seg), len(STOPS) - 2)
    return lerp(STOPS[i], STOPS[i + 1], seg - i)


# monta a grade de células "ligadas" do word
cells = []  # (col, row)
col_cursor = 0
max_rows = 7
for ch in WORD:
    glyph = FONT[ch]
    w = len(glyph[0])
    for r, line in enumerate(glyph):
        for c, bit in enumerate(line):
            if bit == "1":
                cells.append((col_cursor + c, r))
    col_cursor += w + LETTER_GAP

total_cols = col_cursor - LETTER_GAP
grid_w = total_cols * CELL - GAP
grid_h = max_rows * CELL - GAP

# fonte do subtítulo
sub_font = None
for p in ["/System/Library/Fonts/Menlo.ttc", "/System/Library/Fonts/SFNSMono.ttf",
          "/System/Library/Fonts/Supplemental/Courier New.ttf"]:
    if os.path.exists(p):
        try:
            sub_font = ImageFont.truetype(p, 22)
            break
        except Exception:
            pass
if sub_font is None:
    sub_font = ImageFont.load_default()

sub_h = 60
W = grid_w + PAD * 2
H = PAD + grid_h + 40 + sub_h + PAD // 2

img = Image.new("RGB", (W, H), BG)

# camada de tiles (para o glow)
tiles = Image.new("RGBA", (W, H), (0, 0, 0, 0))
td = ImageDraw.Draw(tiles)
x0 = PAD
y0 = PAD
for (c, r) in cells:
    x = x0 + c * CELL
    y = y0 + r * CELL
    color = grad(c / max(1, total_cols - 1))
    td.rounded_rectangle([x, y, x + TILE, y + TILE], radius=5,
                         fill=color + (255,))

# glow: blur de uma cópia, composto por baixo
glow = tiles.filter(ImageFilter.GaussianBlur(9))
img.paste(Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB"), (0, 0))
img.paste(Image.alpha_composite(img.convert("RGBA"), tiles).convert("RGB"), (0, 0))

draw = ImageDraw.Draw(img)

# borda arredondada fina
draw.rounded_rectangle([6, 6, W - 7, H - 7], radius=18, outline=BORDER, width=2)

# subtítulo centralizado, com leve espaçamento
sub = SUBTITLE
try:
    bbox = draw.textbbox((0, 0), sub, font=sub_font)
    tw = bbox[2] - bbox[0]
except Exception:
    tw = len(sub) * 11
sx = (W - tw) // 2
sy = y0 + grid_h + 34
draw.text((sx, sy), sub, font=sub_font, fill=SUBCOL)

out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "banner.png")
os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out)
print("wrote", out, img.size)
