#!/usr/bin/env python3
"""
KMK: generates the desktop app icons from the Android launcher icon's vector drawables
(app/src/main/res/drawable/ic_launcher_{background,foreground}.xml, the adaptive icon layers).

Offline, Pillow + numpy only (no SVG renderer needed): the drawables only use filled paths with
M/m, C/c, h, v, z commands, which are flattened to polygons and filled with the non-zero winding rule
(Android's default fill type) at 4x supersampling. The adaptive icon's 72dp safe zone (the part every
launcher shows) is cropped and given rounded corners.

Outputs (committed, referenced by desktopApp/build.gradle.kts):
  komikku.png  (512 px, Linux)   komikku.ico (16-256 px, Windows)   komikku.icns (16-1024 px, macOS)

Run from the repository root:  python3 desktopApp/icons/generate_icons.py
"""
import os
import re
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image, ImageDraw

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = os.path.dirname(os.path.abspath(__file__))
DRAWABLES = os.path.join(ROOT, "..", "..", "app", "src", "main", "res", "drawable")
SIZE = 1024
SUPERSAMPLE = 4
VIEWPORT = 432.0
# Adaptive icons are 108dp; launchers show the inner 72dp.
SAFE_MIN, SAFE_MAX = VIEWPORT * 18 / 108, VIEWPORT * 90 / 108


def tokenize(d):
    return re.findall(r"[A-Za-z]|-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?", d)


def subpaths(d, steps=24):
    """Flattens path data into closed polygons (lists of (x, y))."""
    tokens = tokenize(d)
    i, cmd = 0, None
    x = y = sx = sy = 0.0
    current, result = [], []

    def num():
        nonlocal i
        value = float(tokens[i])
        i += 1
        return value

    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd in "zZ":
                if current:
                    result.append(current)
                current = []
                x, y = sx, sy
                continue
        if cmd in "Mm":
            nx, ny = num(), num()
            if cmd == "m":
                nx, ny = x + nx, y + ny
            if current:
                result.append(current)
            x, y, sx, sy = nx, ny, nx, ny
            current = [(x, y)]
            cmd = "l" if cmd == "m" else "L"  # implicit line-to after move-to
        elif cmd in "Ll":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if cmd == "l" else (nx, ny)
            current.append((x, y))
        elif cmd in "Hh":
            nx = num()
            x = x + nx if cmd == "h" else nx
            current.append((x, y))
        elif cmd in "Vv":
            ny = num()
            y = y + ny if cmd == "v" else ny
            current.append((x, y))
        elif cmd in "Cc":
            pts = [num() for _ in range(6)]
            if cmd == "c":
                pts = [pts[0] + x, pts[1] + y, pts[2] + x, pts[3] + y, pts[4] + x, pts[5] + y]
            x1, y1, x2, y2, x3, y3 = pts
            for s in range(1, steps + 1):
                t = s / steps
                mt = 1 - t
                current.append((
                    mt ** 3 * x + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t ** 3 * x3,
                    mt ** 3 * y + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t ** 3 * y3,
                ))
            x, y = x3, y3
        else:
            raise ValueError(f"unsupported path command {cmd}")
    if current:
        result.append(current)
    return result


def fill(canvas, d, color, size):
    """Non-zero winding fill of path data [d] onto the RGBA float [canvas]."""
    scale = size / VIEWPORT
    winding = np.zeros((size, size), dtype=np.int32)
    for poly in subpaths(d):
        if len(poly) < 3:
            continue
        pts = [(px * scale, py * scale) for px, py in poly]
        area = sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon(pts, fill=1)
        winding += np.asarray(mask, dtype=np.int32) * (1 if area > 0 else -1)
    covered = winding != 0
    rgb = [int(color[i:i + 2], 16) / 255.0 for i in (1, 3, 5)] if len(color) == 7 else \
        [int(color[i:i + 2], 16) / 255.0 for i in (3, 5, 7)]
    for c in range(3):
        canvas[..., c][covered] = rgb[c]
    canvas[..., 3][covered] = 1.0


def render(size):
    canvas = np.zeros((size, size, 4), dtype=np.float64)
    for layer in ("ic_launcher_background.xml", "ic_launcher_foreground.xml"):
        tree = ET.parse(os.path.join(DRAWABLES, layer))
        for path in tree.iter("path"):
            d = path.get(ANDROID + "pathData")
            color = path.get(ANDROID + "fillColor")
            if d and color:
                fill(canvas, d, color, size)
    return Image.fromarray((canvas * 255).round().astype(np.uint8), "RGBA")


def main():
    big = SIZE * SUPERSAMPLE * VIEWPORT / (SAFE_MAX - SAFE_MIN)
    full = render(int(round(big)))
    k = full.width / VIEWPORT
    safe = full.crop((round(SAFE_MIN * k), round(SAFE_MIN * k), round(SAFE_MAX * k), round(SAFE_MAX * k)))
    # Rounded corners (desktop icon shape), drawn at the supersampled size.
    mask = Image.new("L", safe.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, safe.width - 1, safe.height - 1), radius=safe.width // 5, fill=255)
    safe.putalpha(Image.fromarray(np.minimum(np.asarray(safe.getchannel("A")), np.asarray(mask))))
    icon = safe.resize((SIZE, SIZE), Image.LANCZOS)

    icon.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "komikku.png"), optimize=True)
    icon.save(os.path.join(ROOT, "komikku.ico"), sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
    icon.save(os.path.join(ROOT, "komikku.icns"))
    print("icons written to", ROOT)


if __name__ == "__main__":
    main()
