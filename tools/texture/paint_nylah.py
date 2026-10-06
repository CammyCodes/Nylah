"""Paint Nylah's coat onto her model's UV layout.

For every texel the painter works out the exact 3D point on her body that the
texel lands on (using Minecraft's box-UV mapping, read from the 26.1 bytecode
of ModelPart$Cube / $Polygon), then asks one coat function what colour she is
there. Because the coat is defined in 3D, her markings run continuously across
the boxes: the mask flows from forehead onto muzzle, her legs darken toward
the paws, the tail darkens toward its tip, with no seams where boxes meet.

The STYLE is deliberately cute: clean soft colour fields instead of busy
realistic flecking, huge glossy eyes with two sparkles, a soft brown mask that
frames the eyes rather than swallowing them, a tiny nose and a little "w"
mouth. The MARKINGS are hers: tortie-point mask with a few ginger flecks, the
ginger flash on her right forehead, her dark right front paw and pale left,
her dark left hind paw, her darker right ear (seal mottled through ginger; her
left ear is ginger-orange behind), blue eyes, ginger tail band, dark tail tip, ivory bib.

Outputs (src/main/resources/assets/nylah/textures/entity/):
  nylah_8x.png  the master, 8 texels per model unit (512 x 512)
  nylah_4x.png  4 texels per unit, mapped onto the master's palette
  nylah_2x.png  2 texels per unit (the default in game), same palette
and tools/texture/out/uv_debug.png (each face labelled) for checking geometry.

Small features (eyes, nose, mouth) are painted with PRIORITY: if a texel is at
least 30% feature, it is painted from the feature alone, so at 2x her eyes are
still crisp pixel-art eyes rather than smudges averaged into the fur.

Colours come from tools/palette/palette.json (measured from her photos). The
iris and tongue keep their measured hues but are brightened, because the photos
they were measured in were shot in shade.

Her left/right: model -X is HER RIGHT.
"""
import colorsys
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GEO = os.path.join(ROOT, "src", "main", "resources", "assets", "nylah", "geo", "nylah.geo.json")
PALETTE = os.path.join(ROOT, "tools", "palette", "palette.json")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "nylah", "textures", "entity")
DEBUG_DIR = os.path.join(ROOT, "tools", "texture", "out")

SEED = 1907


# ----------------------------------------------------------------- colours

def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def lift(h, light, sat=None):
    """Keep a measured hue, set lightness (and optionally saturation)."""
    r, g, b = hexrgb(h) / 255.0
    hh, ll, ss = colorsys.rgb_to_hls(r, g, b)
    r, g, b = colorsys.hls_to_rgb(hh, light, ss if sat is None else sat)
    return np.array([r, g, b]) * 255.0


with open(PALETTE) as f:
    P = {k: hexrgb(v) for k, v in json.load(f).items()}

IVORY = P["ivory"] * 0.85 + np.array([252, 248, 240]) * 0.15
CREAM = P["ivory"] * 0.6 + P["paw_pale"] * 0.4
FAWN = lift("#d49758", 0.72, 0.42)                  # her back, soft and warm
FAWN_DEEP = lift("#bc8b58", 0.62, 0.38)
MASK = lift("#745734", 0.36, 0.30)                  # soft seal-brown, frames the eyes
MASK_DEEP = lift("#2c231b", 0.20, 0.22)
GINGER = lift("#915d25", 0.52, 0.55)
GINGER_LIGHT = lift("#bba389", 0.66, 0.45)
NOSE = lift("#271a11", 0.17, 0.18)
# Ears, measured from the videos (backs) and photos (fronts): her right ear is the darker
# one, seal mottled through ginger; her left ear's back is a warm ginger-orange.
EAR_GINGER = lift("#d19c71", 0.62, 0.50)            # her left ear's back
EAR_GINGER_DIM = lift("#bc926b", 0.55, 0.38)        # ginger between her right ear's mottles
EAR_SEAL = lift("#6e4e32", 0.30, 0.35)              # the seal mottle on her right ear
EAR_RIM_L = lift("#986e53", 0.46, 0.29)             # ear edge seen from the front, her left
EAR_RIM_R = lift("#70543a", 0.34, 0.32)             # ... and her darker right
EAR_PINK = lift("#d98a96", 0.80, 0.45)
EAR_PINK_DEEP = lift("#d98a96", 0.70, 0.42)
PAW_DARK = lift("#614c34", 0.35, 0.33)             # softened as much as her other darks, no more
PAW_PALE = P["paw_pale"]
TAIL_TIP = lift("#3b2108", 0.27, 0.32)
CHIN = lift("#d9d0bf", 0.86, 0.25)
IRIS = lift("#394454", 0.70, 0.62)                  # her periwinkle, measured hue
IRIS_LOW = lift("#394454", 0.80, 0.66)              # lighter toward the bottom: glossy
IRIS_RIM = lift("#394454", 0.44, 0.50)
PUPIL = np.array([22, 24, 44], float)
WHITE = np.array([255, 255, 255], float)
TONGUE = lift("#893f3f", 0.72, 0.62)
TONGUE_DEEP = lift("#893f3f", 0.62, 0.55)
MOUTH = np.array([150, 74, 86], float)
BLUSH = np.array([242, 160, 168], float)
LINE = lift("#2c231b", 0.16, 0.2)
PAD = lift("#3a2a26", 0.24, 0.18)
PAD_PINK = np.array([214, 140, 150], float)


