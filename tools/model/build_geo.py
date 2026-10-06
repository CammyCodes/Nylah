"""Nylah's skeleton: the single definition of her model.

Writes src/main/resources/assets/nylah/geo/nylah.geo.json, which three things read:
  * the mod (GeoLoader builds a MeshDefinition from it),
  * tools/texture/paint_nylah.py (paints her coat onto exactly these faces),
  * tools/preview (renders her in a browser, for checking likeness and animations).

She is built on BABY-ANIMAL proportions on purpose: a big round head (about
1.5x her body's width) with chubby cheeks, huge eyes set low and wide, a tiny
muzzle, big rounded ears, a short round body, stubby legs and a plump tail
carried up. Her markings are what make her Nylah; the proportions are what
make her adorable.

Units and axes are Minecraft model units: 1/16 block, Y points DOWN, the
ground is y = 24 and her nose points to -Z. Model -X is HER RIGHT side (the
renderer flips X), which matters because she is not symmetrical: her right
front paw is dark, her left is pale, and the ginger flash is on her right.

Each bone's pivot is relative to its parent's pivot (as PartPose.offset is),
each cube's origin is relative to its bone's pivot (as CubeListBuilder.addBox
is). Sizes are whole numbers so every face owns a whole number of UV texels.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "nylah", "geo", "nylah.geo.json")

TEX_W, TEX_H = 64, 64

BONES = []


def bone(name, parent, pivot, rot=(0, 0, 0), cubes=(), group=None):
    BONES.append({
        "name": name,
        "parent": parent,
        "pivot": list(pivot),
        "rot": list(rot),
        "cubes": [dict(c) for c in cubes],
        "group": group or name,
    })


def box(origin, size, inflate=0.0):
    """inflate is a number or (x, y, z): CubeDeformation grows/shrinks the geometry, never the UVs."""
    inf = list(inflate) if isinstance(inflate, (tuple, list)) else [inflate] * 3
    return {"origin": list(origin), "size": list(size), "inflate": inf}


# --- body: a short round loaf. Hips are the root; the chest hangs off them so the spine can bend.
bone("body", None, (0, 17, 2.5), cubes=[box((-2.5, -2.5, -2.5), (5, 5, 5), 0.2)], group="body")
bone("chest", "body", (0, 0, -2.5), cubes=[box((-2.5, -2.5, -5), (5, 5, 5), 0.3)], group="body")

# --- a short neck, then a big round head
bone("neck", "chest", (0, -1.5, -4), rot=(0.3, 0, 0), cubes=[box((-1.5, -3, -1.5), (3, 3, 3))], group="body")
bone("head", "neck", (0, -2.5, -0.5), rot=(-0.3, 0, 0), cubes=[
    box((-3.5, -4.5, -4), (7, 6, 6)),            # the head
    box((-4, -1.5, -3.5), (8, 3, 4)),            # chubby cheeks, a little wider at the bottom
], group="head")
bone("muzzle", "head", (0, 0.4, -4), cubes=[box((-1.5, -0.5, -1), (3, 2, 1))], group="head")
bone("jaw", "head", (0, 1.9, -4.2), cubes=[box((-1, 0, -0.6), (2, 1, 1))], group="head")
bone("tongue", "jaw", (0, -0.05, -0.2), cubes=[box((-0.5, -0.5, -1), (1, 1, 1), -0.12)], group="tongue")
bone("tongue_tip", "tongue", (0, 0, -1), cubes=[box((-0.5, -0.5, -1), (1, 1, 1), -0.18)], group="tongue")
bone("ear_l", "head", (2.2, -4.2, -1), rot=(-0.12, 0, 0.42), cubes=[box((-1.6, -4, -0.5), (4, 4, 1))], group="ear")
bone("ear_r", "head", (-2.2, -4.2, -1), rot=(-0.12, 0, -0.42), cubes=[box((-2.4, -4, -0.5), (4, 4, 1))], group="ear")
# Expression planes just in front of the face (all hidden at rest):
#   lids   - fur-coloured eyelids, scaled 0..1 in Y for blinks and slow blinks
#   happy  - closed, smiling ^ ^ eyes for purring and being stroked
bone("lid_l", "head", (1.75, -2.55, -4.03), cubes=[box((-1.5, 0, 0), (3, 3, 0))], group="lid")
bone("lid_r", "head", (-1.75, -2.55, -4.03), cubes=[box((-1.5, 0, 0), (3, 3, 0))], group="lid")
bone("happy", "head", (0, -2.55, -4.04), cubes=[box((-3.5, 0, 0), (7, 3, 0))], group="happy")
bone("whisker_l", "muzzle", (1.5, 0.4, -0.6), rot=(0, -0.4, 0.06), cubes=[box((0, -1, 0), (4, 2, 0))], group="whisker")
bone("whisker_r", "muzzle", (-1.5, 0.4, -0.6), rot=(0, 0.4, -0.06), cubes=[box((-4, -1, 0), (4, 2, 0))], group="whisker")

# --- stubby front legs: shoulder -> forearm -> round paw
for side, x in (("l", 1.4), ("r", -1.4)):
    bone(f"arm_{side}", "chest", (x, 2, -3.5), cubes=[box((-1, -1, -1), (2, 3, 2))], group="leg")
    bone(f"forearm_{side}", f"arm_{side}", (0, 2, 0), cubes=[box((-1, 0, -1), (2, 2, 2))], group="leg")
    bone(f"hand_{side}", f"forearm_{side}", (0, 2, 0), cubes=[box((-1, 0, -1.8), (2, 1, 3), 0.15)], group="paw")

# --- stubby hind legs: thigh -> shin -> round paw
for side, x in (("l", 1.5), ("r", -1.5)):
    bone(f"thigh_{side}", "body", (x, 1, 2), cubes=[box((-1, -1, -1.5), (2, 3, 3), 0.2)], group="leg")
    bone(f"shin_{side}", f"thigh_{side}", (0, 2, 0.5), cubes=[box((-1, 0, -1), (2, 3, 2))], group="leg")
    bone(f"foot_{side}", f"shin_{side}", (0, 3, 0), cubes=[box((-1, 0, -1.8), (2, 1, 3), 0.15)], group="paw")

# --- a plump tail, carried up in a happy question-mark curl
bone("tail1", "body", (0, -1.5, 2.5), rot=(0.95, 0, 0), cubes=[box((-1, -1, 0), (2, 2, 3), (-0.15, -0.15, 0.1))], group="tail")
bone("tail2", "tail1", (0, 0, 3), rot=(0.35, 0, 0), cubes=[box((-1, -1, 0), (2, 2, 3), (-0.25, -0.25, 0.15))], group="tail")
bone("tail3", "tail2", (0, 0, 3), rot=(0.3, 0, 0), cubes=[box((-1, -1, 0), (2, 2, 3), (-0.32, -0.32, 0.15))], group="tail")
bone("tail4", "tail3", (0, 0, 3), rot=(-0.55, 0, 0), cubes=[box((-1, -1, 0), (2, 2, 3), (-0.38, -0.38, 0.1))], group="tail")
TAIL_SEGMENTS = 4


def uv_size(size):
    w, h, d = size
    return 2 * (d + w), d + h


def pack():
    """Shelf-pack every cube's box-UV region into the texture, biggest first."""
    cubes = [(b, c) for b in BONES for c in b["cubes"]]
    cubes.sort(key=lambda bc: (-uv_size(bc[1]["size"])[1], -uv_size(bc[1]["size"])[0]))
    x = y = shelf = 0
    for b, c in cubes:
        w, h = uv_size(c["size"])
        if x + w > TEX_W:
            x, y = 0, y + shelf
            shelf = 0
        if y + h > TEX_H:
            raise SystemExit(f"UV overflow at {b['name']}: needs a bigger texture")
        c["uv"] = [x, y]
        x += w
        shelf = max(shelf, h)


def main():
    pack()
    geo = {
        "format": 1,
        "texture": [TEX_W, TEX_H],
        "tailSegments": TAIL_SEGMENTS,
        "note": "Generated by tools/model/build_geo.py. Edit that, not this.",
        "bones": BONES,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(geo, f, indent=1)
    print(f"{len(BONES)} bones, {sum(len(b['cubes']) for b in BONES)} cubes -> {OUT}")


if __name__ == "__main__":
    main()
