"""Generates the spell icons, the item, effect and overlay textures, and the spell sounds into src/main/resources.

Every asset is drawn or synthesized here from code, so there is nothing to license and any of it can be tweaked
and regenerated. Output is deterministic (fixed random seed).

    pip install numpy scipy pillow soundfile
    python tools/generate_assets.py               # write icons and sounds
    python tools/generate_assets.py --preview DIR # also write upscaled icons and spectrograms to DIR
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import signal

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/arcane_spells"
ICONS = ASSETS / "textures/gui/sprites/spell"
TEXTURES = ASSETS / "textures"
SOUNDS = ASSETS / "sounds"
SR = 44100


# Icons: 16x16 pixel art (32x32 for the Astral Staff), drawn as tapered strokes and discs with tiered colours,
# then outlined.

def hex_rgba(color, alpha=255):
    color = color.lstrip("#")
    return (int(color[0:2], 16), int(color[2:4], 16), int(color[4:6], 16), alpha)


class Canvas:
    def __init__(self, size=16):
        self.size = size
        self.px = [[None] * size for _ in range(size)]

    def put(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = color

    def stroke(self, points, width_start, width_end, tiers, alpha=255, alpha_end=None):
        """A polyline whose half-width (and optionally opacity) tapers from start to end.
        Tiers: (fraction of width, colour), inner first."""
        alpha_end = alpha if alpha_end is None else alpha_end
        lengths = [math.dist(a, b) for a, b in zip(points, points[1:])]
        total = sum(lengths)
        for y in range(self.size):
            for x in range(self.size):
                p = (x + 0.5, y + 0.5)
                best, best_t, walked = 1e9, 0.0, 0.0
                for (a, b), length in zip(zip(points, points[1:]), lengths):
                    ax, ay = a
                    dx, dy = b[0] - ax, b[1] - ay
                    u = max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (length * length)))
                    d = math.dist(p, (ax + u * dx, ay + u * dy))
                    if d < best:
                        best, best_t = d, (walked + u * length) / total
                    walked += length
                width = width_start + (width_end - width_start) * best_t
                for fraction, color in tiers:
                    if best <= width * fraction:
                        self.put(x, y, hex_rgba(color, round(alpha + (alpha_end - alpha) * best_t)))
                        break

    def disc(self, cx, cy, radius, tiers):
        for y in range(self.size):
            for x in range(self.size):
                d = math.dist((x + 0.5, y + 0.5), (cx, cy))
                for fraction, color in tiers:
                    if d <= radius * fraction:
                        self.put(x, y, hex_rgba(color))
                        break

    def art(self, rows, palette):
        for y, row in enumerate(rows):
            for x, char in enumerate(row):
                if char in palette:
                    self.put(x, y, hex_rgba(palette[char]))

    def outline(self, color):
        """Darkens every empty pixel touching a solid one, so icons read on any background. Faded pixels get no
        outline, which would turn them into dark smudges."""
        add = []
        for y in range(self.size):
            for x in range(self.size):
                if self.px[y][x] is not None:
                    continue
                neighbours = [self.px[ny][nx] for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                              if 0 <= nx < self.size and 0 <= ny < self.size and self.px[ny][nx] is not None]
                if any(n[3] >= 200 for n in neighbours):
                    add.append((x, y, hex_rgba(color)))
        for x, y, c in add:
            self.px[y][x] = c

    def image(self):
        img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        for y in range(self.size):
            for x in range(self.size):
                if self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        return img


def icon_arcane_bolt():
    """A comet: a bright orb up and to the right, its tail thinning and fading away behind it."""
    c = Canvas()
    c.stroke([(1.5, 14.5), (9.5, 6.5)], 0.6, 2.3, [(0.4, "#EBC8FF"), (0.75, "#B46CFF"), (1.0, "#7A2FD6")],
             alpha=60, alpha_end=255)
    c.disc(10.8, 5.2, 3.4, [(0.3, "#FFFFFF"), (0.6, "#EBC8FF"), (0.85, "#B46CFF"), (1.0, "#7A2FD6")])
    for x, y, color in ((13, 10, "#9CF2FF"), (6, 3, "#9CF2FF"), (9, 12, "#F3B5FF"), (4, 8, "#F3B5FF")):
        c.put(x, y, hex_rgba(color))
    c.outline("#2A0F4D")
    return c


def icon_chain_lightning():
    c = Canvas()
    c.stroke([(11.6, 0.6), (5.6, 7.6), (10.6, 7.6), (4.4, 15.4)], 2.0, 1.0,
             [(0.35, "#FFFFFF"), (0.7, "#9CF2FF"), (1.0, "#3FA9E0")])
    for x, y in ((13, 4), (2, 9)):
        c.put(x, y, hex_rgba("#F3B5FF"))
    c.outline("#0E2F4F")
    return c


def icon_blink():
    """Chevrons dashing right: two fading afterimages, then the bright one where the caster arrives."""
    c = Canvas()
    for x0, alpha, tiers in ((0.5, 90, [(1.0, "#B46CFF")]),
                             (4.9, 160, [(1.0, "#D9A2FF")]),
                             (9.3, 255, [(0.5, "#FFFFFF"), (1.0, "#F3B5FF")])):
        c.stroke([(x0, 3.0), (x0 + 4.6, 8.0), (x0, 13.0)], 0.95, 0.95, tiers, alpha)
    c.outline("#3A1052")
    return c


def icon_arcane_ward():
    c = Canvas()
    c.art([
        "................",
        "..KKKKKKKKKKKK..",
        "..KLYYYYYYYYOK..",
        "..KLddddddddOK..",
        "..KLdddVVdddOK..",
        "..KLddVPPVddOK..",
        "..KLdVPWWPVdOK..",
        "..KLdVPWWPVdOK..",
        "..KLddVPPVddOK..",
        "..KLdddVVdddOK..",
        "...KLddddddOK...",
        "...KLYddddYOK...",
        "....KLYddYOK....",
        ".....KLYYOK.....",
        "......KKKK......",
        "................",
    ], {"K": "#3B2408", "L": "#FFF0B0", "Y": "#FFD86B", "O": "#C99A2E",
        "d": "#2B1A55", "V": "#B46CFF", "P": "#EBC8FF", "W": "#FFFFFF"})
    return c


def icon_frost_nova():
    """A snowflake with one-pixel arms. Centred on a pixel centre (7.5) so every line runs through pixel centres
    and stays one pixel wide; branches near the tips of the straight arms make it read as a snowflake, not a star."""
    c = Canvas()
    cx = cy = 7.5
    arm = [(0.55, "#E6FAFF")]
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        c.stroke([(cx, cy), (cx + dx * 7, cy + dy * 7)], 1, 1, arm)
        fork = (cx + dx * 4, cy + dy * 4)
        for side in (-1, 1):
            # Branches angle outwards along the arm, two pixels each way.
            bx, by = dx - side * dy, dy + side * dx
            c.stroke([fork, (fork[0] + bx * 2, fork[1] + by * 2)], 1, 1, arm)
    for dx, dy in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        # Short diagonal sparks; full diagonal arms crowd the outline into a solid block at this size.
        c.stroke([(cx + dx * 2, cy + dy * 2), (cx + dx * 3, cy + dy * 3)], 1, 1, [(0.55, "#9CF2FF")])
    c.disc(cx, cy, 1.5, [(0.75, "#FFFFFF"), (1.0, "#9CF2FF")])
    c.outline("#16466E")
    return c


def icon_celestial_judgment():
    """A pillar of light falling from a star onto a rune circle, with sparks thrown up where it lands."""
    c = Canvas()
    cx, cy, rx, ry = 7.5, 12.6, 6.6, 2.3
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if 0.6 <= d <= 1.2:
                # The near half of the circle, below the beam's foot, is the brighter.
                c.put(x, y, hex_rgba("#FFD27A" if y + 0.5 >= cy else "#C99A2E"))
            elif d < 0.6:
                c.put(x, y, hex_rgba("#FFF0C8", 110))
    c.stroke([(7.5, 1.5), (7.5, 12.4)], 0.95, 1.2, [(0.55, "#FFFFFF"), (1.0, "#FFE08A")])
    for x, y in ((7, 1), (6, 2), (8, 2), (7, 0)):
        c.put(x, y, hex_rgba("#FFFFFF"))
    for x, y, color in ((3, 9, "#FFE08A"), (12, 9, "#FFE08A"), (2, 11, "#BDF3FF"), (13, 11, "#BDF3FF"), (4, 7, "#FFF0C8"),
                        (11, 6, "#FFF0C8")):
        c.put(x, y, hex_rgba(color))
    c.outline("#3B2408")
    return c


def icon_meteor_swarm():
    """Three meteors diving down to the left, blazing heads trailing tails that cool from orange to red."""
    c = Canvas()
    for head, tail in (((4.5, 11.5), (11.0, 5.0)), ((10.6, 13.2), (15.4, 8.4)), ((6.8, 5.4), (11.6, 0.6))):
        c.stroke([tail, head], 0.4, 1.5, [(0.45, "#FFE3A0"), (0.8, "#FF8A3D"), (1.0, "#C2271B")], alpha=70, alpha_end=255)
        c.disc(head[0], head[1], 1.6, [(0.45, "#FFFFFF"), (0.8, "#FFD27A"), (1.0, "#FF8A3D")])
    c.outline("#3B0F06")
    return c


def icon_singularity():
    """A black hole: a dark core in a full, bright photon ring, a thin accretion disk crossing in front of it and
    showing either side of it behind."""
    c = Canvas()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            disk = abs(py - px * 0.12) <= 0.75 and abs(px) <= 7.4
            if disk and (py > 0 or d > 3.5):
                # In front of the hole, or beside it: hot in the middle, cooler towards the edges.
                c.put(x, y, hex_rgba("#FFB8F0" if abs(px) < 4.5 else "#E45CFF" if abs(px) < 6.2 else "#A05CFF"))
            elif d <= 2.6:
                c.put(x, y, hex_rgba("#0A0612"))
            elif d <= 3.5:
                c.put(x, y, hex_rgba("#FFFFFF" if py < 0 else "#EBD8FF"))
    c.outline("#1E0A33")
    return c


def icon_chrono_field():
    """A clock stopped at ten past ten."""
    c = Canvas()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.dist((x + 0.5, y + 0.5), (cx, cy))
            if 5.6 <= d <= 7.1:
                c.put(x, y, hex_rgba("#7FFFE6"))
            elif d < 5.6:
                c.put(x, y, hex_rgba("#0F3B37"))
    for x, y in ((7, 2), (8, 2), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)):
        c.put(x, y, hex_rgba("#DFFFF8"))
    c.stroke([(cx, cy), (cx - 3.0, cy - 1.8)], 0.7, 0.6, [(1.0, "#FFFFFF")])
    c.stroke([(cx, cy), (cx + 3.4, cy - 3.4)], 0.6, 0.5, [(1.0, "#FFFFFF")])
    c.disc(cx, cy, 1.0, [(1.0, "#7FFFE6")])
    c.outline("#0B3A33")
    return c


def icon_astral_gate():
    """An upright oval gate with a rose and gold rim, a violet swirl and a star inside, on a glowing ground ring."""
    c = Canvas()
    cx, cy = 7.5, 7.0
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            e = math.hypot(px / 4.6, py / 6.3)
            g = math.hypot(px / 6.8, (y + 0.5 - 13.6) / 1.6)
            if 0.82 <= e <= 1.08:
                c.put(x, y, hex_rgba("#FFD27A" if px < 0 else "#FFB8F0"))
            elif e < 0.82:
                swirl = math.sin(math.atan2(py, px) * 3 + e * 7)
                c.put(x, y, hex_rgba("#8A4FD6" if swirl > 0.2 else "#4A2380"))
            elif 0.75 <= g <= 1.1 and y >= 12:
                c.put(x, y, hex_rgba("#FFB8F0", 200))
    for x, y in ((7, 7), (7, 6), (7, 8), (6, 7), (8, 7)):
        c.put(x, y, hex_rgba("#FFFFFF"))
    c.outline("#3A1033")
    return c


def icon_planetfall():
    """A planet plunging down to the left out of a starry sky: dark rock cut by lava cracks, a crystal ring, its leading
    edge white-hot and a tail of fire streaming behind it."""
    c = Canvas()
    c.stroke([(15.6, 0.4), (10.4, 5.6)], 0.5, 3.0, [(0.4, "#FFE3A0"), (0.75, "#FF8A3D"), (1.0, "#C2271B")], alpha=50, alpha_end=255)
    cx, cy, r = 7.4, 8.6, 4.9
    tilt = -0.38
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            # The ring, an ellipse tilted across the planet: in front of it below the planet's middle, behind it above.
            rx_, ry_ = px * math.cos(tilt) + py * math.sin(tilt), -px * math.sin(tilt) + py * math.cos(tilt)
            on_ring = abs((rx_ / 7.2) ** 2 + (ry_ / 1.7) ** 2 - 1) < 0.22
            if on_ring and (ry_ > 0 or d > r):
                c.put(x, y, hex_rgba("#F3D9FF" if ry_ > 0 else "#B98CE6"))
                continue
            if d > r:
                continue
            # White-hot along the leading edge (lower left), lava in the cracks, rock lit from the fire behind it.
            lead = (-px + py) / (r * 1.414)
            crack = abs(math.sin(px * 1.3 + py * 0.8 + 1.0)) < 0.2 or abs(math.sin(-px * 0.7 + py * 1.6 + 0.4)) < 0.16
            if d > r - 1.3 and lead > 0.3:
                color = "#FFF3D0" if lead > 0.75 else "#FFB347"
            elif crack:
                color = "#FF7A1F"
            elif px - py > 2.0:
                color = "#6B5A7E"
            else:
                color = "#3E3150"
            c.put(x, y, hex_rgba(color))
    for x, y in ((1, 1), (4, 0), (2, 4), (0, 7), (13, 12), (11, 15)):
        c.put(x, y, hex_rgba("#FFFFFF"))
    c.outline("#1A0F24")
    return c


ICON_MAKERS = {
    "arcane_bolt": icon_arcane_bolt,
    "chain_lightning": icon_chain_lightning,
    "blink": icon_blink,
    "arcane_ward": icon_arcane_ward,
    "frost_nova": icon_frost_nova,
    "celestial_judgment": icon_celestial_judgment,
    "meteor_swarm": icon_meteor_swarm,
    "singularity": icon_singularity,
    "chrono_field": icon_chrono_field,
    "astral_gate": icon_astral_gate,
    "planetfall": icon_planetfall,
}


# Textures under textures/: the Astral Staff item (32x32, like the Arcane Staff) and the judgment beam.

def texture_astral_staff():
    """The Arcane Staff's diagonal shaft, in midnight blue banded with gold, crowned by a gold ring holding a star."""
    c = Canvas(32)
    head = (24.5, 7.5)
    # A deep blue orb inside the ring, glowing towards the star at its heart.
    c.disc(head[0], head[1], 4.7, [(0.55, "#3E5BB8"), (1.0, "#22306E")])
    for y in range(32):
        for x in range(32):
            d = math.dist((x + 0.5, y + 0.5), head)
            if 4.6 <= d <= 5.9:
                # Shaded gold: lit from the top left.
                lit = (x + 0.5 - head[0]) + (y + 0.5 - head[1]) < 0
                c.put(x, y, hex_rgba("#FFE08A" if lit else "#C99A2E"))
    star = [(0.45, "#FFFFFF"), (0.8, "#FFF0C8"), (1.0, "#BDF3FF")]
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        c.stroke([head, (head[0] + dx * 3.6, head[1] + dy * 3.6)], 1.3, 0.5, star)
    for dx, dy in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        c.put(int(head[0] + dx * 2), int(head[1] + dy * 2), hex_rgba("#FFE08A"))
    c.disc(head[0], head[1], 1.3, [(1.0, "#FFFFFF")])
    c.outline("#1E1236")
    # The shaft goes on after the outline, which belongs to the head alone, as on the Arcane Staff.
    for i in range(17):
        x, y = 4 + i, 28 - i
        band = i % 5 == 2
        c.put(x, y, hex_rgba("#7A5A16" if band else "#141029"))
        c.put(x + 1, y, hex_rgba("#FFD27A" if band else "#2D3A8C"))
        c.put(x + 2, y, hex_rgba("#FFF0C8" if band else "#E8C25A"))
    for x, y in ((29, 1), (17, 3), (30, 13)):
        c.put(x, y, hex_rgba("#FFF0C8"))
    return c.image()