# ----------------------------------------------------------------- noise

def _hash3(ix, iy, iz, salt):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + salt * 1442695041 + SEED * 97) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(p, freq, salt=0):
    """Smooth value noise in 3D, 0..1. p is (N, 3) in model units."""
    q = p * freq
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = np.zeros(len(p))
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (np.where(dx, f[:, 0], 1 - f[:, 0]) * np.where(dy, f[:, 1], 1 - f[:, 1])
                     * np.where(dz, f[:, 2], 1 - f[:, 2]))
                out += w * _hash3(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, salt)
    return out


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t)[:, None] if np.ndim(t) else t
    return a * (1 - t) + b * t


def grain(p):
    """A whisper of fur texture; cute means soft, so keep it faint."""
    return (vnoise(p, 2.6, 7) - 0.5) * 0.05 + (vnoise(p, 6.5, 8) - 0.5) * 0.03


def flecks(p, base, strength, salt=0):
    """A few soft tortie flecks (ginger and cream) through a base colour."""
    col = mix(base, GINGER, strength * sstep(0.7, 0.88, vnoise(p, 1.6, 300 + salt)) * 0.55)
    col = mix(col, GINGER_LIGHT, strength * sstep(0.74, 0.92, vnoise(p, 2.4, 200 + salt)) * 0.4)
    return col


# ----------------------------------------------------------------- geometry

def rot_zyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def load_geo():
    with open(GEO) as f:
        geo = json.load(f)
    by = {b["name"]: b for b in geo["bones"]}
    mats = {}

    def world(name):
        if name in mats:
            return mats[name]
        b = by[name]
        m = np.eye(4)
        m[:3, :3] = rot_zyx(*b["rot"])
        m[:3, 3] = b["pivot"]
        if b["parent"]:
            m = world(b["parent"]) @ m
        mats[name] = m
        return m

    for n in by:
        world(n)
    return geo, by, mats


