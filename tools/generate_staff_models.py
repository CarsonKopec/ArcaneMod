"""Generate the staffs' editable GeckoLib geometry/animation JSON; existing textures are read, never changed."""
import json
import math
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/arcane_spells"


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def palette(name):
    image = Image.open(ASSETS / f"textures/item/{name}.png").convert("RGBA")
    pixels = {}
    for y in range(image.height):
        for x in range(image.width):
            rgba = image.getpixel((x, y))
            if rgba[3] == 255:
                pixels.setdefault(rgba[:3], [x + 0.125, y + 0.125])
    return pixels


def model(name, astral):
    colors = palette(name)
    if astral:
        material = {"shaft": (30, 18, 54), "edge": (62, 91, 184), "gold": (201, 154, 46),
                    "light": (255, 224, 138), "crystal": (189, 243, 255), "facet": (255, 255, 255)}
    else:
        material = {"shaft": (52, 32, 68), "edge": (145, 101, 174), "gold": (222, 181, 108),
                    "light": (233, 212, 255), "crystal": (160, 91, 232), "facet": (113, 236, 255)}

    def cube(origin, size, color="shaft", rotation=None, pivot=None):
        uv = colors[material[color]]
        faces = {face: {"uv": uv, "uv_size": [0.75, 0.75]}
                 for face in ("north", "south", "east", "west", "up", "down")}
        value = {"origin": origin, "size": size, "uv": faces}
        if rotation:
            value["rotation"] = rotation
            value["pivot"] = pivot or [origin[i] + size[i] / 2 for i in range(3)]
        return value

    shaft = [cube([-0.8, -20, -0.8], [1.6, 35, 1.6]),
             cube([-0.25, -18, -0.87], [0.5, 32, 0.14], "edge"),
             cube([-0.25, -18, 0.73], [0.5, 32, 0.14], "edge"),
             cube([-1.1, -5, -1.1], [2.2, 8, 2.2]),
             cube([-0.8, -22, -0.8], [1.6, 2, 1.6], "gold"),
             cube([-1.2, 13, -1.2], [2.4, 2, 2.4], "gold"),
             cube([-1.8, 15, -1.8], [3.6, 1, 3.6], "light")]
    for y in (-18, -6, -3, 0, 3, 10):
        shaft.append(cube([-1.25, y, -1.25], [2.5, 0.5, 2.5], "gold"))
    bones = [{"name": "root", "pivot": [0, 0, 0], "cubes": shaft}]
    crown = []
    for angle in (45, 135, 225, 315):
        crown.append(cube([-0.5, 16, 1.5], [1, 5.5, 1], "gold", [0, angle, -12], [0, 16, 0]))
        crown.append(cube([-0.6, 20.5, 2.3], [1.2, 1.2, 1.2], "light", [0, angle, 0], [0, 20.5, 0]))
    bones.append({"name": "crown", "parent": "root", "pivot": [0, 19, 0], "cubes": crown})
    crystal = [cube([-1.6, 19.5, -1.6], [3.2, 4.5, 3.2], "crystal", [0, 45, 0]),
               cube([-1.2, 23, -1.2], [2.4, 2.4, 2.4], "facet", [0, 45, 45]),
               cube([-1, 18, -1], [2, 2, 2], "light", [0, 45, 45]),
               cube([-0.55, 20.2, -1.92], [1.1, 2.7, 0.18], "facet")]
    bones.append({"name": "crystal", "parent": "root", "pivot": [0, 22, 0], "cubes": crystal})
    orbit = []
    for angle in range(0, 360, 90):
        orbit.append(cube([3.8, 21.5, -0.5], [1, 1, 1], "facet", [0, angle, 45], [0, 22, 0]))
    bones.append({"name": "orbit", "parent": "root", "pivot": [0, 22, 0], "cubes": orbit})
    if astral:
        for ring, radius, tilt in (("halo", 5.2, 25), ("inner_halo", 4.2, -45)):
            parts = []
            for step in range(16):
                angle = 2 * math.pi * step / 16
                x, y = math.sin(angle) * radius, 22 + math.cos(angle) * radius
                parts.append(cube([x - 0.25, y - 1.05, -0.25], [0.5, 2.1, 0.5],
                                  "light" if step % 4 == 0 else "gold", [0, 0, -math.degrees(angle) - 90], [x, y, 0]))
            bones.append({"name": ring, "parent": "root", "pivot": [0, 22, 0], "rotation": [tilt, 0, 0], "cubes": parts})
    else:
        # Empty halo bones let both staffs share the same animation file.
        bones.extend({"name": ring, "parent": "root", "pivot": [0, 22, 0]} for ring in ("halo", "inner_halo"))
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": 32, "texture_height": 32,
                        "visible_bounds_width": 3, "visible_bounds_height": 4, "visible_bounds_offset": [0, 0.25, 0]},
        "bones": bones}]}


