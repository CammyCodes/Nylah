"""Nylah's animation library, authored as data.

Writes src/main/resources/assets/nylah/anim/<clip>.json for every clip plus
anim/index.json. The mod's Animator plays them; tools/preview plays the same
files with a JavaScript twin of the sampler.

A clip's tracks are OFFSETS from her rest pose (standing): pos in model units,
rot in radians, scale as a multiplier. Every clip is ABSOLUTE about her body:
an action played while sitting carries the sit posture in its own keys (see
on()), so blending between any two clips is always meaningful.

Rotation cheat-sheet (model space, Y down, nose to -Z, -X is HER RIGHT):
  xRot +  : a part pointing forward tips its front DOWN; a leg swings its foot
            BACK; a part pointing up (neck, ears) leans FORWARD; the tail LIFTS.
  yRot +  : the front turns to her RIGHT.
  zRot +  : the top leans to her LEFT (+X).

Where a pose comes from her photos or videos, the reference is noted.
"""
import copy
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "nylah", "anim")

PI = 3.14159265


def P(**bones):
    """A pose: bone -> {"pos": (x,y,z), "rot": (x,y,z), "scale": (x,y,z)}; missing = rest."""
    return bones


def b(pos=None, rot=None, scale=None):
    d = {}
    if pos is not None:
        d["pos"] = list(pos)
    if rot is not None:
        d["rot"] = list(rot)
    if scale is not None:
        d["scale"] = list(scale)
    return d


def add(base, delta):
    """Combine two poses: positions and rotations add, scales multiply."""
    out = copy.deepcopy(base)
    for bone, ch in delta.items():
        tgt = out.setdefault(bone, {})
        for k, v in ch.items():
            if k == "scale":
                cur = tgt.get("scale", [1, 1, 1])
                tgt["scale"] = [a * c for a, c in zip(cur, v)]
            else:
                cur = tgt.get(k, [0, 0, 0])
                tgt[k] = [a + c for a, c in zip(cur, v)]
    return out


# ------------------------------------------------------------------ postures

STAND = P()

# Sitting tall, front paws together, tail wrapped round her feet (12(8), 12(16), 12(25)).
SIT = P(
    body=b(pos=(0, 3.0, 0.6), rot=(-0.85, 0, 0)),
    chest=b(rot=(-0.15, 0, 0)),
    neck=b(rot=(0.55, 0, 0)),
    head=b(rot=(0.45, 0, 0)),
    arm_l=b(pos=(0, -0.4, 0), rot=(1.0, 0, -0.05)), arm_r=b(pos=(0, -0.4, 0), rot=(1.0, 0, 0.05)),
    thigh_l=b(rot=(-0.6, 0, -0.15)), thigh_r=b(rot=(-0.6, 0, 0.15)),
    shin_l=b(rot=(2.45, 0, 0)), shin_r=b(rot=(2.45, 0, 0)),
    foot_l=b(rot=(-1.2, 0, 0)), foot_r=b(rot=(-1.2, 0, 0)),
    tail1=b(rot=(-1.75, 0.0, 0)),
    tail2=b(rot=(-0.35, 0.95, 0)), tail3=b(rot=(-0.3, 0.95, 0)), tail4=b(rot=(0.55, 0.8, 0)),
)

# The loaf: paws tucked, tail along her side (12(9), 12(11)).
LOAF = P(
    body=b(pos=(0, 4.3, 0)),
    neck=b(rot=(0.05, 0, 0)),
    head=b(rot=(0.05, 0, 0)),
    arm_l=b(rot=(1.45, 0, 0)), arm_r=b(rot=(1.45, 0, 0)),
    forearm_l=b(rot=(-2.6, 0, 0)), forearm_r=b(rot=(-2.6, 0, 0)),
    hand_l=b(rot=(1.1, 0, 0)), hand_r=b(rot=(1.1, 0, 0)),
    thigh_l=b(rot=(-1.4, 0, 0)), thigh_r=b(rot=(-1.4, 0, 0)),
    shin_l=b(rot=(2.8, 0, 0)), shin_r=b(rot=(2.8, 0, 0)),
    foot_l=b(rot=(-1.4, 0, 0)), foot_r=b(rot=(-1.4, 0, 0)),
    tail1=b(rot=(-1.65, -0.5, 0)),
    tail2=b(rot=(-0.35, -0.6, 0)), tail3=b(rot=(-0.3, -0.6, 0)), tail4=b(rot=(0.55, -0.5, 0)),
)