BEAM_FRAMES = 8


def texture_judgment_beam():
    """Eight frames side by side, each 32x256: a white-hot core in a wide golden glow, with streaks that flow down
    one frame-height per frame so the loop is seamless. Drawn additively, so brightness lives in the colour (black is
    invisible) and alpha stays opaque, whichever additive blend the renderer uses."""
    width, height = 32, 256
    shift = height // BEAM_FRAMES
    u = (np.arange(width) + 0.5) / width * 2 - 1
    core = np.exp(-(u / 0.14) ** 2)
    body = np.exp(-(u / 0.55) ** 2) * np.clip(1 - np.abs(u), 0, 1) ** 0.35
    # Its own generator, so adding textures never shifts the sounds' random stream.
    phases = np.random.default_rng(BEAM_FRAMES).uniform(0, 2 * math.pi, (3, width))
    sheet = np.zeros((height, width * BEAM_FRAMES, 4))
    for frame in range(BEAM_FRAMES):
        y = (np.arange(height)[:, None] - frame * shift) / height
        # Whole cycles per texture height, so every frame tiles vertically too.
        streaks = (0.5 * np.sin(2 * math.pi * 3 * y + phases[0]) + 0.3 * np.sin(2 * math.pi * 7 * y + phases[1])
                   + 0.2 * np.sin(2 * math.pi * 13 * y + phases[2]))
        intensity = np.clip(0.95 * core + 0.8 * body * (0.7 + 0.3 * streaks), 0, 1) ** 0.8
        rgb = np.stack([np.ones_like(intensity), 0.78 + 0.22 * intensity, 0.45 + 0.55 * intensity], axis=-1) * intensity[..., None]
        sheet[:, frame * width:(frame + 1) * width, :3] = rgb
        sheet[:, frame * width:(frame + 1) * width, 3] = 1
    return Image.fromarray(np.round(sheet * 255).astype(np.uint8), "RGBA")


