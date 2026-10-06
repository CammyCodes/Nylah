"""Nylah's face as 32x32 pixel art: the mod icon, the GitHub logo and the README banner.

Drawn from shapes on a 32x32 grid (supersampled per pixel, then snapped to a
small palette), with fine features taking priority over fur, so every pixel is
crisp: big glossy eyes with two sparkles, huge ears with pink insides, the
seal mask with the ginger flash on HER right (the viewer's left), a tiny nose,
a little "w" mouth, the blep, and rosy cheeks.

Outputs:
  src/main/resources/assets/nylah/icon.png   128 x 128 (the mod icon)
  docs/logo.png                              512 x 512 (GitHub / social)
  docs/banner.png                            1280 x 400 (README header)
  docs/logo-32.png                           the raw 32 x 32
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

C = {
    "K": (52, 38, 44),      # outline
    "I": (247, 242, 234),   # ivory
    "C": (232, 220, 204),   # cream
    "F": (222, 186, 142),   # fawn
    "M": (128, 96, 72),     # mask brown
    "D": (82, 60, 48),      # mask deep
    "G": (204, 138, 70),    # ginger
    "P": (240, 170, 182),   # ear pink
    "B": (126, 146, 216),   # iris
    "L": (178, 194, 240),   # iris light
    "N": (30, 30, 52),      # pupil
    "W": (255, 255, 255),   # sparkle
    "n": (58, 40, 38),      # nose
    "T": (234, 126, 146),   # tongue
    "R": (244, 164, 172),   # blush
}

N = 32


def ellipse(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def tri(x, y, a, b, c):
    def s(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1, d2, d3 = s((x, y), a, b), s((x, y), b, c), s((x, y), c, a)
    neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
    pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
    return not (neg and pos)


EARS = [((2.6, 14.0), (13.6, 9.6), (6.2, 1.6)), ((29.4, 14.0), (18.4, 9.6), (25.8, 1.6))]
INNER = [((5.0, 12.8), (11.8, 10.2), (7.0, 4.6)), ((27.0, 12.8), (20.2, 10.2), (25.0, 4.6))]
EYES = [(10.4, 18.4), (21.6, 18.4)]


def inside(x, y):
    head = ellipse(x, y, 16, 19.2, 12.6, 10.6) or ellipse(x, y, 16, 23.2, 14.2, 6.6)
    ear = any(tri(x, y, *e) for e in EARS)
    return head or ear


def sample(x, y):
    """(colour key, priority) at a point."""
    if not inside(x, y):
        return None, 0
    # Eyes first: they win.
    for ex, ey in EYES:
        if ellipse(x, y, ex, ey, 3.35, 3.65):
            u, v = (x - ex) / 3.35, (y - ey) / 3.65
            if (u + 0.36) ** 2 + (v + 0.36) ** 2 < 0.3 ** 2 or (u - 0.36) ** 2 + (v - 0.4) ** 2 < 0.15 ** 2:
                return "W", 4
            if u * u + (v + 0.06) ** 2 < 0.6 ** 2:
                return "N", 3
            if u * u + v * v > 0.86:
                return "K", 3
            return ("L" if v > 0.35 else "B"), 2
    # Nose, mouth, blep.
    if 21.6 <= y < 23.2 and abs(x - 16) <= 2.0 - (y - 21.6) * 1.0:
        return "n", 3
    mouth = {(15, 23), (16, 23), (13, 23), (18, 23), (14, 24), (17, 24)}
    if (int(x), int(y)) in mouth:
        return "K", 3
    if 24 <= y < 26 and 15 <= x < 17:
        return "T", 3
    # Ears: pink inside, dark outside.
    for e, i in zip(EARS, INNER):
        if tri(x, y, *e) and not ellipse(x, y, 16, 19.2, 12.6, 10.6):
            if tri(x, y, *i):
                return "P", 1
            return ("G" if x < 16 and 4 < y < 9 else "D"), 1
    # Face.
    col = "I"
    if y < 12.5:
        col = "C"
    m = 0.0
    if abs(x - 16) < 2.4 - max(0, 14 - y) * 0.12 and y > 8:
        m = 1.0                                                    # stripe up the forehead
    if ellipse(x, y, 16, 23.6, 5.6, 4.2):
        m = 1.0                                                    # muzzle
    for ex, ey in EYES:
        if ellipse(x, y, ex + (2.0 if ex < 16 else -2.0), ey + 1.4, 2.3, 2.6):
            m = max(m, 0.8)                                        # inner eye corners
    if m > 0.5:
        col = "D" if abs(x - 16) < 1.2 and y < 21 else "M"
    if ellipse(x, y, 11.0, 11.4, 2.3, 1.9):
        col = "G"                                                  # the ginger flash, her right
    for bx in (5.6, 26.4):
        if ellipse(x, y, bx, 23.2, 1.9, 1.2):
            col = "R"
    return col, 0


def draw():
    grid = [[None] * N for _ in range(N)]
    ss = 4
    for py in range(N):
        for px in range(N):
            votes = {}
            best_p = 0
            filled = 0
            for i in range(ss):
                for j in range(ss):
                    k, p = sample(px + (i + 0.5) / ss, py + (j + 0.5) / ss)
                    if k is None:
                        continue
                    filled += 1
                    votes.setdefault((p, k), 0)
                    votes[(p, k)] += 1
                    best_p = max(best_p, p)
            if filled < ss * ss / 2:
                continue
            # Highest priority that covers 30% of the pixel wins, else the majority.
            chosen = None
            for p in range(4, 0, -1):
                cands = [(n, k) for (pp, k), n in votes.items() if pp == p]
                if cands and sum(n for n, _ in cands) >= 0.3 * ss * ss:
                    chosen = max(cands)[1]
                    break
            if chosen is None:
                chosen = max((n, k) for (pp, k), n in votes.items())[1]
            grid[py][px] = chosen
    # A 1px outline round the silhouette.
    out = [row[:] for row in grid]
    for y in range(N):
        for x in range(N):
            if grid[y][x] is None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < N and 0 <= ny < N) or grid[ny][nx] is None:
                    out[y][x] = "K"
                    break
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            if out[y][x]:
                img.putpixel((x, y), C[out[y][x]] + (255,))
    return img


FONT = {
    "N": ["X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X", "X...X"],
    "Y": ["X...X", "X...X", ".X.X.", "..X..", "..X..", "..X..", "..X.."],
    "L": ["X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"],
    "A": [".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"],
    "H": ["X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"],
}


def banner(face):
    w, h = 1280, 400
    img = Image.new("RGBA", (w, h), (246, 239, 230, 255))
    px = Image.new("RGBA", (w, h))
    # Soft stripes in her colours.
    for y in range(h):
        t = y / h
        r = int(246 * (1 - t) + 236 * t)
        g = int(239 * (1 - t) + 222 * t)
        b = int(230 * (1 - t) + 236 * t)
        for x in range(w):
            px.putpixel((x, y), (r, g, b, 255))
    img = px
    big = face.resize((320, 320), Image.NEAREST)
    img.alpha_composite(big, (110, 40))
    scale = 22
    x0, y0 = 520, 110
    for ch in "NYLAH":
        rows = FONT[ch]
        for ry, row in enumerate(rows):
            for rx, c in enumerate(row):
                if c == "X":
                    for dy in range(scale):
                        for dx in range(scale):
                            img.putpixel((x0 + rx * scale + dx, y0 + ry * scale + dy), C["K"] + (255,))
        x0 += 6 * scale
    # A little periwinkle underline, the colour of her eyes.
    for y in range(y0 + 8 * scale, y0 + 8 * scale + 10):
        for x in range(520, 520 + 29 * scale):
            img.putpixel((x, y), C["B"] + (255,))
    return img


def main():
    face = draw()
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    face.save(os.path.join(ROOT, "docs", "logo-32.png"))
    face.resize((128, 128), Image.NEAREST).save(os.path.join(ROOT, "src", "main", "resources", "assets", "nylah", "icon.png"))
    face.resize((512, 512), Image.NEAREST).save(os.path.join(ROOT, "docs", "logo.png"))
    banner(face).save(os.path.join(ROOT, "docs", "banner.png"))
    print("logo, icon and banner written")


if __name__ == "__main__":
    main()