# Sphinx: like the loaf, front legs out in front.
SPHINX = add(LOAF, P(
    arm_l=b(rot=(-1.45 - 1.4, 0, 0)), arm_r=b(rot=(-1.45 - 1.4, 0, 0)),
    forearm_l=b(rot=(2.6, 0, 0)), forearm_r=b(rot=(2.6, 0, 0)),
    hand_l=b(rot=(0.3, 0, 0)), hand_r=b(rot=(0.3, 0, 0)),
))

# Lying on her side, legs out: sunbathing (video 3, 12(0)).
LIE_SIDE = P(
    body=b(pos=(-0.5, 4.6, 0), rot=(0, 0, 1.45)),
    neck=b(rot=(0, 0, -0.75)),
    head=b(rot=(0.1, 0, -0.35)),
    arm_l=b(rot=(-0.55, 0, 0)), arm_r=b(rot=(-0.4, 0, 0)),
    forearm_l=b(rot=(0.2, 0, 0)), forearm_r=b(rot=(0.3, 0, 0)),
    hand_l=b(rot=(0.5, 0, 0)), hand_r=b(rot=(0.6, 0, 0)),
    thigh_l=b(rot=(0.35, 0, 0)), thigh_r=b(rot=(0.15, 0, 0)),
    shin_l=b(rot=(0.2, 0, 0)), shin_r=b(rot=(0.3, 0, 0)),
    tail1=b(rot=(-1.5, 0, 0)), tail2=b(rot=(-0.3, 0, 0)), tail3=b(rot=(-0.25, 0, 0)), tail4=b(rot=(0.4, 0, 0)),
)

# Curled up asleep, nose tucked toward her tail.
CURL = P(
    body=b(pos=(-0.2, 4.3, 0), rot=(0, 0.25, 0.4)),
    chest=b(rot=(0, -0.55, 0)),
    neck=b(rot=(0.2, -0.6, -0.15)),
    head=b(rot=(0.4, -0.45, -0.3)),
    arm_l=b(rot=(1.3, 0, 0)), arm_r=b(rot=(1.3, 0, 0)),
    forearm_l=b(rot=(-2.4, 0, 0)), forearm_r=b(rot=(-2.4, 0, 0)),
    thigh_l=b(rot=(-1.3, 0, 0)), thigh_r=b(rot=(-1.3, 0, 0)),
    shin_l=b(rot=(2.6, 0, 0)), shin_r=b(rot=(2.6, 0, 0)),
    tail1=b(rot=(-1.6, -0.9, 0)), tail2=b(rot=(-0.35, -0.9, 0)), tail3=b(rot=(-0.3, -0.9, 0)), tail4=b(rot=(0.5, -0.8, 0)),
    ear_l=b(rot=(0.25, 0, 0.15)), ear_r=b(rot=(0.25, 0, -0.15)),
)

# Belly up, paws curled, head upside down looking at you (12(17), 12(18), 12(3)).
BELLY_UP = P(
    body=b(pos=(-1.6, 3.4, 0), rot=(0, 0, 2.35)),
    neck=b(rot=(-0.1, 0, -1.15)),
    head=b(rot=(-0.15, 0, -0.75)),
    arm_l=b(rot=(-0.4, 0, 0.25)), arm_r=b(rot=(-0.55, 0, -0.25)),
    forearm_l=b(rot=(-1.45, 0, 0)), forearm_r=b(rot=(-1.6, 0, 0)),
    hand_l=b(rot=(-1.0, 0, 0)), hand_r=b(rot=(-1.1, 0, 0)),
    thigh_l=b(rot=(-0.6, 0, 0.35)), thigh_r=b(rot=(-0.5, 0, -0.35)),
    shin_l=b(rot=(0.9, 0, 0)), shin_r=b(rot=(1.0, 0, 0)),
    foot_l=b(rot=(-0.6, 0, 0)), foot_r=b(rot=(-0.7, 0, 0)),
    tail1=b(rot=(-1.6, 0, 0)), tail2=b(rot=(-0.3, 0.25, 0)), tail3=b(rot=(-0.25, 0.3, 0)), tail4=b(rot=(0.3, 0.2, 0)),
)