def gold_rim(c, cx, cy, rx, ry, inner, outer):
    """An elliptical band of gold, lit from the top left: inner and outer are the band's edges as fractions of the
    ellipse."""
    for y in range(c.size):
        for x in range(c.size):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = math.hypot(dx, dy)
            if inner <= d <= outer:
                lit = dx + dy < -0.35
                shade = dx + dy > 0.6
                c.put(x, y, hex_rgba("#FFE08A" if lit else "#7A5A16" if shade else "#C99A2E"))


def texture_arcane_ring():
    """A gold band seen from above and a little in front, set with a cut amethyst, the Arcane Staff's crystal."""
    c = Canvas()
    gold_rim(c, 8, 10.2, 5.6, 3.9, 0.66, 1.0)
    # The setting, then the stone in it: light at the top left facet, deep violet below.
    c.disc(8, 5.6, 2.9, [(1.0, "#C99A2E")])
    c.disc(8, 5.6, 2.2, [(0.45, "#E9D4FF"), (0.8, "#AD55FF"), (1.0, "#6A2FA8")])
    c.put(7, 4, hex_rgba("#FFFFFF"))
    c.outline("#1E1236")
    return c.image()


def texture_astral_amulet():
    """A fine gold chain hanging to the Astral Staff's head in small: a gold ring round a deep blue orb, a star at
    its heart."""
    c = Canvas()
    chain = [(1.0, "#E8C25A")]
    c.stroke([(2.5, 0.5), (5.5, 6.0), (7.0, 7.4)], 0.55, 0.55, chain)
    c.stroke([(13.5, 0.5), (10.5, 6.0), (9.0, 7.4)], 0.55, 0.55, chain)
    head = (8.0, 11.0)
    c.disc(head[0], head[1], 3.6, [(0.55, "#3E5BB8"), (1.0, "#22306E")])
    gold_rim(c, head[0], head[1], 4.6, 4.6, 0.8, 1.0)
    star = [(0.5, "#FFFFFF"), (1.0, "#BDF3FF")]
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        c.stroke([head, (head[0] + dx * 2.6, head[1] + dy * 2.6)], 0.9, 0.4, star)
    c.put(7, 10, hex_rgba("#FFFFFF"))
    c.put(8, 10, hex_rgba("#FFFFFF"))
    c.outline("#1E1236")
    return c.image()


