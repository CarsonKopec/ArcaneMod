"""Generates the spell icons and spell sounds into src/main/resources.

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
SOUNDS = ASSETS / "sounds"
SR = 44100


# Icons: 16x16 pixel art, drawn as tapered strokes and discs with tiered colours, then outlined.

def hex_rgba(color, alpha=255):
    color = color.lstrip("#")
    return (int(color[0:2], 16), int(color[2:4], 16), int(color[4:6], 16), alpha)


class Canvas:
    def __init__(self):
        self.px = [[None] * 16 for _ in range(16)]

    def put(self, x, y, color):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = color

    def stroke(self, points, width_start, width_end, tiers, alpha=255, alpha_end=None):
        """A polyline whose half-width (and optionally opacity) tapers from start to end.
        Tiers: (fraction of width, colour), inner first."""
        alpha_end = alpha if alpha_end is None else alpha_end
        lengths = [math.dist(a, b) for a, b in zip(points, points[1:])]
        total = sum(lengths)
        for y in range(16):
            for x in range(16):
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
        for y in range(16):
            for x in range(16):
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
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    continue
                neighbours = [self.px[ny][nx] for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                              if 0 <= nx < 16 and 0 <= ny < 16 and self.px[ny][nx] is not None]
                if any(n[3] >= 200 for n in neighbours):
                    add.append((x, y, hex_rgba(color)))
        for x, y, c in add:
            self.px[y][x] = c

    def image(self):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
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


ICON_MAKERS = {
    "arcane_bolt": icon_arcane_bolt,
    "chain_lightning": icon_chain_lightning,
    "blink": icon_blink,
    "arcane_ward": icon_arcane_ward,
    "frost_nova": icon_frost_nova,
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

    clips = {}
    for name, make in SOUND_MAKERS.items():
        path = SOUNDS / f"{name}.ogg"
        path.parent.mkdir(parents=True, exist_ok=True)
        clips[name] = make().astype(np.float32)
        soundfile.write(path, clips[name], SR, format="OGG", subtype="VORBIS")
    print(f"wrote {len(icons)} icons to {ICONS} and {len(clips)} sounds to {SOUNDS}")

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