POSTURES = {
    "stand": STAND, "sit": SIT, "loaf": LOAF, "sphinx": SPHINX,
    "lie_side": LIE_SIDE, "curl": CURL, "belly_up": BELLY_UP,
}

# Expression helpers.
LIDS_SHUT = P(lid_l=b(scale=(1, 1, 1)), lid_r=b(scale=(1, 1, 1)))
EARS_BACK = P(ear_l=b(rot=(0.35, 0, 0.25)), ear_r=b(rot=(0.35, 0, -0.25)))
EARS_FWD = P(ear_l=b(rot=(-0.2, 0, -0.1)), ear_r=b(rot=(-0.2, 0, 0.1)))
TONGUE_BLEP = P(tongue=b(pos=(0, -0.35, -0.55), scale=(1, 0.5, 1)))
TONGUE_IN = P(tongue=b(pos=(0, 0, 0.9), scale=(1, 0.5, 1)))


CLIPS = {}


def clip(name, posture, length, keys, loop=False, vis=None, events=None, look=0.0,
         blend_in=8, blend_out=8, kind="action"):
    """keys: list of (tick, delta pose); each key is the posture plus that delta."""
    base = POSTURES[posture]
    tracks = {}
    frames = [(t, add(base, d)) for t, d in keys]
    bones = set()
    for _, p in frames:
        bones.update(p.keys())
    bones.update(base.keys())
    for bn in sorted(bones):
        for ch in ("pos", "rot", "scale"):
            if not any(ch in p.get(bn, {}) for _, p in frames):
                continue
            dflt = [1, 1, 1] if ch == "scale" else [0, 0, 0]
            tracks.setdefault(bn, {})[ch] = [[t, [round(v, 4) for v in p.get(bn, {}).get(ch, dflt)]] for t, p in frames]
    CLIPS[name] = {
        "name": name, "kind": kind, "posture": posture, "length": length, "loop": loop,
        "blendIn": blend_in, "blendOut": blend_out, "lookAt": look,
        "tracks": tracks, "vis": vis or {}, "events": events or [],
    }