def faces(cube):
    """The six faces of a box: (name, model normal, uv rect, [A, B, C, D] corners) in bone space.
    Corner order and UV corners are exactly ModelPart$Cube's: A->(u2,v1) B->(u1,v1) C->(u1,v2) D->(u2,v2)."""
    (ox, oy, oz), (w, h, d) = cube["origin"], cube["size"]
    u, v = cube["uv"]
    x0, y0, z0, x1, y1, z1 = ox, oy, oz, ox + w, oy + h, oz + d
    V = [np.array(p, float) for p in (
        (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
        (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1))]
    return [
        ("top",    (0, -1, 0), (u + d, v, u + d + w, v + d),                 [V[5], V[4], V[0], V[1]]),
        ("bottom", (0, 1, 0),  (u + d + w, v + d, u + d + 2 * w, v),         [V[2], V[3], V[7], V[6]]),
        ("right",  (-1, 0, 0), (u, v + d, u + d, v + d + h),                 [V[0], V[4], V[7], V[3]]),
        ("front",  (0, 0, -1), (u + d, v + d, u + d + w, v + d + h),         [V[1], V[0], V[3], V[2]]),
        ("left",   (1, 0, 0),  (u + d + w, v + d, u + 2 * d + w, v + d + h), [V[5], V[1], V[2], V[6]]),
        ("back",   (0, 0, 1),  (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), [V[4], V[5], V[6], V[7]]),
    ]


# ----------------------------------------------------------------- body, legs, tail

def coat_body(pm, normal, bone):
    """Clean ivory with a soft warm fawn saddle along her back."""
    x, y, z = pm[:, 0], pm[:, 1], pm[:, 2]
    top = sstep(18.2, 14.6, y)
    haunch = sstep(1.0, 4.5, z) * sstep(19.0, 15.5, y)
    col = mix(IVORY, CREAM, sstep(19.5, 17.0, y) * 0.5)
    col = mix(col, FAWN, np.clip(0.1 + 0.8 * top + 0.35 * haunch, 0, 1) * 0.85)
    col = mix(col, FAWN_DEEP, haunch * top * 0.3)
    # The soft brownish patches the top-down video shows over her shoulders and hips.
    col = mix(col, FAWN_DEEP, sstep(0.66, 0.86, vnoise(pm, 0.45, 11)) * top * 0.35)
    if normal[2] < 0 and bone in ("chest", "neck"):
        col = mix(col, IVORY, 0.9)                     # her bib
    if normal[1] > 0:
        col = mix(col, IVORY, 0.75)                    # tummy
    return col


def coat_leg(pm, pl, normal, bone):
    """Pale legs; her right front and left hind paws are dark and sooty, her left front
    pale, her right hind lightly sooty."""
    y = pm[:, 1]
    side = bone[-1]
    hind = bone.startswith(("thigh", "shin", "foot"))
    low = sstep(19.0, 23.6, y)
    col = mix(mix(IVORY, FAWN, 0.25), CREAM, low * 0.6)
    if not hind and side == "r":
        soot = sstep(20.2, 22.6, y)
        col = mix(col, flecks(pm, PAW_DARK, 0.8, 40), soot * 0.92)
    elif not hind:
        col = mix(col, PAW_PALE, sstep(21.0, 23.4, y) * 0.95)
    elif side == "l":
        soot = sstep(20.2, 22.6, y)
        col = mix(col, flecks(pm, PAW_DARK, 0.8, 41), soot * 0.92)
    else:
        soot = sstep(21.0, 23.8, y)
        col = mix(col, mix(PAW_DARK, CREAM, 0.45), soot * 0.75)
    if bone.startswith(("hand", "foot")) and normal[1] > 0:
        # Toe beans: one big pad and three little ones.
        x, z = pl[:, 0], pl[:, 2]
        big = ((x / 0.62) ** 2 + ((z - 0.45) / 0.55) ** 2) < 1
        toes = np.zeros(len(x), bool)
        for tx, tz in ((-0.6, -0.9), (0.0, -1.25), (0.6, -0.9)):
            toes |= ((x - tx) ** 2 + (z - tz) ** 2) < 0.27 ** 2
        bean = PAD_PINK if side == "l" and not hind else PAD
        col = np.where((big | toes)[:, None], bean, mix(col, CREAM, 0.3))
    return col


def coat_tail(pm, pl, bone, normal, segments):
    """Fawn at the root, a ginger band, then darkening to a soft seal tip."""
    seg = int(bone[4:]) - 1
    t = (seg * 3 + np.clip(pl[:, 2], 0, 3)) / (3.0 * segments)
    col = mix(FAWN, FAWN_DEEP, sstep(0.15, 0.6, t) * 0.5)
    col = mix(col, GINGER, sstep(0.22, 0.32, t) * sstep(0.55, 0.42, t) * 0.8)
    col = mix(col, TAIL_TIP, sstep(0.62, 0.95, t))
    return col


# ----------------------------------------------------------------- face

# Head space: head front face is z = -4, spans x -3.5..3.5, y -4.5..1.5.
EYES = (1.75, -1.75)
EYE_Y = -1.15
EYE_RX, EYE_RY = 1.12, 1.22


def eye_uv(x, y, ex):
    return (x - ex) / EYE_RX, (y - EYE_Y) / EYE_RY


def eye_inside(x, y):
    inside = np.zeros(len(x), bool)
    for ex in EYES:
        u, v = eye_uv(x, y, ex)
        inside |= (u * u + v * v) <= 1.0
    return inside


def paint_eyes(x, y, col):
    """Huge glossy eyes: periwinkle iris, big round pupil, two sparkles, a soft dark rim."""
    for ex in EYES:
        u, v = eye_uv(x, y, ex)
        r = np.sqrt(u * u + v * v)
        ins = r <= 1.0
        iris = mix(IRIS, IRIS_LOW, sstep(-0.2, 0.8, v))
        iris = mix(iris, IRIS_RIM, sstep(0.78, 1.0, r))
        pup = np.hypot(u, (v + 0.05) * 1.05) <= 0.56
        iris = np.where(pup[:, None], PUPIL, iris)
        # Sparkles: light from above, on HER right (-X) in both eyes, so they read as one light.
        big = np.hypot(u + 0.32, v + 0.36) <= 0.27
        small = np.hypot(u - 0.3, v - 0.34) <= 0.13
        iris = np.where((big | small)[:, None], WHITE, iris)
        col = np.where(ins[:, None], iris, col)
        lash = (r > 1.0) & (r < 1.22) & (v < -0.15)
        col = np.where(lash[:, None], mix(col, LINE, 0.8), col)
    return col


def mask_amount(x, y, z_front):
    """The soft mask: a stripe up the forehead, a rounded muzzle patch, and a ring round each eye."""
    stripe = sstep(1.1, 0.45, np.abs(x) + np.clip(-y - 1.5, 0, 3) * 0.15) * sstep(-4.6, -3.2, y) * 0.85
    muzzle = sstep(1.15, 0.7, np.hypot(x / 2.4, (y - 0.4) / 1.5)) * 0.72
    corners = np.zeros(len(x))
    for ex in EYES:
        # just the inner corner of each eye, where the stripe meets the muzzle
        corners = np.maximum(corners, sstep(0.9, 0.3, np.hypot((x - ex * 0.45) / 0.8, (y + 0.4) / 0.9)) * 0.7)
    return np.maximum.reduce([stripe, muzzle, corners]) * z_front


def face_base(ph):
    """Fur colour anywhere on the head (no eyes, nose or mouth): used by the lids too."""
    x, y, z = ph[:, 0], ph[:, 1], ph[:, 2]
    base = mix(IVORY, CREAM, 0.55)
    base = mix(base, FAWN, sstep(-2.5, -4.5, y) * 0.35 + sstep(-1.0, 2.0, z) * 0.3)
    z_front = sstep(0.0, -3.4, z)
    m = mask_amount(x, y, z_front)
    mcol = mix(MASK, MASK_DEEP, sstep(1.0, 0.0, np.abs(x)) * sstep(-3.0, 0.5, y) * 0.55)
    mcol = flecks(ph * 1.2, mcol, 0.7, 5)
    col = mix(base, mcol, np.clip(m, 0, 1))
    # The ginger flash on her right forehead (her right = -X), a little higher than the eye.
    flash = sstep(1.0, 0.55, np.hypot((x + 1.25) / 1.0, (y + 3.25) / 0.85))
    col = mix(col, mix(GINGER, GINGER_LIGHT, 0.4), flash * 0.85 * z_front)
    # Rosy cheeks, outside and below the eyes, where the mask fades.
    for bx in (2.85, -2.85):
        blush = sstep(1.0, 0.45, np.hypot((x - bx) / 0.75, (y - 0.35) / 0.5))
        col = mix(col, BLUSH, blush * 0.45 * z_front)
    return col * (1 + grain(ph)[:, None])


def is_nose(x, y):
    """Muzzle front: a small rounded inverted triangle, near the top."""
    ny = y + 0.05
    return (ny >= 0) & (ny <= 0.62) & (np.abs(x) <= 0.58 - ny * 0.6)


def is_mouth(x, y):
    """A little "w": a short stroke down from the nose, then two curls out."""
    stem = (np.abs(x) < 0.09) & (y > 0.55) & (y < 0.95)
    curl = (np.abs(np.hypot(np.abs(x) - 0.36, y - 0.88) - 0.36) < 0.085) & (y > 0.88)
    return stem | curl


def coat_head(ph, normal, bone, cube_index):
    x, y, z = ph[:, 0], ph[:, 1], ph[:, 2]
    col = face_base(ph)
    if bone == "head" and cube_index == 0 and normal[2] < 0:
        col = paint_eyes(x, y, col)
    if bone == "muzzle":
        col = mix(col, mix(MASK, GINGER_LIGHT, 0.35), 0.35)
        if normal[2] < 0:
            nose = is_nose(x, y)
            col = np.where(nose[:, None], NOSE, col)
            shine = np.hypot(x + 0.18, y - 0.08) < 0.1
            col = np.where(shine[:, None], mix(NOSE, WHITE, 0.55), col)
            col = np.where(is_mouth(x, y)[:, None], LINE, col)
        if normal[1] > 0:                              # roof of the mouth, two tiny fangs
            col = mix(col, MOUTH, 0.95)
            for fx in (0.65, -0.65):
                col = np.where((((x - fx) ** 2 + (z + 4.85) ** 2) < 0.16 ** 2)[:, None], WHITE, col)
    if bone == "jaw":
        col = mix(CHIN, col, 0.15)
        if normal[1] < 0:                              # floor of the mouth
            col = mix(col, MOUTH, 0.95)
    return col


def coat_ear(pl, normal, bone):
    """Big rounded ears, pink inside with ivory fluff. Her right ear is the darker:
    seal mottled through ginger behind, a deeper rim and pink in front. Her left is
    ginger-orange behind."""
    x, y = pl[:, 0], pl[:, 1]
    cx = 0.4 if bone == "ear_l" else -0.4
    f = np.clip(-y / 4.0, 0, 1)
    half = 2.0 * (1 - f ** 1.7) + 0.2
    dist = np.abs(x - cx)
    alpha = dist <= half
    inner = dist <= half - 0.6
    p3 = np.c_[x, y, np.zeros(len(x))]
    right = bone == "ear_r"
    if normal[2] < 0:
        col = mix(EAR_PINK, EAR_PINK_DEEP, f * 0.6 + (0.4 if right else 0))
        fluff = sstep(0.55, 0.8, vnoise(np.c_[x * 3.5, y * 1.2, np.zeros(len(x))], 1.0, 23)) * sstep(0.5, 0.05, f)
        col = mix(col, IVORY, fluff * 0.8) * (0.9 if right else 1.0)
        col = np.where(inner[:, None], col, EAR_RIM_R if right else EAR_RIM_L)
    elif right:
        col = mix(EAR_GINGER_DIM, EAR_SEAL, sstep(0.42, 0.62, vnoise(p3 * 2, 1.0, 26)) * 0.9)
    else:
        col = flecks(p3, EAR_GINGER, 0.3, 24)
    return col, alpha


def coat_lid(ph):
    """Eyelids: the fur around the eye, with a soft dark lash line at the bottom edge."""
    x, y = ph[:, 0], ph[:, 1]
    col = face_base(ph)
    alpha = np.zeros(len(x), bool)
    for ex in EYES:
        u, v = eye_uv(x, y, ex)
        r = np.sqrt(u * u + v * v)
        alpha |= r <= 1.12
    return col, alpha


def coat_happy(ph):
    """Closed, smiling ^ ^ eyes: fur over the eye with a dark upward arc."""
    x, y = ph[:, 0], ph[:, 1]
    col = face_base(ph)
    alpha = np.zeros(len(x), bool)
    for ex in EYES:
        u, v = eye_uv(x, y, ex)
        alpha |= (u * u + v * v) <= 1.18
        arc = (np.abs(np.hypot(u, v - 0.45) - 0.7) < 0.17) & (v < 0.45)
        col = np.where(arc[:, None], LINE, col)
    return col, alpha


def coat_tongue(pl, normal):
    col = np.tile(TONGUE, (len(pl), 1))
    if normal[1] < 0:
        col = mix(col, TONGUE_DEEP, np.exp(-(pl[:, 0] / 0.14) ** 2) * 0.6)
    return col


def whiskers(pl):
    """Three fine white whiskers fanning from the muzzle."""
    x, y = pl[:, 0], pl[:, 1]
    reach = np.abs(x)
    alpha = np.zeros(len(x), bool)
    for k, slope in enumerate((-0.28, -0.04, 0.2)):
        yy = -0.4 + k * 0.4 + slope * reach + 0.03 * reach * reach
        alpha |= (np.abs(y - yy) < 0.06) & (reach > 0.15) & (reach < 3.0 - k * 0.4)
    return np.tile(WHITE, (len(x), 1)), alpha


# ----------------------------------------------------------------- painting

def head_space(mats, bone_name, by):
    n = bone_name
    while n is not None:
        if n == "head":
            return np.linalg.inv(mats["head"]) @ mats[bone_name]
        n = by[n]["parent"]
    return None


def priority(bone, normal, ph, cube_index):
    """Feature level per sample: 0 fur, 1 iris, 2 pupil / nose / mouth / smile, 3 sparkle.
    A texel is painted from the highest level that covers 30% of it, so at 2x a
    sparkle is pure white and a pupil pure dark, never blended into each other."""
    name = bone["name"]
    x, y = ph[:, 0], ph[:, 1]
    lvl = np.zeros(len(x), int)
    if name == "head" and cube_index == 0 and normal[2] < 0:
        for ex in EYES:
            u, v = eye_uv(x, y, ex)
            ins = (u * u + v * v) <= 1.0
            lvl = np.maximum(lvl, np.where(ins, 1, 0))
            lvl = np.maximum(lvl, np.where(ins & (np.hypot(u, (v + 0.05) * 1.05) <= 0.56), 2, 0))
            spark = (np.hypot(u + 0.32, v + 0.36) <= 0.27) | (np.hypot(u - 0.3, v - 0.34) <= 0.13)
            lvl = np.maximum(lvl, np.where(ins & spark, 3, 0))
    elif name == "muzzle" and normal[2] < 0:
        lvl = np.where(is_nose(x, y) | is_mouth(x, y), 2, 0)
    elif name == "happy":
        for ex in EYES:
            u, v = eye_uv(x, y, ex)
            arc = (np.abs(np.hypot(u, v - 0.45) - 0.7) < 0.17) & (v < 0.45)
            lvl = np.maximum(lvl, np.where(arc, 2, 0))
    return lvl


def evaluate(bone, cube_index, face, mats, by, uv_pts, segments):
    """Colour + alpha + feature priority for UV points (in UV units) on one face."""
    name, normal, (u1, v1, u2, v2), (A, B, C, D) = face
    s = (uv_pts[:, 0] - u1) / (u2 - u1) if u2 != u1 else np.zeros(len(uv_pts))
    t = (uv_pts[:, 1] - v1) / (v2 - v1) if v2 != v1 else np.zeros(len(uv_pts))
    pl = B + s[:, None] * (A - B) + t[:, None] * (C - B)          # bone space
    hom = np.c_[pl, np.ones(len(pl))]
    pm = (mats[bone["name"]] @ hom.T).T[:, :3]                     # model space
    hs = head_space(mats, bone["name"], by)
    ph = (hs @ hom.T).T[:, :3] if hs is not None else None
    g, n = bone["group"], bone["name"]
    alpha = np.ones(len(pl), bool)
    nrm = np.array(normal)
    if g == "body":
        col = coat_body(pm, nrm, n) * (1 + grain(pm)[:, None])
    elif g in ("leg", "paw"):
        col = coat_leg(pm, pl, nrm, n) * (1 + grain(pm)[:, None])
    elif g == "tail":
        col = coat_tail(pm, pl, n, nrm, segments) * (1 + grain(pm)[:, None])
    elif g == "head":
        col = coat_head(ph, nrm, n, cube_index)
    elif g == "ear":
        col, alpha = coat_ear(pl, nrm, n)
    elif g == "tongue":
        col = coat_tongue(pl, nrm)
    elif g == "lid":
        col, alpha = coat_lid(ph)
    elif g == "happy":
        col, alpha = coat_happy(ph)
    elif g == "whisker":
        col, alpha = whiskers(pl)
    else:
        col = np.tile(np.array([255, 0, 255], float), (len(pl), 1))
    prio = priority(bone, nrm, ph, cube_index) if ph is not None else np.zeros(len(pl), int)
    return np.clip(col, 0, 255), alpha, prio


def paint(density, ss=3):
    geo, by, mats = load_geo()
    tw, th = geo["texture"]
    segments = geo.get("tailSegments", 4)
    img = np.zeros((th * density, tw * density, 4), np.uint8)
    for b in geo["bones"]:
        for ci, c in enumerate(b["cubes"]):
            for face in faces(c):
                name, normal, (u1, v1, u2, v2), _ = face
                umin, umax = sorted((u1, u2))
                vmin, vmax = sorted((v1, v2))
                if umax - umin <= 0 or vmax - vmin <= 0:
                    continue
                px0, px1 = int(round(umin * density)), int(round(umax * density))
                py0, py1 = int(round(vmin * density)), int(round(vmax * density))
                xs, ys = np.meshgrid(np.arange(px0, px1), np.arange(py0, py1))
                xs, ys = xs.ravel(), ys.ravel()
                acc = np.zeros((len(xs), 3))
                lacc = np.zeros((4, len(xs), 3))
                ln = np.zeros((4, len(xs)))
                hits = np.zeros(len(xs))
                for i in range(ss):
                    for j in range(ss):
                        uv = np.c_[(xs + (i + 0.5) / ss) / density, (ys + (j + 0.5) / ss) / density]
                        col, alpha, prio = evaluate(b, ci, face, mats, by, uv, segments)
                        acc += col
                        for lv in (1, 2, 3):
                            m = prio == lv
                            lacc[lv] += col * m[:, None]
                            ln[lv] += m
                        hits += alpha
                avg = acc / (ss * ss)
                for lv in (1, 2, 3):                       # higher levels overwrite lower ones
                    feat = ln[lv] >= 0.3 * ss * ss
                    avg[feat] = lacc[lv][feat] / ln[lv][feat][:, None]
                img[ys, xs, :3] = avg.astype(np.uint8)
                img[ys, xs, 3] = np.where(hits >= (ss * ss) / 2, 255, 0)
    return Image.fromarray(img, "RGBA")


KEYS = (IRIS, IRIS_LOW, IRIS_RIM, PUPIL, WHITE, NOSE, TONGUE, TONGUE_DEEP, MOUTH, EAR_PINK,
        LINE, GINGER, PAD, PAD_PINK, BLUSH, MASK, EAR_GINGER, EAR_SEAL, PAW_DARK)


def lock_to(master, img, colours=56):
    """Map a lower-density texture onto the master's palette (alpha kept separately).
    Her key colours are pinned into the palette: median-cut alone drops the iris
    blue, because it covers so little of the texture."""
    base = master.convert("RGB").quantize(colors=colours, method=Image.MEDIANCUT).getpalette()[:colours * 3]
    for k in KEYS:
        base += [int(v) for v in k]
    pal = Image.new("P", (1, 1))
    pal.putpalette(base + [0] * (768 - len(base)))
    rgb = img.convert("RGB").quantize(palette=pal, dither=Image.Dither.NONE).convert("RGB")
    out = rgb.convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def uv_debug():
    geo, by, mats = load_geo()
    tw, th = geo["texture"]
    k = 12
    im = Image.new("RGBA", (tw * k, th * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rng = np.random.default_rng(3)
    for b in geo["bones"]:
        for c in b["cubes"]:
            base = rng.integers(60, 230, 3)
            for i, (name, _, (u1, v1, u2, v2), _) in enumerate(faces(c)):
                umin, umax = sorted((u1, u2))
                vmin, vmax = sorted((v1, v2))
                if umax - umin <= 0 or vmax - vmin <= 0:
                    continue
                shade = tuple(int(x) for x in np.clip(base * (0.7 + 0.1 * i), 0, 255)) + (255,)
                d.rectangle((umin * k, vmin * k, umax * k - 1, vmax * k - 1), fill=shade, outline=(0, 0, 0, 255))
                d.text((umin * k + 2, vmin * k + 1), f"{b['name'][:6]}\n{name[:2]}", fill=(0, 0, 0, 255))
    return im


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(DEBUG_DIR, exist_ok=True)
    uv_debug().save(os.path.join(DEBUG_DIR, "uv_debug.png"))
    if "--debug-only" in sys.argv:
        return
    master = paint(8)
    master.save(os.path.join(OUT_DIR, "nylah_8x.png"))
    for dens in (4, 2):
        lock_to(master, paint(dens)).save(os.path.join(OUT_DIR, f"nylah_{dens}x.png"))
    print("painted nylah_8x / 4x / 2x")


if __name__ == "__main__":
    main()