def sample_track(length, evaluate, steps=32):
    """Bake curves to ordinary Gecko keyframes so the assets need no Molang state."""
    return {f"{length * step / steps:.5f}".rstrip("0").rstrip(".") or "0":
            [round(value, 5) for value in evaluate(length * step / steps)]
            for step in range(steps + 1)}


def smooth(value):
    value = max(0, min(1, value))
    return value * value * (3 - 2 * value)


def planetfall_animations():
    """The crown unfolds into an astrolabe before a compact, physical discharge.

    Channel is a one-shot three-second wind-up. Its terminal transforms exactly
    match the hold loop's first frame; full revolutions keep the loop seamless.
    Root movement stays small enough to preserve the first-person aiming line.
    Empty halo bones on the Arcane staff retain the crystal/crown/orbit fallback.
    """
    channel_length, hold_length, release_length = 3, 2, 1.1
    channel = {
        "root": {
            "position": sample_track(3, lambda t: [0, -0.55 * smooth(t / 3), 0.3 * smooth(t / 3)]),
            "rotation": sample_track(3, lambda t: [-4 * smooth(t / 3), 0, -2 * smooth(t / 3)]),
        },
        "crown": {
            "position": sample_track(3, lambda t: [0, -0.45 * smooth(t / 1.1), 0]),
            "rotation": sample_track(3, lambda t: [0, -7.5 * t * t, 0]),
            "scale": sample_track(3, lambda t: [1 + 0.55 * smooth(t / 1.1),
                                                1 - 0.15 * smooth(t / 1.1),
                                                1 + 0.55 * smooth(t / 1.1)]),
        },
        "crystal": {
            "position": sample_track(3, lambda t: [0, 3.8 * smooth(t / 2.5), 0]),
            "rotation": sample_track(3, lambda t: [0, 60 * t * t, 0]),
            "scale": sample_track(3, lambda t: [1 + 0.3 * smooth(t / 3),
                                                1 + 0.35 * smooth(t / 3),
                                                1 + 0.3 * smooth(t / 3)]),
        },
        "orbit": {
            "position": sample_track(3, lambda t: [0, 3.6 * smooth(t / 2.5), 0]),
            "rotation": sample_track(3, lambda t: [25 * smooth(t / 2), -60 * t * t,
                                                   35 * smooth(t / 2)]),
            "scale": sample_track(3, lambda t: [1 + 1.1 * smooth(t / 2)] * 3),
        },
        "halo": {
            "position": sample_track(3, lambda t: [0, 3.2 * smooth(t / 2), 0]),
            "rotation": sample_track(3, lambda t: [50 * smooth(t / 1.6), 30 * t * t,
                                                   20 * smooth(t / 2)]),
            "scale": sample_track(3, lambda t: [1 + 0.55 * smooth(t / 2)] * 3),
        },
        "inner_halo": {
            "position": sample_track(3, lambda t: [0, 4.2 * smooth(t / 2), 0]),
            "rotation": sample_track(3, lambda t: [-30 * smooth(t / 1.6), -30 * t * t,
                                                   -25 * smooth(t / 2)]),
            "scale": sample_track(3, lambda t: [1 + 0.48 * smooth(t / 2)] * 3),
        },
    }

    # Cosine breathing has zero velocity at both seams. Rotations retain their
    # wind-up angular velocity: crystal/orbit 360 deg/s, rings 180 deg/s.
    breathe = lambda t: (1 - math.cos(math.tau * t / hold_length)) / 2
    hold = {
        "root": {"position": [0, -0.55, 0.3], "rotation": [-4, 0, -2]},
        "crown": {"position": [0, -0.45, 0],
                  "rotation": {"0": [0, -67.5, 0], "2": [0, -157.5, 0]},
                  "scale": sample_track(2, lambda t: [1.55 + 0.07 * breathe(t), 0.85,
                                                      1.55 + 0.07 * breathe(t)])},
        "crystal": {"position": sample_track(2, lambda t: [0, 3.8 + 0.3 * breathe(t), 0]),
                    "rotation": {"0": [0, 540, 0], "2": [0, 1260, 0]},
                    "scale": sample_track(2, lambda t: [1.3 + 0.12 * breathe(t),
                                                        1.35 + 0.12 * breathe(t),
                                                        1.3 + 0.12 * breathe(t)])},
        "orbit": {"position": sample_track(2, lambda t: [0, 3.6 + 0.2 * breathe(t), 0]),
                  "rotation": sample_track(2, lambda t: [25 + 8 * breathe(t), -540 - 360 * t,
                                                         35 - 10 * breathe(t)]),
                  "scale": sample_track(2, lambda t: [2.1 + 0.08 * breathe(t)] * 3)},
        "halo": {"position": sample_track(2, lambda t: [0, 3.2 - 0.15 * breathe(t), 0]),
                 "rotation": sample_track(2, lambda t: [50 + 10 * breathe(t), 270 + 180 * t,
                                                        20 + 6 * breathe(t)]),
                 "scale": sample_track(2, lambda t: [1.55 + 0.06 * breathe(t)] * 3)},
        "inner_halo": {"position": sample_track(2, lambda t: [0, 4.2 + 0.2 * breathe(t), 0]),
                       "rotation": sample_track(2, lambda t: [-30 - 10 * breathe(t), -270 - 180 * t,
                                                              -25 - 6 * breathe(t)]),
                       "scale": sample_track(2, lambda t: [1.48 + 0.06 * breathe(t)] * 3)},
    }

    def discharge(values):
        # Brief compression, strong expansion, then a slower return to the rest
        # pose. Smooth every phase to avoid a one-frame snap at the impact peak.
        times = (0, 0.12, 0.28, 0.62, release_length)

        def evaluate(time):
            for index in range(1, len(times)):
                if time <= times[index]:
                    weight = smooth((time - times[index - 1]) / (times[index] - times[index - 1]))
                    return [start + (end - start) * weight
                            for start, end in zip(values[index - 1], values[index])]
            return values[-1]

        # Preserve the exact authored phase boundaries as well as sampled ease.
        track = sample_track(release_length, evaluate, 44)
        track.update({str(time): values[index] for index, time in enumerate(times)})
        return dict(sorted(track.items(), key=lambda frame: float(frame[0])))

    release = {
        "root": {
            "position": discharge(([0, -0.55, 0.3], [0, -0.85, 0.55], [0, 0.1, -0.35],
                                   [0, -0.12, 0.08], [0, 0, 0])),
            "rotation": discharge(([-4, 0, -2], [-7, 0, -2], [5, 0, 1], [-1.5, 0, 0], [0, 0, 0])),
        },
        "crown": {
            "position": discharge(([0, -0.45, 0], [0, -0.2, 0], [0, -0.9, 0], [0, -0.25, 0], [0, 0, 0])),
            "rotation": discharge(([0, -67.5, 0], [0, -80, 0], [0, -130, 0], [0, -180, 0], [0, -270, 0])),
            "scale": discharge(([1.55, 0.85, 1.55], [1.12, 0.95, 1.12], [1.8, 0.78, 1.8],
                                [1.24, 0.94, 1.24], [1, 1, 1])),
        },
        "crystal": {
            "position": discharge(([0, 3.8, 0], [0, 2.3, 0], [0, 5.2, 0], [0, 1.2, 0], [0, 0, 0])),
            "rotation": discharge(([0, 540, 0], [0, 555, 0], [0, 645, 0], [0, 700, 0], [0, 720, 0])),
            "scale": discharge(([1.3, 1.35, 1.3], [1.03, 1.2, 1.03], [1.7, 1.9, 1.7],
                                [1.16, 1.2, 1.16], [1, 1, 1])),
        },
        "orbit": {
            "position": discharge(([0, 3.6, 0], [0, 2.3, 0], [0, 5, 0], [0, 1.2, 0], [0, 0, 0])),
            "rotation": discharge(([25, -540, 35], [20, -565, 20], [5, -680, -15],
                                   [-8, -720, -8], [0, -720, 0])),
            "scale": discharge(([2.1] * 3, [1.18] * 3, [2.4] * 3, [1.28] * 3, [1] * 3)),
        },
        "halo": {
            "position": discharge(([0, 3.2, 0], [0, 2.3, 0], [0, 4.8, 0], [0, 1.2, 0], [0, 0, 0])),
            "rotation": discharge(([50, 270, 20], [65, 282, 5], [95, 325, -20],
                                   [30, 355, -5], [0, 360, 0])),
            "scale": discharge(([1.55] * 3, [1.05] * 3, [1.95] * 3, [1.24] * 3, [1] * 3)),
        },
        "inner_halo": {
            "position": discharge(([0, 4.2, 0], [0, 2.3, 0], [0, 5.5, 0], [0, 1.2, 0], [0, 0, 0])),
            "rotation": discharge(([-30, -270, -25], [-15, -282, -5], [-75, -325, 20],
                                   [-20, -355, 5], [0, -360, 0])),
            "scale": discharge(([1.48] * 3, [1] * 3, [1.85] * 3, [1.2] * 3, [1] * 3)),
        },
    }
    return {
        "animation.staff.planetfall.channel": {"loop": False, "animation_length": channel_length, "bones": channel},
        "animation.staff.planetfall.hold": {"loop": True, "animation_length": hold_length, "bones": hold},
        "animation.staff.planetfall.release": {"loop": False, "animation_length": release_length, "bones": release},
    }


