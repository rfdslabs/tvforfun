#!/usr/bin/env python3
"""Gera lib/banner.txt — logo ANSI (truecolor) que o CLI imprime. Pure stdlib."""
import os

FONT = {
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
}
WORD = "TVFORFUN"
STOPS = [(34, 211, 238), (79, 124, 255), (139, 92, 246), (236, 72, 153)]
BLOCK = "██"   # cada célula = 2 blocos
EMPTY = "  "
RESET = "\033[0m"


def grad(t):
    t = max(0.0, min(1.0, t))
    seg = t * (len(STOPS) - 1)
    i = min(int(seg), len(STOPS) - 2)
    a, b, f = STOPS[i], STOPS[i + 1], seg - i
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


# colunas (col global, letra)
cols = []  # lista de (letra, col_na_letra, col_global)
cg = 0
for ch in WORD:
    for c in range(5):
        cols.append((ch, c, cg)); cg += 1
    cg += 1  # gap entre letras
total = cg - 1

lines = []
for r in range(7):
    line = []
    for (ch, c, gcol) in cols:
        on = FONT[ch][r][c] == "1"
        if on:
            R, G, B = grad(gcol / max(1, total - 1))
            line.append(f"\033[38;2;{R};{G};{B}m{BLOCK}{RESET}")
        else:
            line.append(EMPTY)
        # gap entre letras
        if c == 4:
            line.append(EMPTY)
    lines.append("  " + "".join(line))

sub = "  \033[38;2;120;196;230m  fire tv / android tv   •   debloat · stream · play" + RESET
art = "\n".join(lines) + "\n" + sub + "\n"

out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib", "banner.txt")
with open(out, "w") as f:
    f.write(art)
print("wrote", out)