def texture_chrono_charm():
    """A pocket-watch charm on a loop: gold case, a pale face the Chrono Field's colour, its hands stopped."""
    c = Canvas()
    # The loop it hangs from.
    for y in range(16):
        for x in range(16):
            d = math.dist((x + 0.5, y + 0.5), (8.0, 2.4))
            if 1.0 <= d <= 2.0:
                c.put(x, y, hex_rgba("#E8C25A"))
    centre = (8.0, 9.6)
    c.disc(centre[0], centre[1], 5.2, [(0.78, "#BDF3FF"), (1.0, "#C99A2E")])
    gold_rim(c, centre[0], centre[1], 5.2, 5.2, 0.8, 1.0)
    # Hour marks at the quarters, then the hands: the hour hand short and dark, the minute hand long.
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        c.put(int(centre[0] + dx * 3.2), int(centre[1] + dy * 3.2), hex_rgba("#3B6E87"))
    c.stroke([centre, (centre[0] + 2.2, centre[1] - 1.6)], 0.6, 0.45, [(1.0, "#22306E")])
    c.stroke([centre, (centre[0] - 0.6, centre[1] - 3.3)], 0.5, 0.35, [(1.0, "#22306E")])
    c.put(8, 9, hex_rgba("#FFE08A"))
    c.outline("#1E1236")
    return c.image()


# Status effect icons: 18x18, as vanilla's, shown on the HUD and in the inventory.

def effect_instant_mana():
    """A drop of mana, the mana bar's violet, with a glint: Instant Mana."""
    c = Canvas(18)
    # A teardrop: a disc below, tapering to a point above.
    c.disc(9, 11.2, 5.2, [(0.45, "#D9C2FF"), (0.8, "#9B6BFF"), (1.0, "#5B2FC0")])
    c.stroke([(9, 2.2), (9, 8.0)], 0.4, 3.9, [(0.5, "#B48CFF"), (1.0, "#5B2FC0")])
    c.put(7, 9, hex_rgba("#FFFFFF"))
    c.put(7, 10, hex_rgba("#FFFFFF"))
    c.put(8, 8, hex_rgba("#F0E6FF"))
    c.outline("#1A0F2E")
    return c.image()


def effect_arcane_flow():
    """Two violet streams curling upwards, tapering as they rise: mana flowing back in."""
    c = Canvas(18)
    tiers = [(0.45, "#E9D4FF"), (1.0, "#9B5CFF")]
    for dx in (-3.5, 3.5):
        c.stroke([(9 + dx - 1.0, 16.5), (9 + dx + 1.8, 12.0), (9 + dx - 1.6, 7.0), (9 + dx + 0.8, 2.5)], 1.6, 0.45, tiers)
    for x, y in ((2, 4), (15, 6), (9, 1)):
        c.put(x, y, hex_rgba("#FFFFFF"))
    c.outline("#1A0F2E")
    return c.image()


def effect_clarity():
    """An open eye, pale as the Clarity potion: a mind seeing clearly."""
    c = Canvas(18)
    for y in range(18):
        for x in range(18):
            # The almond between two arcs, its lids lined in cyan.
            u, v = (x + 0.5 - 9) / 8.0, (y + 0.5 - 9) / 8.0
            lid = 0.55 * (1 - u * u)
            if abs(u) <= 1 and abs(v) <= lid:
                c.put(x, y, hex_rgba("#3B9BB0" if abs(v) > lid - 0.17 else "#F2FCFF"))
    c.disc(9, 9, 3.3, [(0.4, "#123A4A"), (0.75, "#4FC3D9"), (1.0, "#2D8AA0")])
    c.put(8, 8, hex_rgba("#FFFFFF"))
    c.outline("#0E1E2A")
    return c.image()


def texture_corruption_vignette():
    """White, clear at the centre and opaque at the edges, with faint tendrils creeping in: corruption's haze at the
    edges of sight, tinted violet when drawn."""
    size = 256
    y, x = np.mgrid[0:size, 0:size]
    u, v = (x + 0.5) / size * 2 - 1, (y + 0.5) / size * 2 - 1
    r = np.sqrt(u * u + v * v)
    angle = np.arctan2(v, u)
    tendrils = 0.04 * np.sin(angle * 11 + r * 6) + 0.025 * np.sin(angle * 23 - r * 9)
    t = np.clip((r - 0.68 + tendrils) / 0.62, 0, 1)
    alpha = (t * t * (3 - 2 * t)) ** 1.3
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


TEXTURE_MAKERS = {
    "item/astral_staff": texture_astral_staff,
    "effect/judgment_beam": texture_judgment_beam,
    "item/arcane_ring": texture_arcane_ring,
    "item/astral_amulet": texture_astral_amulet,
    "item/chrono_charm": texture_chrono_charm,
    "mob_effect/instant_mana": effect_instant_mana,
    "mob_effect/arcane_flow": effect_arcane_flow,
    "mob_effect/clarity": effect_clarity,
    "misc/corruption_vignette": texture_corruption_vignette,
}


# Sounds: mono 44.1 kHz Ogg Vorbis (Minecraft only attenuates mono sounds with distance).

rng = np.random.default_rng(20261004)


def times(duration):
    return np.arange(int(SR * duration)) / SR


def sweep(f0, f1, duration):
    """Phase of an exponential sweep from f0 to f1 Hz."""
    t = times(duration)
    k = math.log(f1 / f0) / duration
    return 2 * math.pi * f0 * (np.exp(k * t) - 1) / k


def noise(duration):
    return rng.standard_normal(int(SR * duration))


def band(x, low=None, high=None, order=4):
    if low and high:
        sos = signal.butter(order, [low, high], "bandpass", fs=SR, output="sos")
    elif low:
        sos = signal.butter(order, low, "highpass", fs=SR, output="sos")
    else:
        sos = signal.butter(order, high, "lowpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def decay(duration, tau, attack=0.003):
    t = times(duration)
    return np.minimum(t / attack, 1.0) * np.exp(-t / tau)


def swell(duration, peak, release):
    """Rises to full at `peak` seconds, then dies away over `release` seconds."""
    t = times(duration)
    return np.where(t < peak, (t / peak) ** 2, np.exp(-(t - peak) / release))


def bell(f0, duration, partials, attack=0.002):
    """Inharmonic bell: (ratio, amplitude, decay seconds) partials."""
    t = times(duration)
    out = np.zeros_like(t)
    for ratio, amp, tau in partials:
        out += amp * np.sin(2 * math.pi * f0 * ratio * t + rng.uniform(0, 2 * math.pi)) * np.exp(-t / tau)
    # Fade the tail: a bell cut off mid-ring clicks.
    release = np.clip((duration - t) / 0.03, 0.0, 1.0)
    return out * np.minimum(t / attack, 1.0) * release


def crackle(duration, rate, low=1500, length=0.002):
    """Sparse random clicks: electricity."""
    n = int(SR * duration)
    impulses = np.zeros(n)
    count = rng.poisson(rate * duration)
    positions = rng.integers(0, n, count)
    impulses[positions] = rng.uniform(0.3, 1.0, count) * rng.choice([-1, 1], count)
    kernel = rng.standard_normal(int(SR * length)) * np.exp(-np.arange(int(SR * length)) / (SR * length / 4))
    return band(np.convolve(impulses, kernel)[:n], low=low)


def place(total, part, start):
    """`part` mixed into a silent buffer of `total` seconds, starting at `start` seconds."""
    out = np.zeros(int(SR * total))
    i = int(SR * start)
    out[i:i + len(part)] += part[:len(out) - i]
    return out


def finish(x, peak=0.85):
    x = x - np.mean(x)
    fade = int(SR * 0.004)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade * 4:] *= np.linspace(1, 0, fade * 4)
    return x / np.max(np.abs(x)) * peak


