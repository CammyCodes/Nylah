"""Measure Nylah's colours from the reference photos.

Each entry names a photo and a point (as fractions of width/height). The script
takes the median of a small square around that point, so a stray whisker or
highlight can't skew it, and writes:

  tools/palette/palette.json      the measured colours (committed; the texture painter reads it)
  tools/palette/out/samples.png   a contact sheet: each crop beside its swatch, to check the point landed

The photos live in reference/ (git-ignored), so this only runs on a machine that has them.
"""
import json
import os

from PIL import Image, ImageDraw
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PHOTOS = os.path.join(ROOT, "reference", "photos")
OUT_JSON = os.path.join(ROOT, "tools", "palette", "palette.json")
OUT_SHEET = os.path.join(ROOT, "tools", "palette", "out", "samples.png")

# name -> (photo, fx, fy, half-size in px, pixel filter)
# The filter keeps only the pixels that are the thing being measured inside the
# patch (the blue of an iris, not its pupil; the soot on a paw, not the pale toes).
SAMPLES = {
    "ivory":        ("p12_30.jpg", 0.47, 0.52, 18, "median"),  # chest/bib
    "cream":        ("p12_21.jpg", 0.30, 0.80, 30, "median"),  # flank, daylight
    "fawn":         ("p12_16.jpg", 0.12, 0.62, 30, "warm"),    # side/back, daylight
    "fawn_warm":    ("p12_5.jpg",  0.55, 0.45, 18, "median"),  # back under warm lamp light
    "fawn_deep":    ("p12_5.jpg",  0.62, 0.58, 14, "median"),  # haunch shading
    "seal":         ("p12_30.jpg", 0.455, 0.31, 8, "median"),  # nose bridge
    "seal_soft":    ("p12_30.jpg", 0.36, 0.30, 8, "median"),   # mask under her right eye
    "nose":         ("p12_30.jpg", 0.447, 0.355, 5, "median"), # nose leather
    "ginger":       ("p12_30.jpg", 0.405, 0.19, 30, "warm"),   # forehead flash (her right)
    "ginger_light": ("p12_10.jpg", 0.40, 0.23, 20, "warm"),    # forehead flash, second photo
    "iris":         ("p12_30.jpg", 0.355, 0.24, 30, "blue"),   # eye (her right)
    "iris_light":   ("p12_33.jpg", 0.33, 0.29, 30, "blue"),    # eye in daylight
    "ear_dark":     ("p12_30.jpg", 0.20, 0.06, 25, "dark"),    # ear outer rim
    "ear_pink":     ("p12_30.jpg", 0.66, 0.13, 8, "median"),   # ear inside
    "paw_dark":     ("p12_30.jpg", 0.33, 0.93, 40, "dark"),    # right front paw (sooty)
    "paw_pale":     ("p12_30.jpg", 0.545, 0.94, 10, "median"), # left front paw (pale)
    "tail_ginger":  ("p12_5.jpg",  0.37, 0.585, 8, "median"),  # ginger patch on tail
    "tail_tip":     ("p12_5.jpg",  0.43, 0.625, 8, "median"),  # seal tail tip
    "tongue":       ("p12_19.jpg", 0.68, 0.53, 25, "pink"),    # the blep, well lit
    "chin":         ("p12_30.jpg", 0.45, 0.42, 14, "light"),   # pale chin under the mask
}


def keep(patch, mode):
    """Select the pixels a filter is after; fall back to the whole patch if none match."""
    r, g, b = patch[:, 0].astype(int), patch[:, 1].astype(int), patch[:, 2].astype(int)
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if mode == "blue":
        sel = (b > r + 20) & (lum > 60)
    elif mode == "warm":
        sel = (r > b + 35) & (r > g + 8)
    elif mode == "pink":
        sel = (r > g + 40) & (r > 120)
    elif mode == "dark":
        sel = lum <= np.percentile(lum, 25)
    elif mode == "light":
        sel = lum >= np.percentile(lum, 70)
    else:
        sel = np.ones(len(patch), bool)
    return patch[sel] if sel.sum() >= 12 else patch


def sample(photo, fx, fy, h, mode):
    img = Image.open(os.path.join(PHOTOS, photo)).convert("RGB")
    w, ht = img.size
    cx, cy = int(fx * w), int(fy * ht)
    box = (max(cx - h, 0), max(cy - h, 0), min(cx + h, w), min(cy + h, ht))
    patch = keep(np.asarray(img.crop(box)).reshape(-1, 3), mode)
    rgb = tuple(int(v) for v in np.median(patch, axis=0))
    ctx = img.crop((max(cx - 90, 0), max(cy - 90, 0), min(cx + 90, w), min(cy + 90, ht))).resize((180, 180))
    d = ImageDraw.Draw(ctx)
    d.rectangle((90 - h * 180 // 180, 90 - h, 90 + h, 90 + h), outline=(255, 0, 255))
    return rgb, ctx


def main():
    palette = {}
    rows = []
    for name, (photo, fx, fy, h, mode) in SAMPLES.items():
        rgb, ctx = sample(photo, fx, fy, h, mode)
        palette[name] = "#%02x%02x%02x" % rgb
        rows.append((name, rgb, ctx))
    os.makedirs(os.path.dirname(OUT_SHEET), exist_ok=True)
    sheet = Image.new("RGB", (4 * 380, ((len(rows) + 3) // 4) * 200), (30, 30, 30))
    d = ImageDraw.Draw(sheet)
    for i, (name, rgb, ctx) in enumerate(rows):
        x, y = (i % 4) * 380, (i // 4) * 200
        sheet.paste(ctx, (x + 10, y + 10))
        d.rectangle((x + 200, y + 10, x + 370, y + 150), fill=rgb)
        d.text((x + 200, y + 160), f"{name} {palette[name]}", fill=(255, 255, 255))
    sheet.save(OUT_SHEET)
    with open(OUT_JSON, "w") as f:
        json.dump(palette, f, indent=2)
    print(json.dumps(palette, indent=2))


if __name__ == "__main__":
    main()