def held(posture, look=1.0, length=40):
    """A posture as a looping clip (with a gentle breath so it never looks frozen)."""
    clip(posture, posture, length, [(0, {}), (length // 2, P(chest=b(scale=(1.03, 1.03, 1)))), (length, {})],
         loop=True, look=look, kind="posture", blend_in=10, blend_out=10)


for name, look in (("stand", 1.0), ("sit", 1.0), ("loaf", 0.9), ("sphinx", 0.9),
                   ("lie_side", 0.5), ("curl", 0.0), ("belly_up", 0.6)):
    held(name, look)

SHUT = {"lid_l": [[0, True]], "lid_r": [[0, True]]}
HAPPY = {"happy": [[0, True]]}
TONGUE = {"tongue": [[0, True]]}

# --- tongue things -----------------------------------------------------------------

# The blep: just the tip, resting out. Her signature (12, 12(1), 12(19)).
clip("blep", "sit", 120, [(0, TONGUE_IN), (14, TONGUE_BLEP), (100, TONGUE_BLEP), (120, TONGUE_IN)],
     vis={"tongue": [[0, True], [119, False]]}, look=1.0)

clip("lick_lips", "sit", 30, [
    (0, TONGUE_IN), (5, add(TONGUE_BLEP, P(jaw=b(rot=(0.25, 0, 0))))),
    (10, add(TONGUE_BLEP, P(jaw=b(rot=(0.2, 0, 0)), tongue=b(rot=(-0.5, 0.4, 0)), tongue_tip=b(rot=(-0.8, 0, 0))))),
    (16, add(TONGUE_BLEP, P(jaw=b(rot=(0.2, 0, 0)), tongue=b(rot=(-0.5, -0.4, 0)), tongue_tip=b(rot=(-0.8, 0, 0))))),
    (22, TONGUE_IN), (30, {})],
    vis={"tongue": [[0, True], [24, False]]}, events=[[4, "lick"]], look=0.6)

# Lick a paw, then wash her face with it.
_paw_up = P(arm_l=b(rot=(-1.9, 0, -0.35)), forearm_l=b(rot=(-1.3, 0, 0)), hand_l=b(rot=(-0.4, 0, 0)))
_lick = add(_paw_up, add(TONGUE_BLEP, P(neck=b(rot=(0.35, 0, 0)), head=b(rot=(0.4, -0.25, 0)), jaw=b(rot=(0.2, 0, 0)))))
_wash = add(P(arm_l=b(rot=(-2.4, 0, -0.55)), forearm_l=b(rot=(-1.5, 0, 0))),
            P(neck=b(rot=(0.25, 0, 0)), head=b(rot=(0.3, -0.3, 0.45)), ear_l=b(rot=(0.3, 0, 0.2))))
clip("groom_paw", "sit", 110, [
    (0, {}), (12, _paw_up),
    (18, _lick), (24, add(_lick, P(tongue=b(rot=(-0.6, 0, 0))))), (30, _lick), (36, add(_lick, P(tongue=b(rot=(-0.6, 0, 0))))), (42, _lick),
    (54, _wash), (64, add(_wash, P(arm_l=b(rot=(0.6, 0, 0))))), (74, _wash), (84, add(_wash, P(arm_l=b(rot=(0.6, 0, 0))))),
    (98, _paw_up), (110, {})],
    vis={"tongue": [[0, False], [17, True], [44, False]], "lid_l": [[0, False], [18, True], [86, False]], "lid_r": [[0, False], [18, True], [86, False]]},
    events=[[18, "lick"], [30, "lick"], [42, "lick"]])

# Groom her flank: head turned right round (lies in the loaf).
_flank = P(neck=b(rot=(0.35, 1.05, 0)), head=b(rot=(0.65, 0.6, 0.1)), jaw=b(rot=(0.15, 0, 0)))
clip("groom_flank", "loaf", 80, [
    (0, {}), (14, _flank), (20, add(_flank, P(head=b(rot=(-0.25, 0, 0))))), (26, _flank),
    (32, add(_flank, P(head=b(rot=(-0.25, 0, 0))))), (38, _flank), (44, add(_flank, P(head=b(rot=(-0.25, 0, 0))))),
    (54, _flank), (70, {}), (80, {})],
    vis={"lid_l": [[0, False], [14, True], [56, False]], "lid_r": [[0, False], [14, True], [56, False]],
         "tongue": [[0, False], [16, True], [50, False]]},
    events=[[20, "lick"], [32, "lick"], [44, "lick"]])

# A big yawn with a curled tongue (12(27), 12(29)).
_yawn = add(EARS_BACK, P(neck=b(rot=(-0.3, 0, 0)), head=b(rot=(-0.45, 0, 0)), jaw=b(rot=(1.0, 0, 0)),
                          tongue=b(pos=(0, -0.2, -0.2)), tongue_tip=b(rot=(-0.7, 0, 0))))
clip("yawn", "sit", 50, [(0, {}), (10, add(_yawn, P(jaw=b(rot=(-0.5, 0, 0))))), (20, _yawn), (32, _yawn), (42, P(jaw=b(rot=(0.1, 0, 0)))), (50, {})],
     vis={"tongue": [[0, False], [12, True], [40, False]], "happy": [[0, False], [10, True], [40, False]]},
     events=[[12, "yawn"]])

# --- stretches ---------------------------------------------------------------------

# Front stretch: chest down, bum up, front legs reaching (the play bow).
_bow = P(body=b(pos=(0, 0.8, 0), rot=(0.42, 0, 0)), chest=b(rot=(0.15, 0, 0)),
         neck=b(rot=(-0.6, 0, 0)), head=b(rot=(-0.25, 0, 0)),
         arm_l=b(rot=(-1.25, 0, 0)), arm_r=b(rot=(-1.3, 0, 0)), hand_l=b(rot=(0.6, 0, 0), scale=(1.25, 1, 1.1)), hand_r=b(rot=(0.6, 0, 0), scale=(1.25, 1, 1.1)),
         thigh_l=b(rot=(-0.55, 0, 0)), thigh_r=b(rot=(-0.55, 0, 0)), shin_l=b(rot=(0.2, 0, 0)), shin_r=b(rot=(0.2, 0, 0)),
         tail1=b(rot=(0.3, 0, 0)), tail4=b(rot=(0.6, 0, 0)))
clip("stretch_front", "stand", 70, [(0, {}), (18, _bow), (30, add(_bow, P(body=b(rot=(0.05, 0, 0))))), (50, _bow), (64, {}), (70, {})],
     vis={"happy": [[0, False], [16, True], [52, False]]}, events=[[18, "stretch"]])

# Rear-leg stretch: one hind leg pushed out behind, then the other.
_rear_r = P(body=b(pos=(0, -0.2, -0.6), rot=(-0.1, 0, 0)), thigh_r=b(rot=(1.35, 0, 0)), shin_r=b(rot=(-0.6, 0, 0)), foot_r=b(rot=(0.6, 0, 0), scale=(1.2, 1, 1)))
_rear_l = P(body=b(pos=(0, -0.2, -0.6), rot=(-0.1, 0, 0)), thigh_l=b(rot=(1.35, 0, 0)), shin_l=b(rot=(-0.6, 0, 0)), foot_l=b(rot=(0.6, 0, 0), scale=(1.2, 1, 1)))
clip("stretch_back", "stand", 70, [(0, {}), (14, _rear_r), (28, _rear_r), (36, {}), (48, _rear_l), (60, _rear_l), (70, {})])

# The long stretch on her side: everything reaching, toes spread (12(36), 12(4)).
_long = P(chest=b(pos=(0, 0, -0.5)), arm_l=b(rot=(-1.4, 0, 0)), arm_r=b(rot=(-1.25, 0, 0)),
          forearm_l=b(rot=(-0.2, 0, 0)), forearm_r=b(rot=(-0.2, 0, 0)),
          hand_l=b(rot=(-0.4, 0, 0), scale=(1.35, 1, 1.15)), hand_r=b(rot=(-0.4, 0, 0), scale=(1.35, 1, 1.15)),
          thigh_l=b(rot=(0.9, 0, 0)), thigh_r=b(rot=(0.7, 0, 0)), shin_l=b(rot=(-0.2, 0, 0)),
          neck=b(rot=(-0.3, 0, 0)), tail1=b(rot=(0.1, 0, 0)))
clip("stretch_long", "lie_side", 80, [(0, {}), (20, _long), (28, add(_long, P(hand_l=b(scale=(1.1, 1, 1))))), (50, _long), (70, {}), (80, {})],
     vis={"happy": [[0, False], [18, True], [56, False]]}, events=[[20, "stretch"]], look=0.3)

# --- belly-up ----------------------------------------------------------------------

# Roll and wiggle on her back, batting the air (12(17), 12(18)).
_w1 = P(body=b(rot=(0, 0, 0.22)), arm_l=b(rot=(-0.4, 0, 0)), forearm_l=b(rot=(0.5, 0, 0)))
_w2 = P(body=b(rot=(0, 0, -0.22)), arm_r=b(rot=(-0.4, 0, 0)), forearm_r=b(rot=(0.5, 0, 0)))
clip("roll_wiggle", "belly_up", 80, [(0, {}), (12, _w1), (24, _w2), (36, _w1), (48, _w2), (60, _w1), (72, {}), (80, {})],
     look=0.7, events=[[10, "purr"]])

# One arm stretched over her head, toes spread (12(3)).
_reach = P(arm_l=b(rot=(-2.4, 0, 0.35)), forearm_l=b(rot=(1.45, 0, 0)), hand_l=b(rot=(0.9, 0, 0), scale=(1.3, 1, 1.15)),
           neck=b(rot=(0, 0, 0.25)))
clip("belly_reach", "belly_up", 80, [(0, {}), (20, _reach), (60, _reach), (80, {})], look=0.8)

# An upside-down meow (12(27), 12(29)).
clip("upside_meow", "belly_up", 34, [(0, {}), (8, P(jaw=b(rot=(0.75, 0, 0)), head=b(rot=(-0.15, 0, 0)))), (20, P(jaw=b(rot=(0.6, 0, 0)))), (28, {}), (34, {})],
     events=[[8, "meow"]], look=1.0)

# --- affection -----------------------------------------------------------------------

# Slow blink: the cat way of saying "I love you".
clip("slow_blink", "sit", 60, [(0, P(lid_l=b(scale=(1, 0.0, 1)), lid_r=b(scale=(1, 0.0, 1)))),
                               (16, P(lid_l=b(scale=(1, 1.0, 1)), lid_r=b(scale=(1, 1.0, 1)), head=b(rot=(0.12, 0, 0)))),
                               (30, P(lid_l=b(scale=(1, 1.0, 1)), lid_r=b(scale=(1, 1.0, 1)), head=b(rot=(0.12, 0, 0)))),
                               (48, P(lid_l=b(scale=(1, 0.0, 1)), lid_r=b(scale=(1, 0.0, 1)))), (60, {})],
     vis={"lid_l": [[0, True], [52, False]], "lid_r": [[0, True], [52, False]]}, look=1.0)

# Looking straight up at you, chin tipped right back (12(13), 12(14), video 1).
_up = add(P(neck=b(rot=(-0.32, 0, 0)), head=b(rot=(-0.38, 0, 0)), chest=b(rot=(-0.08, 0, 0))),
          P(ear_l=b(rot=(0, 0, 0.2)), ear_r=b(rot=(0, 0, -0.2))))
clip("look_up", "sit", 80, [(0, {}), (14, _up), (66, _up), (80, {})], look=0.0)

# Head bonk: push her forehead into you, then rub along.
_bonk = P(body=b(pos=(0, 0, -1.0)), neck=b(rot=(0.45, -0.2, 0)), head=b(rot=(0.3, -0.2, -0.3)), ear_l=b(rot=(0.3, 0, 0.2)), ear_r=b(rot=(0.3, 0, -0.2)))
clip("head_bonk", "stand", 46, [(0, {}), (10, _bonk), (18, add(_bonk, P(head=b(rot=(0, 0.35, 0.55))))), (26, _bonk), (36, {}), (46, {})],
     vis={"happy": [[0, False], [8, True], [34, False]]}, events=[[10, "purr"]])

# Rubbing her cheek along something, over and over (video 2).
_rub = P(body=b(pos=(0, 1.0, 0)), neck=b(rot=(0.9, 0, 0)), head=b(rot=(0.35, -0.3, -0.5)))
_rub2 = P(body=b(pos=(0, 1.0, -0.3)), neck=b(rot=(0.9, 0, 0)), head=b(rot=(0.35, 0.1, 0.1)))
clip("cheek_rub", "stand", 90, [(0, {}), (12, _rub), (24, _rub2), (36, _rub), (48, _rub2), (60, _rub), (72, _rub2), (84, {}), (90, {})],
     vis={"happy": [[0, False], [12, True], [80, False]]})

# Making biscuits: front paws pressing in turn, eyes happy.
_kl = P(arm_l=b(rot=(-0.45, 0, 0)), forearm_l=b(rot=(0.35, 0, 0)))
_kr = P(arm_r=b(rot=(-0.45, 0, 0)), forearm_r=b(rot=(0.35, 0, 0)))
_kbase = P(body=b(pos=(0, 1.5, 0), rot=(-0.25, 0, 0)), neck=b(rot=(0.1, 0, 0)), head=b(rot=(0.15, 0, 0)))
clip("knead", "stand", 64, [(0, _kbase), (8, add(_kbase, _kl)), (16, _kbase), (24, add(_kbase, _kr)), (32, _kbase),
                           (40, add(_kbase, _kl)), (48, _kbase), (56, add(_kbase, _kr)), (64, _kbase)],
     loop=True, vis=HAPPY, events=[[0, "purr"]])

# Chin scratch: head up and to the side, eyes closed, leaning in (video 4).
_chin = P(neck=b(rot=(-0.35, 0, 0)), head=b(rot=(-0.45, 0.15, 0.45)), ear_l=b(rot=(0.2, 0, 0.25)), ear_r=b(rot=(0.2, 0, -0.25)))
clip("chin_scratch", "sit", 70, [(0, {}), (12, _chin), (30, add(_chin, P(head=b(rot=(0, -0.1, 0.1))))), (50, _chin), (64, {}), (70, {})],
     vis=HAPPY, events=[[6, "purr"]])

# Purring contentedly in the loaf, eyes happy.
clip("happy_purr", "loaf", 60, [(0, {}), (15, P(chest=b(scale=(1.04, 1.04, 1)), ear_l=b(rot=(0.15, 0, 0.15)), ear_r=b(rot=(0.15, 0, -0.15)))),
                                (30, {}), (45, P(chest=b(scale=(1.04, 1.04, 1)))), (60, {})],
     loop=True, vis=HAPPY, events=[[0, "purr"]])

# Paw tap: "excuse me".
_tap = P(arm_r=b(rot=(-1.25, 0, 0.1)), forearm_r=b(rot=(0.3, 0, 0)), hand_r=b(rot=(0.6, 0, 0)))
clip("paw_tap", "sit", 46, [(0, {}), (10, _tap), (15, add(_tap, P(arm_r=b(rot=(0.35, 0, 0))))), (20, _tap),
                            (25, add(_tap, P(arm_r=b(rot=(0.35, 0, 0))))), (34, _tap), (46, {})], look=1.0, events=[[15, "chirp"]])

# --- talking -------------------------------------------------------------------------

# The greeting trill: tail straight up, ears forward, two little chirps.
_trill = add(EARS_FWD, P(tail1=b(rot=(0.5, 0, 0)), tail2=b(rot=(-0.3, 0, 0)), tail3=b(rot=(-0.25, 0, 0)), tail4=b(rot=(0.3, 0, 0)), head=b(rot=(-0.15, 0, 0))))
clip("trill", "stand", 36, [(0, {}), (6, _trill), (10, add(_trill, P(jaw=b(rot=(0.3, 0, 0))))), (14, _trill),
                            (18, add(_trill, P(jaw=b(rot=(0.3, 0, 0))))), (24, _trill), (36, {})],
     events=[[8, "trill"]], look=1.0)

clip("meow", "sit", 30, [(0, {}), (6, P(head=b(rot=(-0.25, 0, 0)), jaw=b(rot=(0.55, 0, 0)))), (16, P(head=b(rot=(-0.2, 0, 0)), jaw=b(rot=(0.4, 0, 0)))), (24, {}), (30, {})],
     events=[[5, "meow"]], look=1.0)

clip("sneeze", "sit", 28, [(0, {}), (8, P(head=b(rot=(-0.3, 0, 0)))), (12, P(neck=b(rot=(0.4, 0, 0)), head=b(rot=(0.45, 0, 0)))), (18, P(head=b(rot=(-0.1, 0, 0)))), (28, {})],
     vis={"happy": [[0, False], [6, True], [18, False]]}, events=[[12, "sneeze"]])

# --- curiosity -----------------------------------------------------------------------

clip("head_tilt", "sit", 50, [(0, {}), (10, add(EARS_FWD, P(head=b(rot=(0, 0, 0.45))))), (40, add(EARS_FWD, P(head=b(rot=(0, 0, 0.45))))), (50, {})], look=1.0)

# Looking back over her right shoulder (12(5)).
_shoulder = P(neck=b(rot=(0, -0.3, 0)), head=b(rot=(0.05, -1.25, 0.1)))
clip("over_shoulder", "sit", 60, [(0, {}), (14, _shoulder), (46, _shoulder), (60, {})])

# Butt wiggle, then pounce.
_crouch = P(body=b(pos=(0, 2.2, 0.4), rot=(0.12, 0, 0)), neck=b(rot=(0.2, 0, 0)), head=b(rot=(-0.3, 0, 0)),
            arm_l=b(rot=(-0.5, 0, 0)), arm_r=b(rot=(-0.5, 0, 0)), forearm_l=b(rot=(1.0, 0, 0)), forearm_r=b(rot=(1.0, 0, 0)),
            thigh_l=b(rot=(-0.9, 0, 0)), thigh_r=b(rot=(-0.9, 0, 0)), shin_l=b(rot=(1.4, 0, 0)), shin_r=b(rot=(1.4, 0, 0)),
            tail1=b(rot=(-1.2, 0, 0)))
_leap = P(body=b(pos=(0, -4, -9), rot=(-0.25, 0, 0)), arm_l=b(rot=(-1.4, 0, 0)), arm_r=b(rot=(-1.4, 0, 0)),
          thigh_l=b(rot=(1.0, 0, 0)), thigh_r=b(rot=(1.0, 0, 0)), tail1=b(rot=(-0.8, 0, 0)))
_land = P(body=b(pos=(0, 0.6, -14)), arm_l=b(rot=(-0.3, 0, 0)), arm_r=b(rot=(-0.3, 0, 0)))
clip("pounce", "stand", 60, [(0, {}), (10, _crouch), (14, add(_crouch, P(body=b(rot=(0, 0, 0.12))))), (18, add(_crouch, P(body=b(rot=(0, 0, -0.12))))),
                             (22, add(_crouch, P(body=b(rot=(0, 0, 0.12))))), (26, add(_crouch, P(body=b(rot=(0, 0, -0.12))))), (30, _crouch),
                             (36, _leap), (42, _land), (52, P(body=b(pos=(0, 0, -14)))), (60, P(body=b(pos=(0, 0, -14))))],
     events=[[34, "pounce"]], look=0.0)

# --- rest ----------------------------------------------------------------------------

clip("sunbathe", "lie_side", 100, [(0, P(lid_l=b(scale=(1, 0.55, 1)), lid_r=b(scale=(1, 0.55, 1)))),
                                   (50, P(lid_l=b(scale=(1, 0.55, 1)), lid_r=b(scale=(1, 0.55, 1)), chest=b(scale=(1.04, 1.04, 1)), tail4=b(rot=(0.5, 0, 0)))),
                                   (100, P(lid_l=b(scale=(1, 0.55, 1)), lid_r=b(scale=(1, 0.55, 1))))],
     loop=True, vis={"lid_l": [[0, True]], "lid_r": [[0, True]]}, look=0.2)

clip("sleep", "curl", 120, [(0, {}), (60, P(chest=b(scale=(1.05, 1.05, 1)))), (120, {})],
     loop=True, vis=SHUT, events=[[0, "sleep"]], look=0.0)

clip("sleep_blep", "curl", 120, [(0, TONGUE_BLEP), (60, add(TONGUE_BLEP, P(chest=b(scale=(1.05, 1.05, 1))))), (120, TONGUE_BLEP)],
     loop=True, vis={**SHUT, **TONGUE}, look=0.0)

# Rubbing past your legs: a lean toward you, tail up with the tip hooked (used while walking a pass).
clip("leg_rub", "stand", 40, [(0, P(body=b(rot=(0, 0, -0.14)), head=b(rot=(0.2, -0.25, -0.35)), tail1=b(rot=(0.6, 0, 0)), tail3=b(rot=(0, 0.4, 0)), tail4=b(rot=(0.4, 0.8, 0)))),
                              (20, P(body=b(rot=(0, 0, -0.18)), head=b(rot=(0.25, -0.35, -0.45)), tail1=b(rot=(0.6, 0, 0)), tail3=b(rot=(0, 0.45, 0)), tail4=b(rot=(0.4, 0.9, 0)))),
                              (40, P(body=b(rot=(0, 0, -0.14)), head=b(rot=(0.2, -0.25, -0.35)), tail1=b(rot=(0.6, 0, 0)), tail3=b(rot=(0, 0.4, 0)), tail4=b(rot=(0.4, 0.8, 0))))],
     loop=True, vis=HAPPY, events=[[0, "purr"]], look=0.0)


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".json"):
            os.remove(os.path.join(OUT, f))
    names = list(CLIPS)
    for n, c in CLIPS.items():
        with open(os.path.join(OUT, n + ".json"), "w") as f:
            json.dump(c, f, separators=(",", ":"))
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump(names, f, indent=1)
    print(f"{len(names)} clips -> {OUT}")


if __name__ == "__main__":
    main()
