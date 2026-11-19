#!/usr/bin/env python3
"""Generates the launcher PNGs (API 24-25), the 512px Play Store icon and the 1024x500 feature graphic.
Own artwork only (a stack of three gold ingots on deep navy). Requires Pillow."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store")
NAVY = (10, 20, 40, 255)
GOLD, GOLD_DEEP, GOLD_LIGHT, GOLD_TOP = (212, 175, 55, 255), (184, 134, 11, 255), (242, 204, 90, 255), (255, 229, 138, 255)

# Same geometry as drawable/ic_launcher_foreground.xml (108 x 108 viewport)
POLYS = [
    (GOLD_DEEP, [(26, 72), (34, 54), (56, 54), (50, 72)]),
    (GOLD_LIGHT, [(34, 54), (40, 45), (59, 45), (56, 54)]),
    (GOLD_DEEP, [(52, 72), (58, 54), (82, 54), (78, 72)]),
    (GOLD_LIGHT, [(58, 54), (64, 45), (82, 45), (82, 54)]),
    (GOLD, [(38, 50), (44, 32), (68, 32), (62, 50)]),
    (GOLD_TOP, [(44, 32), (48, 24), (67, 24), (68, 32)]),
]


def draw_ingots(draw, size, scale=1.0):
    # the artwork spans x:26..82, y:24..72 inside 108 -> centre and enlarge for the full-bleed icon
    cx, cy = 54, 48
    s = size / 108 * scale
    for color, pts in POLYS:
        draw.polygon([(size / 2 + (x - cx) * s, size / 2 + (y - cy) * s) for x, y in pts], fill=color)


def icon(size, round_mask=False, rounded=True):
    ss = 4
    big = size * ss
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    radius = big // 2 if round_mask else (int(big * 0.22) if rounded else 0)
    d.rounded_rectangle([0, 0, big - 1, big - 1], radius=radius, fill=NAVY)
    draw_ingots(d, big, scale=1.45)
    return img.resize((size, size), Image.LANCZOS)


def main():
    for folder, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        out = os.path.join(RES, f"mipmap-{folder}")
        os.makedirs(out, exist_ok=True)
        icon(px).save(os.path.join(out, "ic_launcher.png"))
        icon(px, round_mask=True).save(os.path.join(out, "ic_launcher_round.png"))
    os.makedirs(STORE, exist_ok=True)
    icon(512, rounded=False).save(os.path.join(STORE, "play_store_icon_512.png"))  # Play applies its own mask

    # feature graphic 1024x500
    fg = Image.new("RGBA", (1024, 500), NAVY)
    d = ImageDraw.Draw(fg)
    for y in range(500):  # subtle vertical gradient
        shade = int(12 + 22 * (y / 500))
        d.line([(0, y), (1024, y)], fill=(10, 20 + shade // 2, 40 + shade, 255))
    ingots = Image.new("RGBA", (420, 420), (0, 0, 0, 0))
    draw_ingots(ImageDraw.Draw(ingots), 420, scale=1.5)
    fg.alpha_composite(ingots, (40, 40))
    bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    reg = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    d.text((440, 175), "Gold Calculateur", font=ImageFont.truetype(bold, 56), fill=GOLD_LIGHT)
    d.text((444, 255), "Karat adjustment calculator", font=ImageFont.truetype(reg, 28), fill=(245, 241, 230, 255))
    fg.convert("RGB").save(os.path.join(STORE, "feature_graphic_1024x500.png"))


if __name__ == "__main__":
    main()