def s(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def sound_bolt_charge():
    d = 0.9
    t = times(d)
    phase = sweep(240, 520, d)
    hum = np.sin(phase) + 0.5 * np.sin(2.003 * phase) + 0.25 * np.sin(3.01 * phase)
    hum *= 0.75 + 0.25 * np.sin(2 * math.pi * 9 * t)
    sparkle = band(noise(d), 3000, 7000) * np.linspace(0.2, 1, len(t))
    return finish((0.8 * s(hum) + 0.25 * s(sparkle)) * swell(d, 0.35, 0.4))


def sound_bolt_cast():
    d = 0.8
    t = times(d)
    zap = np.sin(sweep(1500, 200, d)) * decay(d, 0.12)
    whoosh = band(noise(d), 500, 3500) * decay(d, 0.22, attack=0.01)
    thump = np.sin(2 * math.pi * 70 * t) * decay(d, 0.08)
    shimmer = sum(np.sin(2 * math.pi * f * t) for f in (880, 1320, 1760)) * decay(d, 0.3)
    return finish(0.6 * s(zap) + 0.45 * s(whoosh) + 0.5 * s(thump) + 0.2 * s(shimmer))


def sound_chain_cast():
    d = 0.6
    t = times(d)
    buzz = band(signal.sawtooth(2 * math.pi * (110 + 15 * np.sin(2 * math.pi * 23 * t)) * t), 200, 4000)
    buzz *= decay(d, 0.22)
    zap = np.sin(sweep(2600, 600, d)) * decay(d, 0.05)
    return finish(0.45 * s(buzz) + 0.7 * s(crackle(d, 140) * decay(d, 0.25)) + 0.4 * s(zap))


def sound_chain_hit():
    d = 0.35
    t = times(d)
    burst = band(noise(d), low=2000) * decay(d, 0.04)
    zap = np.sin(sweep(3200, 900, d)) * decay(d, 0.06)
    thump = np.sin(2 * math.pi * 90 * t) * decay(d, 0.05)
    return finish(0.5 * s(burst) + 0.6 * s(crackle(d, 220) * decay(d, 0.12)) + 0.45 * s(zap) + 0.35 * s(thump))


def sound_blink():
    d = 0.55
    glide = np.sin(sweep(320, 1900, d)) * swell(d, 0.28, 0.06)
    air = band(noise(d), 300, 5000) * swell(d, 0.26, 0.05)
    twinkle = place(d, bell(3200, 0.25, [(1, 1, 0.08), (1.5, 0.5, 0.05)]), 0.27)
    return finish(0.55 * s(glide) + 0.5 * s(air) + 0.3 * s(twinkle))


def soft_bell(f0, duration, tau):
    return bell(f0, duration, [(1, 1, tau), (2, 0.45, tau * 0.6), (3, 0.2, tau * 0.4), (4.2, 0.1, tau * 0.3)], attack=0.03)


def sound_ward_raise():
    d = 1.3
    t = times(d)
    chord = sum(place(d, soft_bell(f, d - i * 0.06, 0.6), i * 0.06)
                for i, f in enumerate((440, 554.37, 659.25, 880)))
    rise = band(noise(d), 1000, 5000) * swell(d, 0.3, 0.3)
    low = np.sin(2 * math.pi * 110 * t) * swell(d, 0.25, 0.5)
    return finish(0.7 * s(chord) + 0.2 * s(rise) + 0.3 * s(low))


def sound_ward_hit():
    d = 0.45
    t = times(d)
    tink = bell(1240, d, [(1, 1, 0.25), (2.76, 0.6, 0.15), (5.40, 0.35, 0.08), (8.93, 0.2, 0.05)])
    thud = band(noise(d), high=300) * decay(d, 0.04) + np.sin(2 * math.pi * 120 * t) * decay(d, 0.06)
    return finish(0.75 * s(tink) + 0.45 * s(thud))


def sound_ward_break():
    d = 1.0
    t = times(d)
    out = 0.5 * s(band(noise(d), low=1500) * decay(d, 0.12))
    for _ in range(26):
        start = float(rng.exponential(0.12))
        if start > 0.6:
            continue
        f0 = rng.uniform(1500, 5200)
        shard = bell(f0, 0.3, [(1, 1, rng.uniform(0.04, 0.14)), (2.76, 0.4, 0.05)])
        out += place(d, shard, start) * rng.uniform(0.15, 0.35) * math.exp(-start / 0.3)
    out += 0.4 * np.sin(2 * math.pi * 80 * t) * decay(d, 0.09)
    return finish(out)


def sound_ward_fade():
    d = 0.9
    out = sum(place(d, soft_bell(f, d - i * 0.1, 0.35), i * 0.1) * (1 - i * 0.15)
              for i, f in enumerate((880, 739.99, 587.33, 440)))
    air = band(noise(d), high=2000) * swell(d, 0.05, 0.4)
    return finish(0.8 * s(out) + 0.15 * s(air), peak=0.7)


def sound_frost_nova():
    d = 1.1
    t = times(d)
    crack = band(noise(d), low=3000) * decay(d, 0.06)
    crystal = sum(np.sin(2 * math.pi * rng.uniform(2500, 7000) * t + rng.uniform(0, 6.3)) * decay(d, rng.uniform(0.25, 0.7))
                  for _ in range(12))
    whoomp = np.sin(sweep(170, 45, d)) * decay(d, 0.15)
    wind = band(noise(d), 800, 3000) * swell(d, 0.03, 0.5)
    return finish(0.5 * s(crack) + 0.35 * s(crystal) + 0.6 * s(whoomp) + 0.3 * s(wind))


def sound_astral_channel():
    """Every Astral Staff spell's channel: a choir swelling over two seconds, a rising shimmer, and bells as the circle
    closes."""
    d = 2.3
    t = times(d)
    voices = np.zeros_like(t)
    for f in (220, 277.18, 329.63, 440):
        for detune in (-0.004, 0.0, 0.005):
            voices += signal.sawtooth(2 * math.pi * f * (1 + detune) * t + rng.uniform(0, 2 * math.pi))
    # "Ah" vowel formants turn the saws into voices; a 5 Hz vibrato gives them breath.
    choir = band(voices, 600, 820) + 0.6 * band(voices, 1100, 1350) + 0.3 * band(voices, 2400, 2800)
    choir *= 0.85 + 0.15 * np.sin(2 * math.pi * 5 * t)
    swell_in = np.clip(t / (d - 0.35), 0, 1) ** 1.6 * np.clip((d - t) / 0.3, 0, 1)
    shimmer = band(noise(d), 5000, 9000) * np.clip(t / d, 0, 1) ** 2
    bells = place(d, bell(1760, 0.4, [(1, 1, 0.25), (2.0, 0.4, 0.15), (3.0, 0.2, 0.08)]), 1.9)
    bells += place(d, bell(2637, 0.35, [(1, 1, 0.2), (2.0, 0.3, 0.1)]), 1.98)
    return finish(0.75 * s(choir * swell_in) + 0.2 * s(shimmer) + 0.35 * s(bells))


def sound_judgment_gather():
    """Distant thunder rolling in under an ethereal chord, and a rush of air rising to the moment the beam falls."""
    d = 3.0
    t = times(d)
    rumble = band(noise(d), high=140) * swell(d, 2.3, 0.5)
    sub = np.sin(2 * math.pi * 38 * t) * swell(d, 2.4, 0.4)
    pad = sum(np.sin(2 * math.pi * f * t + rng.uniform(0, 6.3)) for f in (110, 164.81, 220, 277.18, 329.63))
    pad *= swell(d, 2.0, 0.8) * (0.8 + 0.2 * np.sin(2 * math.pi * 0.7 * t))
    riser = band(noise(d), 1500, 8000) * np.where(t < 2.5, (t / 2.5) ** 3, np.exp(-(t - 2.5) / 0.05))
    return finish(0.55 * s(rumble) + 0.5 * s(sub) + 0.35 * s(pad) + 0.4 * s(riser))


def sound_judgment_beam():
    """The beam slamming down and roaring: a heavy impact, a deep hum, a wall of noise and sizzling sparks."""
    d = 2.0
    t = times(d)
    envelope = np.minimum(t / 0.02, 1.0) * np.clip((d - t) / 0.45, 0, 1)
    impact = np.sin(sweep(130, 40, d)) * decay(d, 0.25)
    hum = (np.sin(2 * math.pi * 55 * t) + 0.5 * np.sin(2 * math.pi * 110 * t) + 0.3 * np.sin(2 * math.pi * 165.5 * t))
    hum *= 1 + 0.1 * np.sin(2 * math.pi * 6 * t)
    roar = band(noise(d), 150, 2500) * (0.7 + 0.3 * np.sin(2 * math.pi * 31 * t))
    sizzle = crackle(d, 300, low=3000)
    whine = np.sin(sweep(1800, 2400, d))
    return finish(0.6 * s(impact) + (0.45 * s(hum) + 0.55 * s(roar) + 0.3 * s(sizzle) + 0.08 * s(whine)) * envelope)


def sound_judgment_blast():
    """A cataclysm: a sharp crack, a sub-bass boom sweeping down, a long rolling rumble with falling debris, and the
    magic ringing out as it dissipates."""
    d = 5.0
    t = times(d)
    crack = place(d, band(noise(0.3), low=1500) * decay(0.3, 0.04), 0)
    boom = place(d, np.sin(sweep(90, 22, 1.4)) * decay(1.4, 0.5), 0)
    body = band(noise(d), high=900) * decay(d, 0.6, attack=0.005)
    tail = band(noise(d), high=150) * decay(d, 1.8, attack=0.05)
    debris = crackle(d, 60, low=800) * decay(d, 1.2)
    ring = sum(place(d, soft_bell(f, d - 0.3, 1.4), 0.3) for f in (2093, 2637, 3136))
    return finish(0.5 * s(crack) + 0.8 * s(boom) + 0.55 * s(body) + 0.6 * s(tail) + 0.25 * s(debris) + 0.12 * s(ring))


def sound_meteor_fall():
    """A meteor screaming down: a falling whistle over a building roar of burning air and crackling."""
    d = 1.6
    t = times(d)
    whistle = np.sin(sweep(2400, 500, d)) * swell(d, 1.2, 0.25)
    roar = band(noise(d), 200, 2500) * np.linspace(0.2, 1, len(t)) ** 2 * np.clip((d - t) / 0.2, 0, 1)
    sparks = crackle(d, 80, low=1200) * np.linspace(0, 1, len(t))
    return finish(0.35 * s(whistle) + 0.6 * s(roar) + 0.25 * s(sparks))


def sound_meteor_impact():
    """A meteor striking: a sharp crack, a deep boom, and rubble raining down."""
    d = 1.6
    crack = band(noise(d), low=1200) * decay(d, 0.05)
    boom = np.sin(sweep(110, 30, d)) * decay(d, 0.35)
    body = band(noise(d), high=700) * decay(d, 0.4, attack=0.004)
    rubble = crackle(d, 90, low=600) * decay(d, 0.5)
    return finish(0.45 * s(crack) + 0.8 * s(boom) + 0.5 * s(body) + 0.25 * s(rubble))


def sound_singularity_open():
    """Six seconds of black hole: a sub-bass throb sinking and quickening, wind rushing in, a whine climbing to the
    collapse."""
    d = 6.0
    t = times(d)
    rate = 1.5 + 4 * (t / d) ** 2
    throb = np.sin(sweep(55, 30, d)) * (0.6 + 0.4 * np.sin(2 * math.pi * np.cumsum(rate) / SR))
    wind = band(noise(d), 300, 1800) * (0.6 + 0.4 * np.sin(2 * math.pi * 0.7 * t))
    whine = np.sin(sweep(200, 900, d)) * (t / d) ** 2
    envelope = np.minimum(t / 0.4, 1.0) * np.clip((d - t) / 0.05, 0, 1)
    return finish((0.7 * s(throb) + 0.45 * s(wind) + 0.2 * s(whine)) * envelope)


def sound_singularity_collapse():
    """The collapse: air sucked in, then a deep boom and blast, the last of it ringing away."""
    d = 2.5
    suck = band(noise(0.3), 400, 4000) * (times(0.3) / 0.3) ** 3
    boom = np.sin(sweep(90, 25, 2.2)) * decay(2.2, 0.6)
    blast = band(noise(2.2), high=1200) * decay(2.2, 0.5, attack=0.003)
    ring = sum(soft_bell(f, 2.0, 0.8) for f in (1567.98, 2349.32))
    return finish(0.5 * s(place(d, suck, 0)) + 0.8 * s(place(d, boom, 0.25)) + 0.5 * s(place(d, blast, 0.25))
                  + 0.12 * s(place(d, ring, 0.3)))


def sound_chrono_start():
    """Time stopping: a rush sweeping down into silence, one deep tick, and a bell left hanging."""
    d = 1.8
    t = times(0.45)
    rush = band(noise(0.45), 500, 6000) * (1 - t / 0.45) ** 0.5 * np.minimum(t / 0.05, 1)
    fall = np.sin(sweep(1200, 80, 0.45)) * (1 - t / 0.45)
    tick = bell(800, 0.15, [(1, 1, 0.03), (2.7, 0.5, 0.02)]) + np.sin(2 * math.pi * 90 * times(0.15)) * decay(0.15, 0.03)
    chime = soft_bell(523.25, 1.3, 1.0) + 0.5 * soft_bell(783.99, 1.3, 0.8)
    return finish(0.5 * s(place(d, rush, 0)) + 0.4 * s(place(d, fall, 0)) + 0.5 * s(place(d, tick, 0.47))
                  + 0.45 * s(place(d, chime, 0.5)))


def sound_chrono_end():
    """Time resuming: a rush swelling up out of silence, and the world snapping back into motion."""
    d = 1.0
    t = times(0.6)
    rush = band(noise(0.6), 500, 6000) * (t / 0.6) ** 2
    rise = np.sin(sweep(80, 1200, 0.6)) * (t / 0.6)
    snap = bell(1200, 0.3, [(1, 1, 0.06), (2.4, 0.5, 0.04)]) + band(noise(0.3), low=2000) * decay(0.3, 0.02)
    return finish(0.55 * s(place(d, rush, 0)) + 0.35 * s(place(d, rise, 0)) + 0.5 * s(place(d, snap, 0.6)))


def sound_gate_open():
    """A gate opening: a shimmering chord gliding upwards, with a sparkle of air."""
    d = 1.6
    chord = sum(place(d, soft_bell(f, d - i * 0.05, 0.7), i * 0.05) for i, f in enumerate((392, 493.88, 587.33, 783.99)))
    glide = np.sin(sweep(300, 1200, d)) * swell(d, 0.5, 0.6)
    sparkle = band(noise(d), 5000, 10000) * swell(d, 0.3, 0.5)
    return finish(0.6 * s(chord) + 0.3 * s(glide) + 0.25 * s(sparkle))


def sound_gate_cross():
    """Stepping through a gate: a deep whoosh rising, and a bright chime as you arrive."""
    d = 0.9
    whoosh = band(noise(d), 150, 3000) * swell(d, 0.25, 0.2)
    rise = np.sin(sweep(150, 900, d)) * swell(d, 0.22, 0.15)
    chime = place(d, bell(1760, 0.6, [(1, 1, 0.2), (2, 0.4, 0.1), (3, 0.2, 0.06)]), 0.25)
    return finish(0.6 * s(whoosh) + 0.35 * s(rise) + 0.35 * s(chime))


def sound_fizzle():
    d = 0.45
    t = times(d)
    gate = np.clip(np.sin(2 * math.pi * 31 * t + 2.0 * np.sin(2 * math.pi * 7 * t)), 0, 1)
    sputter = band(noise(d), high=1400) * gate * decay(d, 0.15)
    blip = np.sin(sweep(600, 150, d)) * decay(d, 0.1)
    return finish(0.7 * s(sputter) + 0.4 * s(blip), peak=0.7)


def sound_wheel_select():
    d = 0.4
    chime = [(1, 1, 0.18), (2.0, 0.3, 0.1), (3.0, 0.12, 0.06)]
    out = place(d, bell(1318.5, 0.35, chime), 0) + place(d, bell(1975.5, 0.33, chime), 0.07)
    return finish(out, peak=0.6)


def sound_planetfall_rift():
    """The sky tearing open: a ripping crack, a dissonant cosmic choir swelling over a growl that sinks, a hiss of
    stars and the crackle of lightning across the rift."""
    d = 3.6
    t = times(d)
    rip_t = times(0.7)
    rip = place(d, band(noise(0.7), 700, 7000) * decay(0.7, 0.14) * (0.6 + 0.4 * np.sin(2 * math.pi * 90 * rip_t)), 0)
    growl = np.sin(sweep(72, 30, d)) * (0.7 + 0.3 * np.sin(2 * math.pi * 3 * t)) * swell(d, 0.7, 2.6)
    voices = np.zeros_like(t)
    for f in (196.0, 207.65, 293.66, 311.13, 415.3):
        for detune in (-0.005, 0.0, 0.006):
            voices += signal.sawtooth(2 * math.pi * f * (1 + detune) * t + rng.uniform(0, 2 * math.pi))
    choir = band(voices, 500, 760) + 0.6 * band(voices, 1000, 1300) + 0.3 * band(voices, 2300, 2700)
    choir *= swell(d, 1.9, 1.4) * (0.85 + 0.15 * np.sin(2 * math.pi * 4.5 * t))
    shimmer = band(noise(d), 6000, 11000) * swell(d, 2.0, 1.0)
    sparks = crackle(d, 45, low=2000) * swell(d, 1.5, 1.5)
    return finish(0.45 * s(rip) + 0.6 * s(growl) + 0.5 * s(choir) + 0.2 * s(shimmer) + 0.25 * s(sparks))


def sound_planetfall_descent():
    """A world falling: a roar of burning air building to the impact, a deep whistle falling in pitch as it nears, a
    sub-bass swelling under it all and the crackle of fire."""
    d = 4.2
    t = times(d)
    build = (t / d) ** 2
    roar = band(noise(d), 120, 2200) * (0.75 + 0.25 * np.sin(2 * math.pi * 23 * t)) * build
    whistle = np.sin(sweep(900, 240, d)) * (t / d) ** 1.5
    sub = np.sin(sweep(46, 27, d)) * build
    fire = crackle(d, 220, low=1200) * (t / d) ** 1.5
    return finish(0.7 * s(roar) + 0.25 * s(whistle) + 0.6 * s(sub) + 0.3 * s(fire))


def sound_planetfall_impact():
    """The end of a world: a crack like the sky splitting, a boom sweeping down to nothing, a second as the ground gives
    way, a long rolling rumble full of falling debris, and a ringing celestial chord as the dust settles."""
    d = 9.0
    crack = place(d, band(noise(0.4), low=1200) * decay(0.4, 0.06), 0)
    boom = place(d, np.sin(sweep(80, 16, 2.4)) * decay(2.4, 0.9), 0)
    second = place(d, np.sin(sweep(60, 18, 1.9)) * decay(1.9, 0.7), 0.9)
    body = band(noise(d), high=1000) * decay(d, 1.0, attack=0.005)
    tail = band(noise(d), high=120) * decay(d, 3.0, attack=0.1)
    debris = crackle(d, 80, low=700) * decay(d, 2.5)
    ring = sum(place(d, soft_bell(f, d - 1.2, 2.5), 1.0) for f in (1046.5, 1567.98, 2093.0, 2637.0))
    return finish(0.45 * s(crack) + 0.8 * s(boom) + 0.55 * s(second) + 0.5 * s(body) + 0.6 * s(tail) + 0.25 * s(debris)
                  + 0.1 * s(ring))


def sound_planetfall_ringing():
    """The ringing in your ears after a blast too close: a thin, high tone beating slowly against itself, over a
    hiss, fading away as hearing returns."""
    d = 4.5
    t = times(d)
    tone = np.sin(2 * math.pi * 3720 * t) + 0.7 * np.sin(2 * math.pi * 3727 * t) + 0.25 * np.sin(2 * math.pi * 7440 * t)
    hiss = band(noise(d), 5000, 9000)
    return finish(0.7 * s(tone * decay(d, 1.6, attack=0.25)) + 0.2 * s(hiss * decay(d, 0.9, attack=0.1)), peak=0.35)


def sound_planetfall_shard():
    """A burning shard tearing down out of the sky: a shriek falling in pitch, the roar of its fire building as it
    nears, and the crackle of the burning rock."""
    d = 1.4
    t = times(d)
    build = (t / d) ** 1.6
    shriek = np.sin(sweep(1500, 360, d)) * build
    roar = band(noise(d), 180, 2600) * (t / d) ** 2.2
    fire = crackle(d, 170, low=1400) * build
    return finish(0.45 * s(shriek) + 0.65 * s(roar) + 0.3 * s(fire))


def sound_astral_backlash():
    """Corruption lashing back: a breath drawn in backwards, then a sick lurch down into a growl, with a dissonant
    cluster ringing over it and a crackle of something tearing."""
    d = 1.4
    t = times(d)
    inhale = band(noise(d), 600, 4000) * place(d, swell(0.35, 0.33, 0.02), 0)
    dive = np.sin(sweep(520, 55, d)) * place(d, decay(d - 0.3, 0.45), 0.3)
    growl_phase = 2 * math.pi * 62 * t + 3.0 * np.sin(2 * math.pi * 7 * t)
    growl = band(signal.sawtooth(growl_phase), high=900) * place(d, decay(d - 0.3, 0.5, attack=0.02), 0.3)
    cluster = sum(place(d, bell(f, d - 0.3, [(1, 1, 0.5), (2.01, 0.4, 0.3)]), 0.3) for f in (311.1, 329.6, 349.2))
    tear = crackle(d, 160, low=1200) * place(d, decay(d - 0.3, 0.2), 0.3)
    return finish(0.45 * s(inhale) + 0.55 * s(dive) + 0.5 * s(growl) + 0.3 * s(cluster) + 0.35 * s(tear))


def sound_shatter():
    """A frozen creature's ice breaking: a sharp crack, then a spray of glassy shards."""
    d = 0.7
    t = times(d)
    out = 0.6 * s(band(noise(d), low=2500) * decay(d, 0.03))
    for _ in range(22):
        start = float(rng.exponential(0.06))
        if start > 0.35:
            continue
        shard = bell(rng.uniform(2800, 7500), 0.2, [(1, 1, rng.uniform(0.02, 0.08)), (2.4, 0.4, 0.03)])
        out += place(d, shard, start) * rng.uniform(0.15, 0.35) * math.exp(-start / 0.15)
    out += 0.3 * np.sin(sweep(260, 90, d)) * decay(d, 0.05)
    return finish(out)


def sound_conduction():
    """Lightning surging through water: a buzz that swells and climbs, thick with crackle."""
    d = 0.55
    t = times(d)
    buzz = band(signal.sawtooth(sweep(90, 180, d)) * (0.6 + 0.4 * np.sign(np.sin(2 * math.pi * 50 * t))), 150, 5000)
    buzz *= swell(d, 0.08, 0.2)
    fizz = band(noise(d), 4000, 9000) * decay(d, 0.15)
    return finish(0.5 * s(buzz) + 0.6 * s(crackle(d, 320, low=1800) * decay(d, 0.2)) + 0.25 * s(fizz))


SOUND_MAKERS = {
    "spell/arcane_bolt_charge": sound_bolt_charge,
    "spell/arcane_bolt_cast": sound_bolt_cast,
    "spell/chain_lightning_cast": sound_chain_cast,
    "spell/chain_lightning_hit": sound_chain_hit,
    "spell/blink": sound_blink,
    "spell/arcane_ward_raise": sound_ward_raise,
    "spell/arcane_ward_hit": sound_ward_hit,
    "spell/arcane_ward_break": sound_ward_break,
    "spell/arcane_ward_fade": sound_ward_fade,
    "spell/frost_nova": sound_frost_nova,
    "spell/fizzle": sound_fizzle,
    "ui/spell_wheel_select": sound_wheel_select,
    # Added later: new sounds go last, so the random stream each earlier sound draws from never changes.
    "spell/astral_channel": sound_astral_channel,
    "spell/celestial_judgment_gather": sound_judgment_gather,
    "spell/celestial_judgment_beam": sound_judgment_beam,
    "spell/celestial_judgment_blast": sound_judgment_blast,
    "spell/meteor_swarm_fall": sound_meteor_fall,
    "spell/meteor_swarm_impact": sound_meteor_impact,
    "spell/singularity_open": sound_singularity_open,
    "spell/singularity_collapse": sound_singularity_collapse,
    "spell/chrono_field_start": sound_chrono_start,
    "spell/chrono_field_end": sound_chrono_end,
    "spell/astral_gate_open": sound_gate_open,
    "spell/astral_gate_cross": sound_gate_cross,
    "spell/planetfall_rift": sound_planetfall_rift,
    "spell/planetfall_descent": sound_planetfall_descent,
    "spell/planetfall_impact": sound_planetfall_impact,
    "spell/planetfall_ringing": sound_planetfall_ringing,
    "spell/planetfall_shard": sound_planetfall_shard,
    "spell/astral_backlash": sound_astral_backlash,
    "spell/shatter": sound_shatter,
    "spell/conduction": sound_conduction,
}


def main():
    import soundfile

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write upscaled icons and spectrograms here")
    args = parser.parse_args()

    ICONS.mkdir(parents=True, exist_ok=True)
    icons = {name: make().image() for name, make in ICON_MAKERS.items()}
    for name, img in icons.items():
        img.save(ICONS / f"{name}.png")
    for name, make in TEXTURE_MAKERS.items():
        path = TEXTURES / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        make().save(path)

    clips = {}
    for name, make in SOUND_MAKERS.items():
        path = SOUNDS / f"{name}.ogg"
        path.parent.mkdir(parents=True, exist_ok=True)
        clips[name] = make().astype(np.float32)
        soundfile.write(path, clips[name], SR, format="OGG", subtype="VORBIS")
    print(f"wrote {len(icons)} icons, {len(TEXTURE_MAKERS)} textures and {len(clips)} sounds under {ASSETS}")

    if args.preview:
        write_previews(args.preview, icons, clips)


def write_previews(out, icons, clips):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    out.mkdir(parents=True, exist_ok=True)
    scale, pad = 12, 16
    sheet = Image.new("RGBA", (len(icons) * (16 * scale + pad) + pad, 2 * (16 * scale + pad) + pad), (0, 0, 0, 255))
    for row, background in enumerate(((139, 139, 139, 255), (20, 10, 34, 255))):  # hotbar grey, wheel slot
        for i, img in enumerate(icons.values()):
            x, y = pad + i * (16 * scale + pad), pad + row * (16 * scale + pad)
            tile = Image.new("RGBA", (16, 16), background)
            tile.alpha_composite(img)
            sheet.paste(tile.resize((16 * scale, 16 * scale), Image.NEAREST), (x, y))
    sheet.save(out / "icons.png")

    fig, axes = plt.subplots(len(clips), 2, figsize=(12, 2.1 * len(clips)), gridspec_kw={"width_ratios": [1, 2]})
    for (name, clip), (wave_ax, spec_ax) in zip(clips.items(), axes):
        wave_ax.plot(np.arange(len(clip)) / SR, clip, linewidth=0.4)
        wave_ax.set_ylim(-1, 1)
        wave_ax.set_title(f"{name}  peak {np.max(np.abs(clip)):.2f}  rms {np.sqrt(np.mean(clip ** 2)):.3f}", fontsize=8)
        spec_ax.specgram(clip, NFFT=1024, Fs=SR, noverlap=768, cmap="magma", vmin=-120)
        spec_ax.set_ylim(0, 12000)
    fig.tight_layout()
    fig.savefig(out / "sounds.png", dpi=80)
    print(f"previews in {out}")


if __name__ == "__main__":
    main()