def animations():
    idle = {
        "crystal": {"position": {"0": [0, 0, 0], "1.5": [0, 0.7, 0], "3": [0, 0, 0]},
                    "rotation": {"0": [0, 0, 0], "3": [0, 360, 0]}},
        "crown": {"rotation": {"0": [0, 0, 0], "3": [0, -90, 0]}},
        "orbit": {"rotation": {"0": [0, 0, 0], "3": [0, -360, 0]},
                  "position": {"0": [0, 0, 0], "1.5": [0, -0.5, 0], "3": [0, 0, 0]}},
        "halo": {"rotation": {"0": [0, 0, 0], "3": [0, 360, 0]}},
        "inner_halo": {"rotation": {"0": [0, 0, 0], "3": [0, -360, 0]}},
    }
    channel = {
        "crystal": {"position": {"0": [0, 1.2, 0], "0.5": [0, 2, 0], "1": [0, 1.2, 0]},
                    "rotation": {"0": [0, 0, 0], "1": [0, 360, 0]},
                    "scale": {"0": [1.12, 1.12, 1.12], "0.5": [1.25, 1.25, 1.25], "1": [1.12, 1.12, 1.12]}},
        "crown": {"rotation": {"0": [0, 0, 0], "1": [0, -90, 0]}, "scale": [1.25, 1, 1.25]},
        "orbit": {"rotation": {"0": [0, 0, 0], "1": [0, -360, 0]}, "scale": [1.4, 1.4, 1.4]},
        "halo": {"rotation": {"0": [0, 0, 0], "1": [0, 360, 0]}, "scale": [1.15, 1.15, 1.15]},
        "inner_halo": {"rotation": {"0": [0, 0, 0], "1": [0, -360, 0]}, "scale": [1.15, 1.15, 1.15]},
    }
    release = {
        "root": {"rotation": {"0": [0, 0, 0], "0.08": [-7, 0, 0], "0.22": [3, 0, 0], "0.5": [0, 0, 0]}},
        "crystal": {"position": {"0": [0, 1.5, 0], "0.1": [0, 3, 0], "0.5": [0, 0, 0]},
                    "scale": {"0": [1, 1, 1], "0.1": [1.65, 1.65, 1.65], "0.5": [1, 1, 1]},
                    "rotation": {"0": [0, 0, 0], "0.5": [0, 180, 0]}},
        "crown": {"scale": {"0": [1, 1, 1], "0.1": [1.6, 1.1, 1.6], "0.5": [1, 1, 1]}},
    }
    for name in ("orbit", "halo", "inner_halo"):
        release[name] = {"scale": {"0": [1, 1, 1], "0.1": [1.5, 1.5, 1.5], "0.5": [1, 1, 1]},
                         "rotation": {"0": [0, 0, 0], "0.5": [0, 180 if name == "halo" else -180, 0]}}
    return {"format_version": "1.8.0", "animations": {
        "animation.staff.idle": {"loop": True, "animation_length": 3, "bones": idle},
        "animation.staff.channel": {"loop": True, "animation_length": 1, "bones": channel},
        "animation.staff.release": {"loop": False, "animation_length": 0.5, "bones": release},
        **planetfall_animations(),
    }}


def main():
    display = {
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "firstperson_righthand": {"rotation": [0, -15, -12], "translation": [2, -3, -5], "scale": [0.55, 0.55, 0.55]},
        "firstperson_lefthand": {"rotation": [0, 15, 12], "translation": [2, -3, -5], "scale": [0.55, 0.55, 0.55]},
        "gui": {"rotation": [8, -25, -35], "translation": [0, -1, 0], "scale": [0.3, 0.3, 0.3]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 5, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, -35], "translation": [0, -1, 0], "scale": [0.3, 0.3, 0.3]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }
    for name in ("arcane_staff", "astral_staff"):
        save(ASSETS / f"geckolib/models/item/{name}.geo.json", model(name, name == "astral_staff"))
        save(ASSETS / f"geckolib/animations/item/{name}.animation.json", animations())
        save(ASSETS / f"models/item/{name}.json", {"gui_light": "front", "textures": {"particle": f"arcane_spells:item/{name}"}, "display": display})
        save(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:special", "base": f"arcane_spells:item/{name}", "model": {"type": "geckolib:geckolib"}}})


if __name__ == "__main__":
    main()
